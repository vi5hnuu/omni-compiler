package solutions.laxmi.omnicompiler.feature.workspace.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import solutions.laxmi.omnicompiler.core.common.Dispatcher
import solutions.laxmi.omnicompiler.core.common.OmniDispatcher
import solutions.laxmi.omnicompiler.core.data.project.ProjectRepository
import solutions.laxmi.omnicompiler.core.editor.SearchOptions
import solutions.laxmi.omnicompiler.core.model.SourceFile
import javax.inject.Inject

/** One match: 1-based [line]/[column], and the matched range inside [preview] (a trimmed copy of the line). */
data class ProjectSearchHit(
    val fileId: String,
    val line: Int,
    val column: Int,
    val preview: String,
    val matchStart: Int,
    val matchEnd: Int,
)

data class FileSearchResults(val fileId: String, val fileName: String, val hits: List<ProjectSearchHit>)

data class ProjectSearchUiState(
    val query: String = "",
    val options: SearchOptions = SearchOptions(),
    val results: List<FileSearchResults> = emptyList(),
    val matchCount: Int = 0,
    /** More than [ProjectSearchViewModel.MAX_HITS] matches exist; only the first ones are listed. */
    val truncated: Boolean = false,
    val invalidPattern: Boolean = false,
)

/**
 * Text search across every file of a project. Sora searches one open document, so finding where something is used
 * across files is done here over the saved file contents; the editor then highlights matches in the file opened.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class ProjectSearchViewModel @Inject constructor(
    private val projects: ProjectRepository,
    @Dispatcher(OmniDispatcher.Default) private val searchDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val files = MutableStateFlow<List<SourceFile>>(emptyList())
    private val query = MutableStateFlow("")
    private val options = MutableStateFlow(SearchOptions())

    val uiState: StateFlow<ProjectSearchUiState> = combine(files, query.debounce(QUERY_DEBOUNCE_MS), options, ::Triple)
        .mapLatest { (all, text, opts) -> search(all, text, opts) }
        .flowOn(searchDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectSearchUiState())

    /** Reads the project's files once per opening of the search sheet (the caller flushes open editors first). */
    fun open(projectId: String) {
        viewModelScope.launch { files.value = projects.snapshot(projectId)?.files.orEmpty() }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setOptions(value: SearchOptions) {
        options.value = value
    }

    private fun search(all: List<SourceFile>, text: String, opts: SearchOptions): ProjectSearchUiState {
        val base = ProjectSearchUiState(query = text, options = opts)
        if (text.isEmpty()) return base
        val regex = runCatching { pattern(text, opts) }.getOrElse { return base.copy(invalidPattern = true) }
        var count = 0
        var truncated = false
        val results = all.sortedBy { it.position }.mapNotNull { file ->
            val hits = mutableListOf<ProjectSearchHit>()
            file.content.lineSequence().forEachIndexed { index, line ->
                if (truncated) return@forEachIndexed
                regex.findAll(line).filter { it.value.isNotEmpty() }.forEach { match ->
                    if (count == MAX_HITS) {
                        truncated = true
                        return@forEachIndexed
                    }
                    hits += hit(file.id, index, line, match.range)
                    count++
                }
            }
            hits.takeIf { it.isNotEmpty() }?.let { FileSearchResults(file.id, file.name, it) }
        }
        return base.copy(results = results, matchCount = count, truncated = truncated)
    }

    private fun pattern(text: String, opts: SearchOptions): Regex {
        val source = if (opts.regex) text else Regex.escape(text)
        val bounded = if (opts.wholeWord) "\\b(?:$source)\\b" else source
        return if (opts.caseSensitive) Regex(bounded) else Regex(bounded, RegexOption.IGNORE_CASE)
    }

    /** Keeps the matched text visible in long lines by showing a window around it. */
    private fun hit(fileId: String, index: Int, line: String, range: IntRange): ProjectSearchHit {
        val leading = line.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
        val start = maxOf(leading, range.first - PREVIEW_CONTEXT)
        val end = minOf(line.length, range.last + 1 + PREVIEW_CONTEXT)
        // An ellipsis marks where the line was cut, so a preview never seems to start mid-word.
        val prefix = if (start > leading) ELLIPSIS else ""
        val suffix = if (end < line.length) ELLIPSIS else ""
        return ProjectSearchHit(
            fileId = fileId,
            line = index + 1,
            column = range.first + 1,
            preview = prefix + line.substring(start, end) + suffix,
            matchStart = prefix.length + range.first - start,
            matchEnd = prefix.length + range.last + 1 - start,
        )
    }

    companion object {
        const val MAX_HITS = 500
        private const val QUERY_DEBOUNCE_MS = 250L
        private const val PREVIEW_CONTEXT = 40
        private const val ELLIPSIS = "…"
    }
}
