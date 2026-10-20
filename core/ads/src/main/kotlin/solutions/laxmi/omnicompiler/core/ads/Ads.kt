package solutions.laxmi.omnicompiler.core.ads

import android.app.Activity
import androidx.compose.runtime.staticCompositionLocalOf
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ads for the whole app. Consent comes first; nothing loads until ads may be requested. Screens place banners
 * ([AdBanner]) and list slots ([NativeAdSlot]), report breaks and runs, and offer the [pass]; whether a full-screen ad
 * actually appears is decided in one place, [FullScreenAdPolicy].
 */
interface Ads {
    val canRequestAds: StateFlow<Boolean>

    /** True when the user must be able to reopen the consent choices (shown as "Privacy choices" in Settings). */
    val privacyOptionsRequired: StateFlow<Boolean>

    val pass: AdFreePass
    val native: NativeAdSource

    /** Runs the consent flow and starts the SDK; call from the activity on every start (it is cheap after the first). */
    fun start(activity: Activity)

    fun showPrivacyOptions(activity: Activity)

    /** A natural break (another project opened, a project started from an example). Shows a preloaded ad if allowed. */
    fun onNaturalBreak(activity: Activity, userBusy: Boolean = false)

    /** The user is leaving the editor for another screen; a break once they have run their code a few times. */
    fun onLeavingEditor(activity: Activity)

    /** A run the user watched has finished; a [failed] one keeps full-screen ads away while they fix it. */
    fun onRunFinished(failed: Boolean)

    fun onBackground()

    /** The app is in front again; [openedForContent] when it came up to open a file from another app. */
    fun onForeground(activity: Activity, openedForContent: Boolean)
}

/** No ads (previews, tests). */
object NoAds : Ads {
    override val canRequestAds: StateFlow<Boolean> = MutableStateFlow(false)
    override val privacyOptionsRequired: StateFlow<Boolean> = MutableStateFlow(false)
    override val pass: AdFreePass = NoAdFreePass
    override val native: NativeAdSource = NoNativeAds
    override fun start(activity: Activity) = Unit
    override fun showPrivacyOptions(activity: Activity) = Unit
    override fun onNaturalBreak(activity: Activity, userBusy: Boolean) = Unit
    override fun onLeavingEditor(activity: Activity) = Unit
    override fun onRunFinished(failed: Boolean) = Unit
    override fun onBackground() = Unit
    override fun onForeground(activity: Activity, openedForContent: Boolean) = Unit
}

private object NoAdFreePass : AdFreePass {
    override val available: StateFlow<Boolean> = MutableStateFlow(false)
    override val state: StateFlow<PassState> = MutableStateFlow(PassState.Unavailable)
    override fun retry() = Unit
    override fun watch(activity: Activity, onResult: (PassResult) -> Unit) = onResult(PassResult.NotReady)
}

/** Provided at the app root so any screen can place an ad or report a break. */
val LocalAds = staticCompositionLocalOf<Ads> { NoAds }

@Singleton
internal class AdMobAds @Inject constructor(
    private val sdk: AdsSdk,
    private val fullScreen: FullScreenAds,
    override val pass: RewardedAdFreePass,
    override val native: AdMobNativeAds,
) : Ads {
    override val canRequestAds: StateFlow<Boolean> get() = sdk.canRequestAds
    override val privacyOptionsRequired: StateFlow<Boolean> get() = sdk.privacyOptionsRequired

    override fun start(activity: Activity) = sdk.start(activity)
    override fun showPrivacyOptions(activity: Activity) = sdk.showPrivacyOptions(activity)
    override fun onNaturalBreak(activity: Activity, userBusy: Boolean) = fullScreen.onNaturalBreak(activity, userBusy)
    override fun onLeavingEditor(activity: Activity) = fullScreen.onLeavingEditor(activity)
    override fun onRunFinished(failed: Boolean) = fullScreen.onRunFinished(failed)
    override fun onBackground() = fullScreen.onBackground()
    override fun onForeground(activity: Activity, openedForContent: Boolean) = fullScreen.onForeground(activity, openedForContent)
}

@Module
@InstallIn(SingletonComponent::class)
internal interface AdsModule {
    @Binds
    fun bindsAds(impl: AdMobAds): Ads
}
