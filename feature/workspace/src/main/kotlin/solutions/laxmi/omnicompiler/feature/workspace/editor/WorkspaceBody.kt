package solutions.laxmi.omnicompiler.feature.workspace.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import solutions.laxmi.omnicompiler.core.designsystem.component.InfoBanner
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniCompactButton
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.editor.CodeEditor
import solutions.laxmi.omnicompiler.core.editor.CodeEditorState
import solutions.laxmi.omnicompiler.core.editor.EditorDiagnostic
import solutions.laxmi.omnicompiler.core.editor.EditorDocument
import solutions.laxmi.omnicompiler.core.editor.SymbolRow
import solutions.laxmi.omnicompiler.core.model.RunMode
import solutions.laxmi.omnicompiler.core.model.RunPhase
import solutions.laxmi.omnicompiler.core.model.SourceFile
import solutions.laxmi.omnicompiler.core.model.TestCase
import solutions.laxmi.omnicompiler.feature.workspace.console.BenchmarkSheet
import solutions.laxmi.omnicompiler.feature.workspace.console.ConsoleEvent
import solutions.laxmi.omnicompiler.feature.workspace.console.ConsoleOverlay
import solutions.laxmi.omnicompiler.feature.workspace.console.ConsolePeek
import solutions.laxmi.omnicompiler.feature.workspace.console.ConsoleSheet
import solutions.laxmi.omnicompiler.feature.workspace.console.ConsoleSheetActions
import solutions.laxmi.omnicompiler.feature.workspace.console.ConsoleTab
import solutions.laxmi.omnicompiler.feature.workspace.console.ConsoleViewModel
import solutions.laxmi.omnicompiler.feature.workspace.console.LimitsSheet
import solutions.laxmi.omnicompiler.feature.workspace.console.ProofSheet
import solutions.laxmi.omnicompiler.feature.workspace.console.RateLimitSheet
import solutions.laxmi.omnicompiler.feature.workspace.console.TestActions
import solutions.laxmi.omnicompiler.feature.workspace.console.TestEditorSheet
import solutions.laxmi.omnicompiler.feature.workspace.console.problemsFor

/** Where a picked stdin file should go. */
private enum class StdinTarget { Input, TestEditor }

