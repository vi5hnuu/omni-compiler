package solutions.laxmi.omnicompiler.feature.workspace.editor

import solutions.laxmi.omnicompiler.feature.workspace.search.ProjectSearchSheet
import solutions.laxmi.omnicompiler.feature.workspace.search.ProjectSearchHit
import android.content.res.Configuration
import solutions.laxmi.omnicompiler.core.model.OpenFile
import kotlinx.coroutines.flow.flowOf
import androidx.compose.ui.platform.LocalConfiguration
import solutions.laxmi.omnicompiler.core.editor.EditorCommand
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import solutions.laxmi.omnicompiler.feature.workspace.console.ConsolePeekHeight
import solutions.laxmi.omnicompiler.core.designsystem.component.SheetHandle
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniDimens
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BottomSheetScaffold
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import solutions.laxmi.omnicompiler.core.ui.asString
import solutions.laxmi.omnicompiler.feature.workspace.R
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import solutions.laxmi.omnicompiler.core.designsystem.component.InfoBanner
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.editor.CodeEditor
import solutions.laxmi.omnicompiler.core.editor.CodeEditorState
import solutions.laxmi.omnicompiler.core.editor.EditorDiagnostic
import solutions.laxmi.omnicompiler.core.editor.EditorDocument
import solutions.laxmi.omnicompiler.core.editor.EditorLineHint
import solutions.laxmi.omnicompiler.core.editor.SymbolRow
import solutions.laxmi.omnicompiler.core.model.RunMode
import solutions.laxmi.omnicompiler.core.model.RunPhase
import solutions.laxmi.omnicompiler.core.model.RunRecord
import solutions.laxmi.omnicompiler.core.model.Verdict
import solutions.laxmi.omnicompiler.core.model.FileHeader
import solutions.laxmi.omnicompiler.core.model.TestCase
import solutions.laxmi.omnicompiler.feature.workspace.console.BenchmarkSheet
import solutions.laxmi.omnicompiler.feature.workspace.console.ConsoleEvent
import solutions.laxmi.omnicompiler.feature.workspace.console.ConsoleOverlay
import solutions.laxmi.omnicompiler.feature.workspace.console.ConsolePeek
import solutions.laxmi.omnicompiler.feature.workspace.console.ConsoleSheet
import solutions.laxmi.omnicompiler.feature.workspace.console.ConsoleSheetActions
import solutions.laxmi.omnicompiler.feature.workspace.console.ConsoleTab
import solutions.laxmi.omnicompiler.feature.workspace.console.ConsoleViewModel
import solutions.laxmi.omnicompiler.feature.workspace.console.KeepWorkSheet
import solutions.laxmi.omnicompiler.feature.workspace.console.LimitsSheet
import solutions.laxmi.omnicompiler.feature.workspace.console.ProofSheet
import solutions.laxmi.omnicompiler.feature.workspace.console.RateLimitSheet
import solutions.laxmi.omnicompiler.feature.workspace.console.TestActions
import solutions.laxmi.omnicompiler.feature.workspace.console.TestEditorSheet
import solutions.laxmi.omnicompiler.feature.workspace.console.problemsFor

/** Where a picked stdin file should go. */
private enum class StdinTarget { Input, TestEditor }

