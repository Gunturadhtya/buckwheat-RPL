package com.danilkinkin.buckwheat.di

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.danilkinkin.buckwheat.budgetDataStore
import com.danilkinkin.buckwheat.data.ExtendCurrency
import com.danilkinkin.buckwheat.data.dao.BudgetProfileDao
import com.danilkinkin.buckwheat.data.entities.BudgetProfile
import com.danilkinkin.buckwheat.util.DAY
import com.danilkinkin.buckwheat.util.roundToDay
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.util.Date
import javax.inject.Inject

/** DataStore key that holds the uid of the currently active [BudgetProfile]. */
val activeBudgetProfileIdKey = intPreferencesKey("activeBudgetProfileId")

class MultiBudgetRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val budgetProfileDao: BudgetProfileDao,
    private val getCurrentDateUseCase: GetCurrentDateUseCase,
) {

    // ── Observables ──────────────────────────────────────────────────────────

    fun getAllProfiles() = budgetProfileDao.getAll()

    fun getActiveProfileId() = context.budgetDataStore.data.map {
        it[activeBudgetProfileIdKey]
    }

    // ── Initialization ───────────────────────────────────────────────────────

    /**
     * Ensures there is always at least one [BudgetProfile] row that mirrors
     * the budget data already stored in [budgetDataStore].
     *
     * **Why this is needed:** Before multi-budget support was added, budget
     * data lived exclusively in [budgetDataStore] with no corresponding
     * [BudgetProfile] row. When a user with an existing budget adds a *new*
     * budget for the first time, [persistCurrentStateToActiveProfile] is
     * called first — but it exits early because [activeBudgetProfileIdKey]
     * is `null`. The new profile then overwrites DataStore, losing the
     * original budget entirely.
     *
     * Call this once at startup (e.g. from [MultiBudgetViewModel.init]).
     * It is a no-op when a profile already exists or when DataStore holds
     * no meaningful budget yet (i.e. [lastChangeDailyBudgetDate] is null,
     * meaning the user has never configured a budget period).
     */
    suspend fun ensureDefaultProfile() {
        // Already has at least one profile — nothing to do.
        if (budgetProfileDao.count() > 0) return

        val prefs = context.budgetDataStore.data.first()

        // If no budget period has ever been configured, there is nothing
        // worth snapshotting into a profile — let the normal first-run flow
        // (SetBudget screen) handle it.
        val lastChangeDailyBudgetDate = prefs[lastChangeDailyBudgetDateStoreKey] ?: return

        // Snapshot whatever is currently in DataStore into a new profile.
        val uid = budgetProfileDao.insert(
            BudgetProfile(
                name = "Budget 1",
                sortOrder = 0,
                currency = prefs[currencyStoreKey] ?: "",
                budget = prefs[budgetStoreKey] ?: "0.00",
                spent = prefs[spentStoreKey] ?: "0.00",
                dailyBudget = prefs[dailyBudgetStoreKey] ?: "0.00",
                spentFromDailyBudget = prefs[spentFromDailyBudgetStoreKey] ?: "0.00",
                startPeriodDate = prefs[startPeriodDateStoreKey],
                finishPeriodDate = prefs[finishPeriodDateStoreKey],
                finishPeriodActualDate = prefs[finishPeriodActualDateStoreKey],
                lastChangeDailyBudgetDate = lastChangeDailyBudgetDate,
            )
        )

        // Mark it as the active profile so future persist/switch calls work.
        setActiveProfileId(uid.toInt())
    }

    /**
     * Called right after [SpendsRepository.setBudget] writes the very first
     * budget into [budgetDataStore] during onboarding.
     *
     * ensureDefaultProfile() only runs once at app startup, before any budget
     * exists on a fresh install — so it no-ops and defers to the first-run
     * flow, which never calls it again. Without this, `budget_profiles` stays
     * empty forever. This snapshots the current DataStore state into a new
     * profile and activates it. No-op if a profile already exists.
     */
    suspend fun ensureActiveProfileForCurrentBudget() {
        if (budgetProfileDao.count() > 0) return

        val prefs = context.budgetDataStore.data.first()

        val uid = budgetProfileDao.insert(
            BudgetProfile(
                name = "Init Budget",
                sortOrder = 0,
                currency = prefs[currencyStoreKey] ?: "",
                budget = prefs[budgetStoreKey] ?: "0.00",
                spent = prefs[spentStoreKey] ?: "0.00",
                dailyBudget = prefs[dailyBudgetStoreKey] ?: "0.00",
                spentFromDailyBudget = prefs[spentFromDailyBudgetStoreKey] ?: "0.00",
                startPeriodDate = prefs[startPeriodDateStoreKey],
                finishPeriodDate = prefs[finishPeriodDateStoreKey],
                finishPeriodActualDate = prefs[finishPeriodActualDateStoreKey],
                lastChangeDailyBudgetDate = prefs[lastChangeDailyBudgetDateStoreKey],
            )
        )

        setActiveProfileId(uid.toInt())
    }

    // ── Mutations ────────────────────────────────────────────────────────────

    /**
     * Creates a brand-new, empty budget profile with [name] and appends it at
     * the end of the list. Returns the uid of the created profile.
     */
    suspend fun createProfile(name: String): Int {
        val existing = budgetProfileDao.getAllSuspend()
        val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1

        val uid = budgetProfileDao.insert(
            BudgetProfile(
                name = name,
                sortOrder = nextOrder,
                startPeriodDate = roundToDay(getCurrentDateUseCase()).time,
            )
        )
        return uid.toInt()
    }

    /**
     * Renames an existing profile.
     */
    suspend fun renameProfile(uid: Int, newName: String) {
        val profile = budgetProfileDao.getById(uid) ?: return
        budgetProfileDao.update(profile.copy(name = newName))
    }

    /**
     * Deletes a profile. If the deleted profile is currently active, the first
     * remaining profile becomes active (or null if none remain).
     */
    suspend fun deleteProfile(uid: Int) {
        budgetProfileDao.deleteById(uid)

        val activeId = getActiveProfileId().first()
        if (activeId == uid) {
            val remaining = budgetProfileDao.getAllSuspend()
            val newActive = remaining.firstOrNull()?.uid
            setActiveProfileId(newActive)
        }
    }

    /**
     * Switches the active budget profile and mirrors its persisted values back
     * into the shared [budgetDataStore] so that the rest of the app (which reads
     * from [budgetStoreKey], [spentStoreKey], etc.) works without modification.
     */
    suspend fun switchToProfile(uid: Int) {
        val profile = budgetProfileDao.getById(uid) ?: return

        context.budgetDataStore.edit { prefs ->
            prefs[budgetStoreKey] = profile.budget
            prefs[spentStoreKey] = profile.spent
            prefs[dailyBudgetStoreKey] = profile.dailyBudget
            prefs[spentFromDailyBudgetStoreKey] = profile.spentFromDailyBudget
            prefs[currencyStoreKey] = profile.currency

            profile.startPeriodDate?.let { prefs[startPeriodDateStoreKey] = it }
            profile.finishPeriodDate?.let { prefs[finishPeriodDateStoreKey] = it }
                ?: prefs.remove(finishPeriodDateStoreKey)
            profile.finishPeriodActualDate?.let { prefs[finishPeriodActualDateStoreKey] = it }
                ?: prefs.remove(finishPeriodActualDateStoreKey)
            profile.lastChangeDailyBudgetDate?.let { prefs[lastChangeDailyBudgetDateStoreKey] = it }
                ?: prefs.remove(lastChangeDailyBudgetDateStoreKey)
        }

        setActiveProfileId(uid)
    }

    /**
     * Flushes the current DataStore values back into the [BudgetProfile] row so
     * that switching away does not lose unsaved state. Should be called just
     * before [switchToProfile].
     */
    suspend fun persistCurrentStateToActiveProfile() {
        val activeId = getActiveProfileId().first() ?: return
        val profile = budgetProfileDao.getById(activeId) ?: return

        val prefs = context.budgetDataStore.data.first()

        budgetProfileDao.update(
            profile.copy(
                budget = prefs[budgetStoreKey] ?: profile.budget,
                spent = prefs[spentStoreKey] ?: profile.spent,
                dailyBudget = prefs[dailyBudgetStoreKey] ?: profile.dailyBudget,
                spentFromDailyBudget = prefs[spentFromDailyBudgetStoreKey]
                    ?: profile.spentFromDailyBudget,
                currency = prefs[currencyStoreKey] ?: profile.currency,
                startPeriodDate = prefs[startPeriodDateStoreKey],
                finishPeriodDate = prefs[finishPeriodDateStoreKey],
                finishPeriodActualDate = prefs[finishPeriodActualDateStoreKey],
                lastChangeDailyBudgetDate = prefs[lastChangeDailyBudgetDateStoreKey],
            )
        )
    }

    /**
     * Sets the budget for the given profile (mirrors [SpendsRepository.setBudget]).
     */
    suspend fun setBudgetForProfile(
        uid: Int,
        newBudget: BigDecimal,
        newFinishDate: Date,
    ) {
        val profile = budgetProfileDao.getById(uid) ?: return
        val now = getCurrentDateUseCase()

        budgetProfileDao.update(
            profile.copy(
                budget = newBudget.toString(),
                spent = BigDecimal.ZERO.toString(),
                dailyBudget = BigDecimal.ZERO.toString(),
                spentFromDailyBudget = BigDecimal.ZERO.toString(),
                startPeriodDate = roundToDay(now).time,
                finishPeriodDate = Date(roundToDay(newFinishDate).time + DAY - 1000).time,
                finishPeriodActualDate = null,
                lastChangeDailyBudgetDate = roundToDay(now).time,
            )
        )
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private suspend fun setActiveProfileId(uid: Int?) {
        context.budgetDataStore.edit { prefs ->
            if (uid != null) {
                prefs[activeBudgetProfileIdKey] = uid
            } else {
                prefs.remove(activeBudgetProfileIdKey)
            }
        }
    }
}