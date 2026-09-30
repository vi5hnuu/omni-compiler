package solutions.laxmi.omnicompiler.feature.projects

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import solutions.laxmi.omnicompiler.core.data.practice.PracticeRepository
import solutions.laxmi.omnicompiler.core.data.project.ProjectRepository
import solutions.laxmi.omnicompiler.core.data.project.ProjectTemplate
import solutions.laxmi.omnicompiler.core.data.runtime.RuntimeRepository
import solutions.laxmi.omnicompiler.core.model.AppError
import solutions.laxmi.omnicompiler.core.model.Example
import solutions.laxmi.omnicompiler.core.model.Language
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.Problem
import solutions.laxmi.omnicompiler.core.model.Runtime
import solutions.laxmi.omnicompiler.core.model.TestCaseDraft
import solutions.laxmi.omnicompiler.core.ui.userMessage
import javax.inject.Inject

sealed interface PracticeEvent {
    data class Open(val projectId: String) : PracticeEvent
    data class Message(val text: String) : PracticeEvent
}

/** Shared "start coding" logic for examples and problems. */
internal class PracticeLauncher @Inject constructor(
    private val projects: ProjectRepository,
    private val runtimes: RuntimeRepository,
) {
    /** Replaces the current project's tests with [tests], keeping its code. */
    suspend fun useInCurrentProject(tests: List<TestCaseDraft>): Outcome<String> {
        val id = projects.lastProjectId.first() ?: return Outcome.Failure(AppError.NotFound("Open a project first."))
        projects.replaceTests(id, tests)
        return Outcome.Success(id)
    }

    suspend fun newProject(name: String, runtime: Runtime?, code: String?, tests: List<TestCaseDraft>): Outcome<String> {
        val target = runtime ?: runtimes.defaultRuntime()
            ?: return Outcome.Failure(AppError.Offline("Connect to the internet to load languages."))
        return projects.create(target, ProjectTemplate(name, code, tests))
    }
}

data class ExamplesUiState(val examples: List<Example> = emptyList(), val problems: List<Problem> = emptyList())

@HiltViewModel
class ExamplesViewModel @Inject internal constructor(
    practice: PracticeRepository,
    private val launcher: PracticeLauncher,
) : ViewModel() {
    private val state = MutableStateFlow(ExamplesUiState())
    val uiState: StateFlow<ExamplesUiState> = state

    private val events = Channel<PracticeEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        viewModelScope.launch { state.value = ExamplesUiState(practice.examples(), practice.problems()) }
    }

    fun useExample(example: Example, inNewProject: Boolean) {
        viewModelScope.launch {
            val result = if (inNewProject) launcher.newProject(example.id, runtime = null, code = null, tests = example.tests)
            else launcher.useInCurrentProject(example.tests)
            when (result) {
                is Outcome.Success -> events.send(PracticeEvent.Open(result.value))
                is Outcome.Failure -> events.send(PracticeEvent.Message(result.error.userMessage()))
            }
        }
    }
}

data class ProblemUiState(
    val problem: Problem? = null,
    val solutionLanguages: List<Language> = emptyList(),
    val allLanguages: List<Language> = emptyList(),
)

@HiltViewModel(assistedFactory = ProblemViewModel.Factory::class)
class ProblemViewModel @AssistedInject internal constructor(
    @Assisted slug: String,
    practice: PracticeRepository,
    runtimes: RuntimeRepository,
    private val launcher: PracticeLauncher,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(slug: String): ProblemViewModel
    }

    private val problem = MutableStateFlow<Problem?>(null)
    private val events = Channel<PracticeEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val uiState: StateFlow<ProblemUiState> = combine(problem, runtimes.languages) { p, languages ->
        val runnable = languages.filter { it.defaultRuntime?.isRunnable == true }
        ProblemUiState(
            problem = p,
            solutionLanguages = runnable.filter { p?.solutions?.containsKey(it.base) == true },
            allLanguages = runnable,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProblemUiState())

    init {
        viewModelScope.launch { problem.value = practice.problem(slug) }
    }

    /** Opens a new project with the problem's tests and, when available, its starter solution in [language]. */
    fun solve(language: Language?) {
        val current = problem.value ?: return
        viewModelScope.launch {
            val result = launcher.newProject(
                name = current.slug,
                runtime = language?.defaultRuntime,
                code = language?.let { current.solutions[it.base] },
                tests = current.tests,
            )
            when (result) {
                is Outcome.Success -> events.send(PracticeEvent.Open(result.value))
                is Outcome.Failure -> events.send(PracticeEvent.Message(result.error.userMessage()))
            }
        }
    }

    fun useTestsInCurrentProject() {
        val current = problem.value ?: return
        viewModelScope.launch {
            when (val result = launcher.useInCurrentProject(current.tests)) {
                is Outcome.Success -> events.send(PracticeEvent.Open(result.value))
                is Outcome.Failure -> events.send(PracticeEvent.Message(result.error.userMessage()))
            }
        }
    }
}
