package solutions.laxmi.omnicompiler.feature.workspace.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import solutions.laxmi.omnicompiler.core.ui.UiText
import solutions.laxmi.omnicompiler.core.ui.asString
import solutions.laxmi.omnicompiler.feature.workspace.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import solutions.laxmi.omnicompiler.core.designsystem.component.EmptyState
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniSpinner
import solutions.laxmi.omnicompiler.core.designsystem.component.SheetHandle
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.editor.CodeEditor
import solutions.laxmi.omnicompiler.core.editor.EditorDocument
import solutions.laxmi.omnicompiler.core.editor.SymbolRow
import solutions.laxmi.omnicompiler.core.editor.rememberCodeEditorState
import solutions.laxmi.omnicompiler.core.model.SourceFile
import solutions.laxmi.omnicompiler.core.navigation.AppearanceRoute
import solutions.laxmi.omnicompiler.core.navigation.DeveloperRoute
import solutions.laxmi.omnicompiler.core.navigation.EditorRoute
import solutions.laxmi.omnicompiler.core.navigation.ExamplesRoute
import solutions.laxmi.omnicompiler.core.navigation.HistoryRoute
import solutions.laxmi.omnicompiler.core.navigation.LanguagePickerRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.navigation.ProfileRoute
import solutions.laxmi.omnicompiler.core.navigation.ProjectsRoute
import solutions.laxmi.omnicompiler.core.navigation.SettingsRoute
import solutions.laxmi.omnicompiler.core.navigation.UsageRoute

@Composable
fun EditorScreen(route: EditorRoute, navigator: Navigator) {
    val viewModel = hiltViewModel<EditorViewModel, EditorViewModel.Factory>(key = route.toString()) { it.create(route) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is EditorEvent.Message -> snackbar.showSnackbar(event.text.asString(resources))
                is EditorEvent.OpenProject -> navigator.replace(EditorRoute(event.projectId))
            }
        }
    }

    EditorContent(
        state = state,
        snackbar = snackbar,
        actions = EditorActions(
            onRetry = viewModel::retry,
            onSelectFile = viewModel::selectFile,
            onContentChanged = viewModel::onContentChanged,
            onToggleMinimap = viewModel::toggleMinimap,
            onToggleWordWrap = viewModel::toggleWordWrap,
            onAddFile = viewModel::addFile,
            onAddHeaderPair = viewModel::addHeaderPair,
            onRenameFile = viewModel::renameFile,
            onDeleteFile = viewModel::deleteFile,
            onRenameProject = viewModel::renameProject,
            onResetToStarter = viewModel::resetToStarter,
            onNewProject = viewModel::newProject,
            onOpenProject = { id -> navigator.replace(EditorRoute(id)) },
            onPickRuntime = { state.workspace?.project?.id?.let { navigator.navigate(LanguagePickerRoute(it)) } },
            onAccount = { navigator.navigate(ProfileRoute) },
            onDestination = { destination ->
                navigator.navigate(
                    when (destination) {
                        DrawerDestination.Projects -> ProjectsRoute
                        DrawerDestination.Examples -> ExamplesRoute
                        DrawerDestination.History -> HistoryRoute
                        DrawerDestination.Usage -> UsageRoute
                        DrawerDestination.Developer -> DeveloperRoute
                        DrawerDestination.Settings -> SettingsRoute
                    },
                )
            },
            onAppearance = { navigator.navigate(AppearanceRoute) },
        ),
    )
}

