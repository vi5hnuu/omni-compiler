package solutions.laxmi.omnicompiler.feature.workspace.console

import solutions.laxmi.omnicompiler.core.common.formatMillis
import solutions.laxmi.omnicompiler.core.model.RunMode
import solutions.laxmi.omnicompiler.core.model.RunPhase
import solutions.laxmi.omnicompiler.core.model.RunRecord
import solutions.laxmi.omnicompiler.core.model.TestResult
import solutions.laxmi.omnicompiler.core.model.Verdict
import solutions.laxmi.omnicompiler.core.ui.VerdictState

/**
 * Presentation rules shared by the peek, console and tests views.
 * A STDIN_ONLY run has no expectation, so its WA is just "program ran": shown as output, not failure.
 */
internal fun RunRecord.displayVerdict(): Verdict? = when {
    mode == RunMode.STDIN_ONLY && verdict == Verdict.WA -> Verdict.AC
    else -> verdict
}

internal fun RunRecord.headline(): String = when (phase) {
    RunPhase.QUEUED_OFFLINE -> "Waiting for connection"
    RunPhase.SUBMITTING -> "Submitting…"
    RunPhase.PENDING -> "Queued"
    RunPhase.RUNNING -> if (testCount > 1) "Running tests ${results.size} / $testCount" else "Running"
    RunPhase.CANCELLED -> "Cancelled"
    RunPhase.DETACHED -> "Stopped listening"
    RunPhase.FAILED -> errorMessage ?: "Run failed"
    RunPhase.DONE -> {
        val result = verdict
        val failed = firstFailure
        when {
            result == null -> "Finished"
            mode == RunMode.STDIN_ONLY && (result == Verdict.AC || result == Verdict.WA) -> "Program finished"
            result == Verdict.AC -> "Accepted"
            failed != null && result != Verdict.CE && testCount > 1 -> "${result.label} on ${testName(failed.index)}"
            else -> result.label
        }
    }
}

internal fun RunRecord.testName(index: Int): String = testNames.getOrNull(index - 1)?.takeIf { it.isNotBlank() } ?: "test $index"

/** `#3 · 2/7 · 41 ms` meta used in the peek and banners. */
internal fun RunRecord.meta(): String = buildList {
    firstFailure?.takeIf { verdict != Verdict.CE && mode == RunMode.TESTS }?.let { add("#${it.index}") }
    if (mode == RunMode.TESTS && verdict != Verdict.CE) add("$passed/$testCount")
    totalTimeMs?.let { add(formatMillis(it)) }
    if (fromCache) add("cached")
}.joinToString(" · ")

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
