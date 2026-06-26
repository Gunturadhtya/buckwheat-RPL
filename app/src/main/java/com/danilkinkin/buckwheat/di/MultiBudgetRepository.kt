package com.danilkinkin.buckwheat.di

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.danilkinkin.buckwheat.budgetDataStore
import com.danilkinkin.buckwheat.data.ExtendCurrency
import com.danilkinkin.buckwheat.data.RestedBudgetDistributionMethod
import com.danilkinkin.buckwheat.data.dao.BudgetProfileDao
import com.danilkinkin.buckwheat.data.entities.BudgetProfile
import com.danilkinkin.buckwheat.util.DAY
import com.danilkinkin.buckwheat.util.countDays
import com.danilkinkin.buckwheat.util.endOfDay
import com.danilkinkin.buckwheat.util.isToday
import com.danilkinkin.buckwheat.util.roundToDay
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val budgetMutex: BudgetMutex,
) {

    private val profileInitMutex = Mutex()

    // ── Observables ──────────────────────────────────────────────────────────

    fun getAllProfiles() = budgetProfileDao.getAll()

    fun getActiveProfileId() = context.budgetDataStore.data.map {
        it[activeBudgetProfileIdKey]
    }

    // ── Initialization ───────────────────────────────────────────────────────

    suspend fun ensureProfileExists() = profileInitMutex.withLock {
        if (budgetProfileDao.count() > 0) return@withLock

        val prefs = budgetMutex.mutex.withLock {
            context.budgetDataStore.data.first()
        }

        val lastChangeDailyBudgetDate = prefs[lastChangeDailyBudgetDateStoreKey]
            ?: return@withLock

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

        setActiveProfileId(uid.toInt())
    }

    // ── Mutations ────────────────────────────────────────────────────────────

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

    suspend fun renameProfile(uid: Int, newName: String) {
        val profile = budgetProfileDao.getById(uid) ?: return
        budgetProfileDao.update(profile.copy(name = newName))
    }

    suspend fun deleteProfile(uid: Int) {
        budgetProfileDao.deleteById(uid)

        val activeId = getActiveProfileId().first()
        if (activeId == uid) {
            val remaining = budgetProfileDao.getAllSuspend()
            val newActive = remaining.firstOrNull()?.uid
            setActiveProfileId(newActive)
        }
    }

    suspend fun switchToProfile(uid: Int) {
        val profile = budgetProfileDao.getById(uid) ?: return

        val distributionMethod = context.budgetDataStore.data.first()
            .let { prefs ->
                prefs[restedBudgetDistributionMethodStoreKey]?.let {
                    RestedBudgetDistributionMethod.valueOf(it)
                } ?: RestedBudgetDistributionMethod.ASK
            }

        val updatedProfile = applyDayChangeIfNeeded(profile, distributionMethod)
        if (updatedProfile !== profile) {
            budgetProfileDao.update(updatedProfile)
        }

        budgetMutex.mutex.withLock {
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
        }

        setActiveProfileId(uid)
    }

    suspend fun persistCurrentStateToActiveProfile() {
        val activeId = getActiveProfileId().first() ?: return
        val profile = budgetProfileDao.getById(activeId) ?: return

        budgetMutex.mutex.withLock {
            persistProfileUnderLock(profile)
        }
    }

    internal suspend fun persistCurrentStateUnderLock(activeId: Int) {
        val profile = budgetProfileDao.getById(activeId) ?: return
        // budgetMutex is already held — read directly without re-locking.
        val prefs = context.budgetDataStore.data.first()
        persistPrefsToProfile(profile, prefs)
    }

    // ── Package-private DB helper ─────────────────────────────────────────────

    private suspend fun persistProfileUnderLock(profile: BudgetProfile) {
        val prefs = context.budgetDataStore.data.first()
        persistPrefsToProfile(profile, prefs)
    }

    private suspend fun persistPrefsToProfile(profile: BudgetProfile, prefs: Preferences) {
        budgetProfileDao.update(
            profile.copy(
                budget = prefs[budgetStoreKey] ?: profile.budget,
                spent  = prefs[spentStoreKey]  ?: profile.spent,
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
                finishPeriodDate = endOfDay(newFinishDate).time,
                finishPeriodActualDate = null,
                lastChangeDailyBudgetDate = roundToDay(now).time,
            )
        )
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun applyDayChangeIfNeeded(
        profile: BudgetProfile,
        distributionMethod: RestedBudgetDistributionMethod,
    ): BudgetProfile {
        val lastChangeDailyBudgetDate = profile.lastChangeDailyBudgetDate
            ?.let { Date(it) } ?: return profile

        if (isToday(lastChangeDailyBudgetDate)) return profile

        val finishPeriodDate = profile.finishPeriodDate
            ?.let { Date(it) } ?: return profile

        val finishPeriodActualDate = profile.finishPeriodActualDate?.let { Date(it) }

        val now = getCurrentDateUseCase()

        val finishDayNotReached = if (finishPeriodActualDate == null) {
            countDays(finishPeriodDate, now) > 0
        } else {
            countDays(finishPeriodActualDate, now) > 0
        }

        if (!finishDayNotReached) return profile

        val budget = profile.budget.toBigDecimalOrNull() ?: return profile
        val spent = profile.spent.toBigDecimalOrNull() ?: return profile
        val dailyBudget = profile.dailyBudget.toBigDecimalOrNull() ?: return profile
        val spentFromDailyBudget = profile.spentFromDailyBudget.toBigDecimalOrNull() ?: return profile

        val restDays = countDays(finishPeriodDate, now).coerceAtLeast(1)
        val restBudget = budget - spent - spentFromDailyBudget

        val newDailyBudget = restBudget
            .divide(restDays.toBigDecimal(), 2, RoundingMode.HALF_EVEN)

        val hasLeftover = dailyBudget - spentFromDailyBudget > BigDecimal.ZERO

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


        return profile.copy(
            spent = (spent + spentFromDailyBudget).toString(),
            spentFromDailyBudget = BigDecimal.ZERO.toString(),
            dailyBudget = chosenDailyBudget.toString(),
            lastChangeDailyBudgetDate = roundToDay(now).time,
        )
    }

    private suspend fun setActiveProfileId(uid: Int?) {
        budgetMutex.mutex.withLock {
            context.budgetDataStore.edit { prefs ->
                if (uid != null) {
                    prefs[activeBudgetProfileIdKey] = uid
                } else {
                    prefs.remove(activeBudgetProfileIdKey)
                }
            }
        }
    }
}