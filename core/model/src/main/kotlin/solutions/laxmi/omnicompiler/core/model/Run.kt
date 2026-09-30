package solutions.laxmi.omnicompiler.core.model

import kotlin.time.Instant

/** Lifecycle of one run as the user sees it. */
enum class RunPhase {
    /** Waiting for connectivity in the offline queue. */
    QUEUED_OFFLINE,
    SUBMITTING,
    /** Accepted by the judge, waiting for a worker VM. */
    PENDING,
    RUNNING,
    DONE,
    /** Could not be submitted or the job failed server-side. */
    FAILED,
    /** Stopped before a worker picked it up. */
    CANCELLED,
    /** Stopped listening; the job keeps running and appears in history. */
    DETACHED,

    /** Neither the stream nor polling could follow the job; it may still finish and appear in history. */
    LOST;

    val isActive: Boolean get() = this == SUBMITTING || this == PENDING || this == RUNNING
}

/** A compiler message parsed from `file:line:col: severity: message` output or the judge's structured error. */
data class CompileProblem(
    val fileName: String?,
    val line: Int?,
    val column: Int?,
    val message: String,
    val isError: Boolean,
)

/** One console entry: a run of a project, live or finished. */
data class RunRecord(
    val id: String,
    val projectId: String,
    val jobId: String?,
    val runtimeId: String,
    val mode: RunMode,
    val phase: RunPhase,
    val verdict: Verdict?,
    val totalTimeMs: Int?,
    val testCount: Int,
    val testNames: List<String>,
    /** Project test ids in judge order (result `index` 1 is `testIds[0]`); empty for stdin runs and old runs. */
    val testIds: List<String>,
    val results: List<TestResult>,
    val compileOutput: String?,
    val problems: List<CompileProblem>,
    val errorMessage: String?,
    val fromCache: Boolean,
    val startedAt: Instant,
) {
    val passed: Int get() = results.count { it.verdict == Verdict.AC }
    val firstFailure: TestResult? get() = results.firstOrNull { it.verdict.isFailure }
}

data class BenchmarkRun(val jobId: String, val verdict: Verdict?, val totalTimeMs: Int?)

/** Result of running the same program N times through `POST /batch/execute`. */
data class BenchmarkResult(
    val runs: List<BenchmarkRun>,
    val rejected: List<BatchRejected>,
) {
    private val times get() = runs.mapNotNull { it.totalTimeMs }.sorted()
    val minMs: Int? get() = times.firstOrNull()
    val maxMs: Int? get() = times.lastOrNull()
    val medianMs: Int? get() = times.takeIf { it.isNotEmpty() }?.let { it[it.size / 2] }
}
