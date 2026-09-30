package solutions.laxmi.omnicompiler.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import solutions.laxmi.omnicompiler.core.designsystem.R

val Archivo = FontFamily(
    Font(R.font.archivo_regular, FontWeight.Normal),
    Font(R.font.archivo_semibold, FontWeight.SemiBold),
    Font(R.font.archivo_extrabold, FontWeight.ExtraBold),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
)

val FiraCode = FontFamily(
    Font(R.font.fira_code_regular, FontWeight.Normal),
    Font(R.font.fira_code_semibold, FontWeight.SemiBold),
)

val IbmPlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

/** Type roles used by the design: 800-weight headings with tight tracking, 600 labels, mono for code/meta. */
@Immutable
data class OmniTypography(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val titleSmall: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    val overline: TextStyle,
    val button: TextStyle,
    val code: TextStyle,
    val mono: TextStyle,
    val monoSmall: TextStyle,
    val badge: TextStyle,
)

private val base = TextStyle(fontFamily = Archivo)

val DefaultOmniTypography = OmniTypography(
    display = base.copy(fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em),
    headline = base.copy(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.025).em),
    title = base.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em),
    titleSmall = base.copy(fontSize = 13.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold),
    body = base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodyStrong = base.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    bodySmall = base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    label = base.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold),
    overline = base.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.em),
    button = base.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold),
    code = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, lineHeight = 18.sp),
    mono = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, lineHeight = 16.sp),
    monoSmall = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, lineHeight = 13.sp),
    badge = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold),
)

val LocalOmniTypography = staticCompositionLocalOf { DefaultOmniTypography }
