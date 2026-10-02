package solutions.laxmi.omnicompiler.feature.workspace.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniIconButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextButton
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.editor.CodeEditorState
import solutions.laxmi.omnicompiler.core.editor.SearchOptions
import solutions.laxmi.omnicompiler.feature.workspace.R

/** Query and options to open the find bar with. */
internal data class FindSeed(val query: String, val options: SearchOptions)

/**
 * Find (and optionally replace) in the open file, replacing the breadcrumb. Matching and replacing are Sora's
 * `EditorSearcher`; this only holds the query, the options and the replacement text.
 */
@Composable
internal fun FindReplaceBar(state: CodeEditorState, startWithReplace: Boolean, onClose: () -> Unit, seed: FindSeed? = null) {
    val colors = OmniTheme.colors
    // A seed (from project search) pre-fills the bar so the opened file's matches are highlighted straight away.
    var query by rememberSaveable(seed) { mutableStateOf(seed?.query.orEmpty()) }
    var replacement by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable(startWithReplace) { mutableStateOf(startWithReplace) }
    var caseSensitive by rememberSaveable(seed) { mutableStateOf(seed?.options?.caseSensitive ?: false) }
    var wholeWord by rememberSaveable(seed) { mutableStateOf(seed?.options?.wholeWord ?: false) }
    var regex by rememberSaveable(seed) { mutableStateOf(seed?.options?.regex ?: false) }
    val focus = remember { FocusRequester() }
    val options = SearchOptions(caseSensitive, wholeWord, regex)
    LaunchedEffect(Unit) { if (seed == null) focus.requestFocus() }
    // Re-run whenever the pattern or an option changes, so the count and highlights always match what's typed.
    LaunchedEffect(query, options) { state.search(query, options) }

    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .drawBehind { drawLine(colors.divider, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f) },
    ) {
        Row(Modifier.fillMaxWidth().height(40.dp).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(OmniIcons.Search, null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
            BarField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.editor_find_placeholder),
                imeAction = ImeAction.Search,
                onImeAction = state::findNext,
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            if (query.isNotEmpty()) {
                val label = when {
                    state.searchInvalid -> stringResource(R.string.editor_find_invalid)
                    state.searchMatches == 0 -> "0"
                    else -> stringResource(R.string.editor_find_count, state.searchIndex + 1, state.searchMatches)
                }
                Text(label, style = OmniTheme.typography.monoSmall, color = if (state.searchInvalid) colors.accentText else colors.textTertiary)
            }
            OmniIconButton(OmniIcons.ChevronUp, stringResource(R.string.editor_previous_match), state::findPrevious, iconSize = 15.dp)
            OmniIconButton(OmniIcons.ChevronDown, stringResource(R.string.editor_next_match), state::findNext, iconSize = 15.dp)
            OmniIconButton(OmniIcons.Edit, stringResource(R.string.editor_find_more), { expanded = !expanded }, selected = expanded, iconSize = 15.dp)
            OmniIconButton(OmniIcons.Close, stringResource(R.string.editor_close_find), {
                state.stopSearch()
                onClose()
            }, iconSize = 15.dp)
        }
        if (expanded) {
            Row(
                Modifier.fillMaxWidth().height(40.dp).padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OptionToggle("Aa", stringResource(R.string.editor_find_case), caseSensitive) { caseSensitive = it }
                OptionToggle("W", stringResource(R.string.editor_find_whole_word), wholeWord) { wholeWord = it }
                OptionToggle(".*", stringResource(R.string.editor_find_regex), regex) { regex = it }
                BarField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    placeholder = stringResource(R.string.editor_replace_placeholder),
                    imeAction = ImeAction.Done,
                    onImeAction = { state.replaceCurrent(replacement) },
                    modifier = Modifier.weight(1f),
                )
                OmniTextButton(stringResource(R.string.editor_replace), { state.replaceCurrent(replacement) }, enabled = state.searchMatches > 0)
                OmniTextButton(stringResource(R.string.editor_replace_all), { state.replaceAll(replacement) }, enabled = state.searchMatches > 0)
            }
        }
    }
}

@Composable
private fun BarField(value: String, onValueChange: (String) -> Unit, placeholder: String, imeAction: ImeAction, onImeAction: () -> Unit, modifier: Modifier) {
    val colors = OmniTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = OmniTheme.typography.mono.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onSearch = { onImeAction() }, onDone = { onImeAction() }),
        modifier = modifier.padding(horizontal = 8.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, style = OmniTheme.typography.mono, color = colors.textTertiary)
            inner()
        },
    )
}

/** Small square toggle for a search option (case, whole word, regex). */
@Composable
private fun OptionToggle(label: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = OmniTheme.colors
    Box(
        Modifier
            .size(30.dp)
            .background(if (checked) colors.surfaceRaised else colors.surface)
            .border(1.dp, if (checked) colors.accent else colors.border)
            .clickable(role = Role.Checkbox) { onChange(!checked) }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = OmniTheme.typography.monoSmall, color = if (checked) colors.accentText else colors.textSecondary)
    }
}
