package com.nlevcluster.sender.report.trip.db

import android.content.Context
import android.util.Log
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RenameColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nlevcluster.sender.report.alerts.AlertDao
import com.nlevcluster.sender.report.alerts.AlertEntity

@Database(
    entities = [
        TripReportEntity::class,
        AlertEntity::class
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = TripReportDatabase.MigrationV2::class),
        AutoMigration(from = 2, to = 3, spec = TripReportDatabase.MigrationV3::class),
        AutoMigration(from = 3, to = 4, spec = TripReportDatabase.MigrationV4::class)
    ]
)

abstract class TripReportDatabase : RoomDatabase() {
    abstract fun tripReportDao(): TripReportDao
    abstract fun alertDao(): AlertDao

    @DeleteColumn(columnName = "TRIP_ID", tableName = "trip_report")
    open class MigrationV2 : AutoMigrationSpec {
        init {
            Log.d("RoomMigration", "MigrationV2 init")
        }

        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            Log.d("RoomMigration", "MigrationV2 applied from ${db.version}")
        }
    }

    @RenameColumn(
        tableName = "trip_report",
        fromColumnName = "MODE_DURATION",
        toColumnName = "MODE_STATS"
    )
    @DeleteColumn(columnName = "START_ENERGY", tableName = "trip_report")
    @DeleteColumn(columnName = "END_ENERGY", tableName = "trip_report")
    open class MigrationV3 : AutoMigrationSpec {
        init {
            Log.d("RoomMigration", "MigrationV3 init")
        }

        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            Log.d("RoomMigration", "MigrationV3 applied from ${db.version}")
        }
    }

    @DeleteColumn(columnName = "SPEED_ALERTS", tableName = "trip_report")
    @DeleteColumn(columnName = "IDLE_ALERTS", tableName = "trip_report")
    @DeleteColumn(columnName = "HARSH_ALERTS", tableName = "trip_report")
    open class MigrationV4 : AutoMigrationSpec {
        init {
            Log.d("RoomMigration", "MigrationV4 init")
        }

        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            Log.d("RoomMigration", "MigrationV4 applied from ${db.version}")
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
                        Log.d("RoomMigration", "Trip DB opened at version ${db.version}")
                        // Maximum durability settings
                        db.execSQL("PRAGMA synchronous = FULL") //TODO - To be removed with power management feature in AOSP
                    }

                    override fun onCreate(db: SupportSQLiteDatabase) {
                        Log.d("RoomMigration", "Trip DB created at version ${db.version}")
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