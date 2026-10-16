package solutions.laxmi.omnicompiler.feature.workspace.editor

import solutions.laxmi.omnicompiler.core.model.OpenFile
import kotlinx.coroutines.flow.Flow
import androidx.activity.compose.LocalActivity
import solutions.laxmi.omnicompiler.core.ads.LocalAds
import solutions.laxmi.omnicompiler.core.review.LocalAppReview
import solutions.laxmi.omnicompiler.core.navigation.SourceControlRoute
import solutions.laxmi.omnicompiler.core.navigation.PreviewRoute
import solutions.laxmi.omnicompiler.core.ui.R as CommonR
import solutions.laxmi.omnicompiler.core.ui.NewProjectSheet
import solutions.laxmi.omnicompiler.core.ui.shareFile
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniSnackbarHost
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
import solutions.laxmi.omnicompiler.core.editor.CodeEditorState
import solutions.laxmi.omnicompiler.core.designsystem.theme.rememberWindowLayout
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import solutions.laxmi.omnicompiler.core.editor.EditorDocument
import solutions.laxmi.omnicompiler.core.editor.SymbolRow
import solutions.laxmi.omnicompiler.core.editor.rememberCodeEditorState
import solutions.laxmi.omnicompiler.core.model.ProjectSummary
import solutions.laxmi.omnicompiler.core.model.FileHeader
import solutions.laxmi.omnicompiler.core.navigation.AppearanceRoute
import solutions.laxmi.omnicompiler.core.navigation.DeveloperRoute
import solutions.laxmi.omnicompiler.core.navigation.EditorRoute
import solutions.laxmi.omnicompiler.core.navigation.ExamplesRoute
import solutions.laxmi.omnicompiler.core.navigation.HistoryRoute
import solutions.laxmi.omnicompiler.core.navigation.LanguagePickerRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.navigation.ProfileRoute
import solutions.laxmi.omnicompiler.core.navigation.ProjectsRoute
import solutions.laxmi.omnicompiler.core.navigation.RepoImportRoute
import solutions.laxmi.omnicompiler.core.navigation.SettingsRoute
import solutions.laxmi.omnicompiler.core.navigation.SignUpRoute
import solutions.laxmi.omnicompiler.core.navigation.UsageRoute

