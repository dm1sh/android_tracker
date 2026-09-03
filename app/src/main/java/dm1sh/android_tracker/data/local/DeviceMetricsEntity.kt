package dm1sh.android_tracker.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "device_metrics",
    indices = [Index(value = ["synced"]), Index(value = ["capturedAt"])]
)
data class DeviceMetricsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val capturedAt: Long,
    val batteryLevel: Int,
    val batteryState: String,
    val storageFreeBytes: Long,
    val storageTotalBytes: Long,
    val networkState: String,
    val wifiSsid: String?,
    val synced: Boolean = false
)