/** Editor + console for a resolved project. Owns the console ViewModel, keyed by project. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColumnScope.WorkspaceBody(
    state: EditorUiState,
    projectId: String,
    activeFile: FileHeader,
    editorState: CodeEditorState,
    splitState: CodeEditorState,
    typing: Boolean,
    searching: Boolean,
    onSearchChange: (Boolean) -> Unit,
    onOpenDrawer: () -> Unit,
    onShowNewFile: () -> Unit,
    onRenameProject: () -> Unit,
    onConfirmReset: () -> Unit,
    onFileMenu: (FileHeader) -> Unit,
    drawerOpen: Boolean,
    snackbar: SnackbarHostState,
    actions: EditorActions,
) {
    val console = hiltViewModel<ConsoleViewModel, ConsoleViewModel.Factory>(key = "console-$projectId") { it.create(projectId) }
    val consoleState by console.uiState.collectAsStateWithLifecycle()
    val stdin by console.stdin.collectAsStateWithLifecycle()
    val overlay by console.overlay.collectAsStateWithLifecycle()
    val loadedTestStdin by console.loadedTestStdin.collectAsStateWithLifecycle()

    val resources = LocalResources.current
    // The console is a standard (persistent) bottom sheet: collapsed it shows the peek row, dragged or tapped it expands.
    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val scope = rememberCoroutineScope()
    val consoleExpanded = sheetState.targetValue == SheetValue.Expanded
    var tab by rememberSaveable { mutableStateOf(ConsoleTab.Console) }
    var editingTest by remember { mutableStateOf<TestCase?>(null) }
    var creatingTest by rememberSaveable { mutableStateOf(false) }
    var showLimits by rememberSaveable { mutableStateOf(false) }
    var showBenchmark by rememberSaveable { mutableStateOf(false) }
    var stdinTarget by rememberSaveable { mutableStateOf(StdinTarget.Input) }
    var findWithReplace by rememberSaveable { mutableStateOf(false) }
    var goingToLine by rememberSaveable { mutableStateOf(false) }
    var showShortcuts by rememberSaveable { mutableStateOf(false) }
    var projectSearch by rememberSaveable { mutableStateOf(false) }
    // A project-search result waits here until its file is the one the editor shows, then the caret moves to it.
    var pendingHit by remember { mutableStateOf<ProjectSearchHit?>(null) }
    var findSeed by remember { mutableStateOf<FindSeed?>(null) }
    // Split view: the second pane's file (never the primary's, so two panes never save over each other).
    var splitFileId by rememberSaveable { mutableStateOf<String?>(null) }
    var splitFraction by rememberSaveable { mutableStateOf(0.5f) }
    val wide = LocalConfiguration.current.screenWidthDp.dp >= SplitMinWidth
    val projectFiles = state.workspace?.files.orEmpty()
    val splitFile = splitFileId?.let { id -> projectFiles.firstOrNull { it.id == id && it.id != activeFile.id } }
        ?.takeIf { wide }
    // Both panes write pending edits before anything reads the files (runs, tests).
    // Each pane reads only its own file's text, and only again when that file is replaced outside the editor.
    val activeText by remember(activeFile.id) { actions.fileText(activeFile.id) }.collectAsStateWithLifecycle(null)
    val splitText by remember(splitFile?.id) { splitFile?.let { actions.fileText(it.id) } ?: flowOf(null) }
        .collectAsStateWithLifecycle(null)
    fun flushEditors() {
        editorState.flush()
        splitState.flush()
    }
    // Choosing the second pane's file in the first pane swaps the two instead of opening it twice.
    val selectFile: (String) -> Unit = { id ->
        if (id == splitFileId) splitFileId = activeFile.id
        actions.onSelectFile(id)
    }

    // The drawer slides over the editor; a keyboard left open would cover its lower half.
    LaunchedEffect(drawerOpen) {
        if (drawerOpen) {
            editorState.hideKeyboard()
            splitState.hideKeyboard()
        }
    }

    LaunchedEffect(pendingHit, editorState.shownDocumentId) {
        val hit = pendingHit ?: return@LaunchedEffect
        if (editorState.shownDocumentId != hit.fileId) return@LaunchedEffect
        pendingHit = null
        onSearchChange(true)
        editorState.goTo(hit.line, hit.column)
    }

    // Hardware-keyboard shortcuts raise these from inside the code view.
    LaunchedEffect(editorState) {
        editorState.commands.collect { command ->
            when (command) {
                EditorCommand.Find -> {
                    findWithReplace = false
                    onSearchChange(true)
                }
                EditorCommand.Replace -> {
                    findWithReplace = true
                    onSearchChange(true)
                }
                EditorCommand.GoToLine -> goingToLine = true
            }
        }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) console.loadStdinFromFile(uri.toString(), intoTestEditor = stdinTarget == StdinTarget.TestEditor)
    }
    fun pickStdin(target: StdinTarget) {
        stdinTarget = target
        pickFile.launch(arrayOf("text/*", "application/octet-stream"))
    }

    LaunchedEffect(console) {
        console.eventFlow.collect { event ->
            when (event) {
                // Snackbars suspend until dismissed; launched so they never hold back RunStarted.
                is ConsoleEvent.Message -> launch { snackbar.showSnackbar(event.text.asString(resources)) }
                is ConsoleEvent.TestDeleted -> launch {
                    val result = snackbar.showSnackbar(
                        message = resources.getString(R.string.tests_deleted),
                        actionLabel = resources.getString(R.string.editor_undo),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) console.restoreTest(event.test)
                }
                is ConsoleEvent.RunStarted -> {
                    tab = if (event.mode == RunMode.TESTS) ConsoleTab.Tests else ConsoleTab.Console
                    launch { sheetState.expand() }
                }
            }
        }
    }

    fun openConsole(target: ConsoleTab? = null) {
        target?.let { tab = it }
        scope.launch { sheetState.expand() }
    }
    fun collapseConsole() {
        scope.launch { sheetState.partialExpand() }
    }
    // Typing in the code gets the whole height; the console folds down to make room.
    LaunchedEffect(typing) { if (typing && sheetState.currentValue == SheetValue.Expanded) sheetState.partialExpand() }

    // Back peels layers top-down: the open drawer handles itself, then the console sheet, then the find bar.
    BackHandler(enabled = !drawerOpen && consoleExpanded) { collapseConsole() }
    BackHandler(enabled = !drawerOpen && !consoleExpanded && searching) { onSearchChange(false) }

    val hardwareKeyboard = LocalConfiguration.current.keyboard != Configuration.KEYBOARD_NOKEYS
    val latest = consoleState.latest
    val running = consoleState.isRunning
    // Edits are debounced; persist them before the judge reads the project.
    val runTests: () -> Unit = {
        flushEditors()
        console.runTests()
    }
    // A page tab's primary action is its preview; Run would send the project's entry file to the judge instead.
    val previewsPage = isPage(activeFile)
    val runButton = when {
        !running -> if (previewsPage) RunButtonState.Preview else RunButtonState.Run
        latest?.phase == RunPhase.PENDING || latest?.phase == RunPhase.SUBMITTING -> RunButtonState.Stop
        else -> RunButtonState.Detach
    }
    val stopOrRun: () -> Unit = when (runButton) {
        RunButtonState.Run -> runTests
        RunButtonState.Preview -> ({ actions.onPreview(activeFile.id) })
        RunButtonState.Stop, RunButtonState.Detach -> console::stop
    }

    if (typing) {
        TypingTopBar(activeFile.name, editorState, runEnabled = true, onMenu = onOpenDrawer, onRun = stopOrRun, runButton = runButton)
    } else {
        ReadingTopBar(
            projectName = state.workspace?.project?.name.orEmpty(),
            runtimeLabel = state.runtime?.let { stringResource(R.string.editor_runtime_label, state.language?.name ?: it.language, it.version) }
                ?: state.workspace?.project?.runtimeId.orEmpty(),
            minimapOn = state.settings.minimap,
            runEnabled = true,
            onMenu = onOpenDrawer,
            onRuntimeClick = actions.onPickRuntime,
            onSearch = {
                // Search reads saved files, so pending edits are written first.
                flushEditors()
                projectSearch = true
            },
            onToggleMinimap = actions.onToggleMinimap,
            onRun = stopOrRun,
            runButton = runButton,
            overflow = {
                OverflowMenu(
                    listOf(
                        MenuGroup(
                            stringResource(R.string.editor_menu_group_run),
                            listOfNotNull(
                                // Pages preview from the app-bar button; browser JavaScript runs in a page with a console.
                                MenuAction(stringResource(R.string.editor_menu_run_in_browser), OmniIcons.ExternalLink) { actions.onPreview(activeFile.id) }.takeIf { isBrowserScript(activeFile) },
                                MenuAction(stringResource(R.string.editor_menu_run_with_input), OmniIcons.Terminal) { openConsole(ConsoleTab.Input) },
                                MenuAction(stringResource(R.string.editor_menu_benchmark), OmniIcons.Chart) { showBenchmark = true }.takeIf { state.runTools },
                                MenuAction(stringResource(R.string.editor_menu_limits), OmniIcons.Timer) { showLimits = true }.takeIf { state.runTools },
                            ),
                        ),
                        MenuGroup(
                            stringResource(R.string.editor_menu_group_edit),
                            listOf(
                                MenuAction(stringResource(R.string.editor_menu_find_replace), OmniIcons.Search) {
                                    findWithReplace = true
                                    onSearchChange(true)
                                },
                                MenuAction(stringResource(R.string.editor_go_to_line), OmniIcons.ArrowRight) { goingToLine = true },
                                MenuAction(stringResource(if (state.settings.wordWrap) R.string.editor_menu_wrap_off else R.string.editor_menu_wrap_on), OmniIcons.WrapText, actions.onToggleWordWrap),
                            ),
                        ),
                        MenuGroup(
                            stringResource(R.string.editor_menu_group_project),
                            listOfNotNull(
                                MenuAction(stringResource(R.string.editor_new_file), OmniIcons.Plus, onShowNewFile),
                                MenuAction(stringResource(if (splitFile != null) R.string.editor_menu_unsplit else R.string.editor_menu_split), OmniIcons.Layers) {
                                    flushEditors()
                                    splitFileId = if (splitFile != null) null else projectFiles.firstOrNull { it.id != activeFile.id }?.id
                                }.takeIf { wide && projectFiles.size > 1 },
                                MenuAction(stringResource(R.string.editor_menu_source_control), OmniIcons.Link, actions.onSourceControl).takeIf { state.workspace?.project?.remote != null },
                                MenuAction(stringResource(R.string.editor_menu_save_to_origin), OmniIcons.Upload, actions.onSaveToOrigin).takeIf { state.workspace?.project?.hasOrigin == true },
                                MenuAction(stringResource(R.string.editor_menu_rename_project), OmniIcons.Edit, onRenameProject),
                                MenuAction(stringResource(R.string.editor_menu_share_project), OmniIcons.Share, actions.onShareProject),
                                MenuAction(stringResource(R.string.editor_menu_change_language), OmniIcons.Braces, actions.onPickRuntime),
                                state.workspace?.entry?.let { entry ->
                                    MenuAction(stringResource(R.string.editor_menu_reset, entry.name), OmniIcons.Refresh, onConfirmReset)
                                },
                            ),
                        ),
                        MenuGroup(
                            stringResource(R.string.editor_menu_group_help),
                            listOfNotNull(
                                MenuAction(stringResource(R.string.editor_menu_appearance), OmniIcons.Settings, actions.onAppearance),
                                // The shortcuts need a hardware keyboard, so the list is offered only while one is attached.
                                MenuAction(stringResource(R.string.editor_shortcuts), OmniIcons.Keyboard) { showShortcuts = true }
                                    .takeIf { hardwareKeyboard },
                            ),
                        ),
                    ),
                )
            },
        )
        if (!consoleState.online) {
            InfoBanner(icon = OmniIcons.WifiOff, text = stringResource(R.string.editor_offline_banner))
        }
        FileTabs(
            files = state.workspace?.files.orEmpty(),
            activeFileId = activeFile.id,
            entryShortCode = state.language?.shortCode,
            dirtyFileId = { activeFile.id.takeIf { editorState.isDirty } },
            onSelect = { selectFile(it.id) },
            onFileMenu = { if (!it.isEntry) onFileMenu(it) },
            onAdd = onShowNewFile,
        )
    }
    if (searching) {
        FindReplaceBar(
            editorState,
            startWithReplace = findWithReplace,
            onClose = {
                findSeed = null
                onSearchChange(false)
            },
            seed = findSeed,
        )
    } else if (!typing) {
        Breadcrumb(activeFile.name)
    }

    val problems = consoleState.problemsFor(activeFile.name, activeFile.isEntry)
    val sameLanguage = activeFile.isEntry || activeFile.name.substringAfterLast('.', "") == state.workspace?.entry?.name?.substringAfterLast('.', "")
    val colors = OmniTheme.colors
    // The bottom strip keeps one height in every mode: resizing the code view while the keyboard opens makes it
    // lose input focus. It shows the console summary normally and the symbol keys while typing code.
    val peekHeight = OmniDimens.sheetHandle + ConsolePeekHeight
    val symbolStrip = typing && state.settings.symbolRow
    // Clipped: the collapsed sheet's hidden part would otherwise draw over the system navigation bar.
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
        val expandedHeight = maxHeight * SHEET_FRACTION
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = peekHeight,
            // The console is part of the layout, not a floating card: full width on tablets and in landscape too.
            sheetMaxWidth = Dp.Unspecified,
            sheetSwipeEnabled = !typing,
            sheetShape = RectangleShape,
            sheetContainerColor = colors.surface,
            sheetContentColor = colors.textPrimary,
            sheetTonalElevation = 0.dp,
            sheetShadowElevation = 0.dp,
            sheetDragHandle = {
                val border = Modifier.drawBehind { drawLine(colors.borderStrong, Offset(0f, 0f), Offset(size.width, 0f), 1f) }
                // No drag affordance while typing: the sheet can't be dragged then.
                if (typing) Spacer(border.fillMaxWidth().height(OmniDimens.sheetHandle)) else SheetHandle(border)
            },
            containerColor = colors.background,
            sheetContent = {
                if (symbolStrip) {
                    SymbolRow(if (splitState.hasFocus) splitState else editorState, Modifier.height(ConsolePeekHeight))
                } else Column(Modifier.fillMaxWidth().height(expandedHeight - OmniDimens.sheetHandle)) {
                    ConsolePeek(latest, expanded = consoleExpanded, onToggle = { if (consoleExpanded) collapseConsole() else openConsole() })
                    ConsoleSheet(
                        state = consoleState,
                        tab = tab,
                        stdin = stdin,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        actions = ConsoleSheetActions(
                            onClose = ::collapseConsole,
                            onSelectTab = { tab = it },
                            onClear = console::clearConsole,
                            onStop = console::stop,
                            onRunWithInput = {
                                flushEditors()
                                console.runWithInput()
                            },
                            onStdinChange = console::setStdin,
                            onLoadStdinFile = { pickStdin(StdinTarget.Input) },
                            onSaveStdinAsTest = console::saveStdinAsTest,
                            onVerify = console::verify,
                            onGoTo = { problem ->
                                collapseConsole()
                                val target = state.workspace?.files?.firstOrNull { it.name == problem.fileName } ?: activeFile
                                if (target.id != activeFile.id) actions.onSelectFile(target.id)
                                problem.line?.let { editorState.goTo(it, problem.column ?: 1) }
                            },
                            onCancelPending = console::cancelPending,
                            onSendPendingNow = console::sendPendingNow,
                            onSendWhenOnline = console::setSendWhenOnline,
                            tests = TestActions(
                                onRunAll = runTests,
                                onRunOne = { test ->
                                    flushEditors()
                                    console.runTests(setOf(test.id))
                                },
                                onAdd = { creatingTest = true },
                                onEdit = { editingTest = it },
                                onDuplicate = console::duplicateTest,
                                onDelete = console::deleteTest,
                                onAcceptOutput = console::acceptOutput,
                                onRaiseLimits = { showLimits = true },
                            ),
                        ),
                    )
                }
            },
        ) {
            // The collapsed sheet overlays the bottom; reserve its height so the status bar stays visible above it.
            Column(Modifier.fillMaxSize().padding(bottom = peekHeight)) {
                val primaryEditor: @Composable (Modifier) -> Unit = { paneModifier ->
                    CodeEditor(
                        state = editorState,
                        document = activeText?.takeIf { it.id == activeFile.id }?.let { text ->
                            EditorDocument(
                                id = activeFile.id,
                                revision = text.contentVersion,
                                text = text.content,
                                fileName = activeFile.name,
                                languageBase = state.runtime?.language,
                                isEntry = activeFile.isEntry,
                                // Comment syntax is known for the project's language; other file types get none.
                                lineComment = state.language?.lineComment?.takeIf { sameLanguage },
                                blockComment = state.language?.blockComment?.takeIf { sameLanguage },
                            )
                        },
                        settings = state.settings,
                        onTextChange = actions.onContentChanged,
                        diagnostics = remember(problems) { problems.map { EditorDiagnostic(it.line!!, it.column, it.message, it.isError) } },
                        onRunShortcut = if (consoleState.runSettings.runOnCtrlEnter) runTests else null,
                        lineHint = runHint(activeFile, currentText(editorState, activeFile.id, activeText), state.runtime?.language, consoleState.tests.size, latest, running),
                        onLineHintClick = runTests,
                        modifier = paneModifier,
                    )
                }
                if (splitFile == null) {
                    primaryEditor(Modifier.weight(1f).fillMaxWidth())
                } else {
                    SplitPanes(
                        fraction = splitFraction,
                        onFractionChange = { splitFraction = it },
                        first = primaryEditor,
                        second = { paneModifier ->
                            Column(paneModifier) {
                                FileTabs(
                                    files = projectFiles.filter { it.id != activeFile.id },
                                    activeFileId = splitFile.id,
                                    entryShortCode = state.language?.shortCode,
                                    dirtyFileId = { splitFile.id.takeIf { splitState.isDirty } },
                                    onSelect = { splitFileId = it.id },
                                    onFileMenu = { },
                                    onAdd = onShowNewFile,
                                )
                                CodeEditor(
                                    state = splitState,
                                    document = splitText?.takeIf { it.id == splitFile.id }?.let { text ->
                                        EditorDocument(
                                            id = splitFile.id,
                                            revision = text.contentVersion,
                                            text = text.content,
                                            fileName = splitFile.name,
                                            languageBase = state.runtime?.language,
                                            isEntry = splitFile.isEntry,
                                        )
                                    },
                                    settings = state.settings,
                                    onTextChange = actions.onContentChanged,
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                )
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
                EditorStatusBar(editorState, state.settings.tabSize, problems = latest?.problems?.size ?: 0)
            }
        }
    }

    if (goingToLine) {
        GoToLineDialog(editorState.lineCount, onDismiss = { goingToLine = false }) { line ->
            goingToLine = false
            editorState.goTo(line)
        }
    }
    if (showShortcuts) ShortcutsSheet(onDismiss = { showShortcuts = false })
    val projectId = state.workspace?.project?.id
    if (projectSearch && projectId != null) {
        ProjectSearchSheet(
            projectId = projectId,
            onOpen = { hit, query, options ->
                projectSearch = false
                findSeed = FindSeed(query, options)
                pendingHit = hit
                if (hit.fileId != activeFile.id) selectFile(hit.fileId)
            },
            onDismiss = { projectSearch = false },
        )
    }

    if (creatingTest || editingTest != null) {
        TestEditorSheet(
            initial = editingTest,
            loadedStdin = loadedTestStdin,
            onLoadStdinFile = { pickStdin(StdinTarget.TestEditor) },
            onDismiss = {
                creatingTest = false
                editingTest = null
                console.consumeLoadedTestStdin()
            },
            onSave = { name, input, expected ->
                console.saveTest(editingTest, name, input, expected)
                creatingTest = false
                editingTest = null
                console.consumeLoadedTestStdin()
            },
        )
    }
    if (showLimits) {
        LimitsSheet(
            current = consoleState.limits,
            runtimeDefaults = consoleState.runtimeDefaults,
            bypassCache = consoleState.runSettings.bypassCache,
            onBypassCache = console::setBypassCache,
            onDismiss = { showLimits = false },
            onSave = {
                console.setLimits(it)
                showLimits = false
            },
        )
    }
    when (val current = overlay) {
        is ConsoleOverlay.RateLimited -> RateLimitSheet(current.retryAfterSeconds, current.snapshot, console::dismissOverlay)
        is ConsoleOverlay.Verifying -> ProofSheet(busy = true, proof = null, onDismiss = console::dismissOverlay)
        is ConsoleOverlay.Proof -> ProofSheet(busy = false, proof = current.proof, onDismiss = console::dismissOverlay)
        is ConsoleOverlay.KeepWork -> KeepWorkSheet(
            offer = current.offer,
            onCreateAccount = {
                console.dismissOverlay()
                actions.onConvertGuest()
            },
            onDismiss = console::dismissOverlay,
        )
        is ConsoleOverlay.Benchmarking, is ConsoleOverlay.Benchmark, null -> Unit
    }
    if (showBenchmark || overlay is ConsoleOverlay.Benchmarking || overlay is ConsoleOverlay.Benchmark) {
        BenchmarkSheet(
            defaultCopies = consoleState.runSettings.benchmarkCopies,
            busy = overlay is ConsoleOverlay.Benchmarking,
            result = (overlay as? ConsoleOverlay.Benchmark)?.result,
            onDismiss = {
                showBenchmark = false
                console.dismissOverlay()
            },
            onStart = {
                flushEditors()
                console.benchmark(it)
            },
        )
    }
}

/** The file's text as the app last saw it: the editor's latest save, else what was loaded. */
private fun currentText(editor: CodeEditorState, fileId: String, loaded: OpenFile?): String? =
    editor.savedText?.takeIf { it.documentId == fileId }?.text ?: loaded?.takeIf { it.id == fileId }?.content

