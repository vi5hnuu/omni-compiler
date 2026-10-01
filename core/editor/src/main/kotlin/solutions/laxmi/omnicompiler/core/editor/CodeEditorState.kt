package solutions.laxmi.omnicompiler.core.editor

import io.github.rosemoe.sora.util.regex.RegexBackrefGrammar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.rosemoe.sora.widget.CodeEditor
import solutions.laxmi.omnicompiler.core.model.EditorSettings
import io.github.rosemoe.sora.widget.EditorSearcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import io.github.rosemoe.sora.widget.SelectionMovement
import java.util.regex.PatternSyntaxException

@Immutable
data class CursorPosition(val line: Int, val column: Int)

/** One minimap row: leading indent and trimmed length, in characters. */
@Immutable
data class MinimapLine(val indent: Int, val length: Int)

/** How find matches text (maps to Sora's search types). */
@Immutable
data class SearchOptions(val caseSensitive: Boolean = false, val wholeWord: Boolean = false, val regex: Boolean = false)

/** Requests the editor raises for the screen to handle (e.g. from hardware-keyboard shortcuts). */
enum class EditorCommand { Find, Replace, GoToLine }

enum class CaretDirection { Left, Right, Up, Down }

/** Comment syntax of the open document's language; drives "toggle comment". */
@Immutable
data class CommentStyle(val line: String?, val block: Pair<String, String>?)

