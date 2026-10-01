package solutions.laxmi.omnicompiler.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** App chrome in dark (Signal) or light (Paper); editor themes are chosen separately (see `EditorTheme`). */
@Composable
fun OmniTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val colors = if (darkTheme) SignalColors else PaperColors
    // Material components we still use (menus, snackbars, sliders) inherit the palette and square shapes.
    val material = (if (darkTheme) darkColorScheme() else lightColorScheme()).copy(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        secondary = colors.textSecondary,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceRaised,
        onSurfaceVariant = colors.textSecondary,
        surfaceContainerLowest = colors.surface,
        surfaceContainerLow = colors.surface,
        surfaceContainer = colors.surfaceRaised,
        surfaceContainerHigh = colors.surfaceRaised,
        surfaceContainerHighest = colors.surfaceMuted,
        outline = colors.border,
        outlineVariant = colors.divider,
        error = colors.accent,
        onError = colors.onAccent,
        scrim = colors.scrim,
        // Snackbars use the inverse roles; without these they fall back to Material's baseline palette.
        inverseSurface = colors.textPrimary,
        inverseOnSurface = colors.background,
        // The action sits on the inverted surface, so it takes the opposite palette's readable accent.
        inversePrimary = (if (darkTheme) PaperColors else SignalColors).accentText,
        // No tonal tint: elevated menus and sheets keep the palette's flat surfaces.
        surfaceTint = Color.Transparent,
    )
    val square = RoundedCornerShape(0.dp)
    CompositionLocalProvider(
        LocalOmniColors provides colors,
        LocalOmniTypography provides DefaultOmniTypography,
        LocalTextSelectionColors provides TextSelectionColors(colors.accent, colors.accent.copy(alpha = 0.35f)),
    ) {
        MaterialTheme(
            colorScheme = material,
            shapes = Shapes(square, square, square, square, square),
            typography = MaterialTypography,
            content = content,
        )
    }
}

object OmniTheme {
    val colors: OmniColors
        @Composable @ReadOnlyComposable get() = LocalOmniColors.current

    val typography: OmniTypography
        @Composable @ReadOnlyComposable get() = LocalOmniTypography.current
}

/** Material components (menus, snackbars, dialogs, text fields' defaults) speak the app's typeface too. */
private val MaterialTypography = with(DefaultOmniTypography) {
    Typography(
        displayLarge = display, displayMedium = display, displaySmall = display,
        headlineLarge = headline, headlineMedium = headline, headlineSmall = headline,
        titleLarge = title, titleMedium = title, titleSmall = titleSmall,
        bodyLarge = body, bodyMedium = body, bodySmall = bodySmall,
        labelLarge = button, labelMedium = label, labelSmall = label,
    )
}
