package solutions.laxmi.omnicompiler.core.model

import kotlin.time.Instant

/** Everything needed for `POST /execute`. [idempotencyKey] makes retries (offline queue) safe. */
data class ExecutionRequest(
    val runtimeId: String,
    val code: String,
    val files: List<NamedSource>,
    val tests: List<TestCaseDraft>,
    val limits: Limits,
    val interactive: Boolean = false,
    val bypassCache: Boolean = false,
    val idempotencyKey: String,
)

data class NamedSource(val name: String, val content: String)

/** How a run should be presented: graded against expectations, or as a plain program run. */
enum class RunMode { TESTS, STDIN_ONLY }

data class CompileDiagnostic(val line: Int?, val column: Int?, val message: String)

data class TestResult(
    val index: Int,
    val verdict: Verdict,
    val timeMs: Int?,
    val stdin: String?,
    val expected: String?,
    val stdout: String?,
    val stderr: String?,
)

data class Job(
    val id: String,
    val status: JobStatus,
    val verdict: Verdict?,
    val totalTimeMs: Int?,
    val results: List<TestResult>,
    val code: String?,
)

/** Incremental updates while a job runs; produced by the WebSocket stream or the polling fallback. */
sealed interface JobEvent {
    data class Status(val status: JobStatus) : JobEvent
    data class TestFinished(val result: TestResult) : JobEvent
    data class Completed(
        val verdict: Verdict?,
        val totalTimeMs: Int?,
        val stderr: String?,
        val diagnostic: CompileDiagnostic?,
        val job: Job?,
    ) : JobEvent
}

data class Submission(
    val id: String,
    val runtimeId: String,
    val status: JobStatus,
    val verdict: Verdict?,
    val createdAt: Instant,
    val totalTimeMs: Int?,
)

data class BatchResult(
    val accepted: List<BatchAccepted>,
    val rejected: List<BatchRejected>,
)

data class BatchAccepted(val index: Int, val jobId: String)
data class BatchRejected(val index: Int, val error: String)

/** Signed reproducibility proof from `POST /jobs/{id}/replay`. */
data class ReplayProof(
    val verdict: Verdict?,
    val totalTimeMs: Int?,
    val version: String,
    val runtimeId: String,
    val imageHash: String,
    val codeHash: String,
    val inputHash: String,
    val signature: String,
    val signedAt: String,
)

/** A run waiting for connectivity (offline queue). */
data class PendingRun(
    val id: String,
    val projectId: String,
    val request: ExecutionRequest,
    val mode: RunMode,
    val createdAt: Instant,
)
