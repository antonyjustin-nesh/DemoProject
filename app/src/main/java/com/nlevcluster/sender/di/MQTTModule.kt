package com.nlevcluster.sender.di

import android.content.Context
import com.google.gson.Gson
import com.nlevcluster.sender.BuildConfig
import com.nlevcluster.sender.logging.LogHelper
import com.nlevcluster.sender.mqtt.MQTTClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object MQTTModule {

    @Named("local")
    @Provides
    fun providesLocalMqttClient(
        @ApplicationContext context: Context,
        gson: Gson,
        logHelper: LogHelper
    ): MQTTClient {
        return MQTTClient(
            context,
            gson,
            logHelper,
            BuildConfig.SERVER_URI,
            BuildConfig.CLIENT_ID,
            BuildConfig.MQTT_FEED_TOPIC,
        )
    }

    @Named("portal")
    @Provides
    fun providesPortalMqttClient(
        @ApplicationContext context: Context,
        gson: Gson,
        logHelper: LogHelper
    ): MQTTClient {
        return MQTTClient(
            context,
            gson,
            logHelper,
            BuildConfig.PORTAL_MQTT_SERVER_URI,
            BuildConfig.PORTAL_MQTT_CLIENT_ID,
            BuildConfig.PORTAL_MQTT_FEED_TOPIC,
        )
    }

}