package com.danilkinkin.buckwheat.data.seeder

import java.math.BigDecimal

data class SeederConfig(
    val daysToSeed: Int = 365,
    val profileName: String = "Test Budget",
    val currency: String = "IDR",
    val incomeFrequencyDays: Int = 30,
    val incomeAmount: BigDecimal = BigDecimal("6000000.00"),
    val minExpensesPerDay: Int = 1,
    val maxExpensesPerDay: Int = 4,
    val minExpenseAmount: Double = 5000.0,
    val maxExpenseAmount: Double = 250000.0,
    val categories: List<String> = listOf(
        "Groceries", "Dining", "Transport", "Utilities", "Entertainment"
    )
)