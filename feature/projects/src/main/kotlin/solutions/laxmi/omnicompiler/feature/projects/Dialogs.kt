package solutions.laxmi.omnicompiler.feature.projects

import solutions.laxmi.omnicompiler.core.ui.R as CommonR
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.RectangleShape
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextField
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme

@Composable
internal fun TextPromptDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = OmniTheme.colors.surfaceRaised,
        title = { Text(title, style = OmniTheme.typography.title, color = OmniTheme.colors.textPrimary) },
        text = { OmniTextField(value, { value = it }, textStyle = OmniTheme.typography.code) },
        confirmButton = { OmniTextButton(stringResource(CommonR.string.common_save), { onConfirm(value) }, enabled = value.isNotBlank()) },
        dismissButton = { OmniTextButton(stringResource(CommonR.string.common_cancel), onDismiss, color = OmniTheme.colors.textSecondary) },
    )
}

@Composable
internal fun ConfirmPrompt(title: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = OmniTheme.colors.surfaceRaised,
        title = { Text(title, style = OmniTheme.typography.title, color = OmniTheme.colors.textPrimary) },
        text = { Text(message, style = OmniTheme.typography.body, color = OmniTheme.colors.textSecondary) },
        confirmButton = { OmniTextButton(confirm, onConfirm) },
        dismissButton = { OmniTextButton(stringResource(CommonR.string.common_cancel), onDismiss, color = OmniTheme.colors.textSecondary) },
    )
}
