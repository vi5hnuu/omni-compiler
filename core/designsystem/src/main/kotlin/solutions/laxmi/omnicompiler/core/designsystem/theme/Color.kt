package solutions.laxmi.omnicompiler.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * One outcome colour family: a solid [fill] with its [onFill] text (badges, banners), [text] for coloured
 * words on the page background, [tint] for row washes and [highlight] for marked text (diff lines).
 */
@Immutable
data class StatusTone(val fill: Color, val onFill: Color, val text: Color, val tint: Color, val highlight: Color)

/** Verdict families; every screen maps a verdict through `core/ui` to one of these, never picks colours itself. */
@Immutable
data class StatusColors(
    /** AC. */
    val accepted: StatusTone,
    /** WA, RE, IE. */
    val rejected: StatusTone,
    /** TLE, MLE. */
    val limit: StatusTone,
    /** CE. */
    val compile: StatusTone,
)

/**
 * Tokens from the omni compiler design ("Signal" in dark, its paper counterpart in light): one red accent,
 * square corners. Components read these semantic roles, never raw hex values.
 */
@Immutable
data class OmniColors(
    val isDark: Boolean,
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
    val hairline: Color,
    val divider: Color,
    val border: Color,
    val borderStrong: Color,
    val ring: Color,
    val scrim: Color,
    val gesturePill: Color,
    val dragHandle: Color,
    val status: StatusColors,
)

val SignalColors = OmniColors(
    isDark = true,
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
    hairline = Color(0x12FFFFFF),
    divider = Color(0x17FFFFFF),
    border = Color(0x29FFFFFF),
    borderStrong = Color(0x38FFFFFF),
    ring = Color(0xFFF5F4F4),
    scrim = Color(0x99000000),
    gesturePill = Color(0xFFB3AFAF),
    dragHandle = Color(0xFF4F4C4C),
    status = StatusColors(
        accepted = StatusTone(Color(0xFF22C55E), Color(0xFF03140A), Color(0xFF4ADE80), Color(0x1F22C55E), Color(0xFF14532D)),
        rejected = StatusTone(Color(0xFFEC3013), Color(0xFFFFFFFF), Color(0xFFFF5A3F), Color(0x1FEC3013), Color(0xFF7C1405)),
        limit = StatusTone(Color(0xFFF59E0B), Color(0xFF1A1000), Color(0xFFFBBF24), Color(0x1FF59E0B), Color(0xFF78350F)),
        compile = StatusTone(Color(0xFF8B5CF6), Color(0xFFFFFFFF), Color(0xFFA78BFA), Color(0x1F8B5CF6), Color(0xFF4C1D95)),
    ),
)

/** Light theme: white ground, near-black text, the same red accent darkened where it sits on white text. */
val PaperColors = OmniColors(
    isDark = false,
    canvas = Color(0xFFEFEEEE),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF8F7F7),
    surfaceRaised = Color(0xFFF0EFEF),
    surfaceKey = Color(0xFFE8E6E6),
    surfaceMuted = Color(0xFFE0DEDE),
    textPrimary = Color(0xFF121111),
    textSecondary = Color(0xFF4F4C4C),
    textTertiary = Color(0xFF726E6E),
    textDisabled = Color(0xFFB3AFAF),
    accent = Color(0xFFE02D12),
    accentText = Color(0xFFC0260D),
    accentStrong = Color(0xFFA81F09),
    onAccent = Color(0xFFFFFFFF),
    hairline = Color(0x0F000000),
    divider = Color(0x17000000),
    border = Color(0x26000000),
    borderStrong = Color(0x3D000000),
    ring = Color(0xFF121111),
    scrim = Color(0x66000000),
    gesturePill = Color(0xFF4F4C4C),
    dragHandle = Color(0xFFB3AFAF),
    status = StatusColors(
        accepted = StatusTone(Color(0xFF15803D), Color(0xFFFFFFFF), Color(0xFF15803D), Color(0x1A15803D), Color(0xFFBBF7D0)),
        rejected = StatusTone(Color(0xFFD92D10), Color(0xFFFFFFFF), Color(0xFFC0260D), Color(0x14D92D10), Color(0xFFFFD4CB)),
        limit = StatusTone(Color(0xFFB45309), Color(0xFFFFFFFF), Color(0xFFB45309), Color(0x1AB45309), Color(0xFFFDE68A)),
        compile = StatusTone(Color(0xFF7C3AED), Color(0xFFFFFFFF), Color(0xFF6D28D9), Color(0x147C3AED), Color(0xFFDDD6FE)),
    ),
)

val LocalOmniColors = staticCompositionLocalOf { SignalColors }
