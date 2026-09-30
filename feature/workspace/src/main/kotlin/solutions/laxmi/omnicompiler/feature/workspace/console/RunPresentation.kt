package solutions.laxmi.omnicompiler.feature.workspace.console

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import solutions.laxmi.omnicompiler.core.model.RunMode
import solutions.laxmi.omnicompiler.core.model.RunPhase
import solutions.laxmi.omnicompiler.core.model.RunRecord
import solutions.laxmi.omnicompiler.core.model.TestResult
import solutions.laxmi.omnicompiler.core.model.Verdict
import solutions.laxmi.omnicompiler.core.ui.VerdictState
import solutions.laxmi.omnicompiler.core.ui.formatDuration
import solutions.laxmi.omnicompiler.core.ui.labelRes
import solutions.laxmi.omnicompiler.feature.workspace.R

/**
 * Presentation rules shared by the peek, console and tests views.
 * A STDIN_ONLY run has no expectation, so its WA is just "program ran": shown as output, not failure.
 */
internal fun RunRecord.displayVerdict(): Verdict? = when {
    mode == RunMode.STDIN_ONLY && verdict == Verdict.WA -> Verdict.AC
    else -> verdict
}

@Composable
internal fun RunRecord.headline(): String = when (phase) {
    RunPhase.QUEUED_OFFLINE -> stringResource(R.string.run_waiting_connection)
    RunPhase.SUBMITTING -> stringResource(R.string.run_submitting)
    RunPhase.PENDING -> stringResource(R.string.run_queued)
    RunPhase.RUNNING -> if (testCount > 1) stringResource(R.string.run_running_tests, results.size, testCount) else stringResource(R.string.run_running)
    RunPhase.CANCELLED -> stringResource(R.string.run_cancelled)
    RunPhase.DETACHED -> stringResource(R.string.run_detached)
    RunPhase.LOST -> stringResource(R.string.run_lost)
    // Only server text is stored with a failure; otherwise show the generic line.
    RunPhase.FAILED -> errorMessage ?: stringResource(R.string.run_failed)
    RunPhase.DONE -> {
        val result = verdict
        val failed = firstFailure
        when {
            result == null -> stringResource(R.string.run_finished)
            mode == RunMode.STDIN_ONLY && (result == Verdict.AC || result == Verdict.WA) -> stringResource(R.string.run_program_finished)
            failed != null && result != Verdict.CE && testCount > 1 ->
                stringResource(R.string.run_verdict_on_test, stringResource(result.labelRes), testName(failed.index))
            else -> stringResource(result.labelRes)
        }
    }
}

@Composable
internal fun RunRecord.testName(index: Int): String =
    testNames.getOrNull(index - 1)?.takeIf { it.isNotBlank() } ?: stringResource(R.string.run_test_fallback_name, index)

/** `#3 · 2/7 · 41 ms` meta used in the peek and banners. */
@Composable
internal fun RunRecord.meta(): String {
    val parts = mutableListOf<String>()
    firstFailure?.takeIf { verdict != Verdict.CE && mode == RunMode.TESTS }?.let { parts += stringResource(R.string.run_meta_failed_test, it.index) }
    if (mode == RunMode.TESTS && verdict != Verdict.CE) parts += stringResource(R.string.run_meta_passed, passed, testCount)
    totalTimeMs?.let { parts += formatDuration(it) }
    if (fromCache) parts += stringResource(R.string.console_cached)
    return parts.joinToString(" · ")
}

/** One square per test for the strip: finished verdicts, the running test, then pending. */
internal fun RunRecord.stripStates(): List<VerdictState> {
    if (verdict == Verdict.CE) return emptyList()
    val byIndex = results.associateBy { it.index }
    return (1..testCount).map { index ->
        byIndex[index]?.let { VerdictState.Done(if (mode == RunMode.STDIN_ONLY && it.verdict == Verdict.WA) Verdict.AC else it.verdict) }
            ?: if (phase == RunPhase.RUNNING && index == results.size + 1) VerdictState.Running else VerdictState.Pending
    }
}

internal fun TestResult.isOutputMismatch() = verdict == Verdict.WA && expected != null && stdout != null

/** Lines that differ between expected and actual output (trailing newlines ignored, as the judge does). */
internal fun diffLines(expected: String, actual: String): Set<Int> {
    val e = expected.replace("\r\n", "\n").trimEnd('\n').split('\n')
    val a = actual.replace("\r\n", "\n").trimEnd('\n').split('\n')
    return (0 until maxOf(e.size, a.size)).filter { e.getOrNull(it) != a.getOrNull(it) }.toSet()
}
