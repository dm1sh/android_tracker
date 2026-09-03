package dm1sh.android_tracker.data.repository

import android.content.Context
import dm1sh.android_tracker.data.local.DeviceMetricsDao
import dm1sh.android_tracker.data.local.UsageEventDao
import dm1sh.android_tracker.data.remote.DeviceMetricDto
import dm1sh.android_tracker.data.remote.MetricsBatchRequest
import dm1sh.android_tracker.data.remote.TrackerApi
import dm1sh.android_tracker.data.remote.UsageBatchRequest
import dm1sh.android_tracker.data.remote.UsageEventDto
import dm1sh.android_tracker.domain.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes all unsynced local rows to the server and marks only the
 * acknowledged records as synced. Failed/unacknowledged rows remain
 * unsynced and are retried on the next run.
 */
@Singleton
class SyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usageEventDao: UsageEventDao,
    private val deviceMetricsDao: DeviceMetricsDao,
    private val trackerApi: TrackerApi,
    private val settingsRepository: SettingsRepository
) {

    data class SyncResult(
        val pushedUsage: Int,
        val pushedMetrics: Int,
        val error: String? = null
    )

    suspend fun sync(): SyncResult {
        val settings = settingsRepository.settings.first()
        if (settings.serverUrl.isBlank()) {
            return SyncResult(0, 0, error = "Server URL not set")
        }
        val baseUrl = settings.serverUrl.trimEnd('/')

        val unsyncedUsage = usageEventDao.getUnsynced()
        val unsyncedMetrics = deviceMetricsDao.getUnsynced()

        var pushedUsage = 0
        var pushedMetrics = 0

        try {
            if (unsyncedUsage.isNotEmpty()) {
                val request = UsageBatchRequest(
                    deviceId = settings.deviceId,
                    events = unsyncedUsage.map {
                        UsageEventDto(
                            clientId = it.id,
                            eventType = it.eventType,
                            packageName = it.packageName,
                            className = it.className,
                            timestamp = it.timestamp
                        )
                    }
                )
                val response = trackerApi.pushUsageEvents(baseUrl, request)
                val accepted = response.acceptedClientIds
                if (accepted.isNotEmpty()) {
                    usageEventDao.markSynced(accepted)
                }
                pushedUsage = accepted.size
            }

            if (unsyncedMetrics.isNotEmpty()) {
                val request = MetricsBatchRequest(
                    deviceId = settings.deviceId,
                    metrics = unsyncedMetrics.map {
                        DeviceMetricDto(
                            clientId = it.id,
                            capturedAt = it.capturedAt,
                            batteryLevel = it.batteryLevel,
                            batteryState = it.batteryState,
                            storageFreeBytes = it.storageFreeBytes,
                            storageTotalBytes = it.storageTotalBytes,
                            networkState = it.networkState,
                            wifiSsid = it.wifiSsid
                        )
                    }
                )
                val response = trackerApi.pushDeviceMetrics(baseUrl, request)
                val accepted = response.acceptedClientIds
                if (accepted.isNotEmpty()) {
                    deviceMetricsDao.markSynced(accepted)
                }
                pushedMetrics = accepted.size
            }

            return SyncResult(pushedUsage, pushedMetrics)
        } catch (e: ResponseException) {
            return SyncResult(0, 0, error = "Server rejected request (${e.response.status.value})")
        } catch (e: Exception) {
            return SyncResult(0, 0, error = e.message ?: e.javaClass.simpleName)
        }
    }
}
