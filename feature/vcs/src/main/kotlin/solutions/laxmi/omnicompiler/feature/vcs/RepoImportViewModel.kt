package solutions.laxmi.omnicompiler.feature.vcs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import solutions.laxmi.omnicompiler.core.data.git.GitRepository
import solutions.laxmi.omnicompiler.core.model.GitHost
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.RemoteEntry
import solutions.laxmi.omnicompiler.core.model.RemoteRepo
import solutions.laxmi.omnicompiler.core.ui.UiText
import solutions.laxmi.omnicompiler.core.ui.toUiText
import javax.inject.Inject

/** Browsing inside one repository: its branches and the folder currently shown. */
data class RepoBrowse(
    val repo: RemoteRepo,
    val branches: List<String> = emptyList(),
    val branch: String = repo.defaultBranch,
    val path: String = "",
    val entries: List<RemoteEntry> = emptyList(),
)

data class RepoImportUiState(
    val hosts: List<GitHost> = emptyList(),
    /** Connected login per host, shown on the host tabs so two accounts are easy to tell apart. */
    val logins: Map<GitHost, String> = emptyMap(),
    val host: GitHost? = null,
    val query: String = "",
    val repos: List<RemoteRepo> = emptyList(),
    val canLoadMore: Boolean = false,
    val browse: RepoBrowse? = null,
    val loading: Boolean = false,
    val importing: Boolean = false,
    val error: UiText? = null,
) {
    val visibleRepos: List<RemoteRepo> get() = if (query.isBlank()) repos else repos.filter { it.fullName.contains(query.trim(), ignoreCase = true) }
}

@HiltViewModel
class RepoImportViewModel @Inject constructor(private val git: GitRepository) : ViewModel() {
    private val state = MutableStateFlow(RepoImportUiState())
    val uiState: StateFlow<RepoImportUiState> = state.asStateFlow()

    private val opened = Channel<String>(Channel.CONFLATED)
    /** Emits the new project's id once an import finishes. */
    val openedFlow = opened.receiveAsFlow()

    private var page = 1
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            val accounts = git.accounts.first()
            val hosts = accounts.map { it.host }
            state.update { it.copy(hosts = hosts, logins = accounts.associate { it.host to it.login }) }
            hosts.firstOrNull()?.let(::selectHost)
        }
    }

    fun selectHost(host: GitHost) {
        page = 1
        state.update { it.copy(host = host, repos = emptyList(), browse = null, error = null) }
        loadMore()
    }

    fun setQuery(value: String) = state.update { it.copy(query = value) }

    fun loadMore() {
        val host = state.value.host ?: return
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            state.update { it.copy(loading = true, error = null) }
            when (val result = git.repos(host, page)) {
                is Outcome.Success -> {
                    page++
                    state.update { it.copy(repos = it.repos + result.value, canLoadMore = result.value.size >= PAGE_SIZE, loading = false) }
                }
                is Outcome.Failure -> state.update { it.copy(loading = false, error = result.error.toUiText()) }
            }
        }
    }

    fun openRepo(repo: RemoteRepo) {
        state.update { it.copy(browse = RepoBrowse(repo), error = null) }
        viewModelScope.launch {
            (git.branches(repo) as? Outcome.Success)?.value?.let { branches -> state.update { it.copy(browse = it.browse?.copy(branches = branches)) } }
        }
        showFolder("")
    }

    fun selectBranch(branch: String) {
        state.update { it.copy(browse = it.browse?.copy(branch = branch)) }
        showFolder("")
    }

    private var folderJob: Job? = null

    fun showFolder(path: String) {
        val browse = state.value.browse ?: return
        // Only the folder asked for last may fill the list; a slower earlier response must not replace it.
        folderJob?.cancel()
        folderJob = viewModelScope.launch {
            state.update { it.copy(loading = true, error = null, browse = it.browse?.copy(path = path, entries = emptyList())) }
            when (val result = git.list(browse.repo, state.value.browse?.branch ?: browse.branch, path)) {
                is Outcome.Success -> state.update { it.copy(loading = false, browse = it.browse?.copy(entries = result.value)) }
                is Outcome.Failure -> state.update { it.copy(loading = false, error = result.error.toUiText()) }
            }
        }
    }

    /** Repeats whatever failed: the open folder when browsing, otherwise the repository list. */
    fun retry() {
        val browse = state.value.browse
        if (browse != null) showFolder(browse.path) else loadMore()
    }

    /** Back inside the browser: parent folder, then the repository list. Returns false when there's nowhere to go. */
    fun up(): Boolean {
        val browse = state.value.browse ?: return false
        if (browse.path.isEmpty()) {
            state.update { it.copy(browse = null) }
        } else {
            showFolder(browse.path.substringBeforeLast('/', ""))
        }
        return true
    }

    fun importCurrent() {
        val browse = state.value.browse ?: return
        if (state.value.importing) return
        viewModelScope.launch {
            state.update { it.copy(importing = true, error = null) }
            when (val result = git.importFolder(browse.repo, browse.branch, browse.path)) {
                is Outcome.Success -> opened.send(result.value)
                is Outcome.Failure -> state.update { it.copy(error = result.error.toUiText()) }
            }
            state.update { it.copy(importing = false) }
        }
    }

    private companion object {
        const val PAGE_SIZE = 100
    }
}
