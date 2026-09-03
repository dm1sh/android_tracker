package dm1sh.android_tracker.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dm1sh.android_tracker.data.repository.MetricsRepository
import dm1sh.android_tracker.data.repository.UsageRepository
import dm1sh.android_tracker.domain.SettingsRepository

/**
 * Fetches UsageStatsManager events and captures device metrics in a single run,
 * storing them locally marked as unsynced. Runs once with no retries: if the
 * fetch cannot happen (e.g. usage access is missing), the run simply fails
 * rather than retrying.
 */
@HiltWorker
class UsageWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val usageRepository: UsageRepository,
    private val metricsRepository: MetricsRepository,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        return try {
            usageRepository.fetchAndStore()
            metricsRepository.captureAndStore()
            settingsRepository.setFetchStatus(fetchedAt = now, error = null)
            Result.success()
        } catch (e: Exception) {
            settingsRepository.setFetchStatus(fetchedAt = now, error = e.message ?: e.javaClass.simpleName)
            Result.failure()
        }
    }
}
