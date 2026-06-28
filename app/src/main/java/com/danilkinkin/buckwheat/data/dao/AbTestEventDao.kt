package com.danilkinkin.buckwheat.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.danilkinkin.buckwheat.data.entities.AbTestEvent

@Dao
interface AbTestEventDao {
    @Insert
    suspend fun insert(event: AbTestEvent)

    @Query("SELECT * FROM ab_test_events ORDER BY timestamp ASC")
    suspend fun getAllEvents(): List<AbTestEvent>

    @Query("DELETE FROM ab_test_events")
    suspend fun deleteAll()
}
