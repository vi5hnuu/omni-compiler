package solutions.laxmi.omnicompiler.core.data.execution

import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import solutions.laxmi.omnicompiler.core.common.ApplicationScope
import solutions.laxmi.omnicompiler.core.common.IdGenerator
import solutions.laxmi.omnicompiler.core.common.TimeSource
import solutions.laxmi.omnicompiler.core.data.connectivity.ConnectivityObserver
import solutions.laxmi.omnicompiler.core.data.project.ProjectRepository
import solutions.laxmi.omnicompiler.core.data.runtime.RuntimeRepository
import solutions.laxmi.omnicompiler.core.database.dao.PendingRunDao
import solutions.laxmi.omnicompiler.core.database.dao.RunDao
import solutions.laxmi.omnicompiler.core.database.entity.PendingRunEntity
import solutions.laxmi.omnicompiler.core.datastore.PreferencesStore
import solutions.laxmi.omnicompiler.core.model.AppError
import solutions.laxmi.omnicompiler.core.model.ErrorReason
import solutions.laxmi.omnicompiler.core.model.BenchmarkResult
import solutions.laxmi.omnicompiler.core.model.BenchmarkRun
import solutions.laxmi.omnicompiler.core.model.CompileDiagnostic
import solutions.laxmi.omnicompiler.core.model.ExecutionRequest
import solutions.laxmi.omnicompiler.core.model.Job as JudgeJob
import solutions.laxmi.omnicompiler.core.model.JobEvent
import solutions.laxmi.omnicompiler.core.model.JobStatus
import solutions.laxmi.omnicompiler.core.model.NamedSource
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.PendingRun
import solutions.laxmi.omnicompiler.core.model.RateLimitSnapshot
import solutions.laxmi.omnicompiler.core.model.ReplayProof
import solutions.laxmi.omnicompiler.core.model.RunMode
import solutions.laxmi.omnicompiler.core.model.RunPhase
import solutions.laxmi.omnicompiler.core.model.RunRecord
import solutions.laxmi.omnicompiler.core.model.TestCaseDraft
import solutions.laxmi.omnicompiler.core.model.TestResult
import solutions.laxmi.omnicompiler.core.model.Verdict
import solutions.laxmi.omnicompiler.core.model.getOrNull
import solutions.laxmi.omnicompiler.core.network.RateLimitTracker
import solutions.laxmi.omnicompiler.core.network.source.JudgeNetworkDataSource
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

/** What to run. [testIds] limits a TESTS run to some cases ("Run only this"); [stdin] feeds a STDIN_ONLY run. */
data class RunOptions(
    val mode: RunMode = RunMode.TESTS,
    val testIds: Set<String>? = null,
    val stdin: String? = null,
)

/**
 * Runs projects on ls-judge and keeps a per-project console. Live runs are tracked in the
 * application scope, so leaving the editor never loses a result; finished runs are persisted.
 */
interface ExecutionRepository {
    fun observeConsole(projectId: String): Flow<List<RunRecord>>
    val pendingRuns: Flow<List<PendingRun>>
    val rateLimit: StateFlow<RateLimitSnapshot?>

    /** Validates and submits; returns once the judge accepted the job (or it was queued offline). */
    suspend fun run(projectId: String, options: RunOptions): Outcome<Unit>

    /** Cancels a queued job, or stops listening to one a worker already picked up. Fails when the cancel didn't reach the judge. */
    suspend fun stop(projectId: String): Outcome<Unit>

    suspend fun clearConsole(projectId: String)

    /** Sends offline-queued runs; returns false when connectivity is still missing (retry later). */
    suspend fun sendPendingRuns(force: Boolean = false): Boolean
    suspend fun cancelPending(runId: String)

    suspend fun benchmark(projectId: String, copies: Int): Outcome<BenchmarkResult>
    suspend fun replay(jobId: String): Outcome<ReplayProof>
    suspend fun job(jobId: String): Outcome<JudgeJob>
}

