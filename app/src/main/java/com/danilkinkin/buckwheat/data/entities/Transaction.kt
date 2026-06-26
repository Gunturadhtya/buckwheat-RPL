package com.danilkinkin.buckwheat.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.util.*

enum class TransactionType {
    SET_DAILY_BUDGET,
    INCOME,
    SPENT
}

@Entity(tableName = "transactions")
data class Transaction(
    @ColumnInfo(name = "type")
    val type: TransactionType,

    @ColumnInfo(name = "value")
    val value: BigDecimal,

    @ColumnInfo(name = "date")
    val date: Date,

    @ColumnInfo(name = "comment", defaultValue = "")
    val comment: String = "",

    /**
     * Foreign key (soft) to [com.danilkinkin.buckwheat.data.entities.BudgetProfile.uid].
     *
     * Default is 0, which is the sentinel value assigned by migration 6→7 to all
     * pre-existing transactions that were recorded before profile-isolation was
     * introduced. On first launch after the migration, [SpendsRepository] reassigns
     * these rows to the active profile so they are not orphaned.
     *
     * New transactions always receive the ID of the currently active profile.
     */
    @ColumnInfo(name = "budget_profile_id", defaultValue = "0")
    val budgetProfileId: Int = 0,
) {
    @PrimaryKey(autoGenerate = true) var uid: Int = 0
}