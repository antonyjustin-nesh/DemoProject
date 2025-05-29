package com.nlevcluster.sender.di

import android.content.Context
import com.google.gson.Gson
import com.hivemq.client.internal.shaded.javax.inject.Singleton
import com.nlevcluster.sender.nllogger.LogHelper
import com.nlevcluster.sender.trip.db.TripReportDao
import com.nlevcluster.sender.trip.db.TripReportDatabase
import com.nlevcluster.sender.util.MQTTUtil
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlin.math.log

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
    fun provideMqttClient(logHelper: LogHelper): MQTTUtil = MQTTUtil(logHelper)
}
