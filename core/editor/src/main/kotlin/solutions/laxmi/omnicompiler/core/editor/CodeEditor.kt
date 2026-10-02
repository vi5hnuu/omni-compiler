package solutions.laxmi.omnicompiler.core.editor

import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import androidx.compose.runtime.SideEffect
import android.content.Context
import android.graphics.Typeface
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.InlayHintClickEvent
import io.github.rosemoe.sora.graphics.inlayHint.TextInlayHintRenderer
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHintsContainer
import io.github.rosemoe.sora.lang.styling.inlayHint.TextInlayHint
import io.github.rosemoe.sora.event.EditorKeyEvent
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import solutions.laxmi.omnicompiler.core.designsystem.R as DesignR
import solutions.laxmi.omnicompiler.core.model.CodeFont
import solutions.laxmi.omnicompiler.core.model.EditorSettings

/** A diagnostic to underline, with 1-based position (column optional: whole line when absent). */
data class EditorDiagnostic(val line: Int, val column: Int?, val message: String, val isError: Boolean = true)

/** A tappable label drawn after the end of 1-based [line] (the design's code lens, e.g. "▶ Run"). */
data class EditorLineHint(val line: Int, val text: String)

/**
 * The buffer to show. [id] names the document (a file); bumping [revision] forces the view to reload
 * [text] (e.g. after "reset to starter"), which also resets that file's undo history.
 */
data class EditorDocument(
    val id: String,
    val revision: Int,
    val text: String,
    val fileName: String,
    val languageBase: String?,
    val isEntry: Boolean,
    /** Comment syntax used by "toggle comment"; null tokens disable it. */
    val lineComment: String? = null,
    val blockComment: Pair<String, String>? = null,
) {
    internal val key: String get() = "$id@$revision"
}

/**
 * Code editor (Sora + TextMate) with the design's minimap and active-line marker.
 * The native view owns the text while a document is open; edits flow out via [onTextChange] (debounced).
 * A null [document] means its text is still loading: the view keeps showing what it has.
 *
 * Each file keeps its own Sora document (text, undo history, caret, scroll) while it stays among the recently
 * shown ones, and the text appears before its grammar is ready: colours follow when it loads.
 */
@Composable
fun CodeEditor(
    state: CodeEditorState,
    document: EditorDocument?,
    settings: EditorSettings,
    onTextChange: (documentId: String, text: String) -> Unit,
    modifier: Modifier = Modifier,
    diagnostics: List<EditorDiagnostic> = emptyList(),
    readOnly: Boolean = false,
    onRunShortcut: (() -> Unit)? = null,
    lineHint: EditorLineHint? = null,
    onLineHintClick: () -> Unit = {},
) {
    val context = LocalContext.current
    // AUTO follows the app theme, so the editor switches with it (Paper in light, Signal in dark).
    val darkApp = OmniTheme.colors.isDark
    val effective = remember(settings, darkApp) { settings.copy(theme = settings.theme.resolved(darkApp)) }
    val palette = remember(effective.theme) { effective.theme.palette() }
    val currentOnTextChange by rememberUpdatedState(onTextChange)
    val currentRunShortcut by rememberUpdatedState(onRunShortcut)
    val currentLineHintClick by rememberUpdatedState(onLineHintClick)
    val grammar = remember(document?.fileName, document?.languageBase, document?.isEntry) {
        document?.let { EditorLanguages.grammarFor(it.fileName, it.languageBase, it.isEntry) }
    }
    // Grammars parse lazily on first use; the document is shown meanwhile and coloured once this is set.
    var loadedGrammar by remember { mutableStateOf<LoadedGrammar?>(null) }
    LaunchedEffect(grammar) {
        withContext(Dispatchers.IO) { EditorLanguages.ensureLoaded(context, grammar) }
        loadedGrammar = LoadedGrammar(grammar)
    }
    // The view's colour scheme comes from the registered themes, so it waits for them (once per process, fast).
    var themesReady by remember { mutableStateOf(EditorLanguages.themesReady) }
    LaunchedEffect(Unit) {
        if (themesReady) return@LaunchedEffect
        withContext(Dispatchers.IO) { EditorLanguages.ensureThemes(context) }
        themesReady = true
    }
    SideEffect { state.onTextChanged = { id, text -> currentOnTextChange(id, text) } }

    Box(modifier.background(palette.background)) {
        if (themesReady) AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                createEditor(ctx, state, onRunShortcut = { currentRunShortcut?.invoke() }, onLineHintClick = { currentLineHintClick() })
            },
            update = { editor ->
                editor.applySettings(context, state, effective, palette)
                editor.isEditable = !readOnly
                // Decorations belong to a document: apply them only once it is the one shown.
                if (document != null && state.boundKey == document.key) {
                    editor.setDiagnostics(diagnostics)
                    editor.setLineHint(lineHint)
                }
            },
            onRelease = { editor ->
                state.flush()
                state.release()
                editor.release()
            },
        )
    }

    // Show the document; restarting on a new key cancels a document still being prepared.
    LaunchedEffect(state, document?.key, themesReady) {
        if (themesReady) document?.let { state.show(it, grammar) }
    }
    // Colour it once its grammar is ready (and only rebuild the language when the grammar changes).
    LaunchedEffect(state, loadedGrammar, state.boundKey) {
        val ready = loadedGrammar?.takeIf { it.grammar == grammar } ?: return@LaunchedEffect
        if (state.boundKey == document?.key) state.useLanguage(ready.grammar)
    }

    // Persist the buffer whenever the app goes to the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, state) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) state.flush() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

