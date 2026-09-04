package dm1sh.android_tracker.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dm1sh.android_tracker.domain.SettingsRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {

    companion object {
        const val USAGE_WORK_NAME = "usage-fetch"
        const val PUSH_WORK_NAME = "server-push"
    }

    private val workManager: WorkManager = WorkManager.getInstance(context)

    /**
     * (Re)schedules the periodic workers from current settings.
     * This is invoked on app start and whenever settings change.
     */
    suspend fun rescheduleAll() {
        val settings = settingsRepository.settings.first()

        val usageInterval = settings.fetchIntervalMin
            .coerceAtLeast(SettingsRepository.MIN_PERIODIC_INTERVAL_MIN)
        val pushInterval = settings.pushIntervalMin
            .coerceAtLeast(SettingsRepository.MIN_PERIODIC_INTERVAL_MIN)

        val usageRequest = PeriodicWorkRequestBuilder<UsageWorker>(usageInterval, TimeUnit.MINUTES).build()
        val pushRequest = PeriodicWorkRequestBuilder<ServerPushWorker>(pushInterval, TimeUnit.MINUTES)
            .setConstraints(pushConstraints())
            .build()

        workManager.enqueueUniquePeriodicWork(
            USAGE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            usageRequest
        )
        workManager.enqueueUniquePeriodicWork(
            PUSH_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            pushRequest
        )
    }

    /** Manual run of the local update (fetch usage + capture metrics in one worker). */
    fun runLocalUpdateNow() {
        val usage = OneTimeWorkRequestBuilder<UsageWorker>()
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(
            USAGE_WORK_NAME + "-manual",
            ExistingWorkPolicy.REPLACE,
            usage
        )
    }

    /** Manual push to the server, honoring the network-connected constraint. */
    fun runPushNow() {
        val push = OneTimeWorkRequestBuilder<ServerPushWorker>()
            .setConstraints(pushConstraints())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(
            PUSH_WORK_NAME + "-manual",
            ExistingWorkPolicy.REPLACE,
            push
        )
    }

    private fun pushConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
