package solutions.laxmi.omnicompiler.feature.workspace.console

import solutions.laxmi.omnicompiler.feature.workspace.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButtonStyle
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme

/** Input tab: free-form stdin for a plain run (no expected output, so no pass/fail). */
@Composable
internal fun InputPanel(
    stdin: String,
    onStdinChange: (String) -> Unit,
    running: Boolean,
    onRun: () -> Unit,
    onLoadFile: () -> Unit,
    onSaveAsTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OmniTheme.colors
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.input_explainer),
            style = OmniTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        Box(Modifier.weight(1f).fillMaxWidth().background(colors.background).padding(10.dp)) {
            BasicTextField(
                value = stdin,
                onValueChange = onStdinChange,
                textStyle = OmniTheme.typography.code.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxSize(),
                decorationBox = { inner ->
                    if (stdin.isEmpty()) Text(stringResource(R.string.input_placeholder), style = OmniTheme.typography.code, color = colors.textTertiary)
                    inner()
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OmniButton(stringResource(R.string.input_from_file), onLoadFile, Modifier.weight(1f), style = OmniButtonStyle.Secondary, leadingIcon = OmniIcons.Upload, trailingIcon = null)
            OmniButton(stringResource(R.string.input_save_as_test), onSaveAsTest, Modifier.weight(1f), style = OmniButtonStyle.Secondary, leadingIcon = OmniIcons.Plus, trailingIcon = null)
        }
        OmniButton(stringResource(R.string.input_run), onRun, enabled = !running, trailingIcon = OmniIcons.Play)
    }
}