private fun createEditor(context: Context, state: CodeEditorState, onRunShortcut: () -> Unit, onLineHintClick: () -> Unit): CodeEditor =
    CodeEditor(context).apply {
        state.editor = this
        isLineNumberEnabled = true
        setPinLineNumber(true)
        setDividerWidth(0f)
        setDividerMargin(8 * dpUnit, 6 * dpUnit)
        // Clear of the active-line bar and of system edge handles (e.g. Samsung's edge panel) on the left edge.
        setLineNumberMarginLeft(8 * dpUnit)
        setHighlightCurrentLine(true)
        setHighlightBracketPair(true)
        setBlockLineWidth(1f)
        setCursorWidth(2 * dpUnit)
        setScrollBarEnabled(false)
        setScalable(false)
        props.symbolPairAutoCompletion = true
        props.autoIndent = true
        // Tapping a line number selects the whole line, like desktop editors.
        props.actionWhenLineNumberClicked = io.github.rosemoe.sora.widget.DirectAccessProps.LN_ACTION_SELECT_LINE
        props.overScrollEnabled = false
        getComponent(EditorAutoCompletion::class.java).setEnabledAnimation(false)
        registerInlayHintRenderer(TextInlayHintRenderer.DefaultInstance)

        subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
            if (event.action != ContentChangeEvent.ACTION_SET_NEW_TEXT) state.onContentChanged()
            state.lineCount = text.lineCount
            state.canUndo = canUndo()
            state.canRedo = canRedo()
            diagnostics?.let { container ->
                val change = event.changedText.length
                if (event.action == ContentChangeEvent.ACTION_INSERT) container.shiftOnInsert(event.changeStart.index, event.changeStart.index + change)
                if (event.action == ContentChangeEvent.ACTION_DELETE) container.shiftOnDelete(event.changeStart.index, event.changeEnd.index)
            }
        }
        subscribeEvent(SelectionChangeEvent::class.java) { event, _ ->
            state.cursor = CursorPosition(event.left.line + 1, event.left.column + 1)
        }
        // stopSearch() publishes this too, after clearing the pattern; Sora's counters throw without a query.
        subscribeEvent(PublishSearchResultEvent::class.java) { _, _ ->
            val active = searcher.hasQuery()
            state.searchMatches = if (active) searcher.matchedPositionCount else 0
            state.searchIndex = if (active) searcher.currentMatchedPositionIndex else -1
        }
        subscribeEvent(EditorKeyEvent::class.java) { event, _ ->
            if (event.eventType != EditorKeyEvent.Type.DOWN) return@subscribeEvent
            // Shortcuts Sora doesn't have; its own (Ctrl+A/C/X/V/Z/Y/D, Ctrl+Shift+arrows) keep working.
            val handled = when {
                event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_ENTER -> onRunShortcut().let { true }
                event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_S -> state.flush().let { true }
                event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_F -> state.raise(EditorCommand.Find).let { true }
                event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_H -> state.raise(EditorCommand.Replace).let { true }
                event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_G -> state.raise(EditorCommand.GoToLine).let { true }
                event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_SLASH -> state.toggleComment().let { true }
                event.isAltPressed && event.keyCode == KeyEvent.KEYCODE_DPAD_UP -> state.moveLines(up = true).let { true }
                event.isAltPressed && event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> state.moveLines(up = false).let { true }
                event.isCtrlPressed && event.isShiftPressed && event.keyCode == KeyEvent.KEYCODE_K -> state.deleteLines().let { true }
                else -> false
            }
            if (handled) event.markAsConsumed()
        }
        subscribeEvent(InlayHintClickEvent::class.java) { event, _ ->
            onLineHintClick()
            event.intercept()
        }
        setOnFocusChangeListener { _, focused -> state.hasFocus = focused }
    }