/** Design E1 code lens: "▶ Run · 7 tests · last WA #3" after the entry file's `main` line. */
@Composable
private fun runHint(file: FileHeader, text: String?, languageBase: String?, testCount: Int, latest: RunRecord?, running: Boolean): EditorLineHint? {
    if (!file.isEntry || running || text == null) return null
    val line = remember(text, languageBase) { EntryPoint.line(languageBase, text) } ?: return null
    val parts = mutableListOf(stringResource(R.string.editor_run_hint))
    if (testCount > 0) parts += pluralStringResource(R.plurals.editor_run_hint_tests, testCount, testCount)
    latest?.takeIf { it.mode == RunMode.TESTS }?.verdict?.let { verdict ->
        val failed = latest.firstFailure?.takeIf { verdict != Verdict.CE }
        parts += if (failed != null) stringResource(R.string.editor_run_hint_last_failed, verdict.code, failed.index)
        else stringResource(R.string.editor_run_hint_last, verdict.code)
    }
    return EditorLineHint(line, parts.joinToString(" · "))
}

private const val SHEET_FRACTION = 0.78f

private fun FileHeader.extension() = name.substringAfterLast('.', "").lowercase()

/** Files the preview renders as a page (HTML, Markdown). */
private fun isPage(file: FileHeader) = file.extension() in setOf("html", "htm", "md", "markdown")

/** JavaScript the preview can also run in a page with a browser console. */
private fun isBrowserScript(file: FileHeader) = file.extension() in setOf("js", "mjs")
