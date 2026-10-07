package solutions.laxmi.omnicompiler.feature.workspace.console

import solutions.laxmi.omnicompiler.core.model.RunPhase
import solutions.laxmi.omnicompiler.feature.workspace.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniCompactButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniIconButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTab
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTabRow
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.model.CompileProblem
import solutions.laxmi.omnicompiler.core.model.RunMode
import solutions.laxmi.omnicompiler.core.model.ProjectPaths

internal class ConsoleSheetActions(
    val onClose: () -> Unit,
    val onSelectTab: (ConsoleTab) -> Unit,
    val onClear: () -> Unit,
    val onStop: () -> Unit,
    val onRunWithInput: () -> Unit,
    val onStdinChange: (String) -> Unit,
    val onLoadStdinFile: () -> Unit,
    val onSaveStdinAsTest: () -> Unit,
    val onVerify: (String) -> Unit,
    val onGoTo: (CompileProblem) -> Unit,
    val onCancelPending: (solutions.laxmi.omnicompiler.core.model.PendingRun) -> Unit,
    val onSendPendingNow: () -> Unit,
    val onSendWhenOnline: (Boolean) -> Unit,
    val tests: TestActions,
)

/** Console body (design C1/T1/V1/V2): the tabs shown when the console sheet is expanded. */
@Composable
internal fun ConsoleSheet(
    state: ConsoleUiState,
    tab: ConsoleTab,
    stdin: String,
    actions: ConsoleSheetActions,
    modifier: Modifier = Modifier,
) {
    val colors = OmniTheme.colors
    val latestProblems = state.latest?.problems.orEmpty()
    Column(modifier.background(colors.surface)) {
        OmniTabRow(
            tabs = listOf(
                OmniTab(stringResource(R.string.console_tab_console)),
                OmniTab(stringResource(R.string.console_tab_tests), badge = state.tests.size.toString()),
                OmniTab(stringResource(R.string.console_tab_input)),
                OmniTab(stringResource(R.string.console_tab_problems), badge = latestProblems.size.takeIf { it > 0 }?.toString(), badgeIsAlert = latestProblems.any { it.isError }),
            ),
            selectedIndex = tab.ordinal,
            onSelect = { actions.onSelectTab(ConsoleTab.entries[it]) },
        ) {
            if (tab == ConsoleTab.Console) OmniIconButton(OmniIcons.Trash, stringResource(R.string.console_clear), actions.onClear, size = 32.dp, iconSize = 14.dp)
            OmniIconButton(OmniIcons.ChevronDown, stringResource(R.string.console_close), actions.onClose, size = 32.dp, iconSize = 14.dp)
        }
        if (state.pending.isNotEmpty() || !state.online) {
            OfflineQueueCard(state, actions.onCancelPending, actions.onSendPendingNow, actions.onSendWhenOnline)
        }
        val body = Modifier.weight(1f).fillMaxWidth()
        when (tab) {
            ConsoleTab.Console -> ConsoleLog(state.runs, actions.onVerify, body)
            ConsoleTab.Tests -> TestsPanel(state.tests, state.latestTestRun, state.isRunning, actions.tests, body)
            ConsoleTab.Input -> InputPanel(stdin, actions.onStdinChange, state.isRunning, actions.onRunWithInput, actions.onLoadStdinFile, actions.onSaveStdinAsTest, body)
            ConsoleTab.Problems -> ProblemsPanel(state.latest, actions.onGoTo, body)
        }
        if (tab == ConsoleTab.Console) ConsoleFooter(state, stdin, actions)
    }
}

/** Footer: quick stdin + Run, or Stop while a run is active. */
@Composable
private fun ConsoleFooter(state: ConsoleUiState, stdin: String, actions: ConsoleSheetActions) {
    val colors = OmniTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .drawBehind { drawLine(colors.divider, Offset(0f, 0f), Offset(size.width, 0f), 1f) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.console_stdin_prompt), style = OmniTheme.typography.mono, color = colors.accentText)
        Box(Modifier.weight(1f)) {
            // A one-line field would squash multi-line input; summarise it and edit it in the Input tab instead.
            if ('\n' in stdin) {
                val lines = stdin.trimEnd('\n').count { it == '\n' } + 1
                Text(
                    pluralStringResource(R.plurals.console_stdin_lines, lines, lines),
                    style = OmniTheme.typography.mono,
                    color = colors.textSecondary,
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { actions.onSelectTab(ConsoleTab.Input) },
                )
            } else BasicTextField(
                value = stdin,
                onValueChange = actions.onStdinChange,
                singleLine = true,
                textStyle = OmniTheme.typography.mono.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                decorationBox = { inner ->
                    if (stdin.isEmpty()) Text(stringResource(R.string.console_stdin_placeholder), style = OmniTheme.typography.mono, color = colors.textTertiary)
                    inner()
                },
            )
        }
        val latest = state.latest
        if (state.isRunning && latest != null) {
            OmniCompactButton(stringResource(if (latest.phase == RunPhase.PENDING || latest.phase == RunPhase.SUBMITTING) R.string.editor_stop else R.string.editor_detach), OmniIcons.Stop, actions.onStop, height = 32.dp)
        } else {
            OmniCompactButton(stringResource(R.string.editor_run), OmniIcons.Play, actions.onRunWithInput, height = 32.dp)
        }
    }
}

internal fun ConsoleUiState.problemsFor(fileName: String, isEntry: Boolean): List<CompileProblem> =
    latest?.takeIf { it.mode == RunMode.TESTS || it.mode == RunMode.STDIN_ONLY }?.problems.orEmpty()
        .filter { it.line != null && (it.fileName.refersTo(fileName) || (isEntry && it.fileName == null)) }

/**
 * Whether a compiler's file name means the project file at [path]: the same path, or, when the compiler printed only
 * a bare name (no folder), that file's name.
 */
internal fun String?.refersTo(path: String): Boolean =
    this == path || (this != null && '/' !in this && this == ProjectPaths.basename(path))
