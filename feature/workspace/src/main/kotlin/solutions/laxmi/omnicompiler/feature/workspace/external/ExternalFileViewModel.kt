package solutions.laxmi.omnicompiler.feature.workspace.external

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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import solutions.laxmi.omnicompiler.core.data.file.ExternalFile
import solutions.laxmi.omnicompiler.core.data.file.ExternalFileRepository
import solutions.laxmi.omnicompiler.core.data.project.ProjectRepository
import solutions.laxmi.omnicompiler.core.data.settings.SettingsRepository
import solutions.laxmi.omnicompiler.core.model.EditorSettings
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.navigation.ExternalFileRoute
import solutions.laxmi.omnicompiler.core.ui.UiText
import solutions.laxmi.omnicompiler.core.ui.toUiText

enum class SaveState { Saved, Saving, Failed }

data class ExternalFileUiState(
    val loading: Boolean = true,
    val file: ExternalFile? = null,
    val loadError: UiText? = null,
    val save: SaveState = SaveState.Saved,
    val openingAsProject: Boolean = false,
)

sealed interface ExternalFileEvent {
    data class Message(val text: UiText) : ExternalFileEvent
    data class OpenProject(val projectId: String) : ExternalFileEvent
}

/** Edits a file another app opened with us, writing changes back to it (no copy, no project). */
@HiltViewModel(assistedFactory = ExternalFileViewModel.Factory::class)
class ExternalFileViewModel @AssistedInject constructor(
    @Assisted private val route: ExternalFileRoute,
    private val files: ExternalFileRepository,
    private val projects: ProjectRepository,
    settings: SettingsRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: ExternalFileRoute): ExternalFileViewModel
    }

    private val state = MutableStateFlow(ExternalFileUiState())
    val uiState: StateFlow<ExternalFileUiState> = state.asStateFlow()

    val editorSettings: StateFlow<EditorSettings> = settings.editorSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorSettings())

    private val events = Channel<ExternalFileEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    /** Saves run one at a time, so an older text never lands after a newer one. */
    private val saving = Mutex()
    private var latestText: String? = null

    init {
        viewModelScope.launch {
            when (val result = files.open(route.uri)) {
                is Outcome.Success -> state.update { it.copy(loading = false, file = result.value) }
                is Outcome.Failure -> state.update { it.copy(loading = false, loadError = result.error.toUiText()) }
            }
        }
    }

    /** Called with the editor's debounced text; writes it back to the original file. */
    fun onTextChanged(text: String) {
        latestText = text
        val file = state.value.file?.takeIf { it.writable } ?: return
        state.update { it.copy(save = SaveState.Saving) }
        viewModelScope.launch {
            saving.withLock {
                val result = files.save(file.uri, text)
                // A newer edit may have queued meanwhile; its own save reports the final state.
                if (latestText != text) return@withLock
                state.update { it.copy(save = if (result is Outcome.Success) SaveState.Saved else SaveState.Failed) }
                if (result is Outcome.Failure) events.send(ExternalFileEvent.Message(result.error.toUiText()))
            }
        }
    }

    /** Copies the current text (the caller flushes the editor first) into a new project, where it can be run. */
    fun openAsProject() {
        val file = state.value.file ?: return
        val currentText = latestText ?: file.text
        if (state.value.openingAsProject) return
        state.update { it.copy(openingAsProject = true) }
        viewModelScope.launch {
            when (val result = projects.importText(file.name, currentText)) {
                is Outcome.Success -> events.send(ExternalFileEvent.OpenProject(result.value))
                is Outcome.Failure -> events.send(ExternalFileEvent.Message(result.error.toUiText()))
            }
            state.update { it.copy(openingAsProject = false) }
        }
    }
}
