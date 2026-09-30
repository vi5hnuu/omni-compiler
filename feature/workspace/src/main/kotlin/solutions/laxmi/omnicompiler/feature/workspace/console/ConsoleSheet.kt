package solutions.laxmi.omnicompiler.feature.workspace.console

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniCompactButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniIconButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTab
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTabRow
import solutions.laxmi.omnicompiler.core.designsystem.component.SheetHandle
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.model.CompileProblem
import solutions.laxmi.omnicompiler.core.model.RunMode

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

/** Expanded console (design C1/T1/V1/V2): tabs over the lower part of the editor. */
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
    Column(
        modifier
            .background(colors.surface)
            .drawBehind { drawLine(colors.borderStrong, Offset(0f, 0f), Offset(size.width, 0f), 1f) },
    ) {
        SheetHandle(Modifier.pointerInput(Unit) { detectVerticalDragGestures { _, drag -> if (drag > 8f) actions.onClose() } })
        OmniTabRow(
            tabs = listOf(
                OmniTab("Console"),
                OmniTab("Tests", badge = state.tests.size.toString()),
                OmniTab("Input"),
                OmniTab("Problems", badge = latestProblems.size.takeIf { it > 0 }?.toString(), badgeIsAlert = latestProblems.any { it.isError }),
            ),
            selectedIndex = tab.ordinal,
            onSelect = { actions.onSelectTab(ConsoleTab.entries[it]) },
        ) {
            if (tab == ConsoleTab.Console) OmniIconButton(OmniIcons.Trash, "Clear console", actions.onClear, size = 32.dp, iconSize = 14.dp)
            OmniIconButton(OmniIcons.ChevronDown, "Close console", actions.onClose, size = 32.dp, iconSize = 14.dp)
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
        Text("stdin ›", style = OmniTheme.typography.mono, color = colors.accentText)
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = stdin,
                onValueChange = actions.onStdinChange,
                singleLine = true,
                textStyle = OmniTheme.typography.mono.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                decorationBox = { inner ->
                    if (stdin.isEmpty()) Text("custom input, then Run", style = OmniTheme.typography.mono, color = colors.textTertiary)
                    inner()
                },
            )
        }
        val latest = state.latest
        if (state.isRunning && latest != null) {
            OmniCompactButton(if (latest.phase.name == "PENDING") "Stop" else "Detach", OmniIcons.Stop, actions.onStop, height = 32.dp)
        } else {
            OmniCompactButton("Run", OmniIcons.Play, actions.onRunWithInput, height = 32.dp)
        }
    }
}

internal fun ConsoleUiState.problemsFor(fileName: String, isEntry: Boolean): List<CompileProblem> =
    latest?.takeIf { it.mode == RunMode.TESTS || it.mode == RunMode.STDIN_ONLY }?.problems.orEmpty()
        .filter { it.line != null && (it.fileName == fileName || (isEntry && it.fileName == null)) }
