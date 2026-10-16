package solutions.laxmi.omnicompiler.core.review

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.net.toUri
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import solutions.laxmi.omnicompiler.core.common.ApplicationScope
import solutions.laxmi.omnicompiler.core.common.TimeSource
import solutions.laxmi.omnicompiler.core.datastore.PreferencesStore
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** The Play Store listing to open; release and debug builds both point at the published app. */
data class ReviewConfig(val storePackage: String)

/**
 * Ratings: the "Rate" buttons open the Play Store listing, and Google Play's in-app review card is requested at
 * positive moments once [ReviewPolicy] allows. Google's guidance keeps the two apart: a button must never call the
 * review API, because Play's quota may silently show nothing.
 */
interface AppReview {
    /** Counts a launch; call once per app start. */
    fun onLaunch()

    /** A run the user watched passed. Counted always; the card is requested only when [mayPrompt] and the policy allow. */
    fun onPositiveMoment(activity: Activity, mayPrompt: Boolean = true)

    fun openStoreListing(context: Context)
}

/** No ratings (previews, tests). */
object NoAppReview : AppReview {
    override fun onLaunch() = Unit
    override fun onPositiveMoment(activity: Activity, mayPrompt: Boolean) = Unit
    override fun openStoreListing(context: Context) = Unit
}

/** Provided at the app root so any screen can open the listing or report a positive moment. */
val LocalAppReview = staticCompositionLocalOf<AppReview> { NoAppReview }

@Singleton
internal class PlayAppReview @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: PreferencesStore,
    private val time: TimeSource,
    private val config: ReviewConfig,
    @ApplicationScope private val scope: CoroutineScope,
) : AppReview {

    private val manager by lazy { ReviewManagerFactory.create(context) }
    private val policy = ReviewPolicy()
    private val requesting = AtomicBoolean(false)

    override fun onLaunch() {
        scope.launch { preferences.recordLaunch(time.now().toEpochMilliseconds()) }
    }

    override fun onPositiveMoment(activity: Activity, mayPrompt: Boolean) {
        // Held weakly: the request takes a moment, and the activity may be gone (rotation, Back) when it returns.
        val host = WeakReference(activity)
        scope.launch(Dispatchers.Main.immediate) {
            preferences.recordAcceptedRun()
            if (!mayPrompt || !requesting.compareAndSet(false, true)) return@launch
            try {
                val now = time.now().toEpochMilliseconds()
                if (!policy.mayRequest(now, preferences.engagement())) return@launch
                val info = manager.requestReviewFlow().await()
                val current = host.get()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return@launch
                preferences.setLastReviewRequestAt(now)
                // Play doesn't say whether the card showed or was rated; the app carries on either way.
                manager.launchReviewFlow(current, info).await()
            } catch (e: ReviewException) {
                // No Play Store, or Play declined: never surfaced to the user (Google's guidance).
                Timber.w(e, "In-app review unavailable")
            } finally {
                requesting.set(false)
            }
        }
    }

    override fun openStoreListing(context: Context) {
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=${config.storePackage}".toUri())
            .setPackage(PLAY_STORE_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (e: ActivityNotFoundException) {
            // No Play Store app: the listing in a browser.
            val web = Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=${config.storePackage}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(web)
            } catch (e: ActivityNotFoundException) {
                Timber.w(e, "Nothing can open the store listing")
            }
        }
    }

    private companion object {
        const val PLAY_STORE_PACKAGE = "com.android.vending"
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface ReviewModule {
    @Binds
    fun bindsAppReview(impl: PlayAppReview): AppReview
}
