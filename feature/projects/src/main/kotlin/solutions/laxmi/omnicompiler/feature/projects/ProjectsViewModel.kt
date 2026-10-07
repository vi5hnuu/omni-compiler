package solutions.laxmi.omnicompiler.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import solutions.laxmi.omnicompiler.core.common.TimeSource
import solutions.laxmi.omnicompiler.core.data.project.ProjectExporter
import solutions.laxmi.omnicompiler.core.data.project.ProjectRepository
import solutions.laxmi.omnicompiler.core.data.project.ProjectTemplate
import solutions.laxmi.omnicompiler.core.data.project.SharedFile
import solutions.laxmi.omnicompiler.core.data.runtime.RuntimeRepository
import solutions.laxmi.omnicompiler.core.model.Language
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.ProjectFilter
import solutions.laxmi.omnicompiler.core.model.ProjectSummary
import solutions.laxmi.omnicompiler.core.ui.UiText
import solutions.laxmi.omnicompiler.core.ui.toUiText
import javax.inject.Inject
import kotlin.time.Instant

data class ProjectRowUi(val summary: ProjectSummary, val shortCode: String)

data class ProjectsUiState(
    val query: String = "",
    val filter: ProjectFilter = ProjectFilter.ALL,
    val projects: List<ProjectRowUi> = emptyList(),
    val counts: Map<ProjectFilter, Int> = emptyMap(),
    val languages: List<Language> = emptyList(),
    val currentProjectId: String? = null,
    val now: Instant = Instant.fromEpochMilliseconds(0),
    val refreshing: Boolean = false,
    /** An import is copying files; further imports wait so a double tap can't import twice. */
    val importing: Boolean = false,
)

sealed interface ProjectsEvent {
    data class Open(val projectId: String) : ProjectsEvent
    data class Share(val file: SharedFile) : ProjectsEvent
    data class Message(val text: UiText) : ProjectsEvent
}

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projects: ProjectRepository,
    private val runtimes: RuntimeRepository,
    private val exporter: ProjectExporter,
    private val time: TimeSource,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val refreshing = MutableStateFlow(false)
    private val importing = MutableStateFlow(false)
    private val filter = MutableStateFlow(ProjectFilter.ALL)
    private val events = Channel<ProjectsEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private val shortCodes = MutableStateFlow<Map<String, String>>(emptyMap())

    val uiState: StateFlow<ProjectsUiState> = combine(
        query.flatMapLatest { q -> projects.observeSummaries(q, ProjectFilter.ALL) },
        query,
        filter,
        combine(runtimes.languages, shortCodes, ::Pair),
        combine(projects.lastProjectId, refreshing, importing, ::Triple),
    ) { all, q, f, (languages, codes), (lastId, isRefreshing, isImporting) ->
        // One query feeds both the visible list and every tab's count.
        val visible = all.filter(f::matches)
        ProjectsUiState(
            query = q,
            filter = f,
            projects = visible.map { ProjectRowUi(it, codes[it.project.runtimeId.substringBefore('-')] ?: it.project.runtimeId.take(2)) },
            counts = ProjectFilter.entries.associateWith { filter -> all.count(filter::matches) },
            languages = languages,
            currentProjectId = lastId,
            now = time.now(),
            refreshing = isRefreshing,
            importing = isImporting,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectsUiState())

    init {
        viewModelScope.launch {
            shortCodes.value = runtimes.allLanguageInfo().associate { it.base to it.shortCode }
        }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setFilter(value: ProjectFilter) {
        filter.value = value
    }

    fun create(name: String, language: Language) {
        val runtime = language.defaultRuntime ?: return
        viewModelScope.launch {
            when (val result = projects.create(runtime, ProjectTemplate(name.ifBlank { "${language.base}-scratch" }, code = null, tests = null))) {
                is Outcome.Success -> events.send(ProjectsEvent.Open(result.value))
                is Outcome.Failure -> events.send(ProjectsEvent.Message(result.error.toUiText()))
            }
        }
    }

    fun rename(projectId: String, name: String) = launchReporting { projects.rename(projectId, name) }

    fun duplicate(projectId: String) {
        viewModelScope.launch {
            when (val result = projects.duplicate(projectId)) {
                is Outcome.Success -> events.send(ProjectsEvent.Message(UiText.Res(R.string.projects_duplicated)))
                is Outcome.Failure -> events.send(ProjectsEvent.Message(result.error.toUiText()))
            }
        }
    }

    /** Re-reads the projects folder (pull to refresh): picks up projects added or edited in other apps. */
    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            val result = projects.refreshFromDisk()
            refreshing.value = false
            if (result is Outcome.Failure) events.send(ProjectsEvent.Message(result.error.toUiText()))
        }
    }

    fun importFolder(treeUri: String) = open { projects.importFolder(treeUri) }

    fun importFile(documentUri: String) = open { projects.importFile(documentUri) }

    private fun open(block: suspend () -> Outcome<String>) {
        if (importing.value) return
        importing.value = true
        viewModelScope.launch {
            try {
                when (val result = block()) {
                    is Outcome.Success -> events.send(ProjectsEvent.Open(result.value))
                    is Outcome.Failure -> events.send(ProjectsEvent.Message(result.error.toUiText()))
                }
            } finally {
                importing.value = false
            }
        }
    }

    fun delete(projectId: String) = launchReporting { projects.delete(projectId) }

    fun export(projectId: String) {
        viewModelScope.launch {
            when (val result = exporter.exportZip(projectId)) {
                is Outcome.Success -> events.send(ProjectsEvent.Share(result.value))
                is Outcome.Failure -> events.send(ProjectsEvent.Message(result.error.toUiText()))
            }
        }
    }

    private fun launchReporting(block: suspend () -> Outcome<Unit>) {
        viewModelScope.launch {
            val result = block()
            if (result is Outcome.Failure) events.send(ProjectsEvent.Message(result.error.toUiText()))
        }
    }
}
