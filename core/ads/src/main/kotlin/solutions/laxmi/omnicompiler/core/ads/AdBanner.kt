package solutions.laxmi.omnicompiler.core.ads

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Anchored adaptive banner for the bottom of list screens. Renders nothing until ads may be requested
 * (consent) or when no unit id is configured. Kept apart from buttons by [spacing] (accidental-click policy).
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier, spacing: Int = 8) {
    val ads = LocalAds.current
    val allowed by ads.canRequestAds.collectAsStateWithLifecycle()
    if (!allowed || BuildConfig.BANNER_UNIT_ID.isBlank()) return
    BoxWithConstraints(modifier.fillMaxWidth().padding(top = spacing.dp)) {
        val width = maxWidth.value.toInt()
        var view by remember { mutableStateOf<AdView?>(null) }
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                AdView(context).apply {
                    adUnitId = BuildConfig.BANNER_UNIT_ID
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, width))
                    loadAd(AdRequest.Builder().build())
                    view = this
                }
            },
            onRelease = { it.destroy() },
        )
        LifecycleResumeEffect(Unit) {
            view?.resume()
            onPauseOrDispose { view?.pause() }
        }
    }
}
