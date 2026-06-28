package com.danilkinkin.buckwheat.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ab_test_events")
data class AbTestEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: String,
    val feature: String,
    val variant: String,
    val respondentId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val success: Int? = null, // 1 for true, 0 for false
    val durationMs: Long? = null,
    val errorCount: Int? = null,
    val questionId: String? = null,
    val score: Int? = null
)
