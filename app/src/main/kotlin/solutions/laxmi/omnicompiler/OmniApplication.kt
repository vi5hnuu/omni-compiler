package solutions.laxmi.omnicompiler

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class OmniApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        // Debug builds log failures to logcat; release builds plant no tree, so nothing is logged there.
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    }

    /** On-demand WorkManager init so workers (offline run queue) get Hilt-injected dependencies. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
