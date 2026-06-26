package com.danilkinkin.buckwheat.di

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.danilkinkin.buckwheat.budgetDataStore
import com.danilkinkin.buckwheat.data.ExtendCurrency
import com.danilkinkin.buckwheat.data.RestedBudgetDistributionMethod
import com.danilkinkin.buckwheat.data.dao.BudgetProfileDao
import com.danilkinkin.buckwheat.data.entities.BudgetProfile
import com.danilkinkin.buckwheat.util.DAY
import com.danilkinkin.buckwheat.util.countDays
import com.danilkinkin.buckwheat.util.isToday
import com.danilkinkin.buckwheat.util.roundToDay
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.lang.Long.min
import java.math.BigDecimal
import java.math.RoundingMode
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
     *
     * Before loading the profile into DataStore, [applyDayChangeIfNeeded] is
     * called so that each profile's own leftover redistribution is applied
     * against its own dates — not against whichever dates happen to be in
     * DataStore at the time of the switch.
     */
    suspend fun switchToProfile(uid: Int) {
        val profile = budgetProfileDao.getById(uid) ?: return

        // Read the redistribution preference from DataStore (it is global, not
        // per-profile, so reading it here is correct).
        val distributionMethod = context.budgetDataStore.data.first()
            .let { prefs ->
                prefs[restedBudgetDistributionMethodStoreKey]?.let {
                    RestedBudgetDistributionMethod.valueOf(it)
                } ?: RestedBudgetDistributionMethod.ASK
            }

        // Apply any pending day-change redistribution for this specific profile
        // using its own stored dates and amounts, then persist the result to DB
        // so the profile row is up-to-date before we load it into DataStore.
        val updatedProfile = applyDayChangeIfNeeded(profile, distributionMethod)
        if (updatedProfile !== profile) {
            budgetProfileDao.update(updatedProfile)
        }

        context.budgetDataStore.edit { prefs ->
            prefs[budgetStoreKey] = updatedProfile.budget
            prefs[spentStoreKey] = updatedProfile.spent
            prefs[dailyBudgetStoreKey] = updatedProfile.dailyBudget
            prefs[spentFromDailyBudgetStoreKey] = updatedProfile.spentFromDailyBudget
            prefs[currencyStoreKey] = updatedProfile.currency

            updatedProfile.startPeriodDate?.let { prefs[startPeriodDateStoreKey] = it }
            updatedProfile.finishPeriodDate?.let { prefs[finishPeriodDateStoreKey] = it }
                ?: prefs.remove(finishPeriodDateStoreKey)
            updatedProfile.finishPeriodActualDate?.let { prefs[finishPeriodActualDateStoreKey] = it }
                ?: prefs.remove(finishPeriodActualDateStoreKey)
            updatedProfile.lastChangeDailyBudgetDate?.let { prefs[lastChangeDailyBudgetDateStoreKey] = it }
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

    /**
     * Applies the day-change redistribution logic directly against a
     * [BudgetProfile]'s own stored values, without touching DataStore.
     *
     * This mirrors the redistribution logic in [SpendsViewModel.runChangeDayAction]
     * but operates on a standalone [BudgetProfile] rather than on whatever
     * happens to be loaded in DataStore at the moment. This is the correct
     * approach for inactive profiles: each profile tracks its own
     * [BudgetProfile.lastChangeDailyBudgetDate] and must be redistributed
     * against its own period dates, not against the dates of whichever profile
     * was last active.
     *
     * Returns an updated copy of [profile] if redistribution was applied, or
     * the original [profile] unchanged if no redistribution was needed (e.g.
     * the profile was already up-to-date today, the period has ended, or no
     * budget period is configured).
     *
     * The [distributionMethod] is a user-level preference (global, not per-profile).
     * When it is [RestedBudgetDistributionMethod.ASK], we fall back to
     * [RestedBudgetDistributionMethod.REST] here — the "ask" dialog only makes
     * sense for the currently active profile, where the UI can show a prompt.
     * For an inactive profile that catches up silently on switch, redistributing
     * the remainder evenly (REST) is the safest assumption; the user will see
     * the result when the profile becomes active.
     */
    private fun applyDayChangeIfNeeded(
        profile: BudgetProfile,
        distributionMethod: RestedBudgetDistributionMethod,
    ): BudgetProfile {
        val lastChangeDailyBudgetDate = profile.lastChangeDailyBudgetDate
            ?.let { Date(it) } ?: return profile   // no budget period configured

        // Already redistributed today — nothing to do.
        if (isToday(lastChangeDailyBudgetDate)) return profile

        val finishPeriodDate = profile.finishPeriodDate
            ?.let { Date(it) } ?: return profile   // no finish date — skip

        val finishPeriodActualDate = profile.finishPeriodActualDate?.let { Date(it) }

        val now = getCurrentDateUseCase()

        val finishDayNotReached = if (finishPeriodActualDate == null) {
            countDays(finishPeriodDate, now) > 0
        } else {
            countDays(finishPeriodActualDate, now) > 0
        }

        // Period is already over — no redistribution needed.
        if (!finishDayNotReached) return profile

        val budget = profile.budget.toBigDecimalOrNull() ?: return profile
        val spent = profile.spent.toBigDecimalOrNull() ?: return profile
        val dailyBudget = profile.dailyBudget.toBigDecimalOrNull() ?: return profile
        val spentFromDailyBudget = profile.spentFromDailyBudget.toBigDecimalOrNull() ?: return profile

        val restDays = countDays(finishPeriodDate, now).coerceAtLeast(1)
        val restBudget = budget - spent - spentFromDailyBudget

        // Compute the new daily budget using the same formula as
        // SpendsRepository.whatBudgetForDay(applyTodaySpends = true).
        val newDailyBudget = restBudget
            .divide(restDays.toBigDecimal(), 2, RoundingMode.HALF_EVEN)

        val hasLeftover = dailyBudget - spentFromDailyBudget > BigDecimal.ZERO

        // When the user prefers ADD_TODAY (carry leftover forward as a bonus),
        // mirror SpendsRepository.howMuchNotSpent(excludeSkippedPart = true).
        // For ASK, fall back to REST — silent catch-up cannot show a dialog.
        val resolvedDistribution = if (distributionMethod == RestedBudgetDistributionMethod.ASK) {
            RestedBudgetDistributionMethod.REST
        } else {
            distributionMethod
        }

        val chosenDailyBudget = if (hasLeftover && resolvedDistribution == RestedBudgetDistributionMethod.ADD_TODAY) {
            val skippedDays = countDays(
                Date(min(now.time, finishPeriodDate.time)),
                lastChangeDailyBudgetDate
            ) - 1

            // howMuchNotSpent(excludeSkippedPart = true) — accumulated leftover
            val accumulatedLeftover = if (restDays == 0) {
                restBudget
            } else {
                restBudget
                    .minus(dailyBudget * skippedDays.toBigDecimal())
                    .divide((restDays).coerceAtLeast(1).toBigDecimal(), 2, RoundingMode.HALF_EVEN)
                    .multiply(skippedDays.coerceAtLeast(0).toBigDecimal())
                    .plus(dailyBudget - spentFromDailyBudget)
            }
            accumulatedLeftover
        } else {
            newDailyBudget
        }

        Log.d(
            "MultiBudgetRepository",
            "applyDayChangeIfNeeded [profile=${profile.uid} \"${profile.name}\" " +
                    "lastChangeDailyBudgetDate=$lastChangeDailyBudgetDate " +
                    "restDays=$restDays " +
                    "restBudget=$restBudget " +
                    "dailyBudget=$dailyBudget " +
                    "chosenDailyBudget=$chosenDailyBudget " +
                    "distributionMethod=$resolvedDistribution]"
        )

        // Commit the previous day's spend into `spent`, reset `spentFromDailyBudget`,
        // set the new daily budget, and advance `lastChangeDailyBudgetDate` to today.
        // This mirrors what SpendsRepository.setDailyBudget() does in DataStore.
        return profile.copy(
            spent = (spent + spentFromDailyBudget).toString(),
            spentFromDailyBudget = BigDecimal.ZERO.toString(),
            dailyBudget = chosenDailyBudget.toString(),
            lastChangeDailyBudgetDate = roundToDay(now).time,
        )
    }

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