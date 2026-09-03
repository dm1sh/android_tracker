package dm1sh.android_tracker.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dm1sh.android_tracker.data.repository.SyncRepository

/**
 * Pushes all unsynced local records to the server and marks them synced
 * once acknowledged. Intended to run with a NetworkType.CONNECTED constraint.
 */
@HiltWorker
class ServerPushWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val result = syncRepository.sync()
        return if (result.error == null) {
            Result.success()
        } else {
            // Transient network errors -> retry; hard failures we still retry
            // since we can't distinguish easily without HTTP codes.
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.success() // leave unsynced for next period rather than fail loudly
            }
        }
    }

    companion object {
        private const val MAX_RETRIES = 3
    }
}
