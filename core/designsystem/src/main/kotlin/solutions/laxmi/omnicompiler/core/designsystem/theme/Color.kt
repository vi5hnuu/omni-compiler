package solutions.laxmi.omnicompiler.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * "Signal" tokens from the omni compiler design: a black ground, one red accent, square corners.
 * Components read these semantic roles, never raw hex values.
 */
@Immutable
data class OmniColors(
    val canvas: Color,
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceKey: Color,
    val surfaceMuted: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val accent: Color,
    val accentText: Color,
    val accentStrong: Color,
    val onAccent: Color,
    val errorTint: Color,
    val hairline: Color,
    val divider: Color,
    val border: Color,
    val borderStrong: Color,
    val ring: Color,
    val scrim: Color,
    val gesturePill: Color,
    val dragHandle: Color,
)

val SignalColors = OmniColors(
    canvas = Color(0xFF050505),
    background = Color(0xFF000000),
    surface = Color(0xFF0B0B0B),
    surfaceRaised = Color(0xFF161515),
    surfaceKey = Color(0xFF1A1919),
    surfaceMuted = Color(0xFF242222),
    textPrimary = Color(0xFFF5F4F4),
    textSecondary = Color(0xFFB3AFAF),
    textTertiary = Color(0xFF7A7676),
    textDisabled = Color(0xFF4F4C4C),
    accent = Color(0xFFEC3013),
    accentText = Color(0xFFFF5A3F),
    accentStrong = Color(0xFF7C1405),
    onAccent = Color(0xFFFFFFFF),
    errorTint = Color(0x1FEC3013),
    hairline = Color(0x12FFFFFF),
    divider = Color(0x17FFFFFF),
    border = Color(0x29FFFFFF),
    borderStrong = Color(0x38FFFFFF),
    ring = Color(0xFFF5F4F4),
    scrim = Color(0x99000000),
    gesturePill = Color(0xFFB3AFAF),
    dragHandle = Color(0xFF4F4C4C),
)

val LocalOmniColors = staticCompositionLocalOf { SignalColors }
