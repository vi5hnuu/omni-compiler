package solutions.laxmi.omnicompiler.feature.workspace.console

import solutions.laxmi.omnicompiler.core.model.Verdict
import solutions.laxmi.omnicompiler.core.model.RunPhase
import solutions.laxmi.omnicompiler.core.data.auth.KeepWorkOffer
import solutions.laxmi.omnicompiler.core.data.auth.GuestPromptRepository
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import solutions.laxmi.omnicompiler.core.data.connectivity.ConnectivityObserver
import solutions.laxmi.omnicompiler.core.data.execution.ExecutionRepository
import solutions.laxmi.omnicompiler.core.data.files.TextDocumentReader
import solutions.laxmi.omnicompiler.core.data.execution.RunOptions
import solutions.laxmi.omnicompiler.core.data.project.ProjectRepository
import solutions.laxmi.omnicompiler.core.data.runtime.RuntimeRepository
import solutions.laxmi.omnicompiler.core.data.settings.SettingsRepository
import solutions.laxmi.omnicompiler.core.model.AppError
import solutions.laxmi.omnicompiler.core.model.BenchmarkResult
import solutions.laxmi.omnicompiler.core.model.Limits
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.PendingRun
import solutions.laxmi.omnicompiler.core.model.RateLimitSnapshot
import solutions.laxmi.omnicompiler.core.model.ReplayProof
import solutions.laxmi.omnicompiler.core.model.RunMode
import solutions.laxmi.omnicompiler.core.model.RunRecord
import solutions.laxmi.omnicompiler.core.model.RunSettings
import solutions.laxmi.omnicompiler.core.model.TestCase
import solutions.laxmi.omnicompiler.core.model.TestCaseDraft
import solutions.laxmi.omnicompiler.core.ui.UiText
import solutions.laxmi.omnicompiler.core.ui.toUiText
import solutions.laxmi.omnicompiler.feature.workspace.R

enum class ConsoleTab { Console, Tests, Input, Problems }

data class ConsoleUiState(
    val runs: List<RunRecord> = emptyList(),
    val tests: List<TestCase> = emptyList(),
    val limits: Limits = Limits.Default,
    val runtimeDefaults: Limits = Limits.Default,
    val pending: List<PendingRun> = emptyList(),
    val online: Boolean = true,
    val rateLimit: RateLimitSnapshot? = null,
    val runSettings: RunSettings = RunSettings(),
) {
    val latest: RunRecord? get() = runs.firstOrNull()
    val latestTestRun: RunRecord? get() = runs.firstOrNull { it.mode == RunMode.TESTS }
    val isRunning: Boolean get() = latest?.phase?.isActive == true
}

/** Transient overlays the console can raise. */
sealed interface ConsoleOverlay {
    data class RateLimited(val retryAfterSeconds: Long?, val snapshot: RateLimitSnapshot?) : ConsoleOverlay
    data object Benchmarking : ConsoleOverlay
    data class Benchmark(val result: BenchmarkResult) : ConsoleOverlay
    data object Verifying : ConsoleOverlay
    data class Proof(val proof: ReplayProof) : ConsoleOverlay
    data class KeepWork(val offer: KeepWorkOffer) : ConsoleOverlay
}

sealed interface ConsoleEvent {
    data class Message(val text: UiText) : ConsoleEvent
    data class RunStarted(val mode: RunMode) : ConsoleEvent
    data class TestDeleted(val test: TestCase) : ConsoleEvent
}

