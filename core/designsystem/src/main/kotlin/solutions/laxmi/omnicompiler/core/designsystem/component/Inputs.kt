package solutions.laxmi.omnicompiler.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import solutions.laxmi.omnicompiler.core.designsystem.R
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniDimens
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme

/**
 * Labelled field: 44 dp, hairline border that becomes a 2 dp white ring on focus, red caret.
 * [trailingAction] renders a link on the label row (e.g. "Forgot password?").
 */
@Composable
fun OmniTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    error: String? = null,
    supporting: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    labelAction: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    textStyle: TextStyle = OmniTheme.typography.body,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = OmniTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ring = when {
        error != null -> colors.accent
        focused -> colors.ring
        else -> colors.border
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null || labelAction != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (label != null) Text(label, style = OmniTheme.typography.label, color = colors.textSecondary, modifier = Modifier.weight(1f))
                labelAction?.invoke()
            }
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = textStyle.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interaction,
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = OmniDimens.inputHeight)
                        .background(colors.surface)
                        .border(if (focused || error != null) OmniDimens.focusRing else 1.dp, ring)
                        // A trailing control brings its own touch target, so the row trims its padding to keep 44 dp.
                        .padding(horizontal = OmniDimens.space12, vertical = if (trailing != null) 2.dp else 10.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(OmniDimens.space10),
                ) {
                    if (leadingIcon != null) Icon(leadingIcon, null, tint = colors.textTertiary, modifier = Modifier.size(16.dp))
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = textStyle, color = colors.textTertiary)
                        }
                        inner()
                    }
                    trailing?.invoke()
                }
            },
        )
        when {
            error != null -> Text(error, style = OmniTheme.typography.bodySmall, color = colors.accentText)
            supporting != null -> Text(supporting, style = OmniTheme.typography.bodySmall, color = colors.textTertiary)
        }
    }
}

@Composable
fun OmniPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = stringResource(R.string.ds_password),
    placeholder: String? = null,
    error: String? = null,
    supporting: String? = null,
    labelAction: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OmniTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        error = error,
        supporting = supporting,
        labelAction = labelAction,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailing = {
            Box(
                Modifier.size(OmniDimens.touchTarget).clickable(role = Role.Button) { visible = !visible },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (visible) OmniIcons.EyeOff else OmniIcons.Eye,
                    contentDescription = stringResource(if (visible) R.string.ds_hide_password else R.string.ds_show_password),
                    tint = OmniTheme.colors.textTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    )
}
