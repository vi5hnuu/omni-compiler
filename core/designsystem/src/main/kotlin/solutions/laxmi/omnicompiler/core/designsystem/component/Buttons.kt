package solutions.laxmi.omnicompiler.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniDimens
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme

enum class OmniButtonStyle { Primary, Secondary, Outline, Light }

/**
 * Full-width action button. Per the design, the label is flush-left and the trailing icon is pushed
 * to the right edge; disabled buttons fade to 45% instead of changing colour.
 */
@Composable
fun OmniButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: OmniButtonStyle = OmniButtonStyle.Primary,
    leadingIcon: ImageVector? = null,
    leadingPainter: Painter? = null,
    trailingIcon: ImageVector? = OmniIcons.ArrowRight,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = OmniDimens.buttonHeight,
) {
    val colors = OmniTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val (container, content) = when (style) {
        OmniButtonStyle.Primary -> (if (pressed) colors.accentStrong else colors.accent) to colors.onAccent
        OmniButtonStyle.Secondary -> (if (pressed) colors.surfaceRaised else colors.surfaceMuted) to colors.textPrimary
        OmniButtonStyle.Outline -> (if (pressed) colors.surfaceRaised else Color.Transparent) to colors.textPrimary
        OmniButtonStyle.Light -> (if (pressed) colors.textSecondary else colors.textPrimary) to colors.background
    }
    val clickable = enabled && !loading
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .background(container)
            .then(if (style == OmniButtonStyle.Outline) Modifier.border(1.dp, colors.borderStrong) else Modifier)
            .clickable(interaction, indication = null, enabled = clickable, role = Role.Button, onClick = onClick)
            .padding(horizontal = OmniDimens.space14),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OmniDimens.space10),
    ) {
        when {
            leadingPainter != null -> Icon(leadingPainter, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(18.dp))
            leadingIcon != null -> Icon(leadingIcon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        }
        Text(text, style = OmniTheme.typography.button, color = content, modifier = Modifier.weight(1f))
        when {
            loading -> OmniSpinner(color = content, size = 14.dp)
            trailingIcon != null -> Icon(trailingIcon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        }
    }
}

/** Compact solid red action used in app bars (Run / Stop). */
@Composable
fun OmniCompactButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 34.dp,
    container: Color = OmniTheme.colors.accent,
    content: Color = OmniTheme.colors.onAccent,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .height(height)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .background(if (pressed) OmniTheme.colors.accentStrong else container)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(11.dp))
        Text(text, style = OmniTheme.typography.button, color = content)
    }
}

/** Square icon target; [selected] shows the raised fill used for active toggles (e.g. minimap). */
@Composable
fun OmniIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    tint: Color = if (selected) OmniTheme.colors.textPrimary else OmniTheme.colors.textSecondary,
    size: Dp = OmniDimens.iconButton,
    iconSize: Dp = 17.dp,
) {
    val colors = OmniTheme.colors
    Box(
        modifier = modifier
            // Dense chrome keeps the small visual size; the touch target still meets the 48 dp minimum.
            .minimumInteractiveComponentSize()
            .size(size)
            .background(if (selected) colors.surfaceRaised else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (enabled) tint else colors.textDisabled, modifier = Modifier.size(iconSize))
    }
}

/** Text-only link in accent colour (e.g. "Forgot password?", "Create account"). */
@Composable
fun OmniTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = OmniTheme.colors.accentText,
    contentPadding: PaddingValues = PaddingValues(vertical = 6.dp),
) {
    Text(
        text = text,
        style = OmniTheme.typography.bodyStrong,
        color = if (enabled) color else OmniTheme.colors.textDisabled,
        modifier = modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(contentPadding),
    )
}

@Composable
fun OmniSpinner(modifier: Modifier = Modifier, color: Color = OmniTheme.colors.accent, size: Dp = 16.dp) {
    androidx.compose.material3.CircularProgressIndicator(
        modifier = modifier.size(size),
        color = color,
        strokeWidth = 2.dp,
        trackColor = Color.Transparent,
        strokeCap = androidx.compose.ui.graphics.StrokeCap.Butt,
    )
}

@Composable
fun HorizontalSpacer(width: Dp) = Box(Modifier.width(width))

internal const val DISABLED_ALPHA = 0.45f
