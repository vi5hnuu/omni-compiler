package solutions.laxmi.omnicompiler.feature.workspace.console

import solutions.laxmi.omnicompiler.core.ui.testCount
import solutions.laxmi.omnicompiler.core.ui.formatDuration
import solutions.laxmi.omnicompiler.feature.workspace.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import solutions.laxmi.omnicompiler.core.common.shortJobId
import solutions.laxmi.omnicompiler.core.designsystem.component.EmptyState
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextButton
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.model.RunMode
import solutions.laxmi.omnicompiler.core.model.RunPhase
import solutions.laxmi.omnicompiler.core.model.RunRecord
import solutions.laxmi.omnicompiler.core.model.Verdict
import solutions.laxmi.omnicompiler.core.ui.VerdictBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Console tab (design C1): the newest run expanded as a tagged log, earlier runs collapsed below a dashed rule. */
@Composable
internal fun ConsoleLog(runs: List<RunRecord>, onVerify: (String) -> Unit, modifier: Modifier = Modifier) {
    if (runs.isEmpty()) {
        EmptyState(stringResource(R.string.console_no_runs_title), stringResource(R.string.console_no_runs_message), modifier, icon = OmniIcons.Terminal)
        return
    }
    LazyColumn(modifier) {
        item(key = runs.first().id) { RunLog(runs.first(), onVerify) }
        items(runs.drop(1), key = { it.id }) { run -> CollapsedRun(run, onVerify) }
    }
}

@Composable
private fun CollapsedRun(run: RunRecord, onVerify: (String) -> Unit) {
    var expanded by rememberSaveable(run.id) { mutableStateOf(false) }
    val colors = OmniTheme.colors
    Column(
        Modifier.drawBehind {
            drawLine(colors.border, Offset(0f, 0f), Offset(size.width, 0f), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        },
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            run.displayVerdict()?.let { VerdictBadge(it) }
            Text(clock(run), style = OmniTheme.typography.monoSmall, color = colors.textTertiary)
            Text(
                run.problems.firstOrNull()?.let { "${it.fileName ?: ""}:${it.line ?: ""} ${it.message}" } ?: run.meta(),
                style = OmniTheme.typography.mono,
                color = colors.textSecondary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
        if (expanded) RunLog(run, onVerify)
    }
}

@Composable
private fun RunLog(run: RunRecord, onVerify: (String) -> Unit) {
    val colors = OmniTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(if (run.phase.isActive) colors.accent else colors.textTertiary))
            Text(
                "  " + if (run.mode == RunMode.STDIN_ONLY) stringResource(R.string.console_command_stdin, run.runtimeId)
                else stringResource(R.string.console_command_tests, run.runtimeId, testCount(run.testCount)),
                style = OmniTheme.typography.mono,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(clock(run), style = OmniTheme.typography.monoSmall, color = colors.textTertiary)
        }
        if (run.phase.isActive || run.phase == RunPhase.QUEUED_OFFLINE) StageTracker(run, Modifier.padding(horizontal = 12.dp, vertical = 6.dp))

        run.jobId?.let { LogRow(stringResource(R.string.console_tag_queued), stringResource(R.string.console_job_line, shortJobId(it), phaseLabel(run)), time = if (run.fromCache) stringResource(R.string.console_cached) else null) }
        when {
            run.verdict == Verdict.CE -> {
                val errors = run.problems.count { it.isError }.coerceAtLeast(1)
                LogRow(stringResource(R.string.console_tag_compile), pluralStringResource(R.plurals.console_compile_errors, errors, errors), tagColor = colors.accentText, tint = true)
            }
            run.results.isNotEmpty() -> LogRow(stringResource(R.string.console_tag_compile), stringResource(R.string.console_compile_ok))
        }
        run.problems.filter { !it.isError }.take(3).forEach { LogRow(stringResource(R.string.console_tag_warn), "${it.fileName}:${it.line}:${it.column} ${it.message}", tagColor = colors.accentText) }
        if (run.verdict != Verdict.CE) TestLines(run)
        if (run.mode == RunMode.STDIN_ONLY) {
            run.results.firstOrNull()?.let { result ->
                result.stdout?.takeIf { it.isNotEmpty() }?.let { LogRow(stringResource(R.string.console_tag_stdout), it.trimEnd()) }
                result.stderr?.takeIf { it.isNotEmpty() }?.let { LogRow(stringResource(R.string.console_tag_stderr), it.trimEnd(), tagColor = colors.accentText) }
            }
        } else {
            run.firstFailure?.stderr?.takeIf { it.isNotBlank() && run.verdict != Verdict.CE }?.let { LogRow(stringResource(R.string.console_tag_stderr), it.trimEnd(), tagColor = colors.accentText) }
        }
        if (run.verdict == Verdict.CE) run.compileOutput?.let { LogRow(stringResource(R.string.console_tag_stderr), it.trimEnd().lines().take(12).joinToString("\n"), tagColor = colors.accentText) }
        run.errorMessage?.let { LogRow(stringResource(R.string.console_tag_error), it, tagColor = colors.accentText, tint = true) }
        if (!run.phase.isActive && run.phase != RunPhase.QUEUED_OFFLINE) {
            LogRow(stringResource(R.string.console_tag_exit), run.exitLine())
            if (run.jobId != null && run.phase == RunPhase.DONE) {
                OmniTextButton(stringResource(R.string.console_verify), { onVerify(run.jobId!!) }, Modifier.padding(horizontal = 12.dp))
            }
        }
    }
}

