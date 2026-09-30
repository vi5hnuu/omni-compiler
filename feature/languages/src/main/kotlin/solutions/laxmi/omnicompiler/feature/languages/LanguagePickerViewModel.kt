package solutions.laxmi.omnicompiler.feature.languages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import solutions.laxmi.omnicompiler.core.data.project.ProjectRepository
import solutions.laxmi.omnicompiler.core.data.runtime.RuntimeRepository
import solutions.laxmi.omnicompiler.core.data.settings.SettingsRepository
import solutions.laxmi.omnicompiler.core.model.Language
import solutions.laxmi.omnicompiler.core.model.LanguageCategory
import solutions.laxmi.omnicompiler.core.model.Limits
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.Runtime
import solutions.laxmi.omnicompiler.core.ui.userMessage

/** Chip filters on the picker: everything, recently used, or one category. */
sealed interface LanguageFilter {
    data object All : LanguageFilter
    data object Recent : LanguageFilter
    data class Category(val category: LanguageCategory) : LanguageFilter
}

data class LanguagePickerUiState(
    val loading: Boolean = true,
    val refreshError: String? = null,
    val query: String = "",
    val filter: LanguageFilter = LanguageFilter.All,
    val languages: List<Language> = emptyList(),
    val recent: List<Language> = emptyList(),
    val totalRuntimes: Int = 0,
    val currentRuntimeId: String? = null,
    val defaultRuntimeId: String? = null,
    val selected: Language? = null,
    val selectedLimits: Map<String, Limits> = emptyMap(),
)

sealed interface LanguagePickerEvent {
    data object Done : LanguagePickerEvent
    data class Message(val text: String) : LanguagePickerEvent
}

@HiltViewModel(assistedFactory = LanguagePickerViewModel.Factory::class)
class LanguagePickerViewModel @AssistedInject constructor(
    @Assisted private val projectId: String,
    private val runtimes: RuntimeRepository,
    private val projects: ProjectRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(projectId: String): LanguagePickerViewModel
    }

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<LanguageFilter>(LanguageFilter.All)
    private val selectedBase = MutableStateFlow<String?>(null)
    private val limits = MutableStateFlow<Map<String, Limits>>(emptyMap())
    private val refreshError = MutableStateFlow<String?>(null)
    private val refreshing = MutableStateFlow(true)

    private val events = Channel<LanguagePickerEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val uiState: StateFlow<LanguagePickerUiState> = combine(
        combine(runtimes.languages, runtimes.recentRuntimeIds, ::Pair),
        combine(query, filter, selectedBase, ::Triple),
        combine(projects.observeWorkspace(projectId), settings.runSettings, ::Pair),
        combine(limits, refreshError, refreshing, ::Triple),
    ) { (languages, recentIds), (q, f, base), (workspace, runSettings), (lims, error, busy) ->
        val recent = recentIds.mapNotNull { id -> languages.firstOrNull { lang -> lang.runtimes.any { it.id == id } } }.distinct()
        val filtered = languages
            .filter { lang ->
                when (f) {
                    LanguageFilter.All -> true
                    LanguageFilter.Recent -> lang in recent
                    is LanguageFilter.Category -> lang.info.category == f.category
                }
            }
            .filter { lang -> q.isBlank() || lang.matches(q.trim()) }
        LanguagePickerUiState(
            loading = busy && languages.isEmpty(),
            refreshError = error,
            query = q,
            filter = f,
            languages = filtered,
            recent = if (f == LanguageFilter.All && q.isBlank()) recent else emptyList(),
            totalRuntimes = languages.sumOf { it.runtimes.size },
            currentRuntimeId = workspace?.project?.runtimeId,
            defaultRuntimeId = runSettings.defaultRuntimeId,
            selected = base?.let { b -> languages.firstOrNull { it.base == b } },
            selectedLimits = lims,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LanguagePickerUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            val result = runtimes.refresh()
            refreshError.value = (result as? Outcome.Failure)?.error?.userMessage()
            refreshing.value = false
        }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setFilter(value: LanguageFilter) {
        filter.value = value
    }

    fun select(language: Language) {
        selectedBase.value = language.base
        viewModelScope.launch {
            limits.value = language.runtimes.associate { it.id to runtimes.defaultLimits(it.id) }
        }
    }

    fun dismissSelection() {
        selectedBase.value = null
    }

    fun use(runtime: Runtime, makeDefault: Boolean) {
        viewModelScope.launch {
            if (makeDefault) settings.updateRun { it.copy(defaultRuntimeId = runtime.id) }
            when (val result = projects.changeRuntime(projectId, runtime)) {
                is Outcome.Success -> events.send(LanguagePickerEvent.Done)
                is Outcome.Failure -> events.send(LanguagePickerEvent.Message(result.error.userMessage()))
            }
        }
    }

    private fun Language.matches(q: String): Boolean =
        info.name.contains(q, ignoreCase = true) ||
            base.contains(q, ignoreCase = true) ||
            runtimes.any { it.id.contains(q, ignoreCase = true) || it.version.contains(q, ignoreCase = true) }
}
