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