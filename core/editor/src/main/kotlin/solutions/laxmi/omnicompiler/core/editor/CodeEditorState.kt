package solutions.laxmi.omnicompiler.core.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class CursorPosition(val line: Int, val column: Int)

/** One minimap row: leading indent and trimmed length, in characters. */
@Immutable
data class MinimapLine(val indent: Int, val length: Int)

/** Visible window of the document as fractions of its total height (0..1). */
@Immutable
data class EditorViewport(val top: Float, val height: Float)

/**
 * UI-facing handle to the native editor. Exposes observable editor facts (cursor, undo state,
 * minimap data) and imperative commands, so screens never touch the Sora view directly.
 */
@Stable
class CodeEditorState internal constructor(private val scope: CoroutineScope) {

    var cursor by mutableStateOf(CursorPosition(1, 1)); internal set
    var canUndo by mutableStateOf(false); internal set
    var canRedo by mutableStateOf(false); internal set
    var lineCount by mutableStateOf(1); internal set
    var minimap by mutableStateOf<List<MinimapLine>>(emptyList()); internal set
    var viewport by mutableStateOf(EditorViewport(0f, 1f)); internal set
    var isDirty by mutableStateOf(false); internal set

    /** The code view holds input focus (as opposed to another text field on screen, e.g. stdin). */
    var hasFocus by mutableStateOf(false); internal set
    var searchMatches by mutableStateOf(0); internal set
    var searchIndex by mutableStateOf(-1); internal set

    /** Top/height of the cursor row in view pixels, for the design's red active-line bar. */
    var activeRow by mutableStateOf<Pair<Float, Float>?>(null); internal set

    internal var editor: CodeEditor? = null
    internal var onTextChanged: ((documentId: String, text: String) -> Unit)? = null

    /** Id of the document currently loaded in the view; edits are always saved against it. */
    internal var boundDocumentId: String? = null
    private var pendingSave: Job? = null

    fun insert(symbol: String) {
        editor?.commitText(symbol)
    }

    fun indent() {
        editor?.indentOrCommitTab()
    }

    fun undo() {
        editor?.undo()
    }

    fun redo() {
        editor?.redo()
    }

    /** Moves the caret to a 1-based [line]/[column] (e.g. from a compile error) and reveals it. */
    fun goTo(line: Int, column: Int = 1) {
        val editor = editor ?: return
        val content = editor.text
        val targetLine = (line - 1).coerceIn(0, content.lineCount - 1)
        val targetColumn = (column - 1).coerceIn(0, content.getColumnCount(targetLine))
        editor.setSelection(targetLine, targetColumn)
        editor.ensureSelectionVisible()
        editor.requestFocus()
    }

    fun search(query: String) {
        val searcher = editor?.searcher ?: return
        if (query.isEmpty()) {
            searcher.stopSearch()
            searchMatches = 0
            searchIndex = -1
        } else {
            searcher.search(query, EditorSearcher.SearchOptions(EditorSearcher.SearchOptions.TYPE_NORMAL, true))
        }
    }

    fun findNext() {
        editor?.searcher?.takeIf { it.hasQuery() }?.gotoNext()
    }

    fun findPrevious() {
        editor?.searcher?.takeIf { it.hasQuery() }?.gotoPrevious()
    }

    fun stopSearch() = search("")

    /** Scrolls so the viewport starts at [fraction] of the document (minimap drag). */
    fun scrollToFraction(fraction: Float) {
        val editor = editor ?: return
        val maxY = editor.scrollMaxY
        val targetY = (fraction.coerceIn(0f, 1f) * (maxY + editor.height)).toInt().coerceIn(0, maxY)
        editor.scroller.forceFinished(true)
        editor.scroller.startScroll(editor.offsetX, editor.offsetY, 0, targetY - editor.offsetY, 0)
        editor.scroller.computeScrollOffset()
        editor.invalidate()
    }

    /** Writes pending edits immediately (file switch, app backgrounded). */
    fun flush() {
        val editor = editor ?: return
        val documentId = boundDocumentId ?: return
        if (!isDirty) return
        pendingSave?.cancel()
        isDirty = false
        onTextChanged?.invoke(documentId, editor.text.toString())
    }

    internal fun onContentChanged() {
        isDirty = true
        pendingSave?.cancel()
        pendingSave = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            flush()
            refreshMinimap()
        }
    }

    internal fun refreshMinimap() {
        val editor = editor ?: return
        val snapshot = editor.text.toString()
        scope.launch {
            minimap = withContext(Dispatchers.Default) {
                snapshot.lineSequence().take(MAX_MINIMAP_LINES).map { line ->
                    val indent = line.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }
                    MinimapLine(indent, line.trim().length)
                }.toList()
            }
        }
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 300L
        const val MAX_MINIMAP_LINES = 4_000
    }
}

@Composable
fun rememberCodeEditorState(): CodeEditorState {
    val scope = rememberCoroutineScope()
    return remember { CodeEditorState(scope) }
}
