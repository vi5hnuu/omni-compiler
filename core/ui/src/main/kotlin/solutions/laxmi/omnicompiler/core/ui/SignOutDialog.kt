package solutions.laxmi.omnicompiler.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextButton
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme

/**
 * Confirms signing out. A guest account has no credentials to sign back in with, so for guests the dialog says the
 * run history will be lost and offers to create an account instead.
 */
@Composable
fun SignOutDialog(isGuest: Boolean, onConfirm: () -> Unit, onCreateAccount: () -> Unit, onDismiss: () -> Unit) {
    val colors = OmniTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = colors.surfaceRaised,
        title = { Text(stringResource(if (isGuest) R.string.sign_out_guest_title else R.string.sign_out_title), style = OmniTheme.typography.title, color = colors.textPrimary) },
        text = { Text(stringResource(if (isGuest) R.string.sign_out_guest_message else R.string.sign_out_message), style = OmniTheme.typography.body, color = colors.textSecondary) },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (isGuest) OmniTextButton(stringResource(R.string.sign_out_create_account), onCreateAccount)
                OmniTextButton(stringResource(R.string.sign_out_confirm), onConfirm, color = if (isGuest) colors.textSecondary else colors.accentText)
            }
        },
        dismissButton = { OmniTextButton(stringResource(R.string.common_cancel), onDismiss, color = colors.textSecondary) },
    )
}
