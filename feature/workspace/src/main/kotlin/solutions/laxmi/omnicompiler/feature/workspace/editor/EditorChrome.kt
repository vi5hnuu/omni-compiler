package solutions.laxmi.omnicompiler.feature.workspace.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniCompactButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniIconButton
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniDimens
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.editor.CodeEditorState
import solutions.laxmi.omnicompiler.core.model.SourceFile
import solutions.laxmi.omnicompiler.core.ui.fileBadgeFor

/** E1 app bar: menu, project + runtime picker, search, minimap toggle, overflow, Run. */
@Composable
internal fun ReadingTopBar(
    projectName: String,
    runtimeLabel: String,
    minimapOn: Boolean,
    runEnabled: Boolean,
    onMenu: () -> Unit,
    onRuntimeClick: () -> Unit,
    onSearch: () -> Unit,
    onToggleMinimap: () -> Unit,
    onRun: () -> Unit,
    overflow: @Composable () -> Unit,
) {
    val colors = OmniTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(OmniDimens.appBarHeight)
            .background(colors.surface)
            .padding(start = 2.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OmniIconButton(OmniIcons.Menu, "Open drawer", onMenu, size = 40.dp, tint = colors.textPrimary, iconSize = 18.dp)
        Column(
            Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClick = onRuntimeClick)
                .padding(vertical = 4.dp),
        ) {
            Text(projectName, style = OmniTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(runtimeLabel, style = OmniTheme.typography.label.copy(fontWeight = null), color = colors.textSecondary, maxLines = 1)
                Icon(OmniIcons.ChevronDown, null, tint = colors.textSecondary, modifier = Modifier.size(12.dp))
            }
        }
        OmniIconButton(OmniIcons.Search, "Find", onSearch)
        OmniIconButton(OmniIcons.Minimap, if (minimapOn) "Hide minimap" else "Show minimap", onToggleMinimap, selected = minimapOn)
        overflow()
        OmniCompactButton("Run", OmniIcons.Play, onRun, modifier = Modifier.padding(start = 4.dp), enabled = runEnabled)
    }
}

/** E2 compact bar shown while the keyboard is up: file name + dirty dot, undo/redo, Run. */
@Composable
internal fun TypingTopBar(
    fileName: String,
    dirty: Boolean,
    state: CodeEditorState,
    runEnabled: Boolean,
    onMenu: () -> Unit,
    onRun: () -> Unit,
) {
    val colors = OmniTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(OmniDimens.compactAppBarHeight)
            .background(colors.surface)
            .drawBehind { drawLine(colors.divider, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f) }
            .padding(start = 2.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OmniIconButton(OmniIcons.Menu, "Open drawer", onMenu, size = 40.dp, tint = colors.textPrimary, iconSize = 18.dp)
        Text(fileName, style = OmniTheme.typography.bodyStrong, color = colors.textPrimary, maxLines = 1)
        if (dirty) Box(Modifier.padding(start = 6.dp).size(5.dp).background(colors.textPrimary))
        Box(Modifier.weight(1f))
        OmniIconButton(OmniIcons.Undo, "Undo", state::undo, enabled = state.canUndo, iconSize = 16.dp)
        OmniIconButton(OmniIcons.Redo, "Redo", state::redo, enabled = state.canRedo, iconSize = 16.dp)
        OmniCompactButton("Run", OmniIcons.Play, onRun, modifier = Modifier.padding(start = 4.dp), enabled = runEnabled, height = 32.dp)
    }
}

