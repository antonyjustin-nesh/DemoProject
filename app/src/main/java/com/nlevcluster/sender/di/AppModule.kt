package com.nlevcluster.sender.di

import android.content.Context
import com.google.gson.Gson
import com.hivemq.client.internal.shaded.javax.inject.Singleton
import com.nlevcluster.sender.report.alerts.AlertDao
import com.nlevcluster.sender.report.charge.db.ChargingReportDao
import com.nlevcluster.sender.report.charge.db.ChargingReportDatabase
import com.nlevcluster.sender.report.trip.db.TripReportDao
import com.nlevcluster.sender.report.trip.db.TripReportDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideTripDao(@ApplicationContext context: Context): TripReportDao {
        return TripReportDatabase.getInstance(context).tripReportDao()
    }

    @Provides
    @Singleton
    fun provideChargingDao(@ApplicationContext context: Context): ChargingReportDao {
        return ChargingReportDatabase.getInstance(context).chargingReportDao()
    }

    @Provides
    @Singleton
    fun provideAlertDao(@ApplicationContext context: Context): AlertDao {
        return TripReportDatabase.getInstance(context).alertDao()
    }

}
