package com.nlevcluster.sender.report.charge.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

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
                ).addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        Log.d("RoomMigration", "Charge DB opened at version ${db.version}")
                        // Maximum durability settings
                        db.execSQL("PRAGMA synchronous = FULL") //TODO - To be removed with power management feature in AOSP
                    }

                    override fun onCreate(db: SupportSQLiteDatabase) {
                        Log.d("RoomMigration", "Charge DB created at version ${db.version}")
                    }

                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        Log.d("RoomMigration", "onDestructiveMigration ${db}")
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }
    }
}