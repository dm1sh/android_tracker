package dm1sh.android_tracker.data.repository

import dm1sh.android_tracker.data.local.DeviceMetricsDao
import dm1sh.android_tracker.data.local.DeviceMetricsEntity
import dm1sh.android_tracker.worker.DeviceInfoProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetricsRepository @Inject constructor(
    private val deviceMetricsDao: DeviceMetricsDao,
    private val deviceInfoProvider: DeviceInfoProvider
) {

    /**
     * Captures current device metrics and persists a new row.
     * Returns the stored row id, or -1 if nothing was stored.
     */
    suspend fun captureAndStore(): Long {
        val metrics = deviceInfoProvider.collect()
        val entity = DeviceMetricsEntity(
            capturedAt = System.currentTimeMillis(),
            batteryLevel = metrics.batteryLevel,
            batteryState = metrics.batteryState,
            storageFreeBytes = metrics.storageFreeBytes,
            storageTotalBytes = metrics.storageTotalBytes,
            networkState = metrics.networkState,
            wifiSsid = metrics.wifiSsid
        )
        return deviceMetricsDao.insert(entity)
    }
}
