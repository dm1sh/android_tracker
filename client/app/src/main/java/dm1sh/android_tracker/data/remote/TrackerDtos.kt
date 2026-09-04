package dm1sh.android_tracker.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UsageEventDto(
    @SerialName("clientId") val clientId: Long,
    @SerialName("eventType") val eventType: Int,
    @SerialName("packageName") val packageName: String,
    @SerialName("className") val className: String? = null,
    @SerialName("timestamp") val timestamp: Long
)

@Serializable
data class UsageBatchRequest(
    @SerialName("deviceName") val deviceName: String,
    @SerialName("events") val events: List<UsageEventDto>
)

@Serializable
data class DeviceMetricDto(
    @SerialName("clientId") val clientId: Long,
    @SerialName("capturedAt") val capturedAt: Long,
    @SerialName("batteryLevel") val batteryLevel: Int,
    @SerialName("batteryState") val batteryState: Int?,
    @SerialName("storageFreeBytes") val storageFreeBytes: Long,
    @SerialName("storageTotalBytes") val storageTotalBytes: Long,
    @SerialName("networkState") val networkState: Int?,
    @SerialName("wifiSsid") val wifiSsid: String? = null
)

@Serializable
data class MetricsBatchRequest(
    @SerialName("deviceName") val deviceName: String,
    @SerialName("metrics") val metrics: List<DeviceMetricDto>
)

@Serializable
data class RejectedItem(
    @SerialName("clientId") val clientId: Long,
    @SerialName("reason") val reason: String? = null
)

@Serializable
data class BatchResponse(
    @SerialName("acceptedClientIds") val acceptedClientIds: List<Long> = emptyList(),
    @SerialName("rejected") val rejected: List<RejectedItem> = emptyList()
)

@Serializable
data class HealthResponse(
    @SerialName("status") val status: String = "ok",
    @SerialName("serverTime") val serverTime: Long? = null
)