@Composable
private fun TestLines(run: RunRecord) {
    val colors = OmniTheme.colors
    if (run.mode == RunMode.STDIN_ONLY) return
    val results = run.results
    val skipped = results.filter { it.verdict == Verdict.SK }
    results.filter { it.verdict != Verdict.SK }.forEach { result ->
        val failing = result.verdict.isFailure
        val message = if (result.isOutputMismatch()) {
            stringResource(R.string.console_mismatch, result.expected!!.firstLine(), result.stdout!!.firstLine())
        } else result.verdict.code
        LogRow(
            tag = stringResource(R.string.console_tag_test, result.index.toString()),
            message = message,
            time = result.timeMs?.let { formatDuration(it) },
            tagColor = if (failing) colors.accentText else colors.textSecondary,
            messageColor = if (failing) colors.accentText else colors.textPrimary,
            tint = failing,
        )
    }
    if (skipped.isNotEmpty()) {
        val range = if (skipped.size == 1) "${skipped.first().index}" else "${skipped.first().index}–${skipped.last().index}"
        LogRow(stringResource(R.string.console_tag_test, range), stringResource(R.string.console_skipped), messageColor = colors.textTertiary)
    }
}

/** tag | message | meta row; failing rows get the design's red tint. */
@Composable
private fun LogRow(
    tag: String,
    message: String,
    time: String? = null,
    tagColor: Color = OmniTheme.colors.textSecondary,
    messageColor: Color = OmniTheme.colors.textPrimary,
    tint: Boolean = false,
) {
    val colors = OmniTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (tint) colors.errorTint else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(tag, style = OmniTheme.typography.mono, color = tagColor, modifier = Modifier.width(64.dp))
        Text(message, style = OmniTheme.typography.mono, color = messageColor, modifier = Modifier.weight(1f))
        if (time != null) Text(time, style = OmniTheme.typography.monoSmall, color = colors.textTertiary)
    }
}

@Composable
private fun phaseLabel(run: RunRecord) = stringResource(
    when (run.phase) {
        RunPhase.PENDING -> R.string.console_job_pending
        RunPhase.RUNNING -> R.string.console_job_running
        else -> R.string.console_job_done
    },
)

@Composable
private fun RunRecord.exitLine(): String = when (phase) {
    RunPhase.DONE -> stringResource(R.string.console_exit_done, formatDuration(totalTimeMs))
    RunPhase.CANCELLED -> stringResource(R.string.console_exit_cancelled)
    RunPhase.DETACHED -> stringResource(R.string.console_exit_detached)
    RunPhase.FAILED -> stringResource(R.string.console_exit_failed)
    RunPhase.LOST -> stringResource(R.string.console_exit_lost)
    else -> ""
}

private fun String.firstLine() = replace("\r\n", "\n").trimEnd('\n').lineSequence().firstOrNull().orEmpty().take(60)

private val clockFormat = ThreadLocal.withInitial { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

internal fun clock(run: RunRecord): String = clockFormat.get()!!.format(Date(run.startedAt.toEpochMilliseconds()))
