package solutions.laxmi.omnicompiler.feature.workspace.console

import solutions.laxmi.omnicompiler.core.ui.formatDuration
import solutions.laxmi.omnicompiler.core.ui.R as CommonR
import solutions.laxmi.omnicompiler.feature.workspace.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import solutions.laxmi.omnicompiler.core.common.shortJobId
import solutions.laxmi.omnicompiler.core.designsystem.component.EmptyState
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButtonStyle
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniIconButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextButton
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.model.RunMode
import solutions.laxmi.omnicompiler.core.model.RunRecord
import solutions.laxmi.omnicompiler.core.model.TestCase
import solutions.laxmi.omnicompiler.core.model.TestResult
import solutions.laxmi.omnicompiler.core.model.Verdict
import solutions.laxmi.omnicompiler.core.ui.VerdictBadge
import solutions.laxmi.omnicompiler.core.ui.VerdictState

internal class TestActions(
    val onRunAll: () -> Unit,
    val onRunOne: (TestCase) -> Unit,
    val onAdd: () -> Unit,
    val onEdit: (TestCase) -> Unit,
    val onDuplicate: (TestCase) -> Unit,
    val onDelete: (TestCase) -> Unit,
    val onAcceptOutput: (TestCase, String) -> Unit,
    val onRaiseLimits: () -> Unit,
)

/** Tests tab (design T1/V3): verdict banner, metrics, expandable test rows with diff, add/run actions. */
@Composable
internal fun TestsPanel(
    tests: List<TestCase>,
    run: RunRecord?,
    running: Boolean,
    actions: TestActions,
    modifier: Modifier = Modifier,
) {
    // The latest TESTS run lines up with the current tests only when it ran all of them, in order.
    val alignedRun = run?.takeIf { it.mode == RunMode.TESTS && it.testCount == tests.size }
    Column(modifier) {
        LazyColumn(Modifier.weight(1f)) {
            if (run != null && run.mode == RunMode.TESTS && !run.phase.isActive && run.verdict != null) {
                item { VerdictBanner(run) }
                item { Metrics(run) }
                if (run.verdict == Verdict.TLE || run.verdict == Verdict.MLE) item { LimitTips(run, actions.onRaiseLimits) }
            }
            if (tests.isEmpty()) {
                item { EmptyState(stringResource(R.string.tests_empty_title), stringResource(R.string.tests_empty_message), icon = OmniIcons.Check) }
            }
            itemsIndexed(tests, key = { _, t -> t.id }) { index, test ->
                TestRow(index, test, alignedRun?.results?.firstOrNull { it.index == index + 1 }, alignedRun?.phase?.isActive == true, actions)
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OmniButton(stringResource(R.string.tests_add), actions.onAdd, Modifier.weight(1f), style = OmniButtonStyle.Secondary, leadingIcon = OmniIcons.Plus, trailingIcon = null)
            OmniButton(
                text = stringResource(R.string.tests_run_all, tests.size),
                onClick = actions.onRunAll,
                modifier = Modifier.weight(1f),
                enabled = tests.isNotEmpty() && !running,
                trailingIcon = OmniIcons.Play,
            )
        }
    }
}

@Composable
private fun VerdictBanner(run: RunRecord) {
    val colors = OmniTheme.colors
    val clipboard = LocalClipboardManager.current
    val verdict = run.verdict ?: return
    val accepted = verdict == Verdict.AC
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (accepted) colors.surfaceRaised else colors.accent)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        VerdictBadge(verdict)
        Column(Modifier.weight(1f)) {
            Text(run.headline(), style = OmniTheme.typography.bodyStrong, color = if (accepted) colors.textPrimary else colors.onAccent)
            Text(
                listOfNotNull(
                    run.jobId?.let { stringResource(R.string.tests_banner_meta_job, shortJobId(it)) },
                    run.totalTimeMs?.let { stringResource(R.string.tests_banner_meta_total, formatDuration(it)) },
                ).joinToString(" · "),
                style = OmniTheme.typography.monoSmall,
                color = if (accepted) colors.textTertiary else colors.onAccent.copy(alpha = 0.8f),
            )
        }
        run.jobId?.let { jobId ->
            OmniIconButton(OmniIcons.Copy, stringResource(R.string.tests_copy_job_id), { clipboard.setText(AnnotatedString(jobId)) }, tint = if (accepted) colors.textSecondary else colors.onAccent)
        }
    }
}

@Composable
private fun Metrics(run: RunRecord) {
    val slowest = run.results.mapNotNull { it.timeMs }.maxOrNull()
    val cells = listOf(
        Triple(stringResource(R.string.tests_metric_slowest), formatDuration(slowest), slowest?.let { it.toFloat() / (run.totalTimeMs ?: it).coerceAtLeast(1) } ?: 0f),
        Triple(stringResource(R.string.tests_metric_passed), stringResource(R.string.stage_tests_progress, run.passed, run.testCount), run.passed.toFloat() / run.testCount.coerceAtLeast(1)),
        Triple(stringResource(R.string.tests_metric_total), formatDuration(run.totalTimeMs), 1f),
        Triple(stringResource(R.string.tests_metric_source), stringResource(if (run.fromCache) R.string.tests_source_cached else R.string.tests_source_fresh), if (run.fromCache) 1f else 0f),
    )
    val colors = OmniTheme.colors
    Row(Modifier.fillMaxWidth().drawBehind { drawLine(colors.divider, Offset(0f, size.height), Offset(size.width, size.height), 1f) }) {
        cells.forEach { (label, value, fraction) ->
            Column(
                Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(label.uppercase(), style = OmniTheme.typography.overline, color = colors.textTertiary)
                Text(value, style = OmniTheme.typography.mono.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = colors.textPrimary)
                Box(Modifier.fillMaxWidth().height(2.dp).background(colors.surfaceMuted)) {
                    Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(colors.textSecondary))
                }
            }
        }
    }
}

