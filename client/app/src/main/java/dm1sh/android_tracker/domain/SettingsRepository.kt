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
class SettingsRepository @param:Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_FETCH_INTERVAL_MIN = longPreferencesKey("fetch_interval_min")
        private val KEY_PUSH_INTERVAL_MIN = longPreferencesKey("push_interval_min")
        private val KEY_DEVICE_NAME = stringPreferencesKey("device_id")
        private val KEY_LAST_FETCH_TIME = longPreferencesKey("last_fetch_time")
        private val KEY_LAST_PUSH_TIME = longPreferencesKey("last_push_time")
        private val KEY_LAST_FETCH_ERROR = stringPreferencesKey("last_fetch_error")
        private val KEY_LAST_PUSH_ERROR = stringPreferencesKey("last_push_error")
        private val KEY_LAST_PUSH_REJECTED = stringPreferencesKey("last_push_rejected")

        const val DEFAULT_FETCH_INTERVAL_MIN = 30L
        const val DEFAULT_PUSH_INTERVAL_MIN = 60L
        const val MIN_PERIODIC_INTERVAL_MIN = 15L
    }

    data class Settings(
        val serverUrl: String = "",
        val fetchIntervalMin: Long = DEFAULT_FETCH_INTERVAL_MIN,
        val pushIntervalMin: Long = DEFAULT_PUSH_INTERVAL_MIN,
        val deviceName: String = DEFAULT_DEVICE_NAME,
        val lastFetchTime: Long = 0L,
        val lastPushTime: Long = 0L,
        val lastFetchError: String? = null,
        val lastPushError: String? = null,
        val lastPushRejected: String? = null
    ) {
        companion object {
            val DEFAULT_DEVICE_NAME: String = Build.MODEL.ifBlank { Build.DEVICE }
        }
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            serverUrl = prefs[KEY_SERVER_URL] ?: "",
            fetchIntervalMin = prefs[KEY_FETCH_INTERVAL_MIN] ?: DEFAULT_FETCH_INTERVAL_MIN,
            pushIntervalMin = prefs[KEY_PUSH_INTERVAL_MIN] ?: DEFAULT_PUSH_INTERVAL_MIN,
            deviceName = prefs[KEY_DEVICE_NAME] ?: Settings.DEFAULT_DEVICE_NAME,
            lastFetchTime = prefs[KEY_LAST_FETCH_TIME] ?: 0L,
            lastPushTime = prefs[KEY_LAST_PUSH_TIME] ?: 0L,
            lastFetchError = prefs[KEY_LAST_FETCH_ERROR],
            lastPushError = prefs[KEY_LAST_PUSH_ERROR],
            lastPushRejected = prefs[KEY_LAST_PUSH_REJECTED]
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

    suspend fun updateDeviceName(deviceName: String) {
        context.dataStore.edit { it[KEY_DEVICE_NAME] = deviceName }
    }

    /** Atomically records the fetch run time and any error surfaced on that run. */
    suspend fun setFetchStatus(fetchedAt: Long?, error: String?) {
        context.dataStore.edit {
            if (error == null) it.remove(KEY_LAST_FETCH_ERROR) else it[KEY_LAST_FETCH_ERROR] = error
            if (fetchedAt != null) it[KEY_LAST_FETCH_TIME] = fetchedAt
        }
    }

    /** Atomically records the push run time, any error, and rejection summary. */
    suspend fun setPushStatus(pushedAt: Long?, error: String?, rejected: String? = null) {
        context.dataStore.edit {
            if (error == null) it.remove(KEY_LAST_PUSH_ERROR) else it[KEY_LAST_PUSH_ERROR] = error
            if (rejected == null) it.remove(KEY_LAST_PUSH_REJECTED) else it[KEY_LAST_PUSH_REJECTED] = rejected
            if (pushedAt != null) it[KEY_LAST_PUSH_TIME] = pushedAt
        }
    }

    /** Clears only the push error, e.g. when a manual push is triggered. */
    suspend fun clearPushError() {
        context.dataStore.edit { it.remove(KEY_LAST_PUSH_ERROR) }
    }

    /** Clears only the fetch error, e.g. when a manual local update is triggered. */
    suspend fun clearFetchError() {
        context.dataStore.edit { it.remove(KEY_LAST_FETCH_ERROR) }
    }

    /** Clears both fetch and push errors, e.g. when settings are reapplied. */
    suspend fun clearErrors() {
        context.dataStore.edit {
            it.remove(KEY_LAST_FETCH_ERROR)
            it.remove(KEY_LAST_PUSH_ERROR)
            it.remove(KEY_LAST_PUSH_REJECTED)
        }
    }
}
