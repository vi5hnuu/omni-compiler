package solutions.laxmi.omnicompiler.core.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import solutions.laxmi.omnicompiler.core.common.ApplicationScope
import solutions.laxmi.omnicompiler.core.common.TimeSource
import solutions.laxmi.omnicompiler.core.datastore.AdFreePassWindow
import solutions.laxmi.omnicompiler.core.datastore.PreferencesStore
import javax.inject.Inject
import javax.inject.Singleton

/** Where the ad-free pass stands, for its entry points (Settings, the editor drawer) and [AdFreePassDialog]. */
sealed interface PassState {
    /** Ads can't be requested (no consent, or no unit configured): the entry points are hidden. */
    data object Unavailable : PassState
    data object Loading : PassState

    /** No video could be loaded right now; [AdFreePass.retry] asks again. */
    data object NoVideo : PassState
    data object Ready : PassState

    /** Full-screen ads are off until [until] (epoch milliseconds). */
    data class Active(val until: Long) : PassState
}

enum class PassResult {
    Granted,

    /** Closed before the reward: no pass. */
    NotEarned,

    /** Offline when asked, or by the time the reward came: no pass. */
    Offline,

    /** No video was ready to show. */
    NotReady,
}

/**
 * A rewarded video that turns full-screen ads (interstitial and app open) off for [BuildConfig.PASS_DURATION_MS].
 * Banners and ads in lists stay. Watching again restarts the window; it never adds time.
 */
interface AdFreePass {
    /** Whether the pass can be offered at all; cheap to observe, it loads nothing (unlike [state]). */
    val available: StateFlow<Boolean>

    val state: StateFlow<PassState>

    fun retry()

    /** Shows the video if one is ready; [onResult] arrives once it closes. */
    fun watch(activity: Activity, onResult: (PassResult) -> Unit)
}

/**
 * There is no backend to verify rewards, so the pass is granted only when all of these hold: the SDK's earned-reward
 * callback fired (it fires only after the video has played), the ad's impression was recorded, and the device was
 * online both when the user asked and when the reward came, so a video cached earlier and played offline earns
 * nothing. The stored window is honoured only while `grantedAt <= now < until`, so turning the clock back ends it.
 */
@Singleton
internal class RewardedAdFreePass @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sdk: AdsSdk,
    private val online: OnlineCheck,
    private val preferences: PreferencesStore,
    private val time: TimeSource,
    @ApplicationScope private val scope: CoroutineScope,
) : AdFreePass {

    private enum class Load { Idle, Loading, Loaded, Failed }

    // Touched only on the main thread, like the SDK's callbacks.
    private val load = MutableStateFlow(Load.Idle)
    private var video: RewardedAd? = null
    private var loadedAt = 0L

    override val available: StateFlow<Boolean> = sdk.ready.map { it && BuildConfig.REWARDED_UNIT_ID.isNotBlank() }
        .stateIn(scope, SharingStarted.Eagerly, false)

    override val state: StateFlow<PassState> = combine(sdk.ready, load, activeWindow()) { ready, load, window ->
        when {
            !ready || BuildConfig.REWARDED_UNIT_ID.isBlank() -> PassState.Unavailable
            window != null -> PassState.Active(window.until)
            load == Load.Loaded -> PassState.Ready
            load == Load.Failed -> PassState.NoVideo
            else -> PassState.Loading
        }
    }
        // A video is fetched only once someone looks at the pass, and not while it is already on.
        .onStart {
            scope.launch(Dispatchers.Main.immediate) {
                sdk.awaitReady()
                if (!isActive()) preload()
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PassState.Unavailable)

    /** Whether the pass is on right now; read at each break rather than trusting [state], which can lag a clock change. */
    suspend fun isActive(): Boolean = preferences.adFreePass.first().isActiveAt(now())

    override fun retry() {
        if (load.value == Load.Failed) load.value = Load.Idle
        preload()
    }

    override fun watch(activity: Activity, onResult: (PassResult) -> Unit) {
        if (!online.isOnline()) return onResult(PassResult.Offline)
        val ad = video?.takeIf { now() - loadedAt < VIDEO_LIFETIME_MS }
        video = null
        load.value = Load.Idle
        if (ad == null) {
            preload()
            return onResult(PassResult.NotReady)
        }
        var impressed = false
        var result = PassResult.NotEarned
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                // A video counts as a full-screen ad, so an interstitial doesn't follow right after it.
                scope.launch { preferences.setLastFullScreenAdAt(now()) }
            }

            override fun onAdImpression() {
                impressed = true
            }

            override fun onAdDismissedFullScreenContent() {
                if (result != PassResult.Granted) preload()
                onResult(result)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                preload()
                onResult(PassResult.NotReady)
            }
        }
        ad.show(activity) { _ ->
            result = when {
                !impressed -> PassResult.NotEarned
                !online.isOnline() -> PassResult.Offline
                else -> PassResult.Granted.also { grant() }
            }
        }
    }

    private fun grant() {
        scope.launch {
            val now = now()
            preferences.setAdFreePass(AdFreePassWindow(grantedAt = now, until = now + BuildConfig.PASS_DURATION_MS))
        }
    }

    private fun preload() {
        if (!sdk.ready.value || BuildConfig.REWARDED_UNIT_ID.isBlank() || load.value != Load.Idle) return
        load.value = Load.Loading
        RewardedAd.load(context, BuildConfig.REWARDED_UNIT_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                video = ad
                loadedAt = now()
                load.value = Load.Loaded
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                load.value = Load.Failed
            }
        })
    }

    /** The stored window while it is active, then null once it runs out. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun activeWindow(): Flow<AdFreePassWindow?> = preferences.adFreePass.transformLatest { window ->
        val now = now()
        if (window == null || !window.isActiveAt(now)) return@transformLatest emit(null)
        emit(window)
        delay(window.until - now)
        emit(null)
    }

    private fun now(): Long = time.now().toEpochMilliseconds()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        // Google: a loaded rewarded ad is good for an hour; drop it a little before that.
        const val VIDEO_LIFETIME_MS = 55 * 60_000L
    }
}

/** Active only inside the window it was granted for; a window longer than a pass (a tampered store) never is. */
internal fun AdFreePassWindow?.isActiveAt(now: Long): Boolean =
    this != null && now in grantedAt until until && until - grantedAt <= BuildConfig.PASS_DURATION_MS
