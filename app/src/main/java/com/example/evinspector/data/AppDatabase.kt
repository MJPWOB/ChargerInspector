package com.example.evinspector.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SessionEntity::class,
        ChargerRecordEntity::class,
        ComponentResultEntity::class,
        PhotoEntity::class,
        ReportEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): InspectionDao
}
