package solutions.laxmi.omnicompiler.core.designsystem.theme

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.window.core.layout.WindowSizeClass

/**
 * How much room the window gives, as Material's width classes; the one place screens ask, so breakpoints stay
 * consistent. [expanded] (≥ 840 dp: landscape tablets, open foldables, landscape phones) switches to side-by-side
 * layouts; [large] (≥ 1200 dp) has room to keep secondary panels open by default.
 */
@Immutable
data class WindowLayout(val expanded: Boolean, val large: Boolean)

@Composable
fun rememberWindowLayout(): WindowLayout {
    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    return WindowLayout(
        expanded = sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND),
        large = sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND),
    )
}
