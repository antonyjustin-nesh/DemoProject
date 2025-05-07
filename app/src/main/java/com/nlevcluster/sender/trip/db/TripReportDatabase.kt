package com.nlevcluster.sender.trip.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TripReportEntity::class,
    ],
    version = 1,
    exportSchema = true
)

abstract class TripReportDatabase : RoomDatabase() {
    abstract fun tripReportDao(): TripReportDao

    companion object {
        @Volatile
        private var INSTANCE: TripReportDatabase? = null

        fun getInstance(context: Context): TripReportDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TripReportDatabase::class.java,
                    "trip_report_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}