package solutions.laxmi.omnicompiler.feature.workspace.editor

import androidx.annotation.StringRes
import solutions.laxmi.omnicompiler.feature.workspace.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.rememberScrollState
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
import solutions.laxmi.omnicompiler.core.designsystem.component.SheetHandle
import solutions.laxmi.omnicompiler.core.designsystem.component.SectionLabel
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniListRow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniCompactButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniIconButton
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniDimens
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.editor.CodeEditorState
import solutions.laxmi.omnicompiler.core.model.FileHeader
import solutions.laxmi.omnicompiler.core.model.ProjectPaths
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
    runButton: RunButtonState = RunButtonState.Run,
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
        OmniIconButton(OmniIcons.Menu, stringResource(R.string.editor_open_drawer), onMenu, size = 40.dp, tint = colors.textPrimary, iconSize = 18.dp)
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
        OmniIconButton(OmniIcons.Search, stringResource(R.string.editor_find), onSearch)
        OmniIconButton(OmniIcons.Minimap, stringResource(if (minimapOn) R.string.editor_hide_minimap else R.string.editor_show_minimap), onToggleMinimap, selected = minimapOn)
        overflow()
        OmniCompactButton(stringResource(runButton.labelRes), runButton.icon, onRun, modifier = Modifier.padding(start = 4.dp), enabled = runEnabled)
    }
}

/** E2 compact bar shown while the keyboard is up: file name + dirty dot, undo/redo, Run. */
@Composable
internal fun TypingTopBar(
    fileName: String,
    state: CodeEditorState,
    runEnabled: Boolean,
    onMenu: () -> Unit,
    onRun: () -> Unit,
    runButton: RunButtonState = RunButtonState.Run,
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
        OmniIconButton(OmniIcons.Menu, stringResource(R.string.editor_open_drawer), onMenu, size = 40.dp, tint = colors.textPrimary, iconSize = 18.dp)
        Text(fileName, style = OmniTheme.typography.bodyStrong, color = colors.textPrimary, maxLines = 1)
        if (state.isDirty) Box(Modifier.padding(start = 6.dp).size(5.dp).background(colors.textPrimary))
        Box(Modifier.weight(1f))
        OmniIconButton(OmniIcons.Undo, stringResource(R.string.editor_undo), state::undo, enabled = state.canUndo, iconSize = 16.dp)
        OmniIconButton(OmniIcons.Redo, stringResource(R.string.editor_redo), state::redo, enabled = state.canRedo, iconSize = 16.dp)
        OmniCompactButton(stringResource(runButton.labelRes), runButton.icon, onRun, modifier = Modifier.padding(start = 4.dp), enabled = runEnabled, height = 32.dp)
    }
}

/** File tabs (32 dp): active tab has a red top bar; long-press opens rename/delete for extra files. */
@Composable
internal fun FileTabs(
    files: List<FileHeader>,
    activeFileId: String?,
    entryShortCode: String?,
    /** Read lazily: it flips on every edit and must not recompose the screen around the tabs. */
    dirtyFileId: () -> String?,
    onSelect: (FileHeader) -> Unit,
    onFileMenu: (FileHeader) -> Unit,
    onAdd: () -> Unit,
) {
    val colors = OmniTheme.colors
    // Tabs show a file's own name; two open files with the same name also show their folder to tell them apart.
    val clashing = remember(files) { files.groupingBy { ProjectPaths.basename(it.name) }.eachCount().filterValues { it > 1 }.keys }
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
                    val baseName = ProjectPaths.basename(file.name)
                    val folder = ProjectPaths.parent(file.name)
                    if (folder.isNotEmpty() && baseName in clashing) {
                        Text("${folder.substringAfterLast('/')}/", style = OmniTheme.typography.bodySmall, color = colors.textTertiary, maxLines = 1)
                    }
                    Text(
                        baseName,
                        style = OmniTheme.typography.bodySmall.copy(fontWeight = if (active) androidx.compose.ui.text.font.FontWeight.SemiBold else null),
                        color = if (active) colors.textPrimary else colors.textSecondary,
                        maxLines = 1,
                    )
                    if (file.id == dirtyFileId()) Box(Modifier.size(5.dp).background(colors.textSecondary))
                    // Rename/share/delete are also on long-press; the button makes them discoverable on the open file.
                    if (active && !file.isEntry) {
                        Icon(
                            OmniIcons.MoreVertical,
                            stringResource(R.string.editor_file_actions, ProjectPaths.basename(file.name)),
                            tint = colors.textTertiary,
                            modifier = Modifier.size(14.dp).clickable(role = Role.Button) { onFileMenu(file) },
                        )
                    }
                }
            }
        }
        OmniIconButton(OmniIcons.Plus, stringResource(R.string.editor_new_file), onAdd, size = 34.dp, iconSize = 14.dp)
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
        Text(stringResource(R.string.editor_breadcrumb_root), style = style, color = colors.textTertiary)
        // workspace › src › util › helper.py: folders dim, the file itself brighter.
        val segments = fileName.split('/')
        segments.forEachIndexed { index, segment ->
            Text("›", style = style, color = colors.textTertiary)
            Text(segment, style = style, color = if (index == segments.lastIndex) colors.textSecondary else colors.textTertiary, maxLines = 1)
        }
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
        Text(stringResource(R.string.editor_cursor_position, state.cursor.line, state.cursor.column), style = style, color = colors.textTertiary)
        Text(stringResource(R.string.editor_indent_spaces, tabSize), style = style, color = colors.textTertiary)
        Text(stringResource(R.string.editor_encoding), style = style, color = colors.textTertiary)
        Box(Modifier.weight(1f))
        if (problems > 0) Text(pluralStringResource(R.plurals.editor_problems, problems, problems), style = style, color = colors.accentText)
    }
}

/** What the app-bar action does right now; the label follows the run's phase, or the open file for web pages. */
internal enum class RunButtonState(@StringRes val labelRes: Int, val icon: ImageVector) {
    Run(R.string.editor_run, OmniIcons.Play),
    /** HTML and Markdown render on the device in the preview; the judge has no runtime for them. */
    Preview(R.string.editor_menu_preview, OmniIcons.Eye),
    Stop(R.string.editor_stop, OmniIcons.Stop),
    Detach(R.string.editor_detach, OmniIcons.Stop),
}

/** One overflow-menu entry. */
internal data class MenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** A titled section of the overflow sheet (run, edit, project, help). */
internal data class MenuGroup(val title: String, val actions: List<MenuAction>)

/**
 * The ⋮ actions as a Material bottom sheet: the list is too long for a dropdown on phones, and a sheet keeps
 * every action reachable with one thumb. Empty groups are skipped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OverflowMenu(groups: List<MenuGroup>) {
    var open by remember { mutableStateOf(false) }
    OmniIconButton(OmniIcons.MoreVertical, stringResource(R.string.editor_more_options), { open = true })
    if (!open) return
    val colors = OmniTheme.colors
    ModalBottomSheet(
        onDismissRequest = { open = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RectangleShape,
        containerColor = colors.surface,
        scrimColor = colors.scrim,
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
            groups.filter { it.actions.isNotEmpty() }.forEach { group ->
                SectionLabel(group.title)
                group.actions.forEach { item ->
                    OmniListRow(
                        item.label,
                        leading = { Icon(item.icon, null, tint = colors.textSecondary, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            open = false
                            item.onClick()
                        },
                    )
                }
            }
        }
    }
}
