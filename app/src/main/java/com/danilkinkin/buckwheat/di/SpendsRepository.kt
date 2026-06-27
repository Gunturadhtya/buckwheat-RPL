package com.danilkinkin.buckwheat.di

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.LiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import com.danilkinkin.buckwheat.budgetDataStore
import com.danilkinkin.buckwheat.data.RestedBudgetDistributionMethod
import com.danilkinkin.buckwheat.data.BudgetTransactionLabel
import com.danilkinkin.buckwheat.data.entities.Transaction
import com.danilkinkin.buckwheat.util.DAY
import com.danilkinkin.buckwheat.data.ExtendCurrency
import com.danilkinkin.buckwheat.data.dao.BudgetProfileDao
import com.danilkinkin.buckwheat.data.dao.TransactionDao
import com.danilkinkin.buckwheat.data.entities.TransactionType
import com.danilkinkin.buckwheat.errorForReport
import com.danilkinkin.buckwheat.util.countDays
import com.danilkinkin.buckwheat.util.endOfDay
import com.danilkinkin.buckwheat.util.isSameDay
import com.danilkinkin.buckwheat.util.roundToDay
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import java.lang.Long.min
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Date
import javax.inject.Inject

val currencyStoreKey = stringPreferencesKey("currency")
val restedBudgetDistributionMethodStoreKey = stringPreferencesKey("restedBudgetDistributionMethod")
val hideOverspendingWarnStoreKey = booleanPreferencesKey("hideOverspendingWarn")

val budgetStoreKey = stringPreferencesKey("budget")
val spentStoreKey = stringPreferencesKey("spent")
val dailyBudgetStoreKey = stringPreferencesKey("dailyBudget")
val spentFromDailyBudgetStoreKey = stringPreferencesKey("spentFromDailyBudget")
val lastChangeDailyBudgetDateStoreKey = longPreferencesKey("lastChangeDailyBudgetDate")
val startPeriodDateStoreKey = longPreferencesKey("startPeriodDate")
val finishPeriodDateStoreKey = longPreferencesKey("finishPeriodDate")
val finishPeriodActualDateStoreKey = longPreferencesKey("finishPeriodActualDate")

