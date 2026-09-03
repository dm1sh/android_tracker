package dm1sh.android_tracker.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "usage_events",
    indices = [Index(value = ["synced"]), Index(value = ["timestamp"])]
)
data class UsageEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventType: Int,
    val packageName: String,
    val className: String?,
    val timestamp: Long,
    val synced: Boolean = false
)
