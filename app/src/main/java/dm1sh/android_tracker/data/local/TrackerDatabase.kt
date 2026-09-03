package dm1sh.android_tracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [UsageEventEntity::class, DeviceMetricsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun usageEventDao(): UsageEventDao
    abstract fun deviceMetricsDao(): DeviceMetricsDao
}
