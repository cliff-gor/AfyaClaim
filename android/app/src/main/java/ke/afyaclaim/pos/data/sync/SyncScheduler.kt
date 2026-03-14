package ke.afyaclaim.pos.data.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class SyncActivity { IDLE, WAITING_FOR_NETWORK, RUNNING, FAILED }

@Singleton
class SyncScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Expedited one-off sync; queued behind any sync already in flight. */
    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(ONE_OFF, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(PERIODIC)
        workManager.cancelUniqueWork(ONE_OFF)
    }

    val activity: Flow<SyncActivity> = workManager.getWorkInfosForUniqueWorkFlow(ONE_OFF).map { infos ->
        when {
            infos.any { it.state == WorkInfo.State.RUNNING } -> SyncActivity.RUNNING
            infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED } ->
                SyncActivity.WAITING_FOR_NETWORK
            infos.lastOrNull()?.state == WorkInfo.State.FAILED -> SyncActivity.FAILED
            else -> SyncActivity.IDLE
        }
    }

    companion object {
        const val PERIODIC = "afyaclaim-sync-periodic"
        const val ONE_OFF = "afyaclaim-sync-now"
    }
}
