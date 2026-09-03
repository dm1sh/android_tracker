package dm1sh.android_tracker.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dm1sh.android_tracker.data.repository.SyncRepository
import dm1sh.android_tracker.domain.SettingsRepository

/**
 * Pushes all unsynced local records to the server and marks them synced
 * once acknowledged. Runs once with no retries and records the push status
 * (last run time and any error) for display in the UI. Intended to run with a
 * NetworkType.CONNECTED constraint.
 */
@HiltWorker
class ServerPushWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val result = syncRepository.sync()
        if (result.error == null) {
            settingsRepository.setPushStatus(pushedAt = now, error = null)
            Result.success()
        } else {
            settingsRepository.setPushStatus(pushedAt = now, error = result.error)
            Result.failure()
        }
    }
}
