package com.nlevcluster.sender.trip.db

import android.content.Context
import android.util.Log
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TripReportEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        AutoMigration (from = 1, to = 2, spec = TripReportDatabase.MigrationV2::class)
    ]
)

abstract class TripReportDatabase : RoomDatabase() {
    abstract fun tripReportDao(): TripReportDao

    @DeleteColumn(columnName = "TRIP_ID", tableName = "trip_report")
    open class MigrationV2 : AutoMigrationSpec {
        init {
            Log.d("RoomMigration", "MigrationV2 init")
        }

        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            Log.d("RoomMigration", "MigrationV2 applied from ${db.version}")
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: TripReportDatabase? = null

        fun getInstance(context: Context): TripReportDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TripReportDatabase::class.java,
                    "Trips"
                ).addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        Log.d("RoomMigration", "DB opened at version ${db.version}")
                    }

                    override fun onCreate(db: SupportSQLiteDatabase) {
                        Log.d("RoomMigration", "DB created at version ${db.version}")
                    }

                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        Log.d("RoomMigration", "onDestructiveMigration ${db}")
                    }
                })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}