private data class AppliedSettings(val settings: EditorSettings, val palette: EditorPalette)

/** Re-applies settings only when they change; AndroidView.update runs on every recomposition. */
private fun CodeEditor.applySettings(context: Context, state: CodeEditorState, settings: EditorSettings, palette: EditorPalette) {
    val applied = AppliedSettings(settings, palette)
    if (getTag(R.id.omni_editor_settings) == applied) return
    setTag(R.id.omni_editor_settings, applied)
    state.appliedSettings = settings

    val typeface = settings.font.typeface(context)
    typefaceText = typeface
    typefaceLineNumber = typeface
    setTextSize(settings.fontSizeSp.toFloat())
    setLineSpacing(0f, settings.lineSpacing.multiplier)
    setLigatureEnabled(settings.ligatures)
    setBlockLineEnabled(settings.indentGuides)
    tabWidth = settings.tabSize
    configureLanguage(settings)
    getComponent(EditorAutoCompletion::class.java).isEnabled = settings.autocomplete
    if (isWordwrap != settings.wordWrap) setWordwrap(settings.wordWrap)
    setNonPrintablePaintingFlags(
        if (settings.showInvisibles) {
            CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or CodeEditor.FLAG_DRAW_WHITESPACE_TRAILING or CodeEditor.FLAG_DRAW_TAB_SAME_AS_SPACE
        } else {
            0
        },
    )
    props.stickyScroll = settings.stickyScroll
    // Sora's own minimap (code overview at the right edge).
    props.showMinimap = settings.minimap
    setDisableSoftKbdIfHardKbdAvailable(settings.hardwareKeyboardOnly)

    val themes = ThemeRegistry.getInstance()
    themes.setTheme(settings.theme.name)
    colorScheme = TextMateColorScheme.create(themes).apply { applyChrome(palette) }
}

