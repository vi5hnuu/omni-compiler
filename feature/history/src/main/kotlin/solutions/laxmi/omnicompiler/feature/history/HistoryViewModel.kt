package solutions.laxmi.omnicompiler.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import solutions.laxmi.omnicompiler.core.data.auth.AuthRepository
import solutions.laxmi.omnicompiler.core.data.history.CachedHistorySummary
import solutions.laxmi.omnicompiler.core.data.history.DayActivity
import solutions.laxmi.omnicompiler.core.data.history.HistoryRepository
import solutions.laxmi.omnicompiler.core.data.runtime.RuntimeRepository
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.Session
import solutions.laxmi.omnicompiler.core.model.Submission
import solutions.laxmi.omnicompiler.core.model.UsageStats
import solutions.laxmi.omnicompiler.core.model.Verdict
import java.util.Calendar
import javax.inject.Inject

/** List items: day headers interleaved with submissions. */
sealed interface HistoryItem {
    data class Header(val dayStartEpochMs: Long) : HistoryItem
    data class Row(val submission: Submission) : HistoryItem
}

data class HistoryUiState(
    val signedIn: Boolean = true,
    val filter: Verdict? = null,
    val summary: CachedHistorySummary = CachedHistorySummary(emptyMap(), 0, null),
    val week: List<DayActivity> = emptyList(),
    val stats: UsageStats? = null,
)

/** A runtime as people read it ("Python", "3.11") rather than its judge id ("python-3.11"). */
data class RuntimeName(val language: String, val version: String)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: HistoryRepository,
    auth: AuthRepository,
    runtimes: RuntimeRepository,
) : ViewModel() {

    /** Display names by runtime id; ids missing from the catalog fall back to the raw id in the row. */
    val runtimeNames: StateFlow<Map<String, RuntimeName>> = runtimes.languages
        .map { languages -> languages.flatMap { language -> language.runtimes.map { it.id to RuntimeName(language.info.name, it.version) } }.toMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val filter = MutableStateFlow<Verdict?>(null)
    private val stats = MutableStateFlow<UsageStats?>(null)

    val items: Flow<PagingData<HistoryItem>> = filter
        .flatMapLatest { verdict -> history.submissions(verdict) }
        .map { data ->
            data.map<Submission, HistoryItem> { HistoryItem.Row(it) }
                .insertSeparators { before, after ->
                    val next = (after as? HistoryItem.Row)?.submission ?: return@insertSeparators null
                    val previous = (before as? HistoryItem.Row)?.submission
                    val day = dayOf(next.createdAt.toEpochMilliseconds())
                    if (previous == null || dayOf(previous.createdAt.toEpochMilliseconds()) != day) HistoryItem.Header(day) else null
                }
        }
        .cachedIn(viewModelScope)

    val uiState: StateFlow<HistoryUiState> = combine(
        auth.session,
        filter,
        history.summary,
        history.observeLastWeek(),
        stats,
    ) { session, f, summary, week, s ->
        HistoryUiState(signedIn = session is Session.Active, filter = f, summary = summary, week = week, stats = s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    init {
        refreshStats()
    }

    fun setFilter(verdict: Verdict?) {
        filter.value = verdict
    }

    fun refreshStats() {
        viewModelScope.launch { (history.stats() as? Outcome.Success)?.let { stats.value = it.value } }
    }

    private fun dayOf(epochMs: Long): Long = Calendar.getInstance().apply {
        timeInMillis = epochMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
