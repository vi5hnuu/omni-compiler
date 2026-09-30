package solutions.laxmi.omnicompiler.feature.workspace.console

import solutions.laxmi.omnicompiler.feature.workspace.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.model.RunPhase
import solutions.laxmi.omnicompiler.core.model.RunRecord

/**
 * Design V1 stage tracker. ls-judge exposes only pending → running → per-test results, so the
 * stages are Queued → Running → Tests n/N → Done (no queue position or VM timings).
 */
@Composable
internal fun StageTracker(run: RunRecord, modifier: Modifier = Modifier) {
    val colors = OmniTheme.colors
    val current = when (run.phase) {
        RunPhase.QUEUED_OFFLINE, RunPhase.SUBMITTING -> 0
        RunPhase.PENDING -> 1
        RunPhase.RUNNING -> if (run.results.isEmpty()) 2 else 3
        else -> 4
    }
    val stages = listOf(
        stringResource(R.string.stage_submitted) to if (run.phase == RunPhase.QUEUED_OFFLINE) stringResource(R.string.stage_offline) else "",
        stringResource(R.string.stage_queued) to "",
        stringResource(R.string.stage_running) to "",
        stringResource(R.string.stage_tests) to stringResource(R.string.stage_tests_progress, run.results.size, run.testCount),
    )
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        stages.forEachIndexed { index, (label, meta) ->
            val reached = index < current
            val active = index == current
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(
                            when {
                                active -> colors.accent
                                reached -> colors.textPrimary
                                else -> colors.surfaceMuted
                            },
                        ),
                )
                Text(label, style = OmniTheme.typography.label, color = if (reached || active) colors.textPrimary else colors.textTertiary)
                if (meta.isNotEmpty()) Text(meta, style = OmniTheme.typography.monoSmall, color = colors.textTertiary)
            }
        }
    }
}
