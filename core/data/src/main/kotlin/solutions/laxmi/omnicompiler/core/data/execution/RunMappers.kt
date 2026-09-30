package solutions.laxmi.omnicompiler.core.data.execution

import kotlinx.serialization.Serializable
import solutions.laxmi.omnicompiler.core.database.entity.RunEntity
import solutions.laxmi.omnicompiler.core.database.entity.RunResultEntity
import solutions.laxmi.omnicompiler.core.model.CompileDiagnostic
import solutions.laxmi.omnicompiler.core.model.ExecutionRequest
import solutions.laxmi.omnicompiler.core.model.Limits
import solutions.laxmi.omnicompiler.core.model.NamedSource
import solutions.laxmi.omnicompiler.core.model.RunMode
import solutions.laxmi.omnicompiler.core.model.RunPhase
import solutions.laxmi.omnicompiler.core.model.RunRecord
import solutions.laxmi.omnicompiler.core.model.TestCaseDraft
import solutions.laxmi.omnicompiler.core.model.TestResult
import solutions.laxmi.omnicompiler.core.model.Verdict
import kotlin.time.Instant

internal fun RunRecord.toEntity(diagnostic: CompileDiagnostic?) = RunEntity(
    id = id,
    projectId = projectId,
    jobId = jobId,
    runtimeId = runtimeId,
    mode = mode.name,
    status = phase.name,
    verdict = verdict?.code,
    totalTimeMs = totalTimeMs,
    testCount = testCount,
    testNames = testNames.joinToString("\n"),
    compileOutput = compileOutput,
    diagnosticLine = diagnostic?.line,
    diagnosticColumn = diagnostic?.column,
    diagnosticMessage = diagnostic?.message,
    errorMessage = errorMessage,
    fromCache = fromCache,
    startedAt = startedAt.toEpochMilliseconds(),
)

internal fun TestResult.toEntity(runId: String) = RunResultEntity(runId, index, verdict.code, timeMs, stdin, expected, stdout, stderr)

internal fun RunEntity.toModel(results: List<RunResultEntity>, entryFileName: String?): RunRecord {
    val diagnostic = diagnosticMessage?.let { CompileDiagnostic(diagnosticLine, diagnosticColumn, it) }
    return RunRecord(
        id = id,
        projectId = projectId,
        jobId = jobId,
        runtimeId = runtimeId,
        mode = RunMode.entries.firstOrNull { it.name == mode } ?: RunMode.TESTS,
        phase = RunPhase.entries.firstOrNull { it.name == status } ?: RunPhase.DONE,
        verdict = Verdict.fromCode(verdict),
        totalTimeMs = totalTimeMs,
        testCount = testCount,
        testNames = testNames.split('\n'),
        results = results.map { TestResult(it.index, Verdict.fromCode(it.verdict) ?: Verdict.IE, it.timeMs, it.stdin, it.expected, it.stdout, it.stderr) },
        compileOutput = compileOutput,
        problems = CompilerOutputParser.parse(compileOutput, diagnostic, entryFileName),
        errorMessage = errorMessage,
        fromCache = fromCache,
        startedAt = Instant.fromEpochMilliseconds(startedAt),
    )
}

/** Versioned JSON stored for offline-queued runs, so an app update can still read old entries. */
@Serializable
internal data class PendingRunPayload(
    val version: Int = 1,
    val runId: String,
    val mode: String,
    val runtimeId: String,
    val code: String,
    val files: List<PendingFile>,
    val tests: List<PendingTest>,
    val timeMs: Int,
    val memMb: Int,
    val interactive: Boolean,
    val bypassCache: Boolean,
    val idempotencyKey: String,
    val entryFileName: String?,
) {
    fun toRequest() = ExecutionRequest(
        runtimeId = runtimeId,
        code = code,
        files = files.map { NamedSource(it.name, it.content) },
        tests = tests.map { TestCaseDraft(it.stdin, it.expected, it.name) },
        limits = Limits(timeMs, memMb),
        interactive = interactive,
        bypassCache = bypassCache,
        idempotencyKey = idempotencyKey,
    )

    companion object {
        fun from(runId: String, mode: RunMode, request: ExecutionRequest, entryFileName: String?) = PendingRunPayload(
            runId = runId,
            mode = mode.name,
            runtimeId = request.runtimeId,
            code = request.code,
            files = request.files.map { PendingFile(it.name, it.content) },
            tests = request.tests.map { PendingTest(it.stdin, it.expected, it.name) },
            timeMs = request.limits.timeMs,
            memMb = request.limits.memMb,
            interactive = request.interactive,
            bypassCache = request.bypassCache,
            idempotencyKey = request.idempotencyKey,
            entryFileName = entryFileName,
        )
    }
}

@Serializable
internal data class PendingFile(val name: String, val content: String)

@Serializable
internal data class PendingTest(val stdin: String, val expected: String, val name: String = "")
