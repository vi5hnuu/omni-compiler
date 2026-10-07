package solutions.laxmi.omnicompiler.feature.workspace.editor

import solutions.laxmi.omnicompiler.core.common.AppFeatures
import solutions.laxmi.omnicompiler.core.data.project.SharedFile
import solutions.laxmi.omnicompiler.core.data.project.ProjectExporter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import solutions.laxmi.omnicompiler.core.data.account.AccountRepository
import solutions.laxmi.omnicompiler.core.data.auth.AuthRepository
import solutions.laxmi.omnicompiler.core.data.project.ProjectRepository
import solutions.laxmi.omnicompiler.core.data.runtime.RuntimeRepository
import solutions.laxmi.omnicompiler.core.data.settings.SettingsRepository
import solutions.laxmi.omnicompiler.core.data.project.ProjectTemplate
import solutions.laxmi.omnicompiler.core.model.Language
import solutions.laxmi.omnicompiler.core.model.EditorSettings
import solutions.laxmi.omnicompiler.core.model.LanguageInfo
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.onSuccess
import solutions.laxmi.omnicompiler.core.model.ProjectFilter
import solutions.laxmi.omnicompiler.core.model.ProjectSummary
import solutions.laxmi.omnicompiler.core.model.WorkspaceOutline
import solutions.laxmi.omnicompiler.core.model.Runtime
import solutions.laxmi.omnicompiler.core.model.Session
import solutions.laxmi.omnicompiler.core.model.FileHeader
import solutions.laxmi.omnicompiler.core.model.OpenFile
import solutions.laxmi.omnicompiler.core.model.User
import solutions.laxmi.omnicompiler.core.navigation.EditorRoute
import solutions.laxmi.omnicompiler.core.ui.UiText
import solutions.laxmi.omnicompiler.core.ui.toUiText
import solutions.laxmi.omnicompiler.feature.workspace.R

data class EditorUiState(
    val loading: Boolean = true,
    val error: UiText? = null,
    val workspace: WorkspaceOutline? = null,
    val runtime: Runtime? = null,
    val language: LanguageInfo? = null,
    val activeFileId: String? = null,
    val settings: EditorSettings = EditorSettings(),
    val user: User? = null,
    /** Benchmark and run-limit actions are offered (debug builds; see AppFeatures.runTools). */
    val runTools: Boolean = false,
) {
    val activeFile: FileHeader? get() = workspace?.files?.firstOrNull { it.id == activeFileId } ?: workspace?.entry
}

/** "Runs this period" for the drawer footer; only shown when the plan has a quota. */
data class DrawerUsage(val used: Long, val limit: Long)

sealed interface EditorEvent {
    data class Message(val text: UiText) : EditorEvent
    data class OpenProject(val projectId: String) : EditorEvent
    data class Share(val file: SharedFile) : EditorEvent
}

