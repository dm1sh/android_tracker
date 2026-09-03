package dm1sh.android_tracker.data.repository

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import dm1sh.android_tracker.data.local.UsageEventDao
import dm1sh.android_tracker.data.local.UsageEventEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usageEventDao: UsageEventDao
) {

    /**
     * Queries usage events since the last stored timestamp and persists new ones.
     * Returns the number of new events stored. Returns 0 if the device is locked
     * or usage access permission is missing.
     */
    suspend fun fetchAndStore(): Int {
        if (!isUserUnlocked() || !hasUsageStatsPermission()) return 0

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val lastTimestamp = usageEventDao.getLastEventTimestamp()
        val endTime = System.currentTimeMillis()
        // Clamp the start to a sane window (system only keeps events for a few days).
        val beginTime = lastTimestamp.coerceAtLeast(endTime - 3 * 24 * 60 * 60 * 1000L)

        val events = usageStatsManager.queryEvents(beginTime, endTime) ?: return 0

        val entities = ArrayList<UsageEventEntity>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            entities.add(
                UsageEventEntity(
                    eventType = event.eventType,
                    packageName = event.packageName ?: "",
                    className = event.className,
                    timestamp = event.timeStamp
                )
            )
        }
        if (entities.isEmpty()) return 0

        usageEventDao.insertAll(entities)
        return entities.size
    }

    private fun isUserUnlocked(): Boolean {
        val userManager = context.getSystemService(Context.USER_SERVICE) as android.os.UserManager
        return userManager.isUserUnlocked
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                "android:get_usage_stats",
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                "android:get_usage_stats",
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }
}
