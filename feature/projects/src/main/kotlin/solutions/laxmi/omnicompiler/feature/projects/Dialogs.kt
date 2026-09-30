package solutions.laxmi.omnicompiler.feature.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniListRow
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextField
import solutions.laxmi.omnicompiler.core.designsystem.component.SheetHandle
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.model.Language
import solutions.laxmi.omnicompiler.core.ui.LanguageTile

/** Name + language for a new project; it starts from that language's reference sample. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewProjectSheet(languages: List<Language>, onDismiss: () -> Unit, onCreate: (String, Language) -> Unit) {
    val colors = OmniTheme.colors
    var name by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var chosenBase by rememberSaveable { mutableStateOf<String?>(null) }
    val runnable = languages.filter { it.defaultRuntime?.isRunnable == true }
    val chosen = runnable.firstOrNull { it.base == chosenBase }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RectangleShape,
        containerColor = colors.surface,
        scrimColor = colors.scrim,
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("New project", style = OmniTheme.typography.title, color = colors.textPrimary, modifier = Modifier.padding(horizontal = 16.dp))
            OmniTextField(
                name, { name = it }, label = "Name", placeholder = chosen?.let { "${it.base}-scratch" } ?: "two-sum",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            OmniTextField(query, { query = it }, placeholder = "Search languages", leadingIcon = OmniIcons.Search, modifier = Modifier.padding(horizontal = 16.dp))
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(runnable.filter { query.isBlank() || it.info.name.contains(query, true) || it.base.contains(query, true) }, key = { it.base }) { language ->
                    OmniListRow(
                        title = language.info.name,
                        subtitle = language.defaultRuntime?.id,
                        selected = language.base == chosenBase,
                        leading = { LanguageTile(language.info.shortCode, selected = language.base == chosenBase) },
                        onClick = { chosenBase = language.base },
                    )
                }
            }
            OmniButton(
                "Create project",
                { chosen?.let { onCreate(name.trim(), it) } },
                Modifier.padding(horizontal = 16.dp),
                enabled = chosen != null,
                trailingIcon = OmniIcons.Check,
            )
        }
    }
}

@Composable
internal fun TextPromptDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = OmniTheme.colors.surfaceRaised,
        title = { Text(title, style = OmniTheme.typography.title, color = OmniTheme.colors.textPrimary) },
        text = { OmniTextField(value, { value = it }, textStyle = OmniTheme.typography.code) },
        confirmButton = { OmniTextButton("Save", { onConfirm(value) }, enabled = value.isNotBlank()) },
        dismissButton = { OmniTextButton("Cancel", onDismiss, color = OmniTheme.colors.textSecondary) },
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
        dismissButton = { OmniTextButton("Cancel", onDismiss, color = OmniTheme.colors.textSecondary) },
    )
}