@HiltViewModel(assistedFactory = EditorViewModel.Factory::class)
class EditorViewModel @AssistedInject constructor(
    @Assisted private val route: EditorRoute,
    private val projects: ProjectRepository,
    private val exporter: ProjectExporter,
    private val runtimes: RuntimeRepository,
    private val settingsRepository: SettingsRepository,
    private val account: AccountRepository,
    private val auth: AuthRepository,
    private val features: AppFeatures,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: EditorRoute): EditorViewModel
    }

    private val projectId = MutableStateFlow(route.projectId)
    private val activeFileId = MutableStateFlow<String?>(null)
    private val startupError = MutableStateFlow<UiText?>(null)

    private val events = Channel<EditorEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    /**
     * The workspace paired with the id it was loaded for. Emits (null, null) until a project is
     * resolved so startup errors still reach the UI; (id, null) means that project no longer exists.
     */
    private val loadedWorkspace = projectId.flatMapLatest { id ->
        if (id == null) flowOf(null to null) else projects.observeOutline(id).map { id to it }
    }

    private val workspace = loadedWorkspace.map { it.second }

    private val runtime = workspace.flatMapLatest { ws ->
        ws?.let { runtimes.observeRuntime(it.project.runtimeId) } ?: flowOf(null)
    }

    private val language = runtime.map { it?.language?.let { base -> runtimes.languageInfo(base) } }

    val uiState: StateFlow<EditorUiState> = combine(
        combine(loadedWorkspace, runtime, language, ::Triple),
        combine(activeFileId, startupError, ::Pair),
        settingsRepository.editorSettings,
        auth.session,
    ) { (loaded, rt, lang), (activeId, error), settings, session ->
        val (loadedId, ws) = loaded
        // A resolved project that reads back as null was deleted while open (e.g. from Projects).
        val shownError = error ?: UiText.Res(R.string.editor_project_deleted).takeIf { loadedId != null && ws == null }
        EditorUiState(
            loading = ws == null && shownError == null,
            error = shownError,
            workspace = ws,
            runtime = rt,
            language = lang,
            activeFileId = activeId?.takeIf { id -> ws?.files?.any { it.id == id } == true } ?: ws?.entry?.id,
            settings = settings,
            user = (session as? Session.Active)?.user,
            runTools = features.runTools,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorUiState())

    /**
     * Drawer-only data lives outside [uiState]: autosave touches the project list, and folding it into the
     * editor state would recompose the whole screen on every save. Collected only while the drawer shows.
     */
    val drawerProjects: StateFlow<List<ProjectSummary>> = projects.observeSummaries("", ProjectFilter.ALL)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Drawer destinations this build offers; Usage and API key are held back when account tools are off. */
    val drawerDestinations: List<DrawerDestination> = DrawerDestination.entries.filter { destination ->
        features.accountTools || (destination != DrawerDestination.Usage && destination != DrawerDestination.Developer)
    }
    private val showsUsage = features.accountTools

    private val usage = MutableStateFlow<DrawerUsage?>(null)
    val drawerUsage: StateFlow<DrawerUsage?> = usage
    private var usageRequest: Job? = null

    /** Refreshes the quota footer each time the drawer opens; a failure just keeps the last value. */
    fun onDrawerOpened() {
        if (!showsUsage || auth.session.value !is Session.Active || usageRequest?.isActive == true) return
        usageRequest = viewModelScope.launch {
            account.billing().onSuccess { billing ->
                usage.value = DrawerUsage(billing.executionsUsed, billing.quotaLimit).takeIf { it.limit > 0 }
            }
        }
    }

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

    /** An open file's text; collected only by the pane showing it. */
    fun observeFile(fileId: String): Flow<OpenFile?> = projects.observeFile(fileId)

    /** The repository orders saves and finishes them even if this screen goes away mid-write. */
    fun onContentChanged(fileId: String, text: String) {
        viewModelScope.launch { projects.updateFileContent(fileId, text) }
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
                is Outcome.Failure -> events.send(EditorEvent.Message(result.error.toUiText()))
            }
        }
    }

    /** Adds `name.h` + `name.<ext>` together for C/C++ projects (design W2 "Header pair"). */
    fun addHeaderPair(baseName: String, sourceExtension: String, onDone: () -> Unit) {
        val id = projectId.value ?: return
        viewModelScope.launch {
            val header = projects.addFile(id, "$baseName.h", "#pragma once\n")
            if (header is Outcome.Failure) return@launch events.send(EditorEvent.Message(header.error.toUiText()))
            when (val source = projects.addFile(id, "$baseName.$sourceExtension", "#include \"$baseName.h\"\n")) {
                is Outcome.Success -> {
                    activeFileId.value = source.value.id
                    onDone()
                }
                is Outcome.Failure -> events.send(EditorEvent.Message(source.error.toUiText()))
            }
        }
    }

    fun renameFile(fileId: String, name: String) {
        val id = projectId.value ?: return
        viewModelScope.launch {
            val result = projects.renameFile(fileId, id, name)
            if (result is Outcome.Failure) events.send(EditorEvent.Message(result.error.toUiText()))
        }
    }

    fun deleteFile(fileId: String) {
        viewModelScope.launch {
            if (activeFileId.value == fileId) activeFileId.value = null
            projects.deleteFile(fileId)
        }
    }

    fun shareFile(fileId: String) = share { id -> exporter.exportFile(id, fileId) }

    fun shareProject() = share { id -> exporter.exportZip(id) }

    /** Writes the entry file back to the document a single-file import came from. */
    fun saveToOrigin() {
        val id = projectId.value ?: return
        viewModelScope.launch {
            val message = when (val result = projects.saveToOrigin(id)) {
                is Outcome.Success -> UiText.Res(R.string.editor_saved_to_origin)
                is Outcome.Failure -> result.error.toUiText()
            }
            events.send(EditorEvent.Message(message))
        }
    }

    private fun share(export: suspend (projectId: String) -> Outcome<SharedFile>) {
        val id = projectId.value ?: return
        viewModelScope.launch {
            when (val result = export(id)) {
                is Outcome.Success -> events.send(EditorEvent.Share(result.value))
                is Outcome.Failure -> events.send(EditorEvent.Message(result.error.toUiText()))
            }
        }
    }

    fun renameProject(name: String) {
        val id = projectId.value ?: return
        viewModelScope.launch {
            val result = projects.rename(id, name)
            if (result is Outcome.Failure) events.send(EditorEvent.Message(result.error.toUiText()))
        }
    }

    fun resetToStarter() {
        val id = projectId.value ?: return
        viewModelScope.launch {
            val result = projects.resetToStarter(id)
            if (result is Outcome.Failure) events.send(EditorEvent.Message(result.error.toUiText()))
        }
    }

    /** Languages for the "New project" sheet; only collected while the sheet is open. */
    val languages: StateFlow<List<Language>> = runtimes.languages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun newProject(name: String, language: Language) {
        val runtime = language.defaultRuntime ?: return
        viewModelScope.launch {
            val template = ProjectTemplate(name.ifBlank { "${language.base}-scratch" }, code = null, tests = null)
            when (val result = projects.create(runtime, template)) {
                is Outcome.Success -> events.send(EditorEvent.OpenProject(result.value))
                is Outcome.Failure -> events.send(EditorEvent.Message(result.error.toUiText()))
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
                is Outcome.Failure -> startupError.value = result.error.toUiText()
            }
        }
    }

    private fun updateSettings(transform: (EditorSettings) -> EditorSettings) {
        viewModelScope.launch { settingsRepository.updateEditor(transform) }
    }
}