class SpendsRepository @Inject constructor(
    @ApplicationContext val context: Context,
    private val transactionDao: TransactionDao,
    private val getCurrentDateUseCase: GetCurrentDateUseCase,
    private val multiBudgetRepository: MultiBudgetRepository,
    private val budgetMutex: BudgetMutex,
    private val budgetProfileDao: BudgetProfileDao
) {
    // ── Profile ID helper ────────────────────────────────────────────────────

    private suspend fun activeProfileId(): Int =
        multiBudgetRepository.getActiveProfileId().first() ?: 0

    // ── Observables (profile-scoped) ─────────────────────────────────────────

    fun getAllTransactions(): LiveData<List<Transaction>> {
        return multiBudgetRepository.getActiveProfileId().asLiveData().switchMap { profileId ->
            transactionDao.getAll(profileId ?: 0)
        }
    }

    fun getAllSpends(): LiveData<List<Transaction>> {
        return multiBudgetRepository.getActiveProfileId().asLiveData().switchMap { profileId ->
            transactionDao.getAll(TransactionType.SPENT, profileId ?: 0)
        }
    }

    fun getAllTags(): LiveData<List<String>> = getAllTransactions().map { transactions ->
        transactions
            .asSequence()
            .filter { transaction -> transaction.comment.isNotEmpty() }
            .groupBy { it.comment }
            .map { it.key to it.value.size }
            .sortedBy { -it.second }
            .map { it.first }
            .distinct()
            .toList()
    }

    fun getBudget() = context.budgetDataStore.data.map {
        (it[budgetStoreKey]?.toBigDecimal() ?: BigDecimal.ZERO).setScale(2)
    }

    fun getSpent() = context.budgetDataStore.data.map {
        (it[spentStoreKey]?.toBigDecimal() ?: BigDecimal.ZERO).setScale(2)
    }

    fun getDailyBudget() = context.budgetDataStore.data.map {
        (it[dailyBudgetStoreKey]?.toBigDecimal() ?: BigDecimal.ZERO).setScale(2)
    }

    fun getSpentFromDailyBudget() = context.budgetDataStore.data.map {
        (it[spentFromDailyBudgetStoreKey]?.toBigDecimal() ?: BigDecimal.ZERO).setScale(2)
    }

    fun getStartPeriodDate() = context.budgetDataStore.data.map {
        it[startPeriodDateStoreKey]?.let { value -> Date(value) } ?: getCurrentDateUseCase()
    }

    fun getFinishPeriodDate() = context.budgetDataStore.data.map {
        it[finishPeriodDateStoreKey]?.let { value -> Date(value) }
    }

    fun getFinishPeriodActualDate() = context.budgetDataStore.data.map {
        it[finishPeriodActualDateStoreKey]?.let { value -> Date(value) }
    }

    fun getLastChangeDailyBudgetDate() = context.budgetDataStore.data.map {
        it[lastChangeDailyBudgetDateStoreKey]?.let { value -> Date(value) }
    }

    fun getCurrency() = context.budgetDataStore.data.map {
        it[currencyStoreKey]?.let { value ->
            ExtendCurrency.getInstance(value)
        } ?: ExtendCurrency(value = null, type = ExtendCurrency.Type.NONE)
    }

    fun getRestedBudgetDistributionMethod() = context.budgetDataStore.data.map { it ->
        it[restedBudgetDistributionMethodStoreKey]?.let {
            RestedBudgetDistributionMethod.valueOf(it)
        } ?: RestedBudgetDistributionMethod.ASK
    }

    fun getHideOverspendingWarn() = context.budgetDataStore.data.map {
        it[hideOverspendingWarnStoreKey] ?: false
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    suspend fun changeDisplayCurrency(currency: ExtendCurrency) {
        budgetMutex.mutex.withLock {
            context.budgetDataStore.edit {
                it[currencyStoreKey] = currency.value ?: ""
            }
        }
    }

    suspend fun changeRestedBudgetDistributionMethod(method: RestedBudgetDistributionMethod) {
        budgetMutex.mutex.withLock {
            context.budgetDataStore.edit {
                it[restedBudgetDistributionMethodStoreKey] = method.toString()
            }
        }
    }

    suspend fun hideOverspendingWarn(hide: Boolean) {
        budgetMutex.mutex.withLock {
            context.budgetDataStore.edit {
                it[hideOverspendingWarnStoreKey] = hide
            }
        }
    }

    suspend fun setBudget(newBudget: BigDecimal, newFinishDate: Date) {
        val profileId = activeProfileId()

        budgetMutex.mutex.withLock {
            context.budgetDataStore.edit {
                it[budgetStoreKey] = newBudget.toString()
                it[spentStoreKey] = BigDecimal.ZERO.toString()
                it[dailyBudgetStoreKey] = BigDecimal.ZERO.toString()
                it[spentFromDailyBudgetStoreKey] = BigDecimal.ZERO.toString()
                it[lastChangeDailyBudgetDateStoreKey] = roundToDay(getCurrentDateUseCase()).time
                it[startPeriodDateStoreKey] = roundToDay(getCurrentDateUseCase()).time
                it[finishPeriodDateStoreKey] = endOfDay(newFinishDate).time
                it.remove(finishPeriodActualDateStoreKey)

                Log.d(
                    "SpendsRepository",
                    "Set budget ["
                            + "budget: ${it[budgetStoreKey]} "
                            + "start date: ${Date(it[startPeriodDateStoreKey]!!)} "
                            + "finish date: ${Date(it[finishPeriodDateStoreKey]!!)}"
                            + "]"
                )
            }
        }

        // Delete only this profile's transactions, not every profile's.
        transactionDao.deleteByProfile(profileId)
        transactionDao.insert(
            Transaction(
                type = TransactionType.INCOME,
                value = newBudget,
                date = getCurrentDateUseCase(),
                budgetProfileId = profileId,
            )
        )

        setDailyBudget(whatBudgetForDay())

        hideOverspendingWarn(false)

        multiBudgetRepository.ensureProfileExists()
    }

    suspend fun changeBudget(newBudget: BigDecimal, newFinishDate: Date) {
        try {
            val profileId = activeProfileId()
            val currentBudget = getBudget().first()

            budgetMutex.mutex.withLock {
                context.budgetDataStore.edit {
                    it[budgetStoreKey] = newBudget.toString()
                    it[lastChangeDailyBudgetDateStoreKey] = roundToDay(getCurrentDateUseCase()).time
                    it[finishPeriodDateStoreKey] = endOfDay(newFinishDate).time
                    it.remove(finishPeriodActualDateStoreKey)

                    Log.d(
                        "SpendsRepository",
                        "Change budget ["
                                + "budget: ${it[budgetStoreKey]} "
                                + "start date: ${Date(it[startPeriodDateStoreKey]!!)} "
                                + "finish date: ${Date(it[finishPeriodDateStoreKey]!!)}"
                                + "]"
                    )
                }
            }

            val adjustment = newBudget - currentBudget

            if (adjustment > BigDecimal.ZERO) {
                transactionDao.insert(
                    Transaction(
                        type = TransactionType.INCOME,
                        value = adjustment,
                        date = getCurrentDateUseCase(),
                        comment = BudgetTransactionLabel.ADJUSTMENT,
                        budgetProfileId = profileId,
                    )
                )
            } else if (adjustment < BigDecimal.ZERO) {
                transactionDao.insert(
                    Transaction(
                        type = TransactionType.SPENT,
                        value = adjustment.abs(),
                        date = getCurrentDateUseCase(),
                        comment = BudgetTransactionLabel.ADJUSTMENT,
                        budgetProfileId = profileId,
                    )
                )
            }

            updateDailyBudget(whatBudgetForDay())
        } catch (e: Exception) {
            Log.e("SpendsRepository", "changeBudget failed", e)
            context.errorForReport = e.stackTraceToString()
        }
    }

    suspend fun addMoneyToBudget(amount: BigDecimal) {
        try {
            require(amount > BigDecimal.ZERO) { "Top up amount must be greater than zero" }

            val profileId = activeProfileId()
            val currentBudget = getBudget().first()

            transactionDao.insert(
                Transaction(
                    type = TransactionType.INCOME,
                    value = amount,
                    date = getCurrentDateUseCase(),
                    comment = BudgetTransactionLabel.TOP_UP,
                    budgetProfileId = profileId,
                )
            )

            budgetMutex.mutex.withLock {
                context.budgetDataStore.edit {
                    it[budgetStoreKey] = (currentBudget + amount).toString()

                    Log.d(
                        "SpendsRepository",
                        "Add money to budget ["
                                + "amount: $amount "
                                + "new budget: ${it[budgetStoreKey]} "
                                + "]"
                    )
                }
            }

            updateDailyBudget(whatBudgetForDay())
        } catch (e: Exception) {
            Log.e("SpendsRepository", "addMoneyToBudget failed", e)
            context.errorForReport = e.stackTraceToString()
            throw e
        }
    }

    suspend fun finishBudget(finishDate: Date) {
        val activeId = multiBudgetRepository.getActiveProfileId().first() ?: return

        budgetMutex.mutex.withLock {
            context.budgetDataStore.edit {
                it[finishPeriodActualDateStoreKey] = finishDate.time

                Log.d(
                    "SpendsRepository",
                    "Finish budget ["
                            + "budget: ${it[budgetStoreKey]} "
                            + "start date: ${Date(it[startPeriodDateStoreKey]!!)} "
                            + "actual finish date: ${Date(it[finishPeriodActualDateStoreKey]!!)}"
                            + "finish date: ${Date(it[finishPeriodDateStoreKey]!!)}"
                            + "]"
                )
            }

            multiBudgetRepository.persistCurrentStateUnderLock(activeId)
        }
    }

    suspend fun updateDailyBudget(newDailyBudget: BigDecimal) {
        budgetMutex.mutex.withLock {
            context.budgetDataStore.edit {
                it[dailyBudgetStoreKey] = newDailyBudget.toString()
                it[lastChangeDailyBudgetDateStoreKey] = roundToDay(getCurrentDateUseCase()).time

                Log.d(
                    "SpendsRepository",
                    "Update daily budget ["
                            + "daily budget: ${it[dailyBudgetStoreKey]} "
                            + "spent: ${it[spentStoreKey]}"
                            + "]"
                )
            }
        }

        val profileId = activeProfileId()
        val dailyBudgetTransactions = transactionDao
            .getAll(TransactionType.SET_DAILY_BUDGET, profileId)
            .asFlow()
            .first()

        val latestTransaction = dailyBudgetTransactions.lastOrNull()

        if (latestTransaction != null) {
            transactionDao.update(latestTransaction.copy(value = newDailyBudget))
        } else {
            transactionDao.insert(
                Transaction(
                    type = TransactionType.SET_DAILY_BUDGET,
                    value = newDailyBudget,
                    date = getCurrentDateUseCase(),
                    budgetProfileId = profileId,
                )
            )
        }
    }

    suspend fun setDailyBudget(newDailyBudget: BigDecimal) {
        val profileId = activeProfileId()

        budgetMutex.mutex.withLock {
            context.budgetDataStore.edit {
                val spent: BigDecimal = it[spentStoreKey]?.toBigDecimal()!!
                val spentFromDailyBudget: BigDecimal =
                    it[spentFromDailyBudgetStoreKey]?.toBigDecimal()!!

                it[dailyBudgetStoreKey] = newDailyBudget.toString()
                it[spentStoreKey] = (spent + spentFromDailyBudget).toString()
                it[lastChangeDailyBudgetDateStoreKey] = roundToDay(getCurrentDateUseCase()).time
                it[spentFromDailyBudgetStoreKey] = BigDecimal.ZERO.toString()

                Log.d(
                    "SpendsRepository",
                    "Set daily budget ["
                            + "daily budget: ${it[dailyBudgetStoreKey]} "
                            + "spent: ${it[spentStoreKey]}"
                            + "]"
                )
            }
        }

        transactionDao.insert(
            Transaction(
                type = TransactionType.SET_DAILY_BUDGET,
                value = newDailyBudget,
                date = getCurrentDateUseCase(),
                budgetProfileId = profileId,
            )
        )
    }

    suspend fun whatBudgetForDay(
        excludeCurrentDay: Boolean = false,
        applyTodaySpends: Boolean = false,
        notCommittedSpent: BigDecimal = BigDecimal.ZERO
    ): BigDecimal {
        val budget = getBudget().first()
        val spent = getSpent().first()
        val dailyBudget = getDailyBudget().first()
        val spentFromDailyBudget = getSpentFromDailyBudget().first()
        val finishPeriodDate =
            getFinishPeriodDate().first() ?: throw Exception("Finish period date is null")

        val restDays =
            countDays(finishPeriodDate, getCurrentDateUseCase()) - if (excludeCurrentDay) 1 else 0
        var restBudget = budget - spent

        restBudget -= notCommittedSpent

        if (applyTodaySpends) {
            restBudget -= spentFromDailyBudget
        } else if (excludeCurrentDay) {
            restBudget -= dailyBudget
        }

        val whatBudgetForDay = restBudget
            .divide(
                restDays.toBigDecimal().coerceAtLeast(BigDecimal(1)),
                2,
                RoundingMode.HALF_EVEN
            )

        Log.d(
            "SpendsRepository",
            "Check what budget for day ["
                    + "date: ${getCurrentDateUseCase()} "
                    + "what budget for day: $whatBudgetForDay "
                    + "excludeCurrentDay: $excludeCurrentDay "
                    + "applyTodaySpends: $applyTodaySpends "
                    + "notCommittedSpent: $notCommittedSpent "
                    + "budget: $budget "
                    + "spent: $spent "
                    + "daily budget: $dailyBudget "
                    + "spent from daily budget: $spentFromDailyBudget "
                    + "rest budget: $restBudget "
                    + "rest days: $restDays"
                    + "]"
        )

        return whatBudgetForDay
    }

    suspend fun howMuchBudgetRest(): BigDecimal {
        val budget = getBudget().first()
        val spent = getSpent().first()
        val spentFromDailyBudget = getSpentFromDailyBudget().first()

        return budget - spent - spentFromDailyBudget
    }

    suspend fun howMuchNotSpent(
        excludeSkippedPart: Boolean = false,
    ): BigDecimal {
        val budget = getBudget().first()
        val spent = getSpent().first()
        val dailyBudget = getDailyBudget().first()
        val spentFromDailyBudget = getSpentFromDailyBudget().first()
        val finishPeriodDate =
            getFinishPeriodDate().first() ?: throw Exception("Finish period date is null")
        val lastChangeDailyBudgetDate =
            getLastChangeDailyBudgetDate().first() ?: getStartPeriodDate().first()

        val restDays = countDays(finishPeriodDate, getCurrentDateUseCase()).coerceAtLeast(0)
        val skippedDays = countDays(
            Date(min(getCurrentDateUseCase().time, finishPeriodDate.time)),
            lastChangeDailyBudgetDate
        ) - 1

        var restBudget = budget - spent

        val howMuchNotSpent = if (restDays == 0) {
            restBudget - spentFromDailyBudget
        } else if (excludeSkippedPart) {
            restBudget
                .minus(dailyBudget * skippedDays.toBigDecimal())
                .divide(
                    (restDays).coerceAtLeast(1).toBigDecimal(),
                    2,
                    RoundingMode.HALF_EVEN,
                )
                .multiply((skippedDays).coerceAtLeast(0).toBigDecimal())
                .plus(dailyBudget - spentFromDailyBudget)
        } else {
            restBudget
                .minus(dailyBudget)
                .divide(
                    (restDays + skippedDays - 1).coerceAtLeast(1).toBigDecimal(),
                    2,
                    RoundingMode.HALF_EVEN,
                )
                .multiply((skippedDays).coerceAtLeast(0).toBigDecimal())
                .plus(dailyBudget - spentFromDailyBudget)
        }

        Log.d(
            "SpendsRepository",
            "How much not spent check ["
                    + "how much not spent: $howMuchNotSpent "
                    + "rest budget: $restBudget "
                    + "restDays: $restDays "
                    + "skippedDays: $skippedDays "
                    + "lastChangeDailyBudgetDate: $lastChangeDailyBudgetDate "
                    + "getCurrentDateUseCase: ${getCurrentDateUseCase()} "
                    + "dailyBudget: $dailyBudget "
                    + "spentFromDailyBudget: $spentFromDailyBudget "
                    + "]"
        )

        return howMuchNotSpent
    }

    suspend fun nextDayBudget(
        excludeSkippedPart: Boolean = false,
    ): BigDecimal {
        val budget = getBudget().first()
        val spent = getSpent().first()
        val dailyBudget = getDailyBudget().first()
        val spentFromDailyBudget = getSpentFromDailyBudget().first()
        val finishPeriodDate =
            getFinishPeriodDate().first() ?: throw Exception("Finish period date is null")
        val lastChangeDailyBudgetDate =
            getLastChangeDailyBudgetDate().first() ?: getStartPeriodDate().first()

        val restDays = countDays(finishPeriodDate, getCurrentDateUseCase()).coerceAtLeast(0)
        val skippedDays = countDays(
            Date(min(getCurrentDateUseCase().time, finishPeriodDate.time)),
            lastChangeDailyBudgetDate
        ) - 1

        var restBudget = budget - spent

        val nextDailyBudget = if (restDays == 0) {
            restBudget - spentFromDailyBudget
        } else if (excludeSkippedPart) {
            restBudget
                .minus(dailyBudget * skippedDays.toBigDecimal())
                .divide(
                    (restDays).coerceAtLeast(1).toBigDecimal(),
                    2,
                    RoundingMode.HALF_EVEN,
                )
        } else {
            restBudget
                .minus(dailyBudget)
                .divide(
                    (restDays + skippedDays - 1).coerceAtLeast(1).toBigDecimal(),
                    2,
                    RoundingMode.HALF_EVEN,
                )
        }

        Log.d(
            "SpendsRepository",
            "Next day budget ["
                    + "next daily budget: $nextDailyBudget "
                    + "rest budget: $restBudget "
                    + "restDays: $restDays "
                    + "skippedDays: $skippedDays "
                    + "lastChangeDailyBudgetDate: $lastChangeDailyBudgetDate "
                    + "getCurrentDateUseCase: ${getCurrentDateUseCase()} "
                    + "dailyBudget: $dailyBudget "
                    + "spentFromDailyBudget: $spentFromDailyBudget "
                    + "]"
        )

        return nextDailyBudget
    }

    suspend fun addSpent(newTransaction: Transaction) {
        val profileId = activeProfileId()
        val stamped = if (newTransaction.budgetProfileId == 0 && profileId != 0) {
            newTransaction.copy(budgetProfileId = profileId)
        } else {
            newTransaction
        }
        this.transactionDao.insert(stamped)

        budgetMutex.mutex.withLock {
            context.budgetDataStore.edit {
                try {
                    if (isSameDay(stamped.date, getCurrentDateUseCase())) {
                        val spentFromDailyBudget =
                            it[spentFromDailyBudgetStoreKey]?.toBigDecimal()!!
                        it[spentFromDailyBudgetStoreKey] =
                            (spentFromDailyBudget + stamped.value).toString()
                    } else {
                        val activeStart = it[startPeriodDateStoreKey]
                        val activeFinish = it[finishPeriodDateStoreKey]
                        val txTime = stamped.date.time

                        val belongsToActiveProfile =
                            activeStart != null &&
                                    activeFinish != null &&
                                    txTime >= activeStart &&
                                    txTime <= activeFinish

                        if (!belongsToActiveProfile) {
                            applySpentToOwningProfile(stamped)
                            return@edit
                        }

                        val finishPeriodDate =
                            it[finishPeriodDateStoreKey]?.let { v -> Date(v) }!!
                        val dailyBudget = it[dailyBudgetStoreKey]?.toBigDecimal()!!
                        val spent = it[spentStoreKey]?.toBigDecimal()!!

                        val spreadDeltaSpentPerRestDays = stamped.value
                            .divide(
                                countDays(finishPeriodDate, getCurrentDateUseCase())
                                    .toBigDecimal(),
                                2,
                                RoundingMode.HALF_EVEN,
                            )

                        it[dailyBudgetStoreKey] =
                            (dailyBudget - spreadDeltaSpentPerRestDays).toString()
                        it[spentStoreKey] = (spent + stamped.value).toString()
                    }
                } catch (e: Exception) {
                    context.errorForReport = e.stackTraceToString()
                }
            }
        }
    }

    suspend fun removeSpent(transactionForRemove: Transaction) {
        this.transactionDao.deleteById(transactionForRemove.uid)

        budgetMutex.mutex.withLock {
            context.budgetDataStore.edit {
                if (isSameDay(transactionForRemove.date, getCurrentDateUseCase())) {
                    val spentFromDailyBudget =
                        it[spentFromDailyBudgetStoreKey]?.toBigDecimal()!!
                    it[spentFromDailyBudgetStoreKey] =
                        (spentFromDailyBudget - transactionForRemove.value).toString()
                } else {
                    val activeStart = it[startPeriodDateStoreKey]
                    val activeFinish = it[finishPeriodDateStoreKey]
                    val txTime = transactionForRemove.date.time

                    val belongsToActiveProfile =
                        activeStart != null &&
                                activeFinish != null &&
                                txTime >= activeStart &&
                                txTime <= activeFinish

                    if (!belongsToActiveProfile) {
                        reverseSpentOnOwningProfile(transactionForRemove)
                        return@edit
                    }

                    val finishPeriodDate =
                        it[finishPeriodDateStoreKey]?.let { v -> Date(v) }!!
                    val dailyBudget = it[dailyBudgetStoreKey]?.toBigDecimal()!!
                    val spent = it[spentStoreKey]?.toBigDecimal()!!
                    val restDays = countDays(finishPeriodDate, getCurrentDateUseCase())
                    val spreadDelta = transactionForRemove.value
                        .divide(restDays.toBigDecimal(), 2, RoundingMode.HALF_EVEN)

                    it[dailyBudgetStoreKey] = (dailyBudget + spreadDelta).toString()
                    it[spentStoreKey] = (spent - transactionForRemove.value).toString()
                }
            }
        }
    }

    private suspend fun reverseSpentOnOwningProfile(transaction: Transaction) {
        val owningProfile = budgetProfileDao.getByDate(transaction.date.time) ?: return
        val profileSpent = owningProfile.spent.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val newSpent = (profileSpent - transaction.value).coerceAtLeast(BigDecimal.ZERO)
        budgetProfileDao.update(owningProfile.copy(spent = newSpent.toString()))
    }

    // ── Migration helper ─────────────────────────────────────────────────────

    suspend fun reassignLegacyTransactions() {
        val unowned = transactionDao.getUnowned()
        if (unowned.isEmpty()) return

        val profileId = activeProfileId()
        if (profileId == 0) {
            Log.w("SpendsRepository", "reassignLegacyTransactions: no active profile yet, skipping")
            return
        }

        Log.d(
            "SpendsRepository",
            "reassignLegacyTransactions: reassigning ${unowned.size} legacy rows to profile $profileId"
        )

        unowned.forEach { tx ->
            transactionDao.update(tx.copy(budgetProfileId = profileId))
        }
    }

    private suspend fun applySpentToOwningProfile(transaction: Transaction) {
        val owningProfile = budgetProfileDao.getByDate(transaction.date.time)

        if (owningProfile == null) {
            Log.w(
                "SpendsRepository",
                "addSpent: no profile owns date ${transaction.date} — " +
                        "transaction recorded but no budget adjusted"
            )
            return
        }

        val profileSpent = owningProfile.spent.toBigDecimalOrNull() ?: BigDecimal.ZERO
        budgetProfileDao.update(
            owningProfile.copy(
                spent = (profileSpent + transaction.value).toString()
            )
        )

        Log.d(
            "SpendsRepository",
            "addSpent: back-dated tx ${transaction.date} charged to " +
                    "profile ${owningProfile.uid} \"${owningProfile.name}\" " +
                    "(spent ${owningProfile.spent} → ${profileSpent + transaction.value})"
        )
    }
}