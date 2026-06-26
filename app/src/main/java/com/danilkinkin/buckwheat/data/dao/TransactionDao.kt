package com.danilkinkin.buckwheat.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.danilkinkin.buckwheat.data.entities.Transaction
import com.danilkinkin.buckwheat.data.entities.TransactionType

@Dao
interface TransactionDao {

    // ── Profile-scoped queries (preferred) ───────────────────────────────────

    @Query("SELECT * FROM transactions WHERE budget_profile_id = :profileId ORDER BY date ASC")
    fun getAll(profileId: Int): LiveData<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE budget_profile_id = :profileId AND type = :type ORDER BY date ASC")
    fun getAll(type: TransactionType, profileId: Int): LiveData<List<Transaction>>

    @Query("DELETE FROM transactions WHERE budget_profile_id = :profileId")
    fun deleteByProfile(profileId: Int)

    // ── Unscoped fallbacks (used only during migration reassignment) ──────────

    /**
     * Returns every transaction regardless of profile. Only used by
     * [com.danilkinkin.buckwheat.di.SpendsRepository.reassignLegacyTransactions]
     * to bulk-update pre-migration rows (budget_profile_id = 0) after the first
     * launch on schema v7.
     */
    @Query("SELECT * FROM transactions WHERE budget_profile_id = 0")
    suspend fun getUnowned(): List<Transaction>

    // ── Single-row operations (profile-neutral) ───────────────────────────────

    @Query("SELECT * FROM transactions WHERE uid = :uid")
    fun getById(uid: Int): Transaction?

    @Insert
    fun insert(vararg transaction: Transaction)

    @Update(entity = Transaction::class, onConflict = OnConflictStrategy.REPLACE)
    fun update(vararg transaction: Transaction)

    @Query("DELETE FROM transactions WHERE uid = :uid")
    fun deleteById(uid: Int)

    /** Hard delete of every row. Only call from tests or when wiping the whole DB. */
    @Query("DELETE FROM transactions")
    fun deleteAll()
}
