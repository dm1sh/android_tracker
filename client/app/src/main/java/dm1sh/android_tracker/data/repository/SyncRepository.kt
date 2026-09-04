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
 * Pushes all unsynced local rows to the server and marks acknowledged records
 * as synced. Rows rejected with reason "duplicate" are also marked synced
 * (the server already has them). Other rejections remain unsynced for retry.
 */
@Singleton
class SyncRepository @param:Inject constructor(
    @ApplicationContext private val context: Context,
    private val usageEventDao: UsageEventDao,
    private val deviceMetricsDao: DeviceMetricsDao,
    private val trackerApi: TrackerApi,
    private val settingsRepository: SettingsRepository
) {

    data class SyncResult(
        val pushedUsage: Int,
        val pushedMetrics: Int,
        val rejectedUsage: Map<String, Int> = emptyMap(),
        val rejectedMetrics: Map<String, Int> = emptyMap(),
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
        var rejectedUsage = emptyMap<String, Int>()
        var rejectedMetrics = emptyMap<String, Int>()

        try {
            if (unsyncedUsage.isNotEmpty()) {
                val request = UsageBatchRequest(
                    deviceName = settings.deviceName,
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
                val duplicateIds = response.rejected
                    .filter { it.reason == "duplicate" }
                    .map { it.clientId }
                val toSync = accepted + duplicateIds
                if (toSync.isNotEmpty()) {
                    usageEventDao.markSynced(toSync)
                }
                pushedUsage = accepted.size
                rejectedUsage = reasonDistribution(response.rejected)
            }

            if (unsyncedMetrics.isNotEmpty()) {
                val request = MetricsBatchRequest(
                    deviceName = settings.deviceName,
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
                val duplicateIds = response.rejected
                    .filter { it.reason == "duplicate" }
                    .map { it.clientId }
                val toSync = accepted + duplicateIds
                if (toSync.isNotEmpty()) {
                    deviceMetricsDao.markSynced(toSync)
                }
                pushedMetrics = accepted.size
                rejectedMetrics = reasonDistribution(response.rejected)
            }

            return SyncResult(pushedUsage, pushedMetrics, rejectedUsage, rejectedMetrics)
        } catch (e: ResponseException) {
            return SyncResult(0, 0, error = "Server rejected request (${e.response.status.value})")
        } catch (e: Exception) {
            return SyncResult(0, 0, error = e.message ?: e.javaClass.simpleName)
        }
    }

    private fun reasonDistribution(
        rejected: List<dm1sh.android_tracker.data.remote.RejectedItem>
    ): Map<String, Int> =
        rejected.groupBy { it.reason ?: "unknown" }
            .mapValues { it.value.size }
}
