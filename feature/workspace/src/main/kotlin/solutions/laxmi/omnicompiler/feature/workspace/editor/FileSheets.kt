package solutions.laxmi.omnicompiler.feature.workspace.editor

import solutions.laxmi.omnicompiler.core.ui.R as CommonR
import androidx.annotation.StringRes
import solutions.laxmi.omnicompiler.feature.workspace.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import solutions.laxmi.omnicompiler.core.designsystem.component.InfoBanner
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButtonStyle
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniChip
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextField
import solutions.laxmi.omnicompiler.core.designsystem.component.SheetHandle
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.model.LanguageInfo

private enum class NewFileKind(@StringRes val labelRes: Int) {
    Empty(R.string.editor_new_file_kind_empty),
    HeaderPair(R.string.editor_new_file_kind_header),
    Data(R.string.editor_new_file_kind_data),
}

/** Design W2: name, how to import it from the entry file, start-from template, toolchain warning. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewFileSheet(
    projectName: String,
    language: LanguageInfo?,
    entryExtension: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, content: String) -> Unit,
    onCreateHeaderPair: (baseName: String, sourceExtension: String) -> Unit,
) {
    val colors = OmniTheme.colors
    val supportsHeaders = language?.base == "c" || language?.base == "cpp"
    var kind by rememberSaveable { mutableStateOf(NewFileKind.Empty) }
    var name by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RectangleShape,
        containerColor = colors.surface,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text(stringResource(R.string.editor_new_file_title, projectName), style = OmniTheme.typography.title, color = colors.textPrimary)
                Text(stringResource(R.string.editor_workspace_path), style = OmniTheme.typography.mono, color = colors.textTertiary)
            }
            val placeholder = when (kind) {
                NewFileKind.Empty -> "helper.$entryExtension"
                NewFileKind.HeaderPair -> "solver"
                NewFileKind.Data -> "input.txt"
            }
            OmniTextField(
                value = name,
                onValueChange = { name = it.trim() },
                label = stringResource(if (kind == NewFileKind.HeaderPair) R.string.editor_new_file_base_name else R.string.editor_new_file_name),
                placeholder = placeholder,
                textStyle = OmniTheme.typography.code,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            )
            if (language != null) {
                Column(
                    Modifier.fillMaxWidth().background(colors.background).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(R.string.editor_new_file_import_hint).uppercase(), style = OmniTheme.typography.overline, color = colors.textTertiary)
                    Text(language.importHint, style = OmniTheme.typography.mono, color = colors.textPrimary)
                }
            }
            Text(stringResource(R.string.editor_new_file_start_from).uppercase(), style = OmniTheme.typography.overline, color = colors.textTertiary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NewFileKind.entries
                    .filter { it != NewFileKind.HeaderPair || supportsHeaders }
                    .forEach { option -> OmniChip(stringResource(option.labelRes), selected = kind == option, onClick = { kind = option }) }
            }
            if (language != null && !language.multiFileSupported) {
                InfoBanner(
                    icon = OmniIcons.Alert,
                    text = stringResource(R.string.editor_new_file_single_entry, language.name),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                OmniButton(stringResource(CommonR.string.common_cancel), onDismiss, Modifier.weight(1f), style = OmniButtonStyle.Secondary, trailingIcon = null)
                OmniButton(
                    text = stringResource(R.string.editor_new_file_create),
                    enabled = name.isNotBlank(),
                    onClick = {
                        when (kind) {
                            NewFileKind.HeaderPair -> onCreateHeaderPair(name.substringBeforeLast('.'), entryExtension)
                            NewFileKind.Data -> onCreate(if ('.' in name) name else "$name.txt", "")
                            NewFileKind.Empty -> onCreate(name, "")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    trailingIcon = OmniIcons.Check,
                )
            }
        }
    }
}

/** Single-field rename dialog used for projects and files. */
@Composable
internal fun RenameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = OmniTheme.colors.surfaceRaised,
        title = { Text(title, style = OmniTheme.typography.title, color = OmniTheme.colors.textPrimary) },
        text = {
            OmniTextField(
                value = value,
                onValueChange = { value = it },
                textStyle = OmniTheme.typography.code,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            )
        },
        confirmButton = { OmniTextButton(stringResource(CommonR.string.common_save), { onConfirm(value) }, enabled = value.isNotBlank()) },
        dismissButton = { OmniTextButton(stringResource(CommonR.string.common_cancel), onDismiss, color = OmniTheme.colors.textSecondary) },
    )
}

/** Confirmation for destructive actions. */
@Composable
internal fun ConfirmDialog(title: String, message: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = OmniTheme.colors.surfaceRaised,
        title = { Text(title, style = OmniTheme.typography.title, color = OmniTheme.colors.textPrimary) },
        text = { Text(message, style = OmniTheme.typography.body, color = OmniTheme.colors.textSecondary) },
        confirmButton = { OmniTextButton(confirmLabel, onConfirm) },
        dismissButton = { OmniTextButton(stringResource(CommonR.string.common_cancel), onDismiss, color = OmniTheme.colors.textSecondary) },
    )
}
