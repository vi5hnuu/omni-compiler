package solutions.laxmi.omnicompiler.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniListRow
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextField
import solutions.laxmi.omnicompiler.core.designsystem.component.SheetHandle
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.model.Language

/**
 * Name + language for a new project; it starts from that language's reference sample. Shared by the projects
 * list and the editor drawer so a project is never created without the user choosing what it is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectSheet(
    languages: List<Language>,
    onDismiss: () -> Unit,
    initialBase: String? = null,
    onCreate: (name: String, language: Language) -> Unit,
) {
    val colors = OmniTheme.colors
    var name by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var chosenBase by rememberSaveable { mutableStateOf(initialBase) }
    val runnable = languages.filter { it.defaultRuntime?.isRunnable == true }
    val chosen = runnable.firstOrNull { it.base == chosenBase }
    val listState = rememberLazyListState()
    // Bring a preselected language (the current project's) into view once the list has loaded.
    val preselectedIndex = runnable.indexOfFirst { it.base == initialBase }
    LaunchedEffect(preselectedIndex >= 0) { if (preselectedIndex >= 0) listState.scrollToItem(preselectedIndex) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RectangleShape,
        containerColor = colors.surface,
        scrimColor = colors.scrim,
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.new_project_title), style = OmniTheme.typography.title, color = colors.textPrimary, modifier = Modifier.padding(horizontal = 16.dp))
            OmniTextField(
                name, { name = it }, label = stringResource(R.string.new_project_name), placeholder = chosen?.let { "${it.base}-scratch" } ?: "two-sum",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            OmniTextField(query, { query = it }, placeholder = stringResource(R.string.new_project_search_languages), leadingIcon = OmniIcons.Search, modifier = Modifier.padding(horizontal = 16.dp))
            LazyColumn(Modifier.heightIn(max = 320.dp), state = listState) {
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
                stringResource(R.string.new_project_create),
                { chosen?.let { onCreate(name.trim(), it) } },
                Modifier.padding(horizontal = 16.dp),
                enabled = chosen != null,
                trailingIcon = OmniIcons.Check,
            )
        }
    }
}