@Composable
fun EditorScreen(route: EditorRoute, navigator: Navigator) {
    val viewModel = hiltViewModel<EditorViewModel, EditorViewModel.Factory>(key = route.toString()) { it.create(route) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current
    val ads = LocalAds.current
    val review = LocalAppReview.current
    val activity = LocalActivity.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    // Wide windows dock the drawer as a sidebar beside the code; it starts open where there's plenty of room.
    val layout = rememberWindowLayout()
    var sidebarOpen by rememberSaveable(layout.large) { mutableStateOf(layout.large) }
    // Opening or open: subscribe to drawer data only then, so editing never pays for it.
    val drawerVisible = if (layout.expanded) sidebarOpen else drawerState.currentValue == DrawerValue.Open || drawerState.targetValue == DrawerValue.Open
    val drawerProjects = if (drawerVisible) viewModel.drawerProjects.collectAsStateWithLifecycle().value else emptyList()
    val drawerUsage by viewModel.drawerUsage.collectAsStateWithLifecycle()
    LaunchedEffect(drawerVisible) { if (drawerVisible) viewModel.onDrawerOpened() }
    var creatingProject by rememberSaveable { mutableStateOf(false) }
    val editorState = rememberCodeEditorState()
    val splitState = rememberCodeEditorState()
    // Replacing this screen ends its view model, so edits still in the autosave debounce are written first.
    val openProject: (String) -> Unit = { id ->
        editorState.flush()
        splitState.flush()
        navigator.replace(EditorRoute(id))
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is EditorEvent.Message -> snackbar.showSnackbar(event.text.asString(resources))
                is EditorEvent.OpenProject -> openProject(event.projectId)
                is EditorEvent.Share -> context.shareFile(event.file.uri, event.file.mimeType, resources.getString(R.string.editor_share_title, event.file.displayName))
            }
        }
    }

    EditorContent(
        state = state,
        editorState = editorState,
        splitState = splitState,
        drawer = DrawerContent(
            state = drawerState,
            projects = drawerProjects,
            usage = drawerUsage,
            destinations = viewModel.drawerDestinations,
            docked = layout.expanded,
            sidebarOpen = sidebarOpen,
            onToggleSidebar = { sidebarOpen = !sidebarOpen },
        ),
        snackbar = snackbar,
        actions = EditorActions(
            onRetry = viewModel::retry,
            onSelectFile = viewModel::selectFile,
            onContentChanged = viewModel::onContentChanged,
            fileText = viewModel::observeFile,
            onToggleMinimap = viewModel::toggleMinimap,
            onToggleWordWrap = viewModel::toggleWordWrap,
            onAddFile = viewModel::addFile,
            onAddHeaderPair = viewModel::addHeaderPair,
            onRenameFile = viewModel::renameFile,
            onDeleteFile = viewModel::deleteFile,
            onShareFile = viewModel::shareFile,
            onShareProject = viewModel::shareProject,
            onSaveToOrigin = viewModel::saveToOrigin,
            onSourceControl = { state.workspace?.project?.id?.let { navigator.navigate(SourceControlRoute(it)) } },
            onPreview = { fileId ->
                state.workspace?.project?.id?.let { navigator.navigate(PreviewRoute(it, fileId)) }
            },
            onRenameProject = viewModel::renameProject,
            onResetToStarter = viewModel::resetToStarter,
            onNewProject = { creatingProject = true },
            onOpenProject = { id ->
                // Switching projects from the drawer is a natural break (never mid-run: runs belong to the old project).
                activity?.let { ads.onNaturalBreak(it) }
                openProject(id)
            },
            onPickRuntime = { state.workspace?.project?.id?.let { navigator.navigate(LanguagePickerRoute(it)) } },
            onAccount = { navigator.navigate(ProfileRoute) },
            onConvertGuest = { navigator.navigate(SignUpRoute(convertGuest = true)) },
            onDestination = { destination ->
                when (destination) {
                    DrawerDestination.Projects -> navigator.navigate(ProjectsRoute)
                    DrawerDestination.ImportGit -> navigator.navigate(RepoImportRoute)
                    DrawerDestination.Examples -> navigator.navigate(ExamplesRoute)
                    DrawerDestination.History -> navigator.navigate(HistoryRoute)
                    DrawerDestination.Usage -> navigator.navigate(UsageRoute)
                    DrawerDestination.Developer -> navigator.navigate(DeveloperRoute)
                    DrawerDestination.Settings -> navigator.navigate(SettingsRoute)
                    DrawerDestination.RateApp -> review.openStoreListing(context)
                }
            },
            onAppearance = { navigator.navigate(AppearanceRoute) },
        ),
    )
    if (creatingProject) {
        val languages by viewModel.languages.collectAsStateWithLifecycle()
        NewProjectSheet(languages, onDismiss = { creatingProject = false }, initialBase = state.runtime?.language) { name, language ->
            creatingProject = false
            viewModel.newProject(name, language)
        }
    }
}

/** What the navigation drawer shows; kept apart from [EditorUiState] (see [EditorViewModel.drawerProjects]). */
internal class DrawerContent(
    val state: DrawerState,
    val projects: List<ProjectSummary>,
    val usage: DrawerUsage?,
    val destinations: List<DrawerDestination>,
    /** Wide window: the drawer is a sidebar beside the editor (toggled by the menu button) and the console docks right. */
    val docked: Boolean,
    val sidebarOpen: Boolean,
    val onToggleSidebar: () -> Unit,
)

