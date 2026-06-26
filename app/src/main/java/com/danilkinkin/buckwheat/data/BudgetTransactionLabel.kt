package com.danilkinkin.buckwheat.data

/**
 * Constants used to label system-generated transactions (top-up and budget adjustment)
 * so they can be distinguished from regular user income/expense in Analytics.
 *
 * These are stored in the [Transaction.comment] field as an MVP approach,
 * avoiding a database schema migration for a new `source` column.
 */
object BudgetTransactionLabel {
    const val TOP_UP = "Budget Top Up"
    const val ADJUSTMENT = "Budget Adjustment"
}
