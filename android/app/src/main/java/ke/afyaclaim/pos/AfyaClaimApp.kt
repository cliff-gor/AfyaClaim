package ke.afyaclaim.pos

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import ke.afyaclaim.pos.data.prefs.SessionStore
import ke.afyaclaim.pos.data.sync.SyncScheduler
import javax.inject.Inject

@HiltAndroidApp
class AfyaClaimApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var scheduler: SyncScheduler
    @Inject lateinit var sessionStore: SessionStore

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (sessionStore.cachedToken() != null) scheduler.schedulePeriodic()
    }
}