/** Design V3 "Things to try" for resource-limit verdicts. */
@Composable
private fun LimitTips(run: RunRecord, onRaiseLimits: () -> Unit) {
    val colors = OmniTheme.colors
    val failing = run.firstFailure
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.tests_things_to_try).uppercase(), style = OmniTheme.typography.overline, color = colors.textTertiary)
        val timeLimited = run.verdict == Verdict.TLE
        val tips = listOfNotNull(
            failing?.let { stringResource(R.string.tests_tip_run_alone, run.testName(it.index)) },
            stringResource(if (timeLimited) R.string.tests_tip_time else R.string.tests_tip_memory),
            stringResource(if (timeLimited) R.string.tests_tip_raise_time else R.string.tests_tip_raise_memory),
        )
        tips.forEachIndexed { i, tip ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("%02d".format(i + 1), style = OmniTheme.typography.mono, color = colors.accentText)
                Text(tip, style = OmniTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
        OmniTextButton(stringResource(R.string.tests_change_limits), onRaiseLimits)
    }
}

@Composable
private fun TestRow(index: Int, test: TestCase, result: TestResult?, runActive: Boolean, actions: TestActions) {
    val colors = OmniTheme.colors
    var expanded by rememberSaveable(test.id) { mutableStateOf(result?.verdict?.isFailure == true) }
    val name = test.name.ifBlank { stringResource(R.string.run_test_default_name, index + 1) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (expanded) colors.background else Color.Transparent)
            .drawBehind { drawLine(colors.hairline, Offset(0f, size.height), Offset(size.width, size.height), 1f) },
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("${index + 1}", style = OmniTheme.typography.mono, color = colors.textTertiary)
            Text(name, style = OmniTheme.typography.bodyStrong, color = if (result?.verdict == Verdict.SK) colors.textTertiary else colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
            when {
                result != null -> VerdictBadge(result.verdict)
                runActive -> VerdictBadge(VerdictState.Pending)
            }
            Text(formatDuration(result?.timeMs), style = OmniTheme.typography.monoSmall, color = colors.textTertiary)
            Icon(if (expanded) OmniIcons.ChevronUp else OmniIcons.ChevronRight, null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
        }
        if (expanded) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val output = result?.stdout
                if (output != null) {
                    val mismatched = diffLines(test.expected, output)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CodeBlock(stringResource(R.string.tests_expected), test.expected, mismatched, Modifier.weight(1f))
                        CodeBlock(stringResource(R.string.tests_output), output, mismatched, Modifier.weight(1f), accentLabel = true)
                    }
                } else {
                    CodeBlock(stringResource(R.string.tests_expected), test.expected, emptySet())
                }
                CodeBlock(stringResource(R.string.tests_stdin), test.stdin, emptySet())
                result?.stderr?.takeIf { it.isNotBlank() }?.let { CodeBlock(stringResource(R.string.tests_stderr), it, emptySet(), accentLabel = true) }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OmniTextButton(stringResource(R.string.tests_run_only_this), { actions.onRunOne(test) })
                    OmniTextButton(stringResource(R.string.tests_edit), { actions.onEdit(test) }, color = colors.textSecondary)
                    OmniTextButton(stringResource(R.string.tests_duplicate), { actions.onDuplicate(test) }, color = colors.textSecondary)
                    if (output != null && result.verdict == Verdict.WA) {
                        OmniTextButton(stringResource(R.string.tests_use_output), { actions.onAcceptOutput(test, output) }, color = colors.textSecondary)
                    }
                    OmniTextButton(stringResource(CommonR.string.common_delete), { actions.onDelete(test) }, color = colors.textSecondary)
                }
            }
        }
    }
}

/** Monospace block; lines listed in [highlight] get the design's dark-red diff fill. */
@Composable
internal fun CodeBlock(label: String, text: String, highlight: Set<Int>, modifier: Modifier = Modifier, accentLabel: Boolean = false) {
    val colors = OmniTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), style = OmniTheme.typography.overline, color = if (accentLabel) colors.accentText else colors.textTertiary)
        val content = buildAnnotatedString {
            val lines = text.replace("\r\n", "\n").trimEnd('\n').split('\n')
            lines.forEachIndexed { i, line ->
                if (i in highlight) withStyle(SpanStyle(background = colors.accentStrong)) { append(line.ifEmpty { " " }) } else append(line)
                if (i < lines.lastIndex) append('\n')
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 28.dp, max = 180.dp)
                .background(colors.surfaceRaised)
                .horizontalScroll(rememberScrollState())
                .padding(8.dp),
        ) {
            Text(if (text.isEmpty()) AnnotatedString(stringResource(R.string.tests_empty_block)) else content, style = OmniTheme.typography.mono, color = if (text.isEmpty()) colors.textTertiary else colors.textPrimary)
        }
    }
}
