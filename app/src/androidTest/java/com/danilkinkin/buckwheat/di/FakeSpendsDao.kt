package com.danilkinkin.buckwheat.di

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.danilkinkin.buckwheat.data.dao.TransactionDao
import com.danilkinkin.buckwheat.data.entities.Transaction
import com.danilkinkin.buckwheat.data.entities.TransactionType

class FakeTransactionDao : TransactionDao {
    private val spends = mutableListOf<Transaction>()

    // ── Profile-scoped queries ────────────────────────────────────────────────

    override fun getAll(profileId: Int): LiveData<List<Transaction>> =
        MutableLiveData(spends.filter { it.budgetProfileId == profileId })

    override fun getAll(type: TransactionType, profileId: Int): LiveData<List<Transaction>> =
        MutableLiveData(spends.filter { it.type == type && it.budgetProfileId == profileId })

    override fun deleteByProfile(profileId: Int) {
        spends.removeIf { it.budgetProfileId == profileId }
    }

    // ── Unscoped fallback (migration helper only) ─────────────────────────────

    override suspend fun getUnowned(): List<Transaction> =
        spends.filter { it.budgetProfileId == 0 }

    // ── Single-row operations ─────────────────────────────────────────────────

    override fun getById(uid: Int): Transaction? =
        spends.firstOrNull { it.uid == uid }

    override fun insert(vararg transaction: Transaction) {
        spends.addAll(transaction)
    }

    override fun update(vararg transaction: Transaction) {
        transaction.forEach { updated ->
            val idx = spends.indexOfFirst { it.uid == updated.uid }
            if (idx >= 0) spends[idx] = updated
        }
    }

    override fun deleteById(uid: Int) {
        spends.removeIf { it.uid == uid }
    }

    /** Only used from tests that wipe the whole in-memory list. */
    override fun deleteAll() {
        spends.clear()
    }
}