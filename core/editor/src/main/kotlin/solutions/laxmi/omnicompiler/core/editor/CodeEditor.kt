package solutions.laxmi.omnicompiler.core.editor

import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import androidx.compose.runtime.SideEffect
import android.content.Context
import android.graphics.Typeface
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
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
 * [text] (e.g. after "reset to starter"), which also resets undo history.
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
 */
@Composable
fun CodeEditor(
    state: CodeEditorState,
    document: EditorDocument,
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
    val grammar = remember(document.fileName, document.languageBase, document.isEntry) {
        EditorLanguages.grammarFor(document.fileName, document.languageBase, document.isEntry)
    }
    // Which grammar is parsed and usable; a document is bound only once its own grammar is.
    var loaded by remember { mutableStateOf<LoadedGrammar?>(null) }

    LaunchedEffect(grammar) {
        withContext(Dispatchers.IO) { EditorLanguages.ensureLoaded(context, grammar) }
        loaded = LoadedGrammar(grammar)
    }
    SideEffect { state.onTextChanged = { id, text -> currentOnTextChange(id, text) } }

    Row(modifier.background(palette.background)) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (loaded != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        createEditor(ctx, state, onRunShortcut = { currentRunShortcut?.invoke() }, onLineHintClick = { currentLineHintClick() })
                    },
                    update = { editor ->
                        editor.applySettings(context, effective, palette)
                        editor.isEditable = !readOnly
                        if (loaded?.grammar == grammar) {
                            editor.bindDocument(state, document, grammar)
                            editor.setDiagnostics(diagnostics)
                            editor.setLineHint(lineHint)
                        }
                    },
                    onRelease = { editor ->
                        state.flush()
                        state.editor = null
                        editor.release()
                    },
                )
                ActiveLineBar(state, palette)
            }
        }
        if (effective.minimap) {
            Minimap(state, palette, errorLines = remember(diagnostics) { diagnostics.filter { it.isError }.map { it.line }.toSet() })
        }
    }

    // Persist the buffer whenever the app goes to the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, state) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) state.flush() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/** Reads the row at draw time: it moves on every scroll frame and must not recompose the editor. */
@Composable
private fun ActiveLineBar(state: CodeEditorState, palette: EditorPalette) {
    Spacer(
        Modifier.fillMaxSize().drawBehind {
            val (top, height) = state.activeRow ?: return@drawBehind
            drawRect(palette.cursor, topLeft = Offset(0f, top), size = Size(2.dp.toPx(), height))
        },
    )
}

private fun createEditor(context: Context, state: CodeEditorState, onRunShortcut: () -> Unit, onLineHintClick: () -> Unit): CodeEditor =
    CodeEditor(context).apply {
        state.editor = this
        isLineNumberEnabled = true
        setPinLineNumber(true)
        setDividerWidth(0f)
        setDividerMargin(8 * dpUnit, 6 * dpUnit)
        setLineNumberMarginLeft(4 * dpUnit)
        setHighlightCurrentLine(true)
        setHighlightBracketPair(true)
        setBlockLineWidth(1f)
        setCursorWidth(2 * dpUnit)
        setScrollBarEnabled(false)
        setInterceptParentHorizontalScrollIfNeeded(true)
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
            updateActiveRow(state)
        }
        subscribeEvent(ScrollEvent::class.java) { _, _ ->
            updateViewport(state)
            updateActiveRow(state)
        }
        subscribeEvent(PublishSearchResultEvent::class.java) { _, _ ->
            state.searchMatches = searcher.matchedPositionCount
            state.searchIndex = searcher.currentMatchedPositionIndex
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
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateViewport(state)
            updateActiveRow(state)
        }
    }

private fun updateViewport(state: CodeEditorState) {
    val editor = state.editor ?: return
    val total = (editor.scrollMaxY + editor.height).toFloat().coerceAtLeast(1f)
    state.viewport = EditorViewport(editor.offsetY / total, editor.height / total)
}

private fun updateActiveRow(state: CodeEditorState) {
    val editor = state.editor ?: return
    if (editor.cursor.isSelected) {
        state.activeRow = null
        return
    }
    val offset = editor.layout.getCharLayoutOffset(editor.cursor.leftLine, editor.cursor.leftColumn)
    val rowHeight = editor.rowHeight.toFloat()
    val top = offset[0] - rowHeight - editor.offsetY
    state.activeRow = if (top + rowHeight < 0 || top > editor.height) null else top to rowHeight
}

private data class AppliedSettings(val settings: EditorSettings, val palette: EditorPalette)

/** Re-applies settings only when they change; AndroidView.update runs on every recomposition. */
private fun CodeEditor.applySettings(context: Context, settings: EditorSettings, palette: EditorPalette) {
    val applied = AppliedSettings(settings, palette)
    if (getTag(R.id.omni_editor_settings) == applied) return
    setTag(R.id.omni_editor_settings, applied)

    val typeface = settings.font.typeface(context)
    typefaceText = typeface
    typefaceLineNumber = typeface
    setTextSize(settings.fontSizeSp.toFloat())
    setLineSpacing(0f, settings.lineSpacing.multiplier)
    setLigatureEnabled(settings.ligatures)
    setBlockLineEnabled(settings.indentGuides)
    tabWidth = settings.tabSize
    (editorLanguage as? TextMateLanguage)?.let { it.tabSize = settings.tabSize; it.isAutoCompleteEnabled = settings.autocomplete }
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

/** Swaps buffers only when the document identity changes, never on ordinary recomposition. */
private fun CodeEditor.bindDocument(state: CodeEditorState, document: EditorDocument, grammar: GrammarId?) {
    if (getTag(R.id.omni_editor_document) == document.key) return
    state.flush()
    setTag(R.id.omni_editor_document, document.key)
    state.boundDocumentId = document.id
    state.commentStyle = CommentStyle(document.lineComment, document.blockComment)
    val previous = editorLanguage
    setEditorLanguage(grammar?.let { TextMateLanguage.create(it.scopeName, true) } ?: EmptyLanguage())
    (previous as? TextMateLanguage)?.destroy()
    setText(document.text)
    state.isDirty = false
    state.lineCount = text.lineCount
    state.canUndo = false
    state.canRedo = false
    state.cursor = CursorPosition(1, 1)
    state.refreshMinimap()
    setTag(R.id.omni_editor_settings, null)
    // setText/setEditorLanguage drop inlay hints; force the next setLineHint to re-add it.
    setTag(R.id.omni_editor_line_hint, null)
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
