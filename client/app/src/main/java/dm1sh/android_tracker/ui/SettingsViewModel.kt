package dm1sh.android_tracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dm1sh.android_tracker.data.local.DeviceMetricsDao
import dm1sh.android_tracker.data.local.UsageEventDao
import dm1sh.android_tracker.data.remote.TrackerApi
import dm1sh.android_tracker.domain.SettingsRepository
import dm1sh.android_tracker.worker.WorkScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val workScheduler: WorkScheduler,
    private val trackerApi: TrackerApi,
    usageEventDao: UsageEventDao,
    deviceMetricsDao: DeviceMetricsDao
) : ViewModel() {

    sealed interface HealthCheckState {
        data object Idle : HealthCheckState
        data object Loading : HealthCheckState
        data class Success(val status: String, val serverTime: Long?) : HealthCheckState
        data class Error(val message: String) : HealthCheckState
        data class SavePrompt(val message: String) : HealthCheckState
    }

    data class SettingsUiState(
        val serverUrl: String = "",
        val fetchIntervalMin: String = SettingsRepository.DEFAULT_FETCH_INTERVAL_MIN.toString(),
        val pushIntervalMin: String = SettingsRepository.DEFAULT_PUSH_INTERVAL_MIN.toString(),
        val deviceId: String = SettingsRepository.Settings.DEFAULT_DEVICE_ID,
        val usageAccessGranted: Boolean = false,
        val localNetworkGranted: Boolean = false,
        val locationGranted: Boolean = false,
        val unsyncedUsage: Int = 0,
        val unsyncedMetrics: Int = 0,
        val lastFetchTime: Long = 0L,
        val lastPushTime: Long = 0L,
        val lastFetchError: String? = null,
        val lastPushError: String? = null,
        val saving: Boolean = false,
        val healthCheck: HealthCheckState = HealthCheckState.Idle,
        val message: String? = null
    )

    /** Pending, validated edits captured when a save is attempted but the server is unreachable. */
    private data class PendingSave(
        val url: String,
        val fetchIntervalMin: Long,
        val pushIntervalMin: Long,
        val deviceId: String
    )

    private var pendingSave: PendingSave? = null

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    val unsyncedCounts: StateFlow<Pair<Int, Int>> = combine(
        usageEventDao.observeUnsyncedCount(),
        deviceMetricsDao.observeUnsyncedCount()
    ) { usage, metrics -> usage to metrics }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0 to 0)

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { s ->
                _state.value = _state.value.copy(
                    serverUrl = s.serverUrl,
                    fetchIntervalMin = s.fetchIntervalMin.toString(),
                    pushIntervalMin = s.pushIntervalMin.toString(),
                    deviceId = s.deviceId,
                    lastFetchTime = s.lastFetchTime,
                    lastPushTime = s.lastPushTime,
                    lastFetchError = s.lastFetchError,
                    lastPushError = s.lastPushError
                )
            }
        }
    }

    fun onServerUrlChange(value: String) {
        _state.value = _state.value.copy(serverUrl = value)
    }

    fun onFetchIntervalChange(value: String) {
        _state.value = _state.value.copy(fetchIntervalMin = value)
    }

    fun onPushIntervalChange(value: String) {
        _state.value = _state.value.copy(pushIntervalMin = value)
    }

    fun onDeviceIdChange(value: String) {
        _state.value = _state.value.copy(deviceId = value)
    }

    fun setUsageAccessGranted(granted: Boolean) {
        _state.value = _state.value.copy(usageAccessGranted = granted)
    }

    fun setLocalNetworkGranted(granted: Boolean) {
        _state.value = _state.value.copy(localNetworkGranted = granted)
    }

    fun setLocationGranted(granted: Boolean) {
        _state.value = _state.value.copy(locationGranted = granted)
    }

    fun scheduleWorkersOnStart() {
        viewModelScope.launch {
            workScheduler.rescheduleAll()
        }
    }

    fun saveSettings() {
        val current = _state.value
        if (current.saving) return

        val fetch = current.fetchIntervalMin.toLongOrNull()
        val push = current.pushIntervalMin.toLongOrNull()
        if (fetch == null || push == null) {
            _state.value = current.copy(message = "Intervals must be whole numbers")
            return
        }
        if (fetch < SettingsRepository.MIN_PERIODIC_INTERVAL_MIN ||
            push < SettingsRepository.MIN_PERIODIC_INTERVAL_MIN
        ) {
            _state.value = current.copy(
                message = "Intervals must be at least ${SettingsRepository.MIN_PERIODIC_INTERVAL_MIN} minutes"
            )
            return
        }

        val url = current.serverUrl.trim()
        val deviceId = current.deviceId.ifBlank { SettingsRepository.Settings.DEFAULT_DEVICE_ID }
        _state.value = current.copy(saving = true, message = null)
        viewModelScope.launch {
            if (url.isBlank()) {
                _state.value = _state.value.copy(
                    saving = false,
                    healthCheck = HealthCheckState.Error("Server URL is empty")
                )
                return@launch
            }

            try {
                val health = trackerApi.health(url)
                pendingSave = PendingSave(url, fetch, push, deviceId)
                _state.value = _state.value.copy(
                    healthCheck = HealthCheckState.Success(health.status, health.serverTime)
                )
                doSave(includeUrl = true)
            } catch (e: Exception) {
                pendingSave = PendingSave(url, fetch, push, deviceId)
                _state.value = _state.value.copy(
                    saving = false,
                    healthCheck = HealthCheckState.SavePrompt(e.message ?: e.javaClass.simpleName)
                )
            }
        }
    }

    /**
     * Persists the pending edits. When [includeUrl] is true the (possibly new)
     * server URL is saved; otherwise only the other fields are saved and the
     * previous server URL is left untouched. Always reschedules the workers and
     * clears prior errors.
     */
    private fun doSave(includeUrl: Boolean) {
        val pending = pendingSave ?: return
        viewModelScope.launch {
            if (includeUrl) {
                settingsRepository.updateServerUrl(pending.url)
            }
            settingsRepository.updateFetchInterval(pending.fetchIntervalMin)
            settingsRepository.updatePushInterval(pending.pushIntervalMin)
            settingsRepository.updateDeviceId(pending.deviceId)
            settingsRepository.clearErrors()
            workScheduler.rescheduleAll()
            pendingSave = null
            _state.value = _state.value.copy(
                saving = false,
                message = "Settings saved"
            )
        }
    }

    /**
     * Handler for the "Cancel" button of the save-prompt dialog: save everything
     * except the server URL, which is left unchanged.
     */
    fun onSaveCancel() {
        _state.value = _state.value.copy(healthCheck = HealthCheckState.Idle)
        doSave(includeUrl = false)
    }

    /**
     * Handler for the "Save" button of the save-prompt dialog: save the server
     * URL as well.
     */
    fun onSaveUrl() {
        _state.value = _state.value.copy(healthCheck = HealthCheckState.Idle)
        if (pendingSave?.url?.isBlank() == true) {
            doSave(includeUrl = false)
        } else {
            doSave(includeUrl = true)
        }
    }

    fun dismissHealthCheck() {
        _state.value = _state.value.copy(healthCheck = HealthCheckState.Idle)
    }

    fun formatTimestamp(millis: Long): String {
        if (millis <= 0L) return "Never"
        return try {
            SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault()).format(Date(millis))
        } catch (_: Exception) {
            "Unknown"
        }
    }

    fun runLocalUpdate() {
        workScheduler.runLocalUpdateNow()
        viewModelScope.launch {
            settingsRepository.clearFetchError()
        }
        _state.value = _state.value.copy(message = "Fetch & metrics scheduled — status updates below")
    }

    fun runPush() {
        workScheduler.runPushNow()
        viewModelScope.launch {
            settingsRepository.clearPushError()
        }
        _state.value = _state.value.copy(message = "Push scheduled — status updates below")
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
