package solutions.laxmi.omnicompiler.core.ads

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import solutions.laxmi.omnicompiler.core.common.TimeSource
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniColors
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** Native ads for list slots. */
interface NativeAdSource {
    /** Suspends until an ad is loaded; the caller then owns it and destroys it when its screen leaves. */
    suspend fun next(): NativeAd
}

/** Never hands out an ad (previews, tests). */
internal object NoNativeAds : NativeAdSource {
    override suspend fun next(): NativeAd = awaitCancellation()
}

/**
 * Keeps up to [POOL_SIZE] ads loaded, one request at a time, and only while lists ask for them. Ads older than
 * Google's one-hour validity are dropped instead of shown.
 */
@Singleton
internal class AdMobNativeAds @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sdk: AdsSdk,
    private val time: TimeSource,
) : NativeAdSource {

    private class Loaded(val ad: NativeAd, val at: Long)

    private val buffered = AtomicInteger(0)
    private val pool = Channel<Loaded>(POOL_SIZE) { undelivered ->
        buffered.decrementAndGet()
        undelivered.ad.destroy()
    }

    // Main thread only, like the loader's callbacks.
    private var loading = false
    private val loader: AdLoader by lazy {
        AdLoader.Builder(context, BuildConfig.NATIVE_UNIT_ID)
            .forNativeAd(::onLoaded)
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT).build())
            .build()
    }

    override suspend fun next(): NativeAd {
        if (BuildConfig.NATIVE_UNIT_ID.isBlank()) awaitCancellation()
        sdk.awaitReady()
        return withContext(Dispatchers.Main.immediate) {
            var fresh: NativeAd? = null
            while (fresh == null) {
                refill()
                val loaded = pool.receive()
                buffered.decrementAndGet()
                if (now() - loaded.at < AD_LIFETIME_MS) fresh = loaded.ad else loaded.ad.destroy()
            }
            refill()
            fresh
        }
    }

    private fun refill() {
        if (loading || buffered.get() >= POOL_SIZE) return
        loading = true
        loader.loadAd(AdRequest.Builder().build())
    }

    private fun onLoaded(ad: NativeAd) {
        loading = false
        buffered.incrementAndGet()
        if (pool.trySend(Loaded(ad, now())).isFailure) {
            buffered.decrementAndGet()
            ad.destroy()
        }
    }

    private fun now(): Long = time.now().toEpochMilliseconds()

    private companion object {
        const val POOL_SIZE = 3
        const val AD_LIFETIME_MS = 55 * 60_000L
    }
}

/**
 * The native ads one list screen shows, one per slot, kept for the screen's lifetime so scrolling back doesn't load
 * again; [rememberNativeAdSlots] destroys them when the screen leaves.
 */
@Stable
class NativeAdSlots internal constructor(private val source: NativeAdSource) {
    private val ads = mutableStateMapOf<Int, NativeAd>()
    private var released = false

    internal fun adAt(slot: Int): NativeAd? = ads[slot]

    internal suspend fun fill(slot: Int) {
        if (slot in ads) return
        val ad = source.next()
        if (released || slot in ads) ad.destroy() else ads[slot] = ad
    }

    internal fun release() {
        released = true
        ads.values.forEach(NativeAd::destroy)
        ads.clear()
    }

    internal companion object {
        /** One ad after every this many rows. */
        const val EVERY = 8

        /** The slot that follows row [index], or null when no ad goes there. */
        fun slotAfter(index: Int): Int? = (index + 1).takeIf { it % EVERY == 0 }?.let { it / EVERY - 1 }
    }
}

@Composable
fun rememberNativeAdSlots(): NativeAdSlots {
    val source = LocalAds.current.native
    val slots = remember(source) { NativeAdSlots(source) }
    DisposableEffect(slots) { onDispose { slots.release() } }
    return slots
}

/**
 * An ad row after list row [index] when that position holds a slot (every [NativeAdSlots.EVERY] rows). Placed inside
 * the row's own item so list keys and paging stay untouched. Renders nothing without consent or a loaded ad.
 */
@Composable
fun NativeAdSlot(slots: NativeAdSlots, index: Int, modifier: Modifier = Modifier) {
    val slot = NativeAdSlots.slotAfter(index) ?: return
    val allowed by LocalAds.current.canRequestAds.collectAsStateWithLifecycle()
    if (!allowed) return
    LaunchedEffect(slots, slot) { slots.fill(slot) }
    slots.adAt(slot)?.let { NativeAdRow(it, modifier) }
}

/** A native ad laid out like a list row, with the "Ad" badge the policy requires; the SDK adds the AdChoices mark. */
@Composable
private fun NativeAdRow(ad: NativeAd, modifier: Modifier) {
    val colors = OmniTheme.colors
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind { drawLine(colors.hairline, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f) },
        factory = { context -> LayoutInflater.from(context).inflate(R.layout.native_ad_row, null) as NativeAdView },
        // No onRelease destroy: the same ad is bound again when its row scrolls back; NativeAdSlots destroys the ad.
        update = { view -> view.bind(ad, colors) },
    )
}

private fun NativeAdView.bind(ad: NativeAd, colors: OmniColors) {
    val headline = findViewById<TextView>(R.id.ad_headline)
    val body = findViewById<TextView>(R.id.ad_body)
    val icon = findViewById<ImageView>(R.id.ad_icon)
    val action = findViewById<TextView>(R.id.ad_call_to_action)
    val badge = findViewById<TextView>(R.id.ad_badge)

    headline.text = ad.headline
    headline.setTextColor(colors.textPrimary.toArgb())
    headlineView = headline
    body.text = ad.body
    body.isVisible = !ad.body.isNullOrBlank()
    body.setTextColor(colors.textTertiary.toArgb())
    bodyView = body
    icon.setImageDrawable(ad.icon?.drawable)
    icon.isVisible = ad.icon != null
    iconView = icon
    action.text = ad.callToAction
    action.isVisible = !ad.callToAction.isNullOrBlank()
    action.setTextColor(colors.onAccent.toArgb())
    action.background = GradientDrawable().apply { setColor(colors.accent.toArgb()) }
    callToActionView = action
    badge.setTextColor(colors.accentText.toArgb())
    badge.background = GradientDrawable().apply { setStroke(resources.displayMetrics.density.toInt().coerceAtLeast(1), colors.accentText.toArgb()) }
    // Registers the asset views above for clicks and impressions; must come after them.
    setNativeAd(ad)
}
