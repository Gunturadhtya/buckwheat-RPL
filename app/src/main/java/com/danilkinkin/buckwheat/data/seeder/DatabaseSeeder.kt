package com.danilkinkin.buckwheat.data.seeder

import com.danilkinkin.buckwheat.data.dao.BudgetProfileDao
import com.danilkinkin.buckwheat.data.dao.TransactionDao
import com.danilkinkin.buckwheat.data.entities.BudgetProfile
import com.danilkinkin.buckwheat.data.entities.Transaction
import com.danilkinkin.buckwheat.data.entities.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

class DatabaseSeeder @Inject constructor(
    private val budgetProfileDao: BudgetProfileDao,
    private val transactionDao: TransactionDao
) {
    /**
     * Seeds one year of realistic expense and income data.
     * Runs safely on the IO dispatcher.
     */
    suspend fun seed(config: SeederConfig = SeederConfig()) = withContext(Dispatchers.IO) {
        // Prevent accidental re-seeding
        if (budgetProfileDao.getProfileCount() > 0) return@withContext

        // Compute period boundaries up front so both the profile insert
        // and the transaction loop use the exact same timestamps.
        val startCal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -config.daysToSeed)
            // Normalise to midnight so date-range queries that truncate to day
            // boundaries always include day 0.
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startDate: Date = startCal.time

        val endCal = Calendar.getInstance().apply {
            // End of today — ensures "today" is inside the profile window so
            // the date picker can always reach the most recent transactions.
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val endDate: Date = endCal.time

        // 1. Insert profile with BOTH period dates set.
        //    finishPeriodDate is required by the getByDate() query used in
        //    the date picker; omitting it caused the picker to find no profile
        //    and show no transactions.
        val initialProfile = BudgetProfile(
            name = config.profileName,
            currency = config.currency,
            startPeriodDate = startDate.time,
            finishPeriodDate = endDate.time,   // FIX: was missing in original
        )
        val profileId = budgetProfileDao.insert(initialProfile).toInt()

        // 2. Generate transactions, one calendar day at a time.
        val transactions = mutableListOf<Transaction>()
        var totalSpent = BigDecimal.ZERO
        var totalIncome = BigDecimal.ZERO

        // Walk forward from startCal (already at midnight of day-0).
        val cursor = startCal.clone() as Calendar

        for (dayOffset in 0..config.daysToSeed) {
            // Vary the time within the day so rows sort naturally inside a day.
            val baseMillis = cursor.timeInMillis

            // Inject periodic income (day 0, 30, 60 … etc.)
            if (dayOffset % config.incomeFrequencyDays == 0) {
                val incomeTime = Date(baseMillis + 9 * 3_600_000L) // ~09:00
                transactions.add(
                    Transaction(
                        type = TransactionType.INCOME,
                        value = config.incomeAmount,
                        date = incomeTime,
                        comment = "Salary/Income",
                        budgetProfileId = profileId
                    )
                )
                totalIncome += config.incomeAmount
            }

            // Inject daily expenses spread across the day.
            val expensesCount = (config.minExpensesPerDay..config.maxExpensesPerDay).random()
            for (i in 0 until expensesCount) {
                val randomValue =
                    config.minExpenseAmount + Math.random() * (config.maxExpenseAmount - config.minExpenseAmount)
                val amount = BigDecimal.valueOf(randomValue).setScale(2, RoundingMode.HALF_UP)

                // Spread expenses between 10:00 and 22:00 to look natural.
                val offsetMillis = (10 + (Math.random() * 12).toInt()) * 3_600_000L
                val txDate = Date(baseMillis + offsetMillis)

                transactions.add(
                    Transaction(
                        type = TransactionType.SPENT,
                        value = amount,
                        date = txDate,
                        comment = config.categories.random(),
                        budgetProfileId = profileId
                    )
                )
                totalSpent += amount
            }

            cursor.add(Calendar.DAY_OF_YEAR, 1)
        }

        // 3. Batch insert all transactions (critical for performance).
        transactionDao.insertAll(transactions)

        // 4. Update the profile with accurate monetary totals.
        //    Keep finishPeriodDate intact — only update the fields that changed.
        val finalProfile = initialProfile.copy(
            uid = profileId,
            budget = totalIncome.toPlainString(),
            spent = totalSpent.toPlainString(),
            finishPeriodActualDate = Date().time,
        )
        budgetProfileDao.update(finalProfile)
    }
}