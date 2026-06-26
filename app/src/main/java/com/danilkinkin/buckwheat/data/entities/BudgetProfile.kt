package com.danilkinkin.buckwheat.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a named budget profile. The user can maintain multiple profiles
 * and swipe between them on the main editor screen.
 *
 * All monetary values are stored as plain [String] to preserve [java.math.BigDecimal]
 * precision (matching the existing convention in [Transaction]).
 */
@Entity(tableName = "budget_profiles")
data class BudgetProfile(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "uid")
    val uid: Int = 0,

    /** Human-readable name shown in the switcher indicator. */
    @ColumnInfo(name = "name")
    val name: String,

    /** ISO 4217 currency code or custom symbol, mirrors [currencyStoreKey] logic. */
    @ColumnInfo(name = "currency", defaultValue = "")
    val currency: String = "",

    @ColumnInfo(name = "budget", defaultValue = "0.00")
    val budget: String = "0.00",

    @ColumnInfo(name = "spent", defaultValue = "0.00")
    val spent: String = "0.00",

    @ColumnInfo(name = "daily_budget", defaultValue = "0.00")
    val dailyBudget: String = "0.00",

    @ColumnInfo(name = "spent_from_daily_budget", defaultValue = "0.00")
    val spentFromDailyBudget: String = "0.00",

    /** Epoch millis; null when no period is set yet. */
    @ColumnInfo(name = "start_period_date")
    val startPeriodDate: Long? = null,

    @ColumnInfo(name = "finish_period_date")
    val finishPeriodDate: Long? = null,

    @ColumnInfo(name = "finish_period_actual_date")
    val finishPeriodActualDate: Long? = null,

    @ColumnInfo(name = "last_change_daily_budget_date")
    val lastChangeDailyBudgetDate: Long? = null,

    /** Display order (ascending). */
    @ColumnInfo(name = "sort_order", defaultValue = "0")
    val sortOrder: Int = 0,
)