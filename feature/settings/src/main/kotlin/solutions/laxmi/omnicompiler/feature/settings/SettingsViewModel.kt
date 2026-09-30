package solutions.laxmi.omnicompiler.feature.settings

import solutions.laxmi.omnicompiler.core.data.project.ProjectFolderRepository
import solutions.laxmi.omnicompiler.core.model.AppTheme
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import solutions.laxmi.omnicompiler.core.data.account.AppConfig
import solutions.laxmi.omnicompiler.core.data.auth.AuthRepository
import solutions.laxmi.omnicompiler.core.data.runtime.RuntimeRepository
import solutions.laxmi.omnicompiler.core.data.settings.SettingsRepository
import solutions.laxmi.omnicompiler.core.model.EditorSettings
import solutions.laxmi.omnicompiler.core.model.Language
import solutions.laxmi.omnicompiler.core.model.Limits
import solutions.laxmi.omnicompiler.core.model.RunSettings
import solutions.laxmi.omnicompiler.core.model.Session
import solutions.laxmi.omnicompiler.core.model.User
import javax.inject.Inject

data class SettingsUiState(
    val editor: EditorSettings = EditorSettings(),
    val run: RunSettings = RunSettings(),
    val user: User? = null,
    val languages: List<Language> = emptyList(),
    val appTheme: AppTheme = AppTheme.SYSTEM,
    val projectsFolder: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val auth: AuthRepository,
    runtimes: RuntimeRepository,
    folders: ProjectFolderRepository,
    val config: AppConfig,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.editorSettings,
        settings.runSettings,
        auth.session,
        runtimes.languages,
        combine(settings.appTheme, folders.displayPath, ::Pair),
    ) { editor, run, session, languages, (appTheme, folder) ->
        SettingsUiState(editor, run, (session as? Session.Active)?.user, languages.filter { it.defaultRuntime?.isRunnable == true }, appTheme, folder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun updateEditor(transform: (EditorSettings) -> EditorSettings) {
        viewModelScope.launch { settings.updateEditor(transform) }
    }

    fun setAppTheme(theme: AppTheme) {
        viewModelScope.launch { settings.setAppTheme(theme) }
    }

    fun updateRun(transform: (RunSettings) -> RunSettings) {
        viewModelScope.launch { settings.updateRun(transform) }
    }

    fun setDefaultRuntime(runtimeId: String?) = updateRun { it.copy(defaultRuntimeId = runtimeId) }

    fun setDefaultLimits(limits: Limits) = updateRun { it.copy(defaultLimits = limits.clamped()) }

    /** The app's session gate navigates to Welcome once the session is cleared. */
    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }
}