private fun EditorColorScheme.applyChrome(p: EditorPalette) {
    fun set(slot: Int, color: androidx.compose.ui.graphics.Color) = setColor(slot, color.toArgb())
    set(EditorColorScheme.WHOLE_BACKGROUND, p.background)
    set(EditorColorScheme.LINE_NUMBER_BACKGROUND, p.background)
    set(EditorColorScheme.LINE_NUMBER_PANEL, p.popup)
    set(EditorColorScheme.LINE_NUMBER_PANEL_TEXT, p.text)
    set(EditorColorScheme.LINE_DIVIDER, p.background)
    set(EditorColorScheme.LINE_NUMBER, p.lineNumber)
    set(EditorColorScheme.LINE_NUMBER_CURRENT, p.lineNumberActive)
    set(EditorColorScheme.CURRENT_LINE, p.activeLine)
    set(EditorColorScheme.SELECTION_INSERT, p.cursor)
    set(EditorColorScheme.SELECTION_HANDLE, p.cursor)
    set(EditorColorScheme.SELECTED_TEXT_BACKGROUND, p.selection)
    set(EditorColorScheme.BLOCK_LINE, p.indentGuide)
    set(EditorColorScheme.BLOCK_LINE_CURRENT, p.indentGuideActive)
    set(EditorColorScheme.SIDE_BLOCK_LINE, p.indentGuide)
    set(EditorColorScheme.COMPLETION_WND_BACKGROUND, p.popup)
    set(EditorColorScheme.COMPLETION_WND_CORNER, p.popupBorder)
    set(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT, p.popupSelected)
    set(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY, p.text)
    set(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY, p.lineNumber)
    set(EditorColorScheme.COMPLETION_WND_TEXT_MATCHED, p.matched)
    set(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND, p.matched)
    set(EditorColorScheme.HIGHLIGHTED_DELIMITERS_UNDERLINE, p.matched)
    set(EditorColorScheme.MATCHED_TEXT_BACKGROUND, p.selection)
    set(EditorColorScheme.PROBLEM_ERROR, p.error)
    set(EditorColorScheme.PROBLEM_WARNING, p.function)
    set(EditorColorScheme.DIAGNOSTIC_TOOLTIP_BACKGROUND, p.popup)
    set(EditorColorScheme.DIAGNOSTIC_TOOLTIP_BRIEF_MSG, p.text)
    set(EditorColorScheme.DIAGNOSTIC_TOOLTIP_DETAILED_MSG, p.lineNumber)
    set(EditorColorScheme.DIAGNOSTIC_TOOLTIP_ACTION, p.matched)
    set(EditorColorScheme.MINIMAP_BACKGROUND, p.background)
    set(EditorColorScheme.MINIMAP_VIEWPORT, p.minimapViewport)
    set(EditorColorScheme.MINIMAP_VIEWPORT_BORDER, p.minimapLine)
    set(EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND, p.popup)
    set(EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR, p.text)
    set(EditorColorScheme.TEXT_INLAY_HINT_BACKGROUND, p.popup)
    set(EditorColorScheme.TEXT_INLAY_HINT_FOREGROUND, p.lineNumberActive)
}

private fun CodeFont.typeface(context: Context): Typeface {
    val res = when (this) {
        CodeFont.JETBRAINS_MONO -> DesignR.font.jetbrains_mono_regular
        CodeFont.FIRA_CODE -> DesignR.font.fira_code_regular
        CodeFont.IBM_PLEX_MONO -> DesignR.font.ibm_plex_mono_regular
    }
    return ResourcesCompat.getFont(context, res) ?: Typeface.MONOSPACE
}

private fun CodeEditor.configureLanguage(settings: EditorSettings?) {
    val language = editorLanguage as? TextMateLanguage ?: return
    settings ?: return
    language.tabSize = settings.tabSize
    language.isAutoCompleteEnabled = settings.autocomplete
}

/**
 * Shows [document]: the cached Sora document when its tab was open recently (keeping undo, caret and scroll),
 * otherwise a new one whose text is split into lines off the main thread ([Content] is thread-safe).
 */
