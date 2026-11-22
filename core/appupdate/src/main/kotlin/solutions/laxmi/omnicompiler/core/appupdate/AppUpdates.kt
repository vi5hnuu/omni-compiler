package solutions.laxmi.omnicompiler.core.appupdate

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which Play release must be installed before the app is used. A release's in-app update priority (0–5) is set when
 * it is published (Play Developer API); one at or above [immediatePriority] is required: Play's full-screen update
 * runs and comes back until it is installed. Lower priorities are left to Play's own auto-update.
 */
data class UpdateConfig(val immediatePriority: Int = 4)

/** Required updates through Google Play's in-app update flow. */
interface AppUpdates {
    /** Call from the activity's onCreate, before it is started: registers the update flow and checks on every resume. */
    fun attach(activity: ComponentActivity)
}

/** Decides from Play's answer whether to run the immediate flow; kept apart from the SDK so it is plain logic. */
internal fun mustUpdateNow(availability: Int, priority: Int, immediateAllowed: Boolean, config: UpdateConfig): Boolean = when {
    // An immediate update the user left (app killed, Back) must be resumed, as Google requires.
    availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> true
    availability == UpdateAvailability.UPDATE_AVAILABLE -> immediateAllowed && priority >= config.immediatePriority
    else -> false
}

@Singleton
internal class PlayAppUpdates @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: UpdateConfig,
) : AppUpdates {

    private val manager by lazy { AppUpdateManagerFactory.create(context) }

    override fun attach(activity: ComponentActivity) {
        // The result needs no handling: a declined required update is offered again on the next resume.
        val launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }
        // One check at a time per activity: quick pause/resume cycles must not stack update flows.
        val checking = AtomicBoolean(false)
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                if (!checking.compareAndSet(false, true)) return
                activity.lifecycleScope.launch {
                    try {
                        val info = check() ?: return@launch
                        manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build())
                    } finally {
                        checking.set(false)
                    }
                }
            }
        })
    }

    /** Play's update info when an immediate update must run now, else null (also when Play can't say). */
    private suspend fun check(): AppUpdateInfo? = try {
        manager.appUpdateInfo.await().takeIf {
            mustUpdateNow(it.updateAvailability(), it.updatePriority(), it.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE), config)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Not installed from Play (debug, sideload) or no Play Store: nothing to update through.
        Timber.w(e, "In-app update check unavailable")
        null
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface AppUpdateModule {
    @Binds
    fun bindsAppUpdates(impl: PlayAppUpdates): AppUpdates
}