/** Editor + console for a resolved project. Owns the console ViewModel, keyed by project. */
@Composable
internal fun ColumnScope.WorkspaceBody(
    state: EditorUiState,
    projectId: String,
    activeFile: SourceFile,
    editorState: CodeEditorState,
    typing: Boolean,
    searching: Boolean,
    onSearchChange: (Boolean) -> Unit,
    onOpenDrawer: () -> Unit,
    onShowNewFile: () -> Unit,
    onRenameProject: () -> Unit,
    onConfirmReset: () -> Unit,
    onFileMenu: (SourceFile) -> Unit,
    snackbar: SnackbarHostState,
    actions: EditorActions,
) {
    val console = hiltViewModel<ConsoleViewModel, ConsoleViewModel.Factory>(key = "console-$projectId") { it.create(projectId) }
    val consoleState by console.uiState.collectAsStateWithLifecycle()
    val stdin by console.stdin.collectAsStateWithLifecycle()
    val overlay by console.overlay.collectAsStateWithLifecycle()
    val loadedTestStdin by console.loadedTestStdin.collectAsStateWithLifecycle()

    var consoleOpen by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(ConsoleTab.Console) }
    var editingTest by remember { mutableStateOf<TestCase?>(null) }
    var creatingTest by rememberSaveable { mutableStateOf(false) }
    var showLimits by rememberSaveable { mutableStateOf(false) }
    var showBenchmark by rememberSaveable { mutableStateOf(false) }
    var stdinTarget by rememberSaveable { mutableStateOf(StdinTarget.Input) }

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
                is ConsoleEvent.Message -> snackbar.showSnackbar(event.text)
                is ConsoleEvent.RunStarted -> {
                    consoleOpen = true
                    tab = if (event.mode == RunMode.TESTS) ConsoleTab.Tests else ConsoleTab.Console
                }
            }
        }
    }

    val latest = consoleState.latest
    val running = consoleState.isRunning
    // Edits are debounced; persist them before the judge reads the project.
    val runTests: () -> Unit = {
        editorState.flush()
        console.runTests()
    }
    val stopOrRun: () -> Unit = if (running) console::stop else runTests
    val runLabel = when {
        !running -> "Run"
        latest?.phase == RunPhase.PENDING || latest?.phase == RunPhase.SUBMITTING -> "Stop"
        else -> "Detach"
    }

    if (typing) {
        TypingTopBar(activeFile.name, editorState.isDirty, editorState, runEnabled = true, onMenu = onOpenDrawer, onRun = stopOrRun)
    } else {
        ReadingTopBar(
            projectName = state.workspace?.project?.name.orEmpty(),
            runtimeLabel = state.runtimeLabel,
            minimapOn = state.settings.minimap,
            runEnabled = true,
            onMenu = onOpenDrawer,
            onRuntimeClick = actions.onPickRuntime,
            onSearch = { onSearchChange(true) },
            onToggleMinimap = actions.onToggleMinimap,
            onRun = stopOrRun,
            runLabel = runLabel,
            overflow = {
                OverflowMenu(
                    listOf(
                        "Run with custom input" to {
                            consoleOpen = true
                            tab = ConsoleTab.Input
                        },
                        "Benchmark…" to { showBenchmark = true },
                        "Run limits…" to { showLimits = true },
                        "New file" to onShowNewFile,
                        "Rename project" to onRenameProject,
                        "Change language" to actions.onPickRuntime,
                        (if (state.settings.wordWrap) "Turn off word wrap" else "Turn on word wrap") to actions.onToggleWordWrap,
                        "Reset to starter code" to onConfirmReset,
                        "Editor appearance" to actions.onAppearance,
                    ),
                )
            },
        )
        if (!consoleState.online) {
            InfoBanner(icon = OmniIcons.WifiOff, text = "You're offline. Edits save on this device. Runs will be sent when you're back.")
        }
        FileTabs(
            files = state.workspace?.files.orEmpty(),
            activeFileId = activeFile.id,
            entryShortCode = state.language?.shortCode,
            dirtyFileId = activeFile.id.takeIf { editorState.isDirty },
            onSelect = { actions.onSelectFile(it.id) },
            onFileMenu = { if (!it.isEntry) onFileMenu(it) },
            onAdd = onShowNewFile,
        )
    }
    if (searching) FindBar(editorState, onClose = { onSearchChange(false) }) else if (!typing) Breadcrumb(activeFile.name)

    val problems = consoleState.problemsFor(activeFile.name, activeFile.isEntry)
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val sheetHeight = maxHeight * SHEET_FRACTION
        CodeEditor(
            state = editorState,
            document = EditorDocument(
                id = activeFile.id,
                revision = state.revisions[activeFile.id] ?: 0,
                text = activeFile.content,
                fileName = activeFile.name,
                languageBase = state.runtime?.language,
                isEntry = activeFile.isEntry,
            ),
            settings = state.settings,
            onTextChange = actions.onContentChanged,
            diagnostics = remember(problems) { problems.map { EditorDiagnostic(it.line!!, it.column, it.message, it.isError) } },
            onRunShortcut = if (consoleState.runSettings.runOnCtrlEnter) runTests else null,
            modifier = Modifier.fillMaxSize(),
        )
        if (consoleOpen && !typing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(OmniTheme.colors.scrim)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { consoleOpen = false },
            )
        }
        SlideUpPanel(visible = consoleOpen && !typing, modifier = Modifier.align(Alignment.BottomCenter)) {
            ConsoleSheet(
                state = consoleState,
                tab = tab,
                stdin = stdin,
                modifier = Modifier.fillMaxWidth().height(sheetHeight),
                actions = ConsoleSheetActions(
                    onClose = { consoleOpen = false },
                    onSelectTab = { tab = it },
                    onClear = console::clearConsole,
                    onStop = console::stop,
                    onRunWithInput = {
                        editorState.flush()
                        console.runWithInput()
                    },
                    onStdinChange = console::setStdin,
                    onLoadStdinFile = { pickStdin(StdinTarget.Input) },
                    onSaveStdinAsTest = console::saveStdinAsTest,
                    onVerify = console::verify,
                    onGoTo = { problem ->
                        consoleOpen = false
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
                            editorState.flush()
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
    }
    if (typing && state.settings.symbolRow) SymbolRow(editorState)
    if (!typing && !consoleOpen) {
        ConsolePeek(latest) { consoleOpen = true }
        EditorStatusBar(editorState, state.settings.tabSize, problems = latest?.problems?.size ?: 0)
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
                editorState.flush()
                console.benchmark(it)
            },
        )
    }
}

/** Kept outside any Row/Column scope so the unscoped AnimatedVisibility overload is used. */
@Composable
private fun SlideUpPanel(visible: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = visible, modifier = modifier, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        content()
    }
}

private const val SHEET_FRACTION = 0.78f