/** A file's text as last saved from the editor. */
@Immutable
data class SavedText(val documentId: String, val text: String)

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

    /** The text last saved from the view, with its file id: what the app sees once autosave settles. */
    var savedText by mutableStateOf<SavedText?>(null); private set

    /** The code view holds input focus (as opposed to another text field on screen, e.g. stdin). */
    var hasFocus by mutableStateOf(false); internal set
    var searchMatches by mutableStateOf(0); internal set
    var searchIndex by mutableStateOf(-1); internal set

    /** The current find pattern is not a valid regular expression. */
    var searchInvalid by mutableStateOf(false); internal set

    private val commandChannel = MutableSharedFlow<EditorCommand>(extraBufferCapacity = 4)
    val commands: SharedFlow<EditorCommand> = commandChannel

    internal var commentStyle: CommentStyle = CommentStyle(null, null)

    /** Top/height of the cursor row in view pixels, for the design's red active-line bar. */
    var activeRow by mutableStateOf<Pair<Float, Float>?>(null); internal set

    internal var editor: CodeEditor? = null
    internal var onTextChanged: ((documentId: String, text: String) -> Unit)? = null

    /** Id of the document currently loaded in the view; edits are always saved against it. */
    internal var boundDocumentId: String? = null

    /** `id@revision` of the shown document; observable so per-document decorations apply once it is shown. */
    internal var boundKey by mutableStateOf<String?>(null)

    /** Documents of recently shown files (text, undo, caret, scroll), reused when their tab is shown again. */
    internal val documents = DocumentCache()

    /** The grammar the view's language was built for; null with [hasLanguage] means plain text. */
    internal var languageGrammar: GrammarId? = null
    internal var hasLanguage = false

    /** Settings last applied to the view, re-applied to each new language (tab size, autocomplete). */
    internal var appliedSettings: EditorSettings? = null
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

    fun search(query: String, options: SearchOptions = SearchOptions()) {
        val searcher = editor?.searcher ?: return
        searchInvalid = false
        if (query.isEmpty()) {
            searcher.stopSearch()
            searchMatches = 0
            searchIndex = -1
            return
        }
        val type = when {
            options.regex -> EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
            options.wholeWord -> EditorSearcher.SearchOptions.TYPE_WHOLE_WORD
            else -> EditorSearcher.SearchOptions.TYPE_NORMAL
        }
        try {
            // `$1`-style back-references in regex replacements.
            searcher.search(query, EditorSearcher.SearchOptions(type, !options.caseSensitive, RegexBackrefGrammar.DEFAULT))
        } catch (e: PatternSyntaxException) {
            searcher.stopSearch()
            searchInvalid = true
            searchMatches = 0
            searchIndex = -1
        }
    }

    /** Replaces the selected match (regex replacements may use `$1` back-references) and moves to the next. */
    fun replaceCurrent(replacement: String) {
        // Sora replaces only when a match is selected; otherwise it moves to the next match first.
        editor?.searcher?.takeIf { it.hasQuery() }?.replaceCurrentMatch(replacement)
    }

    fun replaceAll(replacement: String) {
        editor?.searcher?.takeIf { it.hasQuery() }?.replaceAll(replacement)
    }

    /** Arrow keys for the symbol row: moves the caret like a hardware keyboard would. */
    fun moveCaret(direction: CaretDirection) {
        val movement = when (direction) {
            CaretDirection.Left -> SelectionMovement.LEFT
            CaretDirection.Right -> SelectionMovement.RIGHT
            CaretDirection.Up -> SelectionMovement.UP
            CaretDirection.Down -> SelectionMovement.DOWN
        }
        editor?.moveOrExtendSelection(movement, false)
    }

    fun duplicateLine() {
        editor?.duplicateLine()
    }

    /** Moves the selected lines (or the caret's line) one line up or down, as one undo step. */
    fun moveLines(up: Boolean) {
        val editor = editor ?: return
        val content = editor.text
        val cursor = editor.cursor
        val first = cursor.leftLine
        val last = cursor.rightLine
        if ((up && first == 0) || (!up && last >= content.lineCount - 1)) return
        val block = content.subContent(first, 0, last, content.getColumnCount(last)).toString()
        val neighbourLine = if (up) first - 1 else last + 1
        val neighbour = content.getLineString(neighbourLine)
        content.beginBatchEdit()
        if (up) {
            content.replace(neighbourLine, 0, last, content.getColumnCount(last), "$block\n$neighbour")
        } else {
            content.replace(first, 0, neighbourLine, content.getColumnCount(neighbourLine), "$neighbour\n$block")
        }
        content.endBatchEdit()
        val shift = if (up) -1 else 1
        editor.setSelectionRegion(first + shift, cursor.leftColumn, last + shift, cursor.rightColumn)
    }

    /** Deletes the selected lines (or the caret's line). */
    fun deleteLines() {
        val editor = editor ?: return
        val content = editor.text
        val cursor = editor.cursor
        val first = cursor.leftLine
        val last = cursor.rightLine
        when {
            last < content.lineCount - 1 -> content.delete(first, 0, last + 1, 0)
            first > 0 -> content.delete(first - 1, content.getColumnCount(first - 1), last, content.getColumnCount(last))
            else -> content.delete(0, 0, last, content.getColumnCount(last))
        }
    }

    /**
     * Comments or uncomments the selected lines with the language's line comment; languages that only have
     * block comments get the selection wrapped (or unwrapped) instead.
     */
    fun toggleComment() {
        val editor = editor ?: return
        val content = editor.text
        val cursor = editor.cursor
        val first = cursor.leftLine
        val last = cursor.rightLine
        val token = commentStyle.line
        if (token != null) {
            val lines = (first..last).map { content.getLineString(it) }
            val nonBlank = lines.filter { it.isNotBlank() }
            val allCommented = nonBlank.isNotEmpty() && nonBlank.all { it.trimStart().startsWith(token) }
            content.beginBatchEdit()
            (first..last).forEach { line ->
                val text = content.getLineString(line)
                if (text.isBlank()) return@forEach
                val indent = text.length - text.trimStart().length
                if (allCommented) {
                    val remove = if (text.startsWith("$token ", indent)) token.length + 1 else token.length
                    content.delete(line, indent, line, indent + remove)
                } else {
                    content.insert(line, indent, "$token ")
                }
            }
            content.endBatchEdit()
            return
        }
        val (open, close) = commentStyle.block ?: return
        val endColumn = content.getColumnCount(last)
        val selected = content.subContent(first, 0, last, endColumn).toString()
        val trimmed = selected.trim()
        val replacement = if (trimmed.startsWith(open) && trimmed.endsWith(close)) {
            trimmed.removePrefix(open).removeSuffix(close).trim()
        } else {
            "$open $selected $close"
        }
        content.replace(first, 0, last, endColumn, replacement)
    }

    internal fun raise(command: EditorCommand) {
        commandChannel.tryEmit(command)
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

    /** The view is gone: drop its documents and language so a new view starts clean. */
    internal fun release() {
        editor = null
        documents.clear()
        boundDocumentId = null
        boundKey = null
        languageGrammar = null
        hasLanguage = false
    }

    /** Writes pending edits immediately (file switch, app backgrounded). */
    fun flush() {
        val editor = editor ?: return
        val documentId = boundDocumentId ?: return
        if (!isDirty) return
        pendingSave?.cancel()
        isDirty = false
        val text = editor.text.toString()
        savedText = SavedText(documentId, text)
        onTextChanged?.invoke(documentId, text)
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