@Singleton
internal class DefaultExecutionRepository @Inject constructor(
    private val network: JudgeNetworkDataSource,
    private val projects: ProjectRepository,
    private val runtimes: RuntimeRepository,
    private val runDao: RunDao,
    private val pendingDao: PendingRunDao,
    private val preferences: PreferencesStore,
    private val connectivity: ConnectivityObserver,
    private val scheduler: RunQueueScheduler,
    private val rateLimitTracker: RateLimitTracker,
    private val ids: IdGenerator,
    private val time: TimeSource,
    @ApplicationScope private val scope: CoroutineScope,
) : ExecutionRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val live = MutableStateFlow<Map<String, RunRecord>>(emptyMap())
    private val trackers = ConcurrentHashMap<String, Job>()
    private val sendMutex = Mutex()

    /** Run ids the user stopped while their submit request was still in flight. */
    private val stoppedWhileSubmitting: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override val rateLimit: StateFlow<RateLimitSnapshot?> = rateLimitTracker.snapshot

    init {
        // Queued runs stay put while "send when back online" is off; turning it on must send them.
        scope.launch {
            preferences.runSettings
                .map { it.sendQueuedWhenOnline }
                .distinctUntilChanged()
                .drop(1)
                .filter { it }
                .collect { if (pendingDao.all().isNotEmpty()) scheduler.schedule() }
        }
        scope.launch { resumeInterruptedRuns() }
    }

    /**
     * Accepted runs are saved as they start; one still marked active here belonged to a process that died. The newest
     * per project is followed again (the job may still be running or already done); older ones can't be told apart
     * from abandoned ones and are marked lost.
     */
    private suspend fun resumeInterruptedRuns() {
        val interrupted = runDao.withStatus(ACTIVE_PHASES.map { it.name })
        val newestPerProject = interrupted.distinctBy { it.projectId }.map { it.id }.toSet()
        interrupted.forEach { row ->
            val record = row.toModel(emptyList(), entryFileName = null)
            when {
                live.value.containsKey(record.projectId) -> Unit
                row.id in newestPerProject && record.jobId != null -> {
                    publish(record)
                    // A short budget: the job may be long gone, and the project stays blocked while it is followed.
                    trackers[record.projectId] = scope.launch { track(record, entryFileName = null, maxPolls = RESUME_POLLS) }
                }
                else -> finish(record.copy(phase = RunPhase.LOST), diagnostic = null)
            }
        }
    }

    override val pendingRuns: Flow<List<PendingRun>> = pendingDao.observeAll().map { rows ->
        rows.mapNotNull { row ->
            val payload = decode(row.payload) ?: return@mapNotNull null
            PendingRun(row.id, row.projectId, payload.toRequest(), RunMode.valueOf(payload.mode), Instant.fromEpochMilliseconds(row.createdAt))
        }
    }

    override fun observeConsole(projectId: String): Flow<List<RunRecord>> {
        val persisted = runDao.observeRecent(projectId, CONSOLE_SIZE).flatMapLatest { runs ->
            if (runs.isEmpty()) flowOf(emptyList())
            else runDao.observeResults(runs.map { it.id }).map { results ->
                val byRun = results.groupBy { it.runId }
                runs.map { it.toModel(byRun[it.id].orEmpty(), entryFileName = null) }
            }
        }
        return combine(live.map { it[projectId] }, persisted) { current, saved ->
            if (current == null) saved else listOf(current) + saved.filter { it.id != current.id }
        }
    }

    override suspend fun run(projectId: String, options: RunOptions): Outcome<Unit> {
        // One run per project at a time: an offline-queued run counts, so it can't be overtaken later.
        if (live.value[projectId]?.phase?.isActive == true) return Outcome.Failure(AppError.Conflict(reason = ErrorReason.RunInProgress))
        if (pendingDao.hasPendingFor(projectId)) {
            return Outcome.Failure(AppError.Conflict(reason = ErrorReason.RunQueued))
        }
        val prepared = when (val result = prepare(projectId, options)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.value
        }
        publish(prepared.record)
        if (!connectivity.isOnline.value) {
            enqueueOffline(prepared)
            return Outcome.Success(Unit)
        }
        // Submission runs in the app scope so a closing screen can't orphan an accepted job.
        return scope.async { submitAndTrack(prepared) }.await()
    }

    override suspend fun stop(projectId: String): Outcome<Unit> {
        val record = live.value[projectId] ?: return Outcome.Success(Unit)
        when (record.phase) {
            RunPhase.QUEUED_OFFLINE -> cancelPending(record.id)
            RunPhase.SUBMITTING -> {
                // The request may still be accepted; submitAndTrack withdraws the job when it returns.
                stoppedWhileSubmitting += record.id
                finish(record.copy(phase = RunPhase.CANCELLED), diagnostic = null, cancelTracker = true)
            }
            RunPhase.PENDING -> {
                val jobId = record.jobId ?: return Outcome.Success(Unit)
                when (val result = network.cancel(jobId)) {
                    is Outcome.Success -> finish(record.copy(phase = RunPhase.CANCELLED), diagnostic = null, cancelTracker = true)
                    // 409: a worker already took it; it can't be cancelled, only detached.
                    // 409: a worker already took it; it can't be cancelled, only detached. Any other failure (job gone,
                    // server unreachable) also stops following it, so the project isn't left blocked, and is reported.
                    is Outcome.Failure -> {
                        detach(record)
                        if (result.error !is AppError.Conflict) return result
                    }
                }
            }
            RunPhase.RUNNING -> detach(record)
            else -> Unit
        }
        return Outcome.Success(Unit)
    }

    override suspend fun clearConsole(projectId: String) {
        if (live.value[projectId]?.phase?.isActive != true) live.update { it - projectId }
        runDao.clear(projectId)
    }

    /** The worker and "Send now" may both fire; sending the same queued run twice must not happen. */
    override suspend fun sendPendingRuns(force: Boolean): Boolean = sendMutex.withLock { sendQueued(force) }

    private suspend fun sendQueued(force: Boolean): Boolean {
        if (!force && !preferences.runSettings.first().sendQueuedWhenOnline) return true
        for (row in pendingDao.all()) {
            // A different run already owns this project's console; send this one on the next pass.
            val current = live.value[row.projectId]
            if (current != null && current.id != row.id && current.phase.isActive) continue
            val payload = decode(row.payload)
            if (payload == null) {
                pendingDao.delete(row.id)
                continue
            }
            val record = live.value[row.projectId]?.takeIf { it.id == row.id }
                ?: newRecord(
                    row.id, row.projectId, payload.runtimeId, RunMode.valueOf(payload.mode),
                    payload.tests.map { it.name }, payload.tests.map { it.id }.filter { it.isNotEmpty() }, Instant.fromEpochMilliseconds(row.createdAt),
                )
            val prepared = Prepared(record.copy(phase = RunPhase.SUBMITTING), payload.toRequest(), payload.entryFileName)
            publish(prepared.record)
            val result = submitAndTrack(prepared, fromQueue = true)
            if (result is Outcome.Failure && result.error.isConnectivity()) {
                // Still queued: an active SUBMITTING phase would otherwise block new runs of the project.
                publish(record.copy(phase = RunPhase.QUEUED_OFFLINE))
                return false
            }
            pendingDao.delete(row.id)
        }
        return true
    }

    override suspend fun cancelPending(runId: String) {
        pendingDao.delete(runId)
        live.update { map -> map.filterValues { it.id != runId } }
    }

    override suspend fun benchmark(projectId: String, copies: Int): Outcome<BenchmarkResult> {
        val prepared = when (val result = prepare(projectId, RunOptions())) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.value
        }
        val count = copies.coerceIn(1, MAX_BATCH)
        val requests = List(count) { prepared.request.copy(idempotencyKey = ids.newId()) }
        val batch = when (val result = network.submitBatch(requests)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.value
        }
        // One socket per job keeps us far below the job-poll rate limit; poll only as a fallback.
        val runs = coroutineScope {
            batch.accepted.map { accepted ->
                async {
                    val job = awaitCompletion(accepted.jobId)
                    BenchmarkRun(accepted.jobId, job?.verdict, job?.totalTimeMs)
                }
            }.awaitAll()
        }
        return Outcome.Success(BenchmarkResult(runs, batch.rejected))
    }

    override suspend fun replay(jobId: String) = network.replay(jobId)

    override suspend fun job(jobId: String) = network.job(jobId)

    // ── Pipeline ────────────────────────────────────────────────────────────────────────────────

    private data class Prepared(val record: RunRecord, val request: ExecutionRequest, val entryFileName: String?)

    private suspend fun prepare(projectId: String, options: RunOptions): Outcome<Prepared> {
        val workspace = projects.snapshot(projectId)
            ?: return Outcome.Failure(AppError.NotFound(reason = ErrorReason.ProjectNotFound))
        val entry = workspace.entry ?: return Outcome.Failure(AppError.Validation(reason = ErrorReason.EntryFileMissing))
        if (entry.content.isBlank()) return Outcome.Failure(AppError.Validation(reason = ErrorReason.WriteCodeFirst))
        val extras = workspace.files.filter { !it.isEntry }
        (listOf(entry) + extras).firstOrNull { it.content.encodeToByteArray().size > MAX_SOURCE_BYTES }?.let {
            return Outcome.Failure(AppError.Validation(reason = ErrorReason.SourceTooLarge(it.name, MAX_SOURCE_BYTES / 1024)))
        }
        val chosenTests = when (options.mode) {
            RunMode.STDIN_ONLY -> emptyList()
            RunMode.TESTS -> workspace.tests.filter { options.testIds == null || it.id in options.testIds }
        }
        val selected = when (options.mode) {
            // Unnamed tests keep a blank label; the console shows its localized "Test N" for them.
            RunMode.STDIN_ONLY -> listOf(TestCaseDraft(options.stdin.orEmpty(), expected = ""))
            RunMode.TESTS -> chosenTests.map { t -> TestCaseDraft(t.stdin, t.expected, t.name) }
        }
        if (selected.isEmpty()) return Outcome.Failure(AppError.Validation(reason = ErrorReason.NoTests))
        if (selected.size > MAX_TESTS) return Outcome.Failure(AppError.Validation(reason = ErrorReason.TooManyTests(MAX_TESTS)))
        val runId = ids.newId()
        val request = ExecutionRequest(
            runtimeId = workspace.project.runtimeId,
            code = entry.content,
            files = extras.map { NamedSource(it.name, it.content) },
            tests = selected,
            limits = workspace.project.limits.clamped(),
            bypassCache = preferences.runSettings.first().bypassCache,
            idempotencyKey = runId,
        )
        val record = newRecord(runId, projectId, request.runtimeId, options.mode, selected.map { it.name }, chosenTests.map { it.id }, time.now())
        return Outcome.Success(Prepared(record, request, entry.name))
    }

    private fun newRecord(
        id: String,
        projectId: String,
        runtimeId: String,
        mode: RunMode,
        testNames: List<String>,
        testIds: List<String>,
        startedAt: Instant,
    ) = RunRecord(
        id = id,
        projectId = projectId,
        jobId = null,
        runtimeId = runtimeId,
        mode = mode,
        phase = RunPhase.SUBMITTING,
        verdict = null,
        totalTimeMs = null,
        testCount = testNames.size,
        testNames = testNames,
        testIds = testIds,
        results = emptyList(),
        compileOutput = null,
        problems = emptyList(),
        errorMessage = null,
        fromCache = false,
        startedAt = startedAt,
    )

    private suspend fun submitAndTrack(prepared: Prepared, fromQueue: Boolean = false): Outcome<Unit> {
        val submitted = when (val result = network.submit(prepared.request)) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> {
                // Already recorded as CANCELLED by stop(): neither queue it nor report a failure.
                if (stoppedWhileSubmitting.remove(prepared.record.id)) return Outcome.Success(Unit)
                if (result.error.isConnectivity() && !fromQueue) {
                    enqueueOffline(prepared)
                    return Outcome.Success(Unit)
                }
                if (!(result.error.isConnectivity() && fromQueue)) {
                    // Only server text is persisted; device-side causes are shown by phase (localized in the UI).
                    finish(prepared.record.copy(phase = RunPhase.FAILED, errorMessage = result.error.message.ifBlank { null }), diagnostic = null)
                }
                return result
            }
        }
        if (stoppedWhileSubmitting.remove(prepared.record.id)) {
            // Stopped mid-request: withdraw the accepted job (a 409 means a worker already took it).
            network.cancel(submitted.jobId)
            return Outcome.Success(Unit)
        }
        runtimes.markUsed(prepared.request.runtimeId)
        val accepted = prepared.record.copy(phase = RunPhase.PENDING, jobId = submitted.jobId, fromCache = submitted.servedFromCache)
        publish(accepted)
        // Saved now so the run survives the process: on the next start it is followed again (resumeInterruptedRuns).
        runDao.save(accepted.toEntity(diagnostic = null), emptyList(), CONSOLE_SIZE)
        trackers[accepted.projectId] = scope.launch { track(accepted, prepared.entryFileName) }
        return Outcome.Success(Unit)
    }

    private suspend fun track(start: RunRecord, entryFileName: String?, maxPolls: Int = MAX_POLLS) {
        val jobId = requireNotNull(start.jobId)
        var current = start
        var diagnostic: CompileDiagnostic? = null
        try {
            network.stream(jobId).collect { event ->
                when (event) {
                    is JobEvent.TestFinished -> current = current.withResult(event.result)
                    is JobEvent.Completed -> {
                        diagnostic = event.diagnostic
                        current = current.copy(
                            verdict = event.verdict ?: current.verdict,
                            totalTimeMs = event.totalTimeMs ?: current.totalTimeMs,
                            compileOutput = if (event.verdict == Verdict.CE) event.stderr ?: current.compileOutput else current.compileOutput,
                        )
                    }
                    is JobEvent.Status -> Unit
                }
                publish(current)
            }
        } catch (e: IOException) {
            // Socket unavailable or dropped: the job keeps running server-side, so poll instead.
        }
        val job = pollUntilDone(jobId, maxPolls) { partial -> current = current.mergeJob(partial); publish(current) }
        current = if (job != null) current.mergeJob(job).copy(phase = if (job.status == JobStatus.FAILED && job.verdict == null) RunPhase.FAILED else RunPhase.DONE)
        else current.copy(phase = RunPhase.LOST)
        val withProblems = current.copy(problems = CompilerOutputParser.parse(current.compileOutput, diagnostic, entryFileName))
        finish(withProblems, diagnostic)
        if (withProblems.mode == RunMode.TESTS) projects.recordVerdict(withProblems.projectId, withProblems.verdict)
    }

    /** Fetches the job until it is terminal; results from GET carry stdin/expected that frames lack. */
    private suspend fun pollUntilDone(jobId: String, maxPolls: Int = MAX_POLLS, onProgress: (JudgeJob) -> Unit): JudgeJob? {
        repeat(maxPolls) { attempt ->
            when (val result = network.job(jobId)) {
                is Outcome.Success -> {
                    if (result.value.status.isTerminal) return result.value
                    onProgress(result.value)
                }
                // The judge no longer knows the job (expired or never accepted): nothing left to wait for.
                is Outcome.Failure -> if (result.error is AppError.NotFound) return null
            }
            delay(if (attempt < FAST_POLLS) POLL_INTERVAL_MS else SLOW_POLL_INTERVAL_MS)
        }
        return null
    }

    private suspend fun awaitCompletion(jobId: String): JudgeJob? {
        try {
            network.stream(jobId).lastOrNull()
        } catch (e: IOException) {
            // fall through to polling
        }
        return pollUntilDone(jobId) { }
    }

    private suspend fun detach(record: RunRecord) = finish(record.copy(phase = RunPhase.DETACHED), diagnostic = null, cancelTracker = true)

    private suspend fun finish(record: RunRecord, diagnostic: CompileDiagnostic?, cancelTracker: Boolean = false) {
        if (cancelTracker) trackers.remove(record.projectId)?.cancel()
        runDao.save(record.toEntity(diagnostic), record.results.map { it.toEntity(record.id) }, CONSOLE_SIZE)
        live.update { map -> if (map[record.projectId]?.id == record.id) map - record.projectId else map }
    }

    private suspend fun enqueueOffline(prepared: Prepared) {
        val payload = PendingRunPayload.from(prepared.record.id, prepared.record.mode, prepared.request, prepared.record.testIds, prepared.entryFileName)
        pendingDao.insert(
            PendingRunEntity(prepared.record.id, prepared.record.projectId, json.encodeToString(PendingRunPayload.serializer(), payload), time.now().toEpochMilliseconds()),
        )
        publish(prepared.record.copy(phase = RunPhase.QUEUED_OFFLINE))
        scheduler.schedule()
    }

    private fun publish(record: RunRecord) {
        live.update { it + (record.projectId to record) }
    }

    private fun decode(payload: String): PendingRunPayload? =
        runCatching { json.decodeFromString(PendingRunPayload.serializer(), payload) }.getOrNull()

    private fun RunRecord.withResult(result: TestResult) = copy(
        phase = RunPhase.RUNNING,
        results = (results.filter { it.index != result.index } + result).sortedBy { it.index },
    )

    private fun RunRecord.mergeJob(job: JudgeJob): RunRecord {
        val ce = job.results.firstOrNull { it.verdict == Verdict.CE }
        return copy(
            phase = if (job.status == JobStatus.RUNNING) RunPhase.RUNNING else phase,
            verdict = job.verdict ?: verdict,
            totalTimeMs = job.totalTimeMs ?: totalTimeMs,
            results = if (job.results.isEmpty()) results else job.results,
            compileOutput = ce?.stderr ?: compileOutput,
        )
    }

    private fun AppError.isConnectivity() = this is AppError.Offline || this is AppError.Timeout

    private companion object {
        const val CONSOLE_SIZE = 20
        val ACTIVE_PHASES = listOf(RunPhase.SUBMITTING, RunPhase.PENDING, RunPhase.RUNNING)
        const val MAX_BATCH = 20
        const val MAX_TESTS = 100
        const val MAX_SOURCE_BYTES = 64 * 1024
        const val MAX_POLLS = 600
        /** About 12 s of fast polling for a run picked up again after the app restarted. */
        const val RESUME_POLLS = 15
        const val FAST_POLLS = 40
        const val POLL_INTERVAL_MS = 800L
        const val SLOW_POLL_INTERVAL_MS = 2_000L
    }
}
