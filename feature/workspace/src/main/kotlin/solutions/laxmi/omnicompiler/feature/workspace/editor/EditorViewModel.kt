package solutions.laxmi.omnicompiler.feature.workspace.editor

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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import solutions.laxmi.omnicompiler.core.data.auth.AuthRepository
import solutions.laxmi.omnicompiler.core.data.project.ProjectRepository
import solutions.laxmi.omnicompiler.core.data.runtime.RuntimeRepository
import solutions.laxmi.omnicompiler.core.data.settings.SettingsRepository
import solutions.laxmi.omnicompiler.core.model.EditorSettings
import solutions.laxmi.omnicompiler.core.model.LanguageInfo
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.ProjectFilter
import solutions.laxmi.omnicompiler.core.model.ProjectSummary
import solutions.laxmi.omnicompiler.core.model.ProjectWorkspace
import solutions.laxmi.omnicompiler.core.model.Runtime
import solutions.laxmi.omnicompiler.core.model.Session
import solutions.laxmi.omnicompiler.core.model.SourceFile
import solutions.laxmi.omnicompiler.core.model.User
import solutions.laxmi.omnicompiler.core.navigation.EditorRoute
import solutions.laxmi.omnicompiler.core.ui.userMessage

data class EditorUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val workspace: ProjectWorkspace? = null,
    val runtime: Runtime? = null,
    val language: LanguageInfo? = null,
    val activeFileId: String? = null,
    val revisions: Map<String, Int> = emptyMap(),
    val settings: EditorSettings = EditorSettings(),
    val user: User? = null,
    val projects: List<ProjectSummary> = emptyList(),
) {
    val activeFile: SourceFile? get() = workspace?.files?.firstOrNull { it.id == activeFileId } ?: workspace?.entry
    val runtimeLabel: String get() = runtime?.let { "${language?.name ?: it.language} ${it.version}" } ?: workspace?.project?.runtimeId.orEmpty()
}

sealed interface EditorEvent {
    data class Message(val text: String) : EditorEvent
    data class OpenProject(val projectId: String) : EditorEvent
}

