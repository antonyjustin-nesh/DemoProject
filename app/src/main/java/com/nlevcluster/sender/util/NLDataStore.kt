package com.nlevcluster.sender.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NLDataStore @Inject constructor(
    @ApplicationContext val context: Context
) {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

    companion object PreferencesKeys {
        val IGN_ON_DELAY = intPreferencesKey("ign_on_delay")
        val IGN_OFF_DELAY = intPreferencesKey("ign_off_delay")
        val MQTT_FEED_URL = stringPreferencesKey("mqtt_feed_url")
    }

    suspend fun setMqttFeedUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[MQTT_FEED_URL] = url
        }
    }

    suspend fun mqttFeedUrl(): String? = context.dataStore.data.map {
        it[MQTT_FEED_URL]
    }.first()

    suspend fun setIgnitionOnDelay(delay: Int) {
        context.dataStore.edit { preferences ->
            preferences[IGN_ON_DELAY] = delay
        }
    }

    suspend fun ignitionOnDelay(): Int? = context.dataStore.data.map {
        it[IGN_ON_DELAY]
    }.first()

    suspend fun setIgnitionOffDelay(delay: Int) {
        context.dataStore.edit { preferences ->
            preferences[IGN_OFF_DELAY] = delay
        }
    }

    suspend fun ignitionOffDelay(): Int? = context.dataStore.data.map {
        it[IGN_OFF_DELAY]
    }.first()


}