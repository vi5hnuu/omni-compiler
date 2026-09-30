package solutions.laxmi.omnicompiler.core.ads

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import solutions.laxmi.omnicompiler.core.common.ApplicationScope
import solutions.laxmi.omnicompiler.core.common.Dispatcher
import solutions.laxmi.omnicompiler.core.common.OmniDispatcher
import solutions.laxmi.omnicompiler.core.common.TimeSource
import solutions.laxmi.omnicompiler.core.datastore.PreferencesStore
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ads for the whole app. Consent comes first (Google's UMP form where the law requires it); the SDK starts only
 * once ads may be requested. Screens show banners through [AdBanner] and ask for an interstitial only at
 * natural breaks through [onNaturalBreak]; [InterstitialPolicy] decides whether one actually appears.
 */
interface Ads {
    val canRequestAds: StateFlow<Boolean>

    /** True when the user must be able to reopen the consent choices (shown as "Privacy choices" in Settings). */
    val privacyOptionsRequired: StateFlow<Boolean>

    /** Runs the consent flow and starts the SDK; call from the activity on every start (it is cheap after the first). */
    fun start(activity: Activity)

    fun showPrivacyOptions(activity: Activity)

    /** A natural break (another project opened, a project started from an example). Shows a preloaded ad if allowed. */
    fun onNaturalBreak(activity: Activity, userBusy: Boolean = false)
}

/** No ads (previews, tests). */
object NoAds : Ads {
    override val canRequestAds: StateFlow<Boolean> = MutableStateFlow(false)
    override val privacyOptionsRequired: StateFlow<Boolean> = MutableStateFlow(false)
    override fun start(activity: Activity) = Unit
    override fun showPrivacyOptions(activity: Activity) = Unit
    override fun onNaturalBreak(activity: Activity, userBusy: Boolean) = Unit
}

/** Provided at the app root so any screen can place a banner or report a natural break. */
val LocalAds = staticCompositionLocalOf<Ads> { NoAds }

@Singleton
internal class AdMobAds @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: PreferencesStore,
    private val time: TimeSource,
    @ApplicationScope private val scope: CoroutineScope,
    @Dispatcher(OmniDispatcher.IO) private val io: CoroutineDispatcher,
) : Ads {

    private val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(context)
    private val allowed = MutableStateFlow(consent.canRequestAds())
    private val optionsRequired = MutableStateFlow(false)
    override val canRequestAds: StateFlow<Boolean> = allowed.asStateFlow()
    override val privacyOptionsRequired: StateFlow<Boolean> = optionsRequired.asStateFlow()

    private val sdkStarted = AtomicBoolean(false)
    private val policy = InterstitialPolicy()
    private var interstitial: InterstitialAd? = null
    private var loading = false

    override fun start(activity: Activity) {
        consent.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ -> onConsentKnown() }
        }, { _ ->
            // No network or a UMP outage: fall back to what was decided before (canRequestAds is cached).
            onConsentKnown()
        })
        // Consent from an earlier session lets ads start in parallel with the refresh above.
        if (consent.canRequestAds()) onConsentKnown()
    }

    private fun onConsentKnown() {
        allowed.value = consent.canRequestAds()
        optionsRequired.value = consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (!allowed.value || !sdkStarted.compareAndSet(false, true)) return
        scope.launch(Dispatchers.Main.immediate) {
            // Initialisation does disk work; keep it off the main thread.
            withContext(io) { MobileAds.initialize(context) }
            preloadInterstitial()
        }
    }

    override fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { _ -> allowed.value = consent.canRequestAds() }
    }

    override fun onNaturalBreak(activity: Activity, userBusy: Boolean) {
        val ad = interstitial ?: return preloadInterstitial()
        // Main: the ad state is also touched by SDK callbacks there, and show() must run on it.
        scope.launch(Dispatchers.Main.immediate) {
            val now = time.now().toEpochMilliseconds()
            val first = preferences.firstLaunchAt(now)
            if (!policy.mayShow(now, first, preferences.lastInterstitialAt(), userBusy)) return@launch
            interstitial = null
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() = preloadInterstitial()
                override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) = preloadInterstitial()
            }
            preferences.setLastInterstitialAt(now)
            ad.show(activity)
        }
    }

    private fun preloadInterstitial() {
        if (!allowed.value || !sdkStarted.get() || loading || interstitial != null || BuildConfig.INTERSTITIAL_UNIT_ID.isBlank()) return
        loading = true
        InterstitialAd.load(context, BuildConfig.INTERSTITIAL_UNIT_ID, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitial = ad
                loading = false
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                loading = false
            }
        })
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface AdsModule {
    @Binds
    fun bindsAds(impl: AdMobAds): Ads
}
