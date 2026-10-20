package solutions.laxmi.omnicompiler.feature.workspace.editor

import solutions.laxmi.omnicompiler.feature.workspace.search.ProjectSearchSheet
import android.content.res.Configuration
import solutions.laxmi.omnicompiler.core.model.OpenFile
import kotlinx.coroutines.flow.flowOf
import androidx.compose.ui.platform.LocalConfiguration
import solutions.laxmi.omnicompiler.core.editor.EditorCommand
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.activity.compose.LocalActivity
import solutions.laxmi.omnicompiler.core.review.LocalAppReview
import solutions.laxmi.omnicompiler.core.ads.AdBanner
import solutions.laxmi.omnicompiler.core.ads.LocalAds
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
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
import solutions.laxmi.omnicompiler.feature.workspace.console.refersTo

/** Where a picked stdin file should go. */
private enum class StdinTarget { Input, TestEditor }

/** A caret move waiting for its file to be the one the editor shows; [openFind] keeps the find bar open (search hits). */
private data class PendingGoTo(val fileId: String, val line: Int, val column: Int, val openFind: Boolean)

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
    /** Wide window: the console is a panel beside the code instead of a bottom sheet. */
    docked: Boolean,
    snackbar: SnackbarHostState,
    actions: EditorActions,
) {
    val console = hiltViewModel<ConsoleViewModel, ConsoleViewModel.Factory>(key = "console-$projectId") { it.create(projectId) }
    val consoleState by console.uiState.collectAsStateWithLifecycle()
    val stdin by console.stdin.collectAsStateWithLifecycle()
    val overlay by console.overlay.collectAsStateWithLifecycle()
    val loadedTestStdin by console.loadedTestStdin.collectAsStateWithLifecycle()

    val resources = LocalResources.current
    val review = LocalAppReview.current
    val ads = LocalAds.current
    val activity = LocalActivity.current
    // The console is a standard (persistent) bottom sheet: collapsed it shows the peek row, dragged or tapped it expands.
    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val scope = rememberCoroutineScope()
    // Docked, the console panel is shown or hidden; as a sheet it is expanded or folded to its peek row.
    var consoleOpen by rememberSaveable { mutableStateOf(true) }
    var consoleFraction by rememberSaveable { mutableFloatStateOf(DOCKED_EDITOR_FRACTION) }
    val consoleExpanded = if (docked) consoleOpen else sheetState.targetValue == SheetValue.Expanded
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
    // A search result or compile problem in another file waits here until that file is shown, then the caret moves to it.
    var pendingGoTo by remember { mutableStateOf<PendingGoTo?>(null) }
    var findSeed by remember { mutableStateOf<FindSeed?>(null) }
    // Split view: the second pane's file (never the primary's, so two panes never save over each other).
    var splitFileId by rememberSaveable { mutableStateOf<String?>(null) }
    var splitFraction by rememberSaveable { mutableFloatStateOf(0.5f) }
    // Two files side by side need room in the editor's own area, which a docked console and sidebar share.
    var editorWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val wide = editorWidth >= SplitMinWidth
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

    LaunchedEffect(pendingGoTo, editorState.shownDocumentId) {
        val target = pendingGoTo ?: return@LaunchedEffect
        if (editorState.shownDocumentId != target.fileId) return@LaunchedEffect
        pendingGoTo = null
        if (target.openFind) onSearchChange(true)
        editorState.goTo(target.line, target.column)
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

    fun openConsole(target: ConsoleTab? = null) {
        target?.let { tab = it }
        if (docked) consoleOpen = true else scope.launch { sheetState.expand() }
    }
    fun collapseConsole() {
        if (docked) consoleOpen = false else scope.launch { sheetState.partialExpand() }
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
                // The rating card never competes with another sheet on screen.
                is ConsoleEvent.AcceptedRun -> activity?.let { review.onPositiveMoment(it, mayPrompt = !event.sheetShown) }
                is ConsoleEvent.RunFinished -> ads.onRunFinished(event.failed)
                is ConsoleEvent.RunStarted -> {
                    openConsole(if (event.mode == RunMode.TESTS) ConsoleTab.Tests else ConsoleTab.Console)
                }
            }
        }
    }

    // Typing in the code gets the whole height; the console sheet folds down to make room (a docked panel is beside it).
    LaunchedEffect(typing) { if (!docked && typing && sheetState.currentValue == SheetValue.Expanded) sheetState.partialExpand() }

    // Back peels layers top-down: the open drawer handles itself, then the console sheet, then the find bar. A docked
    // console is part of the layout, not a layer, so Back doesn't close it.
    val sheetOpen = !docked && consoleExpanded
    BackHandler(enabled = !drawerOpen && sheetOpen) { collapseConsole() }
    BackHandler(enabled = !drawerOpen && !sheetOpen && searching) { onSearchChange(false) }

    val hardwareKeyboard = LocalConfiguration.current.keyboard != Configuration.KEYBOARD_NOKEYS
    val latest = consoleState.latest
    val running = consoleState.isRunning
    // Edits are debounced; persist them before the judge reads the project.
    val runTests: () -> Unit = {
        flushEditors()
        console.runTests()
    }
    // Run, Ctrl+Enter and the line hint run the tests, or the code once with the Input tab when there are none.
    val runPrimary: () -> Unit = {
        flushEditors()
        console.runPrimary()
    }
    // A page tab's primary action is its preview; Run would send the project's entry file to the judge instead.
    val previewsPage = isPage(activeFile)
    val runButton = when {
        !running -> if (previewsPage) RunButtonState.Preview else RunButtonState.Run
        latest?.phase == RunPhase.PENDING || latest?.phase == RunPhase.SUBMITTING -> RunButtonState.Stop
        else -> RunButtonState.Detach
    }
    val stopOrRun: () -> Unit = when (runButton) {
        RunButtonState.Run -> runPrimary
        RunButtonState.Preview -> ({ actions.onPreview(activeFile.id) })
        RunButtonState.Stop, RunButtonState.Detach -> console::stop
    }

    if (typing) {
        TypingTopBar(activeFile.name, editorState, runEnabled = true, onMenu = onOpenDrawer, onRun = stopOrRun, runButton = runButton)
    } else {
        ReadingTopBar(
            projectName = state.workspace?.project?.name.orEmpty(),
            runtimeLabel = state.runtime?.let { stringResource(R.string.editor_runtime_label, state.language?.name ?: it.language, it.version) }
                ?: state.workspace?.project?.runtimeId?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.editor_choose_language),
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
    // The console's peek row and tabs: the bottom sheet's content on phones, a side panel on wide windows.
    val consolePanel: @Composable (Modifier) -> Unit = { panelModifier ->
        Column(panelModifier.background(colors.surface)) {
            ConsolePeek(latest, expanded = consoleExpanded, onToggle = { if (consoleExpanded) collapseConsole() else openConsole() })
            ConsoleSheet(
                state = consoleState,
                tab = tab,
                stdin = stdin,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                docked = docked,
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
                        val files = state.workspace?.files.orEmpty()
                        val target = files.firstOrNull { it.name == problem.fileName } ?: files.firstOrNull { problem.fileName.refersTo(it.name) } ?: activeFile
                        problem.line?.let { line -> pendingGoTo = PendingGoTo(target.id, line, problem.column ?: 1, openFind = false) }
                        if (target.id != activeFile.id) selectFile(target.id)
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
            // Only a docked panel (wide windows) carries a banner; on phones the editor stays ad-free.
            if (docked) AdBanner(compact = true, spacing = 16, modifier = Modifier.padding(bottom = 8.dp))
        }
    }
    // The open file (or two, side by side) and the status line under it.
    val editorArea: @Composable (Modifier) -> Unit = { areaModifier ->
        Column(areaModifier.onSizeChanged { editorWidth = with(density) { it.width.toDp() } }) {
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
                    onRunShortcut = if (consoleState.runSettings.runOnCtrlEnter) runPrimary else null,
                    lineHint = runHint(activeFile, currentText(editorState, activeFile.id, activeText), state.runtime?.language, consoleState.tests.size, latest, running),
                    onLineHintClick = runPrimary,
                    modifier = paneModifier,
                )
            }
            // One call site for the primary editor whether or not a second file is open beside it, so opening or closing
            // the split keeps its undo history and scroll.
            SplitPanes(
                fraction = splitFraction,
                onFractionChange = { splitFraction = it },
                first = primaryEditor,
                second = splitFile?.let { split -> { paneModifier ->
                    Column(paneModifier) {
                        FileTabs(
                            files = projectFiles.filter { it.id != activeFile.id },
                            activeFileId = split.id,
                            entryShortCode = state.language?.shortCode,
                            dirtyFileId = { split.id.takeIf { splitState.isDirty } },
                            onSelect = { splitFileId = it.id },
                            onFileMenu = { },
                            onAdd = onShowNewFile,
                        )
                        CodeEditor(
                            state = splitState,
                            document = splitText?.takeIf { it.id == split.id }?.let { text ->
                                EditorDocument(
                                    id = split.id,
                                    revision = text.contentVersion,
                                    text = text.content,
                                    fileName = split.name,
                                    languageBase = state.runtime?.language,
                                    isEntry = split.isEntry,
                                )
                            },
                            settings = state.settings,
                            onTextChange = actions.onContentChanged,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                } },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            // Docked, nothing below the code holds the symbol keys, so they sit above the status line while typing.
            if (docked && symbolStrip) SymbolRow(if (splitState.hasFocus) splitState else editorState, Modifier.height(ConsolePeekHeight))
            EditorStatusBar(editorState, state.settings.tabSize, problems = latest?.problems?.size ?: 0)
        }
    }

    if (docked) {
        // Code on the left, console on the right with a draggable divider; hidden, it leaves its peek row under the code.
        // The editor stays at one place in the composition either way, so toggling the console keeps its state.
        SplitPanes(
            fraction = consoleFraction,
            onFractionChange = { consoleFraction = it },
            first = editorArea,
            second = consolePanel.takeIf { consoleOpen },
            modifier = Modifier.weight(1f).fillMaxWidth().background(colors.background),
            minFirst = DOCKED_EDITOR_MIN_WIDTH,
            minSecond = DOCKED_CONSOLE_MIN_WIDTH,
        )
        if (!consoleOpen) {
            Column(Modifier.fillMaxWidth().background(colors.surface).drawBehind { drawLine(colors.borderStrong, Offset(0f, 0f), Offset(size.width, 0f), 1f) }) {
                ConsolePeek(latest, expanded = false, onToggle = { openConsole() })
            }
        }
    } else {
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
                    } else {
                        consolePanel(Modifier.fillMaxWidth().height(expandedHeight - OmniDimens.sheetHandle))
                    }
                },
            ) {
                // The collapsed sheet overlays the bottom; reserve its height so the status bar stays visible above it.
                editorArea(Modifier.fillMaxSize().padding(bottom = peekHeight))
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
                pendingGoTo = PendingGoTo(hit.fileId, hit.line, hit.column, openFind = true)
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

/** The code's share of a wide window's width when the console docks beside it. */
private const val DOCKED_EDITOR_FRACTION = 0.68f

// Room for the console's tabs and verdict line, and for a readable line of code beside it.
private val DOCKED_CONSOLE_MIN_WIDTH = 360.dp
private val DOCKED_EDITOR_MIN_WIDTH = 320.dp

private fun FileHeader.extension() = name.substringAfterLast('.', "").lowercase()

/** Files the preview renders as a page (HTML, Markdown). */
private fun isPage(file: FileHeader) = file.extension() in setOf("html", "htm", "md", "markdown")

/** JavaScript the preview can also run in a page with a browser console. */
private fun isBrowserScript(file: FileHeader) = file.extension() in setOf("js", "mjs")
