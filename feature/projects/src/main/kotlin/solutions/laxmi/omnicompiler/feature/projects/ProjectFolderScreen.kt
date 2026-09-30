package solutions.laxmi.omnicompiler.feature.projects

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import solutions.laxmi.omnicompiler.core.data.project.FolderStatus
import solutions.laxmi.omnicompiler.core.data.project.ProjectFolderRepository
import solutions.laxmi.omnicompiler.core.designsystem.component.InfoBanner
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTopBar
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.navigation.ProjectFolderRoute
import solutions.laxmi.omnicompiler.core.ui.UiText
import solutions.laxmi.omnicompiler.core.ui.asString
import solutions.laxmi.omnicompiler.core.ui.toUiText
import javax.inject.Inject

data class ProjectFolderUiState(
    val status: FolderStatus = FolderStatus.Checking,
    val path: String? = null,
    val busy: Boolean = false,
    val error: UiText? = null,
)

@HiltViewModel
class ProjectFolderViewModel @Inject constructor(private val folders: ProjectFolderRepository) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<UiText?>(null)
    private val done = Channel<Unit>(Channel.CONFLATED)
    val doneFlow = done.receiveAsFlow()

    val uiState: StateFlow<ProjectFolderUiState> = combine(folders.status, folders.displayPath, busy, error) { status, path, working, failure ->
        ProjectFolderUiState(status, path, working, failure)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectFolderUiState())

    fun choose(treeUri: String) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            error.value = null
            when (val result = folders.choose(treeUri)) {
                is Outcome.Success -> done.send(Unit)
                is Outcome.Failure -> error.value = result.error.toUiText()
            }
            busy.value = false
        }
    }
}

/**
 * Where projects are saved (a folder the user can see in any file manager). Required before the editor opens,
 * and reachable from Settings to switch folders. Uses the system folder picker, so no storage permission is asked.
 */
@Composable
fun ProjectFolderScreen(route: ProjectFolderRoute, navigator: Navigator) {
    val viewModel = hiltViewModel<ProjectFolderViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.choose(uri.toString())
    }
    // On first run the app gate moves on by itself; from Settings, return to where the user came from.
    LaunchedEffect(viewModel) { viewModel.doneFlow.collect { if (route.change) navigator.back() } }
    val colors = OmniTheme.colors
    Column(Modifier.fillMaxSize().background(colors.background).navigationBarsPadding()) {
        if (route.change) {
            OmniTopBar(stringResource(R.string.folder_title_change), onBack = navigator::back)
        } else {
            Text(
                stringResource(R.string.folder_title),
                style = OmniTheme.typography.headline,
                color = colors.textPrimary,
                modifier = Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 24.dp),
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.status == FolderStatus.Lost) {
                InfoBanner(stringResource(R.string.folder_lost), icon = OmniIcons.WifiOff)
            }
            Text(stringResource(R.string.folder_body), style = OmniTheme.typography.body, color = colors.textSecondary)
            Text(stringResource(R.string.folder_privacy), style = OmniTheme.typography.bodySmall, color = colors.textTertiary)
            state.path?.let { Text(stringResource(R.string.folder_current, it), style = OmniTheme.typography.mono, color = colors.textPrimary) }
            if (route.change) Text(stringResource(R.string.folder_change_note), style = OmniTheme.typography.bodySmall, color = colors.textTertiary)
            state.error?.let { Text(it.asString(), style = OmniTheme.typography.bodySmall, color = colors.accentText) }
        }
        OmniButton(
            stringResource(if (state.busy) R.string.folder_working else R.string.folder_choose),
            { pick.launch(documentsFolder()) },
            Modifier.padding(16.dp),
            enabled = !state.busy,
            loading = state.busy,
            leadingIcon = OmniIcons.Folder,
        )
    }
}

/** Opens the picker in Documents where the provider supports an initial location (API 26+; ignored before). */
private fun documentsFolder(): Uri = DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, "primary:Documents")

private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
