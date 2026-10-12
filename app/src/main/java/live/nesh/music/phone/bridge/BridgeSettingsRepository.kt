package live.nesh.music.phone.bridge

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mediaBridgeDataStore by preferencesDataStore(name = "media_bridge")

class BridgeSettingsRepository(private val context: Context) {
    val settings: Flow<BridgeSettings> = context.mediaBridgeDataStore.data.map { preferences ->
        BridgeSettings(
            clusterAddress = preferences[CLUSTER_ADDRESS].orEmpty(),
            clusterName = preferences[CLUSTER_NAME].orEmpty(),
            autoStart = preferences[AUTO_START] ?: false,
        )
    }

    suspend fun saveCluster(address: String, name: String) {
        context.mediaBridgeDataStore.edit { preferences ->
            preferences[CLUSTER_ADDRESS] = address.trim().uppercase()
            preferences[CLUSTER_NAME] = name
        }
    }

    suspend fun setAutoStart(enabled: Boolean) {
        context.mediaBridgeDataStore.edit { preferences -> preferences[AUTO_START] = enabled }
    }

    private companion object {
        val CLUSTER_ADDRESS = stringPreferencesKey("cluster_address")
        val CLUSTER_NAME = stringPreferencesKey("cluster_name")
        val AUTO_START = booleanPreferencesKey("auto_start")
    }
}
