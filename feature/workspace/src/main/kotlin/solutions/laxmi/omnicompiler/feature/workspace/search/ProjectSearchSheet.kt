package solutions.laxmi.omnicompiler.feature.workspace.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniChip
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextField
import solutions.laxmi.omnicompiler.core.designsystem.component.SheetHandle
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.editor.SearchOptions
import solutions.laxmi.omnicompiler.feature.workspace.R

/** "Search in project": matches across every file, grouped by file; tapping one opens it at the match. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProjectSearchSheet(
    projectId: String,
    onOpen: (hit: ProjectSearchHit, query: String, options: SearchOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    val viewModel = hiltViewModel<ProjectSearchViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(projectId) { viewModel.open(projectId) }
    val colors = OmniTheme.colors
    // The field keeps what is typed; the view model searches a debounced copy.
    var typed by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RectangleShape,
        containerColor = colors.surface,
        scrimColor = colors.scrim,
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).navigationBarsPadding().imePadding()) {
            Text(
                stringResource(R.string.project_search_title),
                style = OmniTheme.typography.title,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            OmniTextField(
                typed,
                {
                    typed = it
                    viewModel.setQuery(it)
                },
                modifier = Modifier.padding(horizontal = 16.dp).focusRequester(focus),
                placeholder = stringResource(R.string.project_search_placeholder),
                leadingIcon = OmniIcons.Search,
                error = stringResource(R.string.project_search_invalid_regex).takeIf { state.invalidPattern },
            )
            OptionChips(state.options, viewModel::setOptions)
            Summary(state)
            LazyColumn(Modifier.fillMaxWidth()) {
                state.results.forEach { file ->
                    item(key = "file-" + file.fileId) {
                        Text(
                            stringResource(R.string.project_search_file, file.fileName, file.hits.size),
                            style = OmniTheme.typography.label,
                            color = colors.textSecondary,
                            modifier = Modifier.fillMaxWidth().background(colors.surfaceRaised).padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                    items(file.hits, key = { "${it.fileId}:${it.line}:${it.column}" }) { hit ->
                        HitRow(hit) { onOpen(hit, state.query, state.options) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionChips(options: SearchOptions, onChange: (SearchOptions) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OmniChip(stringResource(R.string.project_search_match_case), options.caseSensitive, { onChange(options.copy(caseSensitive = !options.caseSensitive)) })
        OmniChip(stringResource(R.string.project_search_whole_word), options.wholeWord, { onChange(options.copy(wholeWord = !options.wholeWord)) })
        OmniChip(stringResource(R.string.project_search_regex), options.regex, { onChange(options.copy(regex = !options.regex)) })
    }
}

@Composable
private fun Summary(state: ProjectSearchUiState) {
    if (state.query.isEmpty() || state.invalidPattern) return
    val files = state.results.size
    val text = when {
        state.matchCount == 0 -> stringResource(R.string.project_search_none)
        state.truncated -> stringResource(R.string.project_search_truncated, ProjectSearchViewModel.MAX_HITS)
        else -> stringResource(
            R.string.project_search_summary,
            pluralStringResource(R.plurals.project_search_match_count, state.matchCount, state.matchCount),
            pluralStringResource(R.plurals.project_search_file_count, files, files),
        )
    }
    Text(text, style = OmniTheme.typography.bodySmall, color = OmniTheme.colors.textTertiary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}

@Composable
private fun HitRow(hit: ProjectSearchHit, onClick: () -> Unit) {
    val colors = OmniTheme.colors
    val preview = buildAnnotatedString {
        append(hit.preview.substring(0, hit.matchStart))
        withStyle(SpanStyle(background = colors.accent.copy(alpha = 0.25f), fontWeight = FontWeight.SemiBold)) {
            append(hit.preview.substring(hit.matchStart, hit.matchEnd))
        }
        append(hit.preview.substring(hit.matchEnd))
    }
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(hit.line.toString(), style = OmniTheme.typography.monoSmall, color = colors.textTertiary, modifier = Modifier.width(36.dp))
        Text(preview, style = OmniTheme.typography.mono, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
