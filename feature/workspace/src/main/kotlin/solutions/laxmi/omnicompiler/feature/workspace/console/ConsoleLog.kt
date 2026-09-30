package solutions.laxmi.omnicompiler.feature.workspace.console

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
import solutions.laxmi.omnicompiler.core.common.formatMillis
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
        EmptyState("No runs yet", "Tap Run to execute your tests, or use the Input tab to run with custom stdin.", modifier, icon = OmniIcons.Terminal)
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
                "  $ run ${run.runtimeId}${if (run.mode == RunMode.STDIN_ONLY) " < stdin" else " · ${run.testCount} tests"}",
                style = OmniTheme.typography.mono,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(clock(run), style = OmniTheme.typography.monoSmall, color = colors.textTertiary)
        }
        if (run.phase.isActive || run.phase == RunPhase.QUEUED_OFFLINE) StageTracker(run, Modifier.padding(horizontal = 12.dp, vertical = 6.dp))

        run.jobId?.let { LogRow("queued", "job ${shortJobId(it)} · ${phaseLabel(run)}", time = if (run.fromCache) "cached" else null) }
        when {
            run.verdict == Verdict.CE -> LogRow("compile", "error · ${run.problems.count { it.isError }.coerceAtLeast(1)} problem(s)", tagColor = colors.accentText, tint = true)
            run.results.isNotEmpty() -> LogRow("compile", "ok")
        }
        run.problems.filter { !it.isError }.take(3).forEach { LogRow("warn", "${it.fileName}:${it.line}:${it.column} ${it.message}", tagColor = colors.accentText) }
        if (run.verdict != Verdict.CE) TestLines(run)
        if (run.mode == RunMode.STDIN_ONLY) {
            run.results.firstOrNull()?.let { result ->
                result.stdout?.takeIf { it.isNotEmpty() }?.let { LogRow("stdout", it.trimEnd()) }
                result.stderr?.takeIf { it.isNotEmpty() }?.let { LogRow("stderr", it.trimEnd(), tagColor = colors.accentText) }
            }
        } else {
            run.firstFailure?.stderr?.takeIf { it.isNotBlank() && run.verdict != Verdict.CE }?.let { LogRow("stderr", it.trimEnd(), tagColor = colors.accentText) }
        }
        if (run.verdict == Verdict.CE) run.compileOutput?.let { LogRow("stderr", it.trimEnd().lines().take(12).joinToString("\n"), tagColor = colors.accentText) }
        run.errorMessage?.let { LogRow("error", it, tagColor = colors.accentText, tint = true) }
        if (!run.phase.isActive && run.phase != RunPhase.QUEUED_OFFLINE) {
            LogRow("exit", run.exitLine())
            if (run.jobId != null && run.phase == RunPhase.DONE) {
                OmniTextButton("Verify run", { onVerify(run.jobId!!) }, Modifier.padding(horizontal = 12.dp))
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
            "WA  expected \"${result.expected!!.firstLine()}\"\n    got      \"${result.stdout!!.firstLine()}\""
        } else result.verdict.code
        LogRow(
            tag = "test ${result.index}",
            message = message,
            time = result.timeMs?.let(::formatMillis),
            tagColor = if (failing) colors.accentText else colors.textSecondary,
            messageColor = if (failing) colors.accentText else colors.textPrimary,
            tint = failing,
        )
    }
    if (skipped.isNotEmpty()) {
        val range = if (skipped.size == 1) "${skipped.first().index}" else "${skipped.first().index}–${skipped.last().index}"
        LogRow("test $range", "SK skipped after failure", messageColor = colors.textTertiary)
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

private fun phaseLabel(run: RunRecord) = when (run.phase) {
    RunPhase.PENDING -> "pending"
    RunPhase.RUNNING -> "pending → running"
    else -> "done"
}

private fun RunRecord.exitLine(): String = when (phase) {
    RunPhase.DONE -> "done in ${formatMillis(totalTimeMs)}"
    RunPhase.CANCELLED -> "cancelled before a worker started it"
    RunPhase.DETACHED -> "stopped listening · result will appear in run history"
    RunPhase.FAILED -> "failed"
    else -> ""
}

private fun String.firstLine() = replace("\r\n", "\n").trimEnd('\n').lineSequence().firstOrNull().orEmpty().take(60)

private val clockFormat = ThreadLocal.withInitial { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

internal fun clock(run: RunRecord): String = clockFormat.get()!!.format(Date(run.startedAt.toEpochMilliseconds()))
