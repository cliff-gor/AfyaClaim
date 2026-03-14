package ke.afyaclaim.pos.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ke.afyaclaim.pos.R
import ke.afyaclaim.pos.data.prefs.SessionStore
import ke.afyaclaim.pos.data.remote.ApiErrors
import retrofit2.HttpException
import java.io.IOException

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: SyncEngine,
    private val sessionStore: SessionStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (sessionStore.cachedToken() == null) return Result.success()
        return try {
            engine.run()
            Result.success()
        } catch (e: HttpException) {
            sessionStore.recordSyncError(ApiErrors.message(e))
            if (e.code() in 500..599) Result.retry() else Result.failure()
        } catch (e: IOException) {
            sessionStore.recordSyncError(ApiErrors.message(e))
            Result.retry()
        } catch (e: kotlinx.serialization.SerializationException) {
            sessionStore.recordSyncError("Unexpected response from server.")
            Result.failure()
        }
    }

    /** Needed for expedited work on Android 11 and below, where it runs as a foreground service. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.sync_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(applicationContext.getString(R.string.sync_notification_title))
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "sync"
        private const val NOTIFICATION_ID = 4201
    }
}
