package dm1sh.android_tracker.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dm1sh.android_tracker.data.repository.UsageRepository
import dm1sh.android_tracker.domain.SettingsRepository

/**
 * Fetches UsageStatsManager events since the last recorded event and
 * stores them locally marked as unsynced.
 */
@HiltWorker
class UsageWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val usageRepository: UsageRepository,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val count = usageRepository.fetchAndStore()
            if (count > 0) {
                settingsRepository.updateLastFetchTime(System.currentTimeMillis())
            }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        private const val MAX_RETRIES = 3
    }
}
