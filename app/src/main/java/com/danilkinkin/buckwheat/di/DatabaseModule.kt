package com.danilkinkin.buckwheat.di

import androidx.room.*
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.danilkinkin.buckwheat.data.dao.BudgetProfileDao
import com.danilkinkin.buckwheat.data.dao.StorageDao
import com.danilkinkin.buckwheat.data.dao.TransactionDao
import com.danilkinkin.buckwheat.data.entities.BudgetProfile
import com.danilkinkin.buckwheat.data.entities.Storage
import com.danilkinkin.buckwheat.data.entities.Transaction


class AutoMigration1to2 : AutoMigrationSpec

@DeleteColumn.Entries(
    DeleteColumn(
        tableName = "Spent",
        columnName = "deleted"
    )
)
class AutoMigration2to3 : AutoMigrationSpec

// Preparing for remove storage table
class AutoMigration3to4 : AutoMigrationSpec

// Rename Spent to Transaction
val AutoMigration4to5: Migration = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create the new "transactions" table
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `transactions` " +
                    "(`type` TEXT NOT NULL, " +
                    "`value` TEXT NOT NULL, " +
                    "`date` INTEGER NOT NULL, " +
                    "`comment` TEXT NOT NULL DEFAULT '', " +
                    "`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)"
        )

        // Copy data from the old "Spent" table to the new "transactions" table
        database.execSQL(
            "INSERT INTO `transactions` (`type`, `value`, `date`, `comment`) " +
                    "SELECT 'SPENT', `value`, `date`, `comment` FROM `Spent`"
        )

        // Drop the old "Spent" table
        database.execSQL("DROP TABLE IF EXISTS `Spent`")
    }
}

// Add budget_profiles table for multi-budget support
val AutoMigration5to6: Migration = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `budget_profiles` (" +
                    "`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`currency` TEXT NOT NULL DEFAULT '', " +
                    "`budget` TEXT NOT NULL DEFAULT '0.00', " +
                    "`spent` TEXT NOT NULL DEFAULT '0.00', " +
                    "`daily_budget` TEXT NOT NULL DEFAULT '0.00', " +
                    "`spent_from_daily_budget` TEXT NOT NULL DEFAULT '0.00', " +
                    "`start_period_date` INTEGER, " +
                    "`finish_period_date` INTEGER, " +
                    "`finish_period_actual_date` INTEGER, " +
                    "`last_change_daily_budget_date` INTEGER, " +
                    "`sort_order` INTEGER NOT NULL DEFAULT 0)"
        )
    }
}

// Add budget_profile_id to transactions for per-profile isolation.
// Existing rows receive the sentinel value 0; SpendsRepository.reassignLegacyTransactions()
// upgrades them to the real active profile ID on first launch.
val AutoMigration6to7: Migration = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE `transactions` ADD COLUMN `budget_profile_id` INTEGER NOT NULL DEFAULT 0"
        )
    }
}

@Database(
    entities = [Transaction::class, Storage::class, BudgetProfile::class],
    version = 7,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = AutoMigration1to2::class),
        AutoMigration(from = 2, to = 3, spec = AutoMigration2to3::class),
        AutoMigration(from = 3, to = 4, spec = AutoMigration3to4::class),
    ],
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class DatabaseModule : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    abstract fun storageDao(): StorageDao

    abstract fun budgetProfileDao(): BudgetProfileDao

    companion object {
        val MANUAL_MIGRATIONS = arrayOf<Migration>(
            AutoMigration4to5,
            AutoMigration5to6,
            AutoMigration6to7,
        )
    }
}