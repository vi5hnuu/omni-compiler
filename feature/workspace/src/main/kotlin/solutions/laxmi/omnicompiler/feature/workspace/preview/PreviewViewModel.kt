package solutions.laxmi.omnicompiler.feature.workspace.preview

import kotlinx.coroutines.flow.mapLatest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import solutions.laxmi.omnicompiler.core.data.project.ProjectRepository
import solutions.laxmi.omnicompiler.core.model.SourceFile
import solutions.laxmi.omnicompiler.core.navigation.PreviewRoute

/** What the preview shows: rendered Markdown, an HTML page, or a JavaScript file run inside a blank page. */
enum class PreviewKind { Html, Markdown, JavaScript }

/** One line of the browser console (the page's `console.*`, uncaught errors, or an evaluated expression). */
data class ConsoleLine(val level: Level, val text: String) {
    enum class Level { Log, Warn, Error, Input, Result }
}

data class PreviewUiState(
    val kind: PreviewKind? = null,
    val title: String = "",
    /** File name the page is served as (entry of the served site). */
    val pageName: String = "",
    /** Project files by name, served to the page so relative links, styles and scripts resolve. */
    val files: Map<String, String> = emptyMap(),
    val markdown: String = "",
    val console: List<ConsoleLine> = emptyList(),
)

@HiltViewModel(assistedFactory = PreviewViewModel.Factory::class)
class PreviewViewModel @AssistedInject constructor(
    @Assisted private val route: PreviewRoute,
    projects: ProjectRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: PreviewRoute): PreviewViewModel
    }

    private val console = MutableStateFlow<List<ConsoleLine>>(emptyList())

    // The page needs every file's text; re-read it whenever the outline changes (each save touches the project).
    private val workspace = projects.observeOutline(route.projectId).mapLatest { projects.snapshot(route.projectId) }

    val uiState: StateFlow<PreviewUiState> = combine(workspace, console) { workspace, lines ->
        val files = workspace?.files.orEmpty()
        val target = route.fileId?.let { id -> files.firstOrNull { it.id == id } } ?: workspace?.entry
        val kind = target?.let(::kindOf)
        PreviewUiState(
            kind = kind,
            title = target?.name.orEmpty(),
            pageName = if (kind == PreviewKind.JavaScript) RUNNER_PAGE else target?.name.orEmpty(),
            files = files.associate { it.name to it.content } + (target?.takeIf { kind == PreviewKind.JavaScript }?.let { mapOf(RUNNER_PAGE to runnerPage(it.name)) } ?: emptyMap()),
            markdown = target?.takeIf { kind == PreviewKind.Markdown }?.content.orEmpty(),
            console = lines,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreviewUiState())

    fun log(line: ConsoleLine) = console.update { (it + line).takeLast(MAX_CONSOLE_LINES) }

    fun clearConsole() {
        console.value = emptyList()
    }

    companion object {
        const val RUNNER_PAGE = "__omni_run__.html"
        private const val MAX_CONSOLE_LINES = 500

        fun kindOf(file: SourceFile): PreviewKind? = when (file.name.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> PreviewKind.Html
            "md", "markdown" -> PreviewKind.Markdown
            "js", "mjs" -> PreviewKind.JavaScript
            else -> null
        }

        /** A blank page that loads the script, so browser JavaScript (DOM, console) can run on its own. */
        private fun runnerPage(script: String) =
            "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\"></head>" +
                "<body><script src=\"${encodePath(script)}\"></script></body></html>"
    }
}
