package com.danilkinkin.buckwheat.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.danilkinkin.buckwheat.data.entities.BudgetProfile

@Dao
interface BudgetProfileDao {

    @Query("SELECT * FROM budget_profiles ORDER BY sort_order ASC")
    fun getAll(): LiveData<List<BudgetProfile>>

    @Query("SELECT * FROM budget_profiles ORDER BY sort_order ASC")
    suspend fun getAllSuspend(): List<BudgetProfile>

    @Query("SELECT * FROM budget_profiles WHERE uid = :uid")
    suspend fun getById(uid: Int): BudgetProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: BudgetProfile): Long

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(vararg profile: BudgetProfile)

    @Query("DELETE FROM budget_profiles WHERE uid = :uid")
    suspend fun deleteById(uid: Int)

    @Query("SELECT COUNT(*) FROM budget_profiles")
    suspend fun count(): Int

    @Query("""
    SELECT * FROM budget_profiles
    WHERE start_period_date <= :date
      AND finish_period_date >= :date
    LIMIT 1
""")
    suspend fun getByDate(date: Long): BudgetProfile?

    @Query("SELECT COUNT(uid) FROM budget_profiles")
    suspend fun getProfileCount(): Int

    @Query("SELECT * FROM budget_profiles ORDER BY sort_order ASC")
    suspend fun getAllProfiles(): List<BudgetProfile>
}