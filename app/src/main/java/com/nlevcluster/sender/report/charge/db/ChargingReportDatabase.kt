package com.nlevcluster.sender.report.charge.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChargingSessionEntity::class,
    ],
    version = 1,
    exportSchema = true
)

abstract class ChargingReportDatabase : RoomDatabase() {
    abstract fun chargingReportDao(): ChargingReportDao

    companion object {
        @Volatile
        private var INSTANCE: ChargingReportDatabase? = null

        fun getInstance(context: Context): ChargingReportDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChargingReportDatabase::class.java,
                    "charge_report_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}