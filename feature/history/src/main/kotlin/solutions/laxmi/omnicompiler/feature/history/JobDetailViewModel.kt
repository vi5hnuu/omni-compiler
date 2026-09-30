package solutions.laxmi.omnicompiler.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import solutions.laxmi.omnicompiler.core.data.execution.ExecutionRepository
import solutions.laxmi.omnicompiler.core.data.project.ProjectRepository
import solutions.laxmi.omnicompiler.core.data.project.ProjectTemplate
import solutions.laxmi.omnicompiler.core.data.runtime.RuntimeRepository
import solutions.laxmi.omnicompiler.core.model.Job
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.ReplayProof
import solutions.laxmi.omnicompiler.core.model.TestCaseDraft
import solutions.laxmi.omnicompiler.core.model.Verdict
import solutions.laxmi.omnicompiler.core.navigation.JobDetailRoute
import solutions.laxmi.omnicompiler.core.ui.userMessage

data class JobDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val job: Job? = null,
    val runtimeId: String? = null,
    val verifying: Boolean = false,
    val proof: ReplayProof? = null,
)

sealed interface JobDetailEvent {
    data class OpenProject(val projectId: String) : JobDetailEvent
    data class Message(val text: String) : JobDetailEvent
}

@HiltViewModel(assistedFactory = JobDetailViewModel.Factory::class)
class JobDetailViewModel @AssistedInject constructor(
    @Assisted private val route: JobDetailRoute,
    private val executions: ExecutionRepository,
    private val runtimes: RuntimeRepository,
    private val projects: ProjectRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: JobDetailRoute): JobDetailViewModel
    }

    private val state = MutableStateFlow(JobDetailUiState(runtimeId = route.runtimeId))
    val uiState: StateFlow<JobDetailUiState> = state

    private val events = Channel<JobDetailEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            state.update { it.copy(loading = true, error = null) }
            when (val result = executions.job(route.jobId)) {
                is Outcome.Success -> state.update { it.copy(loading = false, job = result.value) }
                is Outcome.Failure -> state.update { it.copy(loading = false, error = result.error.userMessage()) }
            }
        }
    }

    fun verify() {
        viewModelScope.launch {
            state.update { it.copy(verifying = true) }
            when (val result = executions.replay(route.jobId)) {
                is Outcome.Success -> state.update { it.copy(verifying = false, proof = result.value) }
                is Outcome.Failure -> {
                    state.update { it.copy(verifying = false) }
                    events.send(JobDetailEvent.Message(result.error.userMessage()))
                }
            }
        }
    }

    fun dismissProof() = state.update { it.copy(proof = null) }

    /** Recreates the submission as a project: its code, plus its tests where the judge echoed stdin/expected. */
    fun openAsProject() {
        val job = state.value.job ?: return
        val code = job.code ?: return
        viewModelScope.launch {
            val runtime = route.runtimeId?.let { runtimes.runtime(it) }
            if (runtime == null) {
                events.send(JobDetailEvent.Message("This runtime is no longer available."))
                return@launch
            }
            val tests = job.results
                .filter { it.verdict != Verdict.CE && it.stdin != null }
                .map { TestCaseDraft(it.stdin.orEmpty(), it.expected.orEmpty()) }
                .ifEmpty { null }
            when (val result = projects.create(runtime, ProjectTemplate("job-${route.jobId.take(6)}", code, tests))) {
                is Outcome.Success -> events.send(JobDetailEvent.OpenProject(result.value))
                is Outcome.Failure -> events.send(JobDetailEvent.Message(result.error.userMessage()))
            }
        }
    }
}
