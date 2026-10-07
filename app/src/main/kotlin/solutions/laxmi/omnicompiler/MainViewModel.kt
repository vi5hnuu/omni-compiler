package solutions.laxmi.omnicompiler

import solutions.laxmi.omnicompiler.core.data.project.ProjectRepository
import solutions.laxmi.omnicompiler.core.data.project.ProjectFolderRepository
import solutions.laxmi.omnicompiler.core.data.project.FolderStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import solutions.laxmi.omnicompiler.core.model.AppTheme
import solutions.laxmi.omnicompiler.core.data.settings.SettingsRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import solutions.laxmi.omnicompiler.core.data.auth.AuthRepository
import solutions.laxmi.omnicompiler.core.model.Session
import javax.inject.Inject

/** Coarse app state that decides the root destination: signed in, and a reachable projects folder. */
enum class AppGate { Loading, SignedOut, NeedsFolder, Ready }

@HiltViewModel
class MainViewModel @Inject constructor(
    auth: AuthRepository,
    settings: SettingsRepository,
    private val folders: ProjectFolderRepository,
    private val projects: ProjectRepository,
) : ViewModel() {
    val gate: StateFlow<AppGate> = combine(auth.session, folders.status) { session, folder ->
        when {
            session == Session.Loading -> AppGate.Loading
            session == Session.SignedOut -> AppGate.SignedOut
            folder == FolderStatus.Checking -> AppGate.Loading
            folder != FolderStatus.Ready -> AppGate.NeedsFolder
            else -> AppGate.Ready
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppGate.Loading)

    /** Who is signed in; a change while an auth screen shows means that screen's sign-in just succeeded. */
    val userId: StateFlow<String?> = auth.session
        .map { (it as? Session.Active)?.user?.id }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Back in the foreground: the folder may have been removed, or files edited in another app. */
    private val pendingExternalFile = MutableStateFlow<String?>(null)

    /** A file another app asked us to open; the nav host opens it once and then calls [onExternalFileOpened]. */
    val externalFile: StateFlow<String?> = pendingExternalFile.asStateFlow()

    fun openExternalFile(uri: String) {
        pendingExternalFile.value = uri
    }

    fun onExternalFileOpened() {
        pendingExternalFile.value = null
    }

    fun onForeground() {
        viewModelScope.launch {
            folders.recheck()
            projects.refreshFromDisk()
        }
    }

    val appTheme: StateFlow<AppTheme> = settings.appTheme.stateIn(viewModelScope, SharingStarted.Eagerly, AppTheme.SYSTEM)
}
