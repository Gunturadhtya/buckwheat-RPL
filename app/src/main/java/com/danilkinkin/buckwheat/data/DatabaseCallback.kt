package com.danilkinkin.buckwheat.data

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.danilkinkin.buckwheat.data.seeder.DatabaseSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Provider
import javax.inject.Inject

class DatabaseCallback @Inject constructor(
    // Provider is crucial here to prevent dependency cycles
    private val seederProvider: Provider<DatabaseSeeder>
) : RoomDatabase.Callback() {

    // A detached scope ensures the DB operation finishes even if the calling lifecycle dies
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        applicationScope.launch {
            seederProvider.get().seed()
        }
    }
}