/** Callbacks from the editor UI; grouped so the content composable stays previewable. */
internal class EditorActions(
    val onRetry: () -> Unit,
    val onSelectFile: (String) -> Unit,
    val onContentChanged: (String, String) -> Unit,
    val onToggleMinimap: () -> Unit,
    val onToggleWordWrap: () -> Unit,
    val onAddFile: (String, String, () -> Unit) -> Unit,
    val onAddHeaderPair: (String, String, () -> Unit) -> Unit,
    val onRenameFile: (String, String) -> Unit,
    val onDeleteFile: (String) -> Unit,
    val onRenameProject: (String) -> Unit,
    val onResetToStarter: () -> Unit,
    val onNewProject: () -> Unit,
    val onOpenProject: (String) -> Unit,
    val onPickRuntime: () -> Unit,
    val onAccount: () -> Unit,
    val onDestination: (DrawerDestination) -> Unit,
    val onAppearance: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun EditorContent(state: EditorUiState, snackbar: SnackbarHostState, actions: EditorActions) {
    val colors = OmniTheme.colors
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val editorState = rememberCodeEditorState()
    val typing = WindowInsets.isImeVisible
    var searching by rememberSaveable { mutableStateOf(false) }
    var showNewFile by rememberSaveable { mutableStateOf(false) }
    var renamingProject by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var fileMenuFor by remember { mutableStateOf<SourceFile?>(null) }
    var renamingFile by remember { mutableStateOf<SourceFile?>(null) }

    val workspace = state.workspace
    val activeFile = state.activeFile

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = colors.scrim,
        drawerContent = {
            ModalDrawerSheet(drawerShape = RectangleShape, drawerContainerColor = colors.surface, windowInsets = WindowInsets(0.dp)) {
                EditorDrawer(
                    user = state.user,
                    projects = state.projects,
                    currentProjectId = workspace?.project?.id,
                    currentProjectName = workspace?.project?.name.orEmpty(),
                    files = workspace?.files.orEmpty(),
                    activeFileId = activeFile?.id,
                    entryShortCode = state.language?.shortCode,
                    onAccount = { scope.launch { drawerState.close() }; actions.onAccount() },
                    onProject = { id ->
                        scope.launch { drawerState.close() }
                        if (id != workspace?.project?.id) actions.onOpenProject(id)
                    },
                    onNewProject = { scope.launch { drawerState.close() }; actions.onNewProject() },
                    onFile = { file -> scope.launch { drawerState.close() }; actions.onSelectFile(file.id) },
                    onDestination = { scope.launch { drawerState.close() }; actions.onDestination(it) },
                )
            }
        },
    ) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(colors.surface)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
                when {
                    state.error != null && workspace == null -> StartupError(state.error, actions.onRetry)
                    workspace == null || activeFile == null -> Loading()
                    else -> WorkspaceBody(
                        state = state,
                        projectId = workspace.project.id,
                        activeFile = activeFile,
                        editorState = editorState,
                        typing = typing,
                        searching = searching,
                        onSearchChange = { searching = it },
                        onOpenDrawer = openDrawer,
                        onShowNewFile = { showNewFile = true },
                        onRenameProject = { renamingProject = true },
                        onConfirmReset = { confirmReset = true },
                        onFileMenu = { fileMenuFor = it },
                        snackbar = snackbar,
                        actions = actions,
                    )
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 64.dp))
        }
    }

    if (showNewFile && workspace != null) {
        NewFileSheet(
            projectName = workspace.project.name,
            language = state.language,
            entryExtension = workspace.entry?.name?.substringAfterLast('.', "txt") ?: "txt",
            onDismiss = { showNewFile = false },
            onCreate = { name, content -> actions.onAddFile(name, content) { showNewFile = false } },
            onCreateHeaderPair = { base, ext -> actions.onAddHeaderPair(base, ext) { showNewFile = false } },
        )
    }
    if (renamingProject && workspace != null) {
        RenameDialog(stringResource(R.string.editor_rename_project_title), workspace.project.name, onDismiss = { renamingProject = false }) {
            actions.onRenameProject(it)
            renamingProject = false
        }
    }
    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.editor_reset_title),
            message = state.language?.name?.let { stringResource(R.string.editor_reset_message, it) }
                ?: stringResource(R.string.editor_reset_message_generic),
            confirmLabel = stringResource(R.string.editor_reset),
            onDismiss = { confirmReset = false },
            onConfirm = {
                actions.onResetToStarter()
                confirmReset = false
            },
        )
    }
    fileMenuFor?.let { file ->
        ConfirmDialog(
            title = file.name,
            message = stringResource(R.string.editor_file_actions_message),
            confirmLabel = stringResource(R.string.editor_rename),
            onDismiss = { fileMenuFor = null },
            onConfirm = {
                renamingFile = file
                fileMenuFor = null
            },
        )
    }
    renamingFile?.let { file ->
        RenameDialog(stringResource(R.string.editor_rename_file_title), file.name, onDismiss = { renamingFile = null }) {
            actions.onRenameFile(file.id, it)
            renamingFile = null
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { OmniSpinner() }
}

@Composable
private fun StartupError(message: UiText, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            title = stringResource(R.string.editor_cant_open_title),
            message = message.asString(),
            icon = OmniIcons.WifiOff,
            action = { OmniButton(stringResource(R.string.editor_try_again), onRetry, trailingIcon = OmniIcons.Refresh) },
        )
    }
}