internal suspend fun CodeEditorState.show(document: EditorDocument, grammar: GrammarId?) {
    if (boundKey == document.key || editor == null) return
    val shown = documents.get(document.id, document.revision)
        ?: CachedDocument(withContext(Dispatchers.Default) { Content(document.text) }, document.revision)
    val view = editor ?: return
    flush()
    boundDocumentId?.let(documents::peek)?.let { previous -> previous.scrollX = view.offsetX; previous.scrollY = view.offsetY }
    // A different grammar's colours must not linger on this text while its own grammar loads.
    if (grammar != languageGrammar) applyLanguage(view, null)
    view.setText(shown.content, true, null)
    documents.put(document.id, shown)
    boundDocumentId = document.id
    boundKey = document.key
    shownDocumentId = document.id
    commentStyle = CommentStyle(document.lineComment, document.blockComment)
    isDirty = false
    lineCount = view.text.lineCount
    canUndo = view.canUndo()
    canRedo = view.canRedo()
    cursor = CursorPosition(view.cursor.leftLine + 1, view.cursor.leftColumn + 1)
    // setText drops inlay hints; force the next setLineHint to re-add it.
    view.setTag(R.id.omni_editor_line_hint, null)
    view.setTag(R.id.omni_editor_diagnostics, null)
    // Offsets apply once the new text is laid out.
    view.post { view.scrollTo(shown.scrollX, shown.scrollY) }
}

/** Attaches [grammar]'s language to the shown text, rebuilding it only when the grammar actually changes. */
internal fun CodeEditorState.useLanguage(grammar: GrammarId?) {
    val view = editor ?: return
    // Already attached: the same grammar, or plain text staying plain.
    if (grammar == languageGrammar && (hasLanguage || grammar == null)) return
    applyLanguage(view, grammar)
}

private fun CodeEditorState.applyLanguage(view: CodeEditor, grammar: GrammarId?) {
    // setEditorLanguage destroys the previous language and re-analyses the current text.
    view.setEditorLanguage(grammar?.let { TextMateLanguage.create(it.scopeName, true) } ?: EmptyLanguage())
    view.configureLanguage(appliedSettings)
    languageGrammar = grammar
    hasLanguage = grammar != null
}

private fun CodeEditor.scrollTo(x: Int, y: Int) {
    val maxX = scrollMaxX
    val maxY = scrollMaxY
    scroller.forceFinished(true)
    scroller.startScroll(offsetX, offsetY, x.coerceIn(0, maxX) - offsetX, y.coerceIn(0, maxY) - offsetY, 0)
    scroller.computeScrollOffset()
    invalidate()
}

private fun CodeEditor.setLineHint(hint: EditorLineHint?) {
    if (getTag(R.id.omni_editor_line_hint) == (hint ?: NoHint)) return
    setTag(R.id.omni_editor_line_hint, hint ?: NoHint)
    // The hint comes from saved text, which can trail the live buffer by a debounce; clamp to what's shown.
    val line = hint?.let { (it.line - 1).takeIf { l -> l in 0 until text.lineCount } }
    inlayHints = line?.let { l -> InlayHintsContainer().apply { add(TextInlayHint(l, text.getColumnCount(l), hint.text)) } }
}

/** Tag value for "no hint applied", distinct from the null "unknown, re-apply" state. */
private object NoHint

private fun CodeEditor.setDiagnostics(items: List<EditorDiagnostic>) {
    if (getTag(R.id.omni_editor_diagnostics) == items) return
    setTag(R.id.omni_editor_diagnostics, items)
    val content = text
    val container = DiagnosticsContainer()
    items.forEach { d ->
        val line = (d.line - 1).coerceIn(0, content.lineCount - 1)
        val lineStart = content.getCharIndex(line, 0)
        val lineLength = content.getColumnCount(line)
        val start = d.column?.let { lineStart + (it - 1).coerceIn(0, lineLength) } ?: lineStart
        val end = if (d.column != null) minOf(lineStart + lineLength, start + TOKEN_UNDERLINE) else lineStart + lineLength
        val severity = if (d.isError) DiagnosticRegion.SEVERITY_ERROR else DiagnosticRegion.SEVERITY_WARNING
        container.addDiagnostic(DiagnosticRegion(start, maxOf(end, start + 1), severity, 0L, DiagnosticDetail(d.message, null, null, null)))
    }
    diagnostics = container
}

/** Underline length when only a caret column is known (compiler reports a point, not a range). */
private const val TOKEN_UNDERLINE = 8

/** Wrapper so "plain text is ready" (`grammar == null`) differs from "nothing loaded yet". */
private data class LoadedGrammar(val grammar: GrammarId?)