@HiltViewModel(assistedFactory = ConsoleViewModel.Factory::class)
class ConsoleViewModel @AssistedInject constructor(
    @Assisted private val projectId: String,
    private val savedState: SavedStateHandle,
    private val executions: ExecutionRepository,
    private val projects: ProjectRepository,
    private val runtimes: RuntimeRepository,
    private val settings: SettingsRepository,
    private val documents: TextDocumentReader,
    private val guestPrompt: GuestPromptRepository,
    connectivity: ConnectivityObserver,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(projectId: String): ConsoleViewModel
    }

    private val events = Channel<ConsoleEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private val overlayState = MutableStateFlow<ConsoleOverlay?>(null)
    val overlay: StateFlow<ConsoleOverlay?> = overlayState

    /** Custom stdin survives rotation and process death. */
    val stdin: StateFlow<String> = savedState.getStateFlow(KEY_STDIN, "")

    private val runtimeDefaults = MutableStateFlow(Limits.Default)

    val uiState: StateFlow<ConsoleUiState> = combine(
        executions.observeConsole(projectId),
        projects.observeWorkspace(projectId),
        executions.pendingRuns.map { list -> list.filter { it.projectId == projectId } },
        combine(connectivity.isOnline, executions.rateLimit, runtimeDefaults, ::Triple),
        settings.runSettings,
    ) { runs, workspace, pending, (online, rateLimit, defaults), runSettings ->
        ConsoleUiState(
            runs = runs,
            tests = workspace?.tests.orEmpty(),
            limits = workspace?.project?.limits ?: Limits.Default,
            runtimeDefaults = defaults,
            pending = pending,
            online = online,
            rateLimit = rateLimit,
            runSettings = runSettings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConsoleUiState())

    init {
        viewModelScope.launch {
            projects.observeWorkspace(projectId).map { it?.project?.runtimeId }.collect { runtimeId ->
                if (runtimeId != null) runtimeDefaults.value = runtimes.defaultLimits(runtimeId)
            }
        }
        viewModelScope.launch { offerKeepWorkAfterFirstAccept() }
    }

    /** Guests are invited to keep their work (design X3) when a run they watched here finishes Accepted. */
    private suspend fun offerKeepWorkAfterFirstAccept() {
        val watched = mutableSetOf<String>()
        executions.observeConsole(projectId).collect { runs ->
            val latest = runs.firstOrNull() ?: return@collect
            if (latest.phase.isActive) watched += latest.id
            val accepted = latest.phase == RunPhase.DONE && latest.verdict == Verdict.AC && watched.remove(latest.id)
            if (accepted && overlayState.value == null) {
                guestPrompt.claimOffer(projectId)?.let { overlayState.value = ConsoleOverlay.KeepWork(it) }
            }
        }
    }

    fun runTests(testIds: Set<String>? = null) = run(RunOptions(RunMode.TESTS, testIds = testIds))

    fun runWithInput() = run(RunOptions(RunMode.STDIN_ONLY, stdin = stdin.value))

    fun stop() {
        viewModelScope.launch { executions.stop(projectId) }
    }

    fun clearConsole() {
        viewModelScope.launch { executions.clearConsole(projectId) }
    }

    fun setStdin(value: String) {
        savedState[KEY_STDIN] = value
    }

    private val loadedTestStdinState = MutableStateFlow<String?>(null)

    /** Stdin read from a file for the open test editor; consumed once applied. */
    val loadedTestStdin: StateFlow<String?> = loadedTestStdinState

    fun loadStdinFromFile(uri: String, intoTestEditor: Boolean) {
        viewModelScope.launch {
            when (val result = documents.read(uri)) {
                is Outcome.Success -> if (intoTestEditor) loadedTestStdinState.value = result.value else setStdin(result.value)
                is Outcome.Failure -> events.send(ConsoleEvent.Message(result.error.toUiText()))
            }
        }
    }

    fun consumeLoadedTestStdin() {
        loadedTestStdinState.value = null
    }

    /** Turns the current custom stdin into a test case (expected left empty to fill in). */
    fun saveStdinAsTest() {
        addTest(TestCaseDraft(stdin.value, ""))
        viewModelScope.launch { events.send(ConsoleEvent.Message(UiText.Res(R.string.input_saved_as_test))) }
    }

    fun saveTest(existing: TestCase?, name: String, stdin: String, expected: String) {
        if (existing == null) addTest(TestCaseDraft(stdin, expected, name))
        else updateTest(existing.copy(name = name, stdin = stdin, expected = expected))
    }

    fun addTest(draft: TestCaseDraft = TestCaseDraft("", "")) {
        viewModelScope.launch { projects.addTest(projectId, draft) }
    }

    fun updateTest(test: TestCase) {
        viewModelScope.launch { projects.updateTest(test) }
    }

    fun deleteTest(test: TestCase) {
        viewModelScope.launch {
            projects.deleteTest(test.id)
            events.send(ConsoleEvent.TestDeleted(test))
        }
    }

    /** Undo for [deleteTest]: positions aren't renumbered on delete, so the test returns to its old slot. */
    fun restoreTest(test: TestCase) = updateTest(test)

    fun duplicateTest(test: TestCase) {
        viewModelScope.launch { projects.duplicateTest(test) }
    }

    /** "Save output as expected": turns an observed output into the test's expectation. */
    fun acceptOutput(test: TestCase, output: String) = updateTest(test.copy(expected = output))

    fun setLimits(limits: Limits) {
        viewModelScope.launch { projects.setLimits(projectId, limits) }
    }

    fun setSendWhenOnline(enabled: Boolean) {
        viewModelScope.launch { settings.updateRun { it.copy(sendQueuedWhenOnline = enabled) } }
    }

    fun setBypassCache(enabled: Boolean) {
        viewModelScope.launch { settings.updateRun { it.copy(bypassCache = enabled) } }
    }

    fun cancelPending(run: PendingRun) {
        viewModelScope.launch { executions.cancelPending(run.id) }
    }

    fun sendPendingNow() {
        viewModelScope.launch {
            if (!executions.sendPendingRuns(force = true)) events.send(ConsoleEvent.Message(UiText.Res(R.string.input_still_offline)))
        }
    }

    fun benchmark(copies: Int) {
        viewModelScope.launch {
            settings.updateRun { it.copy(benchmarkCopies = copies) }
            overlayState.value = ConsoleOverlay.Benchmarking
            when (val result = executions.benchmark(projectId, copies)) {
                is Outcome.Success -> overlayState.value = ConsoleOverlay.Benchmark(result.value)
                is Outcome.Failure -> {
                    overlayState.value = null
                    report(result.error)
                }
            }
        }
    }

    fun verify(jobId: String) {
        viewModelScope.launch {
            overlayState.value = ConsoleOverlay.Verifying
            when (val result = executions.replay(jobId)) {
                is Outcome.Success -> overlayState.value = ConsoleOverlay.Proof(result.value)
                is Outcome.Failure -> {
                    overlayState.value = null
                    report(result.error)
                }
            }
        }
    }

    fun dismissOverlay() {
        overlayState.value = null
    }

    private fun run(options: RunOptions) {
        viewModelScope.launch {
            events.send(ConsoleEvent.RunStarted(options.mode))
            val result = executions.run(projectId, options)
            if (result is Outcome.Failure) report(result.error)
        }
    }

    private suspend fun report(error: AppError) {
        if (error is AppError.RateLimited) {
            overlayState.update { ConsoleOverlay.RateLimited(error.retryAfterSeconds, uiState.value.rateLimit) }
        } else {
            events.send(ConsoleEvent.Message(error.toUiText()))
        }
    }

    private companion object {
        const val KEY_STDIN = "console_stdin"
    }
}
