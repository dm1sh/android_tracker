package dm1sh.android_tracker.domain

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "tracker_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_FETCH_INTERVAL_MIN = longPreferencesKey("fetch_interval_min")
        private val KEY_PUSH_INTERVAL_MIN = longPreferencesKey("push_interval_min")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")

        const val DEFAULT_FETCH_INTERVAL_MIN = 30L
        const val DEFAULT_PUSH_INTERVAL_MIN = 60L
        const val MIN_PERIODIC_INTERVAL_MIN = 15L
    }

    data class Settings(
        val serverUrl: String = "",
        val fetchIntervalMin: Long = DEFAULT_FETCH_INTERVAL_MIN,
        val pushIntervalMin: Long = DEFAULT_PUSH_INTERVAL_MIN,
        val deviceId: String = DEFAULT_DEVICE_ID
    ) {
        companion object {
            val DEFAULT_DEVICE_ID: String = Build.MODEL.ifBlank { Build.DEVICE }
        }
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            serverUrl = prefs[KEY_SERVER_URL] ?: "",
            fetchIntervalMin = prefs[KEY_FETCH_INTERVAL_MIN] ?: DEFAULT_FETCH_INTERVAL_MIN,
            pushIntervalMin = prefs[KEY_PUSH_INTERVAL_MIN] ?: DEFAULT_PUSH_INTERVAL_MIN,
            deviceId = prefs[KEY_DEVICE_ID] ?: Settings.DEFAULT_DEVICE_ID
        )
    }

    suspend fun getSettings(): Settings = settings.first()

    suspend fun updateServerUrl(url: String) {
        context.dataStore.edit { it[KEY_SERVER_URL] = url }
    }

    suspend fun updateFetchInterval(minutes: Long) {
        context.dataStore.edit { it[KEY_FETCH_INTERVAL_MIN] = minutes }
    }

    suspend fun updatePushInterval(minutes: Long) {
        context.dataStore.edit { it[KEY_PUSH_INTERVAL_MIN] = minutes }
    }

    suspend fun updateDeviceId(deviceId: String) {
        context.dataStore.edit { it[KEY_DEVICE_ID] = deviceId }
    }
}
