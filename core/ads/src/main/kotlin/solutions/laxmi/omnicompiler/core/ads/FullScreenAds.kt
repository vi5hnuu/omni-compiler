package solutions.laxmi.omnicompiler.core.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import solutions.laxmi.omnicompiler.core.common.ApplicationScope
import solutions.laxmi.omnicompiler.core.common.TimeSource
import solutions.laxmi.omnicompiler.core.datastore.PreferencesStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interstitials at breaks and the app-open ad on returning to the app, both preloaded and both shown only when
 * [FullScreenAdPolicy] allows; they share one "last full-screen ad" time so they never come back to back.
 */
@Singleton
internal class FullScreenAds @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sdk: AdsSdk,
    private val pass: RewardedAdFreePass,
    private val preferences: PreferencesStore,
    private val time: TimeSource,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val policy = FullScreenAdPolicy(BuildConfig.INTERSTITIAL_GRACE_MS, BuildConfig.INTERSTITIAL_MIN_INTERVAL_MS)

    // All on the main thread, like the SDK's callbacks.
    private var interstitial: InterstitialAd? = null
    private var interstitialLoading = false
    private var appOpen: AppOpenAd? = null
    private var appOpenLoadedAt = 0L
    private var appOpenLoading = false
    private var showing = false
    private var runsSinceAd = 0
    private var lastFailedRunAt: Long? = null
    private var backgroundAt: Long? = null

    init {
        scope.launch(Dispatchers.Main.immediate) {
            sdk.ready.collect { ready ->
                if (ready) {
                    preloadInterstitial()
                    preloadAppOpen()
                } else {
                    // Consent withdrawn: ads loaded under the old choice are never shown.
                    interstitial = null
                    appOpen = null
                }
            }
        }
    }

    fun onRunFinished(failed: Boolean) {
        runsSinceAd++
        if (failed) lastFailedRunAt = now()
    }

    fun onNaturalBreak(activity: Activity, userBusy: Boolean) = showInterstitial(activity, userBusy)

    /** Leaving the editor is a break only after some work there, so a quick look around never costs an ad. */
    fun onLeavingEditor(activity: Activity) {
        if (runsSinceAd >= RUNS_BEFORE_EDITOR_BREAK) showInterstitial(activity, userBusy = false)
    }

    fun onBackground() {
        // A full-screen ad covers the app too; that isn't the user leaving.
        if (!showing) backgroundAt = now()
    }

    /**
     * Returning after [BuildConfig.APP_OPEN_AWAY_MS] away shows the app-open ad. Never on a cold start (nothing was
     * left) or when the app was opened to edit a file from elsewhere ([openedForContent]): the user came for that.
     */
    fun onForeground(activity: Activity, openedForContent: Boolean) {
        val away = backgroundAt?.let { now() - it }
        backgroundAt = null
        if (appOpen != null && now() - appOpenLoadedAt >= APP_OPEN_LIFETIME_MS) appOpen = null
        if (away == null || away < BuildConfig.APP_OPEN_AWAY_MS || openedForContent) return
        if (appOpen == null) return preloadAppOpen()
        present(userBusy = false, reload = ::preloadAppOpen) { callback ->
            val ad = appOpen ?: return@present false
            appOpen = null
            ad.fullScreenContentCallback = callback
            ad.show(activity)
            true
        }
    }

    private fun showInterstitial(activity: Activity, userBusy: Boolean) {
        if (interstitial == null) return preloadInterstitial()
        present(userBusy, reload = ::preloadInterstitial) { callback ->
            val ad = interstitial ?: return@present false
            interstitial = null
            ad.fullScreenContentCallback = callback
            ad.show(activity)
            true
        }
    }

    /** Asks the policy, then lets [show] take its ad; [reload] fetches the next one once this one closes. */
    private fun present(userBusy: Boolean, reload: () -> Unit, show: (FullScreenContentCallback) -> Boolean) {
        if (showing || !sdk.ready.value) return
        // Claimed before the policy's reads suspend, so two breaks at once can't both show.
        showing = true
        scope.launch(Dispatchers.Main.immediate) {
            val callback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    runsSinceAd = 0
                    scope.launch { preferences.setLastFullScreenAdAt(now()) }
                }

                override fun onAdDismissedFullScreenContent() = closed(reload)
                override fun onAdFailedToShowFullScreenContent(error: AdError) = closed(reload)
            }
            if (!mayShow(userBusy) || !show(callback)) showing = false
        }
    }

    private fun closed(reload: () -> Unit) {
        showing = false
        reload()
    }

    private suspend fun mayShow(userBusy: Boolean): Boolean {
        val now = now()
        return policy.mayShow(
            FullScreenAdMoment(
                now = now,
                firstLaunchAt = preferences.firstLaunchAt(now),
                lastShownAt = preferences.lastFullScreenAdAt(),
                lastFailedRunAt = lastFailedRunAt,
                passActive = pass.isActive(),
                userBusy = userBusy,
            ),
        )
    }

    private fun preloadInterstitial() {
        if (!sdk.ready.value || interstitialLoading || interstitial != null || BuildConfig.INTERSTITIAL_UNIT_ID.isBlank()) return
        interstitialLoading = true
        InterstitialAd.load(context, BuildConfig.INTERSTITIAL_UNIT_ID, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitial = ad
                interstitialLoading = false
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                interstitialLoading = false
            }
        })
    }

    private fun preloadAppOpen() {
        if (!sdk.ready.value || appOpenLoading || appOpen != null || BuildConfig.APP_OPEN_UNIT_ID.isBlank()) return
        appOpenLoading = true
        AppOpenAd.load(context, BuildConfig.APP_OPEN_UNIT_ID, AdRequest.Builder().build(), object : AppOpenAd.AppOpenAdLoadCallback() {
            override fun onAdLoaded(ad: AppOpenAd) {
                appOpen = ad
                appOpenLoadedAt = now()
                appOpenLoading = false
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                appOpenLoading = false
            }
        })
    }

    private fun now(): Long = time.now().toEpochMilliseconds()

    private companion object {
        const val RUNS_BEFORE_EDITOR_BREAK = 4

        // Google: a loaded app-open ad is good for four hours.
        const val APP_OPEN_LIFETIME_MS = 4 * 60 * 60_000L
    }
}