/** Callbacks from the editor UI; grouped so the content composable stays previewable. */
internal class EditorActions(
    val onRetry: () -> Unit,
    val onSelectFile: (String) -> Unit,
    val onContentChanged: (String, String) -> Unit,
    /** An open file's text, re-emitted only when it is replaced outside the editor. */
    val fileText: (fileId: String) -> Flow<OpenFile?>,
    val onToggleMinimap: () -> Unit,
    val onToggleWordWrap: () -> Unit,
    val onAddFile: (String, String, () -> Unit) -> Unit,
    val onAddHeaderPair: (String, String, () -> Unit) -> Unit,
    val onRenameFile: (String, String) -> Unit,
    val onDeleteFile: (String) -> Unit,
    val onShareFile: (String) -> Unit,
    val onShareProject: () -> Unit,
    val onSaveToOrigin: () -> Unit,
    val onSourceControl: () -> Unit,
    val onPreview: (fileId: String) -> Unit,
    val onRenameProject: (String) -> Unit,
    val onResetToStarter: () -> Unit,
    val onNewProject: () -> Unit,
    val onOpenProject: (String) -> Unit,
    val onPickRuntime: () -> Unit,
    val onAccount: () -> Unit,
    val onConvertGuest: () -> Unit,
    val onDestination: (DrawerDestination) -> Unit,
    val onAppearance: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun EditorContent(
    state: EditorUiState,
    editorState: CodeEditorState,
    splitState: CodeEditorState,
    drawer: DrawerContent,
    snackbar: SnackbarHostState,
    actions: EditorActions,
) {
    val colors = OmniTheme.colors
    val drawerState = drawer.state
    val scope = rememberCoroutineScope()
    // Typing mode is for code only; a keyboard raised by another field (stdin, test editor) keeps the normal chrome.
    val typing = WindowInsets.isImeVisible && (editorState.hasFocus || splitState.hasFocus)
    var searching by rememberSaveable { mutableStateOf(false) }
    var showNewFile by rememberSaveable { mutableStateOf(false) }
    var renamingProject by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var fileMenuFor by remember { mutableStateOf<FileHeader?>(null) }
    var renamingFile by remember { mutableStateOf<FileHeader?>(null) }
    var deletingFile by remember { mutableStateOf<FileHeader?>(null) }

    val workspace = state.workspace
    val activeFile = state.activeFile

    // Shared by the modal drawer (phones) and the sidebar (wide windows); closing a closed drawer does nothing.
    val drawerPanel: @Composable () -> Unit = {
                EditorDrawer(
                    user = state.user,
                    projects = drawer.projects,
                    usage = drawer.usage,
                    destinations = drawer.destinations,
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
    val sidebarShown = drawer.docked && drawer.sidebarOpen
    val mainContent: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(colors.surface)
                    // A shown sidebar already keeps clear of the start edge's bars and cutout.
                    .windowInsetsPadding(screenInsets.only(if (sidebarShown) WindowInsetsSides.End + WindowInsetsSides.Vertical else WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical))
                    .imePadding(),
            ) {
                val openDrawer: () -> Unit = if (drawer.docked) drawer.onToggleSidebar else ({ scope.launch { drawerState.open() } })
                when {
                    state.error != null && workspace == null -> StartupError(state.error, state.errorIsConnection, actions.onRetry)
                    workspace == null || activeFile == null -> Loading()
                    else -> WorkspaceBody(
                        state = state,
                        projectId = workspace.project.id,
                        activeFile = activeFile,
                        editorState = editorState,
                        splitState = splitState,
                        typing = typing,
                        searching = searching,
                        onSearchChange = { searching = it },
                        onOpenDrawer = openDrawer,
                        onShowNewFile = { showNewFile = true },
                        onRenameProject = { renamingProject = true },
                        onConfirmReset = { confirmReset = true },
                        onFileMenu = { fileMenuFor = it },
                        drawerOpen = !drawer.docked && drawerState.targetValue == DrawerValue.Open,
                        docked = drawer.docked,
                        snackbar = snackbar,
                        actions = actions,
                    )
                }
            }
            OmniSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp))
        }
    }

    if (drawer.docked) {
        Row(Modifier.fillMaxSize().background(colors.surface)) {
            if (drawer.sidebarOpen) {
                drawerPanel()
                Box(Modifier.fillMaxHeight().width(1.dp).background(colors.divider))
            }
            Box(Modifier.weight(1f)) { mainContent() }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            // Opens from the menu button only: an edge swipe would fight code scrolling and the console sheet.
            gesturesEnabled = drawerState.isOpen,
            scrimColor = colors.scrim,
            drawerContent = {
                // The drawerState overload is the one that closes the drawer on (predictive) Back.
                ModalDrawerSheet(
                    drawerState = drawerState,
                    drawerShape = RectangleShape,
                    drawerContainerColor = colors.surface,
                    windowInsets = WindowInsets(0.dp),
                    content = { drawerPanel() },
                )
            },
            content = mainContent,
        )
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
        FileActionsSheet(
            file = file,
            onDismiss = { fileMenuFor = null },
            onRename = { renamingFile = file },
            onShare = { actions.onShareFile(file.id) },
            onDelete = { deletingFile = file },
        )
    }
    deletingFile?.let { file ->
        ConfirmDialog(
            title = stringResource(R.string.editor_delete_file_title, file.name),
            message = stringResource(R.string.editor_delete_file_message),
            confirmLabel = stringResource(CommonR.string.common_delete),
            onDismiss = { deletingFile = null },
            onConfirm = {
                actions.onDeleteFile(file.id)
                deletingFile = null
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
private fun StartupError(message: UiText, connection: Boolean, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            title = stringResource(R.string.editor_cant_open_title),
            message = message.asString(),
            icon = if (connection) OmniIcons.WifiOff else OmniIcons.Alert,
            action = { OmniButton(stringResource(R.string.editor_try_again), onRetry, trailingIcon = OmniIcons.Refresh) },
        )
    }
}