/** File tabs (32 dp): active tab has a red top bar; long-press opens rename/delete for extra files. */
@Composable
internal fun FileTabs(
    files: List<SourceFile>,
    activeFileId: String?,
    entryShortCode: String?,
    dirtyFileId: String?,
    onSelect: (SourceFile) -> Unit,
    onFileMenu: (SourceFile) -> Unit,
    onAdd: () -> Unit,
) {
    val colors = OmniTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(OmniDimens.tabHeight)
            .background(colors.surface)
            .drawBehind { drawLine(colors.divider, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f) },
    ) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            files.forEach { file ->
                val active = file.id == activeFileId
                val badge = fileBadgeFor(file.name, entryShortCode, file.isEntry)
                Row(
                    Modifier
                        .fillMaxHeight()
                        .background(if (active) colors.background else Color.Transparent)
                        .drawBehind {
                            if (active) drawRect(colors.accent, size = size.copy(height = 2.dp.toPx()))
                            else drawLine(colors.divider, Offset(size.width - 0.5f, 0f), Offset(size.width - 0.5f, size.height), 1f)
                        }
                        .combinedClickable(role = Role.Tab, onClick = { onSelect(file) }, onLongClick = { onFileMenu(file) })
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        badge,
                        style = OmniTheme.typography.badge.copy(fontSize = OmniTheme.typography.monoSmall.fontSize * 0.95f),
                        color = if (active && file.isEntry) colors.accentText else colors.textTertiary,
                    )
                    Text(
                        file.name,
                        style = OmniTheme.typography.bodySmall.copy(fontWeight = if (active) androidx.compose.ui.text.font.FontWeight.SemiBold else null),
                        color = if (active) colors.textPrimary else colors.textSecondary,
                        maxLines = 1,
                    )
                    if (file.id == dirtyFileId) Box(Modifier.size(5.dp).background(colors.textSecondary))
                }
            }
        }
        OmniIconButton(OmniIcons.Plus, "New file", onAdd, size = 34.dp, iconSize = 14.dp)
    }
}

@Composable
internal fun Breadcrumb(fileName: String) {
    val colors = OmniTheme.colors
    Row(
        Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        val style = OmniTheme.typography.monoSmall
        Text("workspace", style = style, color = colors.textTertiary)
        Text("›", style = style, color = colors.textTertiary)
        Text(fileName, style = style, color = colors.textSecondary, maxLines = 1)
    }
}

/** Inline find bar replacing the breadcrumb while searching. */
@Composable
internal fun FindBar(state: CodeEditorState, onClose: () -> Unit) {
    val colors = OmniTheme.colors
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(colors.surface)
            .drawBehind { drawLine(colors.divider, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f) }
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(OmniIcons.Search, null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
        BasicTextField(
            value = query,
            onValueChange = {
                query = it
                state.search(it)
            },
            singleLine = true,
            textStyle = OmniTheme.typography.mono.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { state.findNext() }),
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp).focusRequester(focus),
            decorationBox = { inner ->
                if (query.isEmpty()) Text("Find in file", style = OmniTheme.typography.mono, color = colors.textTertiary)
                inner()
            },
        )
        if (query.isNotEmpty()) {
            val label = if (state.searchMatches == 0) "0" else "${state.searchIndex + 1}/${state.searchMatches}"
            Text(label, style = OmniTheme.typography.monoSmall, color = colors.textTertiary)
        }
        OmniIconButton(OmniIcons.ChevronUp, "Previous match", state::findPrevious, iconSize = 15.dp)
        OmniIconButton(OmniIcons.ChevronDown, "Next match", state::findNext, iconSize = 15.dp)
        OmniIconButton(OmniIcons.Close, "Close find", {
            state.stopSearch()
            onClose()
        }, iconSize = 15.dp)
    }
}

/** 20 dp status line: cursor, indentation, encoding and problem count. */
@Composable
internal fun EditorStatusBar(state: CodeEditorState, tabSize: Int, problems: Int) {
    val colors = OmniTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(OmniDimens.statusBarHeight)
            .background(colors.surface)
            .drawBehind { drawLine(colors.hairline, Offset(0f, 0f), Offset(size.width, 0f), 1f) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val style = OmniTheme.typography.monoSmall
        Text("Ln ${state.cursor.line}, Col ${state.cursor.column}", style = style, color = colors.textTertiary)
        Text("Spaces $tabSize", style = style, color = colors.textTertiary)
        Text("UTF-8", style = style, color = colors.textTertiary)
        Box(Modifier.weight(1f))
        if (problems > 0) Text(if (problems == 1) "1 problem" else "$problems problems", style = style, color = colors.accentText)
    }
}

@Composable
internal fun OverflowMenu(items: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        OmniIconButton(OmniIcons.MoreVertical, "More options", { open = true })
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = OmniTheme.colors.surfaceRaised,
            shape = androidx.compose.ui.graphics.RectangleShape,
        ) {
            items.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label, style = OmniTheme.typography.bodyStrong, color = OmniTheme.colors.textPrimary) },
                    onClick = {
                        open = false
                        action()
                    },
                    modifier = Modifier.width(220.dp),
                )
            }
        }
    }
}