@HiltViewModel(assistedFactory = EditorViewModel.Factory::class)
class EditorViewModel @AssistedInject constructor(
    @Assisted private val route: EditorRoute,
    private val projects: ProjectRepository,
    private val runtimes: RuntimeRepository,
    private val settingsRepository: SettingsRepository,
    auth: AuthRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: EditorRoute): EditorViewModel
    }

    private val projectId = MutableStateFlow(route.projectId)
    private val activeFileId = MutableStateFlow<String?>(null)
    private val startupError = MutableStateFlow<String?>(null)

    /** Content the editor view currently holds per file; a DB value that differs means an external replace. */
    private val knownContent = mutableMapOf<String, String>()

    /**
     * Saves still being written per file. While one is in flight, a database emission may carry
     * older text (read before the write landed) and must not be mistaken for an external replace.
     * Touched only on the main thread (ViewModel callbacks and viewModelScope).
     */
    private val savesInFlight = mutableMapOf<String, Int>()
    private val revisions = MutableStateFlow<Map<String, Int>>(emptyMap())

    private val events = Channel<EditorEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    /**
     * The workspace paired with the id it was loaded for. Emits (null, null) until a project is
     * resolved so startup errors still reach the UI; (id, null) means that project no longer exists.
     */
    private val loadedWorkspace = projectId.flatMapLatest { id ->
        if (id == null) flowOf(null to null) else projects.observeWorkspace(id).map { id to it }
    }

    private val workspace = loadedWorkspace.map { it.second }

    private val runtime = workspace.flatMapLatest { ws ->
        ws?.let { runtimes.observeRuntime(it.project.runtimeId) } ?: flowOf(null)
    }

    private val language = runtime.map { it?.language?.let { base -> runtimes.languageInfo(base) } }

    val uiState: StateFlow<EditorUiState> = combine(
        combine(loadedWorkspace, runtime, language, ::Triple),
        combine(activeFileId, revisions, startupError, ::Triple),
        settingsRepository.editorSettings,
        auth.session,
        projects.observeSummaries("", ProjectFilter.ALL),
    ) { (loaded, rt, lang), (activeId, revs, error), settings, session, summaries ->
        val (loadedId, ws) = loaded
        ws?.files?.forEach(::trackExternalChanges)
        // A resolved project that reads back as null was deleted while open (e.g. from Projects).
        val shownError = error ?: PROJECT_DELETED.takeIf { loadedId != null && ws == null }
        EditorUiState(
            loading = ws == null && shownError == null,
            error = shownError,
            workspace = ws,
            runtime = rt,
            language = lang,
            activeFileId = activeId?.takeIf { id -> ws?.files?.any { it.id == id } == true } ?: ws?.entry?.id,
            revisions = revs,
            settings = settings,
            user = (session as? Session.Active)?.user,
            projects = summaries,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorUiState())

    init {
        viewModelScope.launch { runtimes.refresh() }
        val requested = route.projectId
        if (requested == null) resolveStartupProject() else viewModelScope.launch { projects.markOpened(requested) }
    }

    fun retry() {
        startupError.value = null
        resolveStartupProject()
    }

    fun selectFile(fileId: String) {
        activeFileId.value = fileId
    }

    fun onContentChanged(fileId: String, text: String) {
        knownContent[fileId] = text
        savesInFlight[fileId] = (savesInFlight[fileId] ?: 0) + 1
        viewModelScope.launch {
            try {
                projects.updateFileContent(fileId, text)
            } finally {
                savesInFlight[fileId] = (savesInFlight[fileId] ?: 1) - 1
            }
        }
    }

    fun toggleMinimap() = updateSettings { it.copy(minimap = !it.minimap) }

    fun toggleWordWrap() = updateSettings { it.copy(wordWrap = !it.wordWrap) }

    fun addFile(name: String, content: String, onDone: () -> Unit) {
        val id = projectId.value ?: return
        viewModelScope.launch {
            when (val result = projects.addFile(id, name, content)) {
                is Outcome.Success -> {
                    activeFileId.value = result.value.id
                    onDone()
                }
                is Outcome.Failure -> events.send(EditorEvent.Message(result.error.userMessage()))
            }
        }
    }

    /** Adds `name.h` + `name.<ext>` together for C/C++ projects (design W2 "Header pair"). */
    fun addHeaderPair(baseName: String, sourceExtension: String, onDone: () -> Unit) {
        val id = projectId.value ?: return
        viewModelScope.launch {
            val header = projects.addFile(id, "$baseName.h", "#pragma once\n")
            if (header is Outcome.Failure) return@launch events.send(EditorEvent.Message(header.error.userMessage()))
            when (val source = projects.addFile(id, "$baseName.$sourceExtension", "#include \"$baseName.h\"\n")) {
                is Outcome.Success -> {
                    activeFileId.value = source.value.id
                    onDone()
                }
                is Outcome.Failure -> events.send(EditorEvent.Message(source.error.userMessage()))
            }
        }
    }

    fun renameFile(fileId: String, name: String) {
        val id = projectId.value ?: return
        viewModelScope.launch {
            val result = projects.renameFile(fileId, id, name)
            if (result is Outcome.Failure) events.send(EditorEvent.Message(result.error.userMessage()))
        }
    }

    fun deleteFile(fileId: String) {
        viewModelScope.launch {
            if (activeFileId.value == fileId) activeFileId.value = null
            projects.deleteFile(fileId)
        }
    }

    fun renameProject(name: String) {
        val id = projectId.value ?: return
        viewModelScope.launch {
            val result = projects.rename(id, name)
            if (result is Outcome.Failure) events.send(EditorEvent.Message(result.error.userMessage()))
        }
    }

    fun resetToStarter() {
        val id = projectId.value ?: return
        viewModelScope.launch {
            val result = projects.resetToStarter(id)
            if (result is Outcome.Failure) events.send(EditorEvent.Message(result.error.userMessage()))
        }
    }

    fun newProject() {
        viewModelScope.launch {
            val runtime = uiState.value.runtime ?: runtimes.defaultRuntime()
            if (runtime == null) {
                events.send(EditorEvent.Message("Connect to the internet to load languages."))
                return@launch
            }
            when (val result = projects.create(runtime)) {
                is Outcome.Success -> events.send(EditorEvent.OpenProject(result.value))
                is Outcome.Failure -> events.send(EditorEvent.Message(result.error.userMessage()))
            }
        }
    }

    private fun resolveStartupProject() {
        viewModelScope.launch {
            when (val result = projects.resolveStartupProject()) {
                is Outcome.Success -> {
                    projectId.value = result.value
                    projects.markOpened(result.value)
                }
                is Outcome.Failure -> startupError.value = result.error.userMessage()
            }
        }
    }

    private fun trackExternalChanges(file: SourceFile) {
        if ((savesInFlight[file.id] ?: 0) > 0) return
        val known = knownContent[file.id]
        if (known == null) {
            knownContent[file.id] = file.content
        } else if (known != file.content) {
            knownContent[file.id] = file.content
            revisions.update { it + (file.id to (it[file.id] ?: 0) + 1) }
        }
    }

    private fun updateSettings(transform: (EditorSettings) -> EditorSettings) {
        viewModelScope.launch { settingsRepository.updateEditor(transform) }
    }

    private companion object {
        const val PROJECT_DELETED = "This project was deleted."
    }
}
