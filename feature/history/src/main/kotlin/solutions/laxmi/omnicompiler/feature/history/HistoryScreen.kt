package solutions.laxmi.omnicompiler.feature.history

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import solutions.laxmi.omnicompiler.core.common.formatMillis
import solutions.laxmi.omnicompiler.core.common.shortJobId
import solutions.laxmi.omnicompiler.core.data.AppErrorException
import solutions.laxmi.omnicompiler.core.data.history.DayActivity
import solutions.laxmi.omnicompiler.core.designsystem.component.EmptyState
import solutions.laxmi.omnicompiler.core.designsystem.component.InfoBanner
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniChip
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniSpinner
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTopBar
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.model.AppError
import solutions.laxmi.omnicompiler.core.model.Submission
import solutions.laxmi.omnicompiler.core.model.Verdict
import solutions.laxmi.omnicompiler.core.navigation.JobDetailRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.navigation.WelcomeRoute
import solutions.laxmi.omnicompiler.core.ui.VerdictBadge
import solutions.laxmi.omnicompiler.core.ui.userMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Design W4: runs recorded by the judge for this account. */
@Composable
fun HistoryScreen(navigator: Navigator) {
    val viewModel = hiltViewModel<HistoryViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems()
    val colors = OmniTheme.colors
    Column(Modifier.fillMaxSize().background(colors.background).navigationBarsPadding()) {
        OmniTopBar("Run history", onBack = navigator::back, subtitle = "Recorded by the judge for this account")
        if (!state.signedIn) {
            EmptyState(
                "Sign in to see history",
                "Runs are tied to your account. Guests and signed-in users keep a history.",
                icon = OmniIcons.History,
                action = { OmniButton("Sign in", { navigator.navigate(WelcomeRoute) }) },
            )
            return@Column
        }
        val refresh = items.loadState.refresh
        PullToRefreshBox(
            isRefreshing = refresh is LoadState.Loading && items.itemCount > 0,
            onRefresh = {
                items.refresh()
                viewModel.refreshStats()
            },
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                item { StatsRow(state) }
                item { WeekChart(state.week) }
                item { VerdictFilters(state, viewModel::setFilter) }
                if (refresh is LoadState.Error) {
                    item {
                        val error = (refresh.error as? AppErrorException)?.error
                        InfoBanner(
                            text = if (error is AppError.Unauthorized) "Your session expired. Sign in again." else error?.userMessage() ?: "Couldn't load history.",
                            icon = OmniIcons.WifiOff,
                            modifier = Modifier.padding(16.dp),
                            action = { OmniTextButton("Retry", items::retry) },
                        )
                    }
                }
                if (refresh is LoadState.NotLoading && items.itemCount == 0) {
                    item { EmptyState("No runs yet", "Runs you start from any device with this account appear here.", icon = OmniIcons.History) }
                }
                items(items.itemCount, key = items.itemKey { it.key() }, contentType = items.itemContentType { it::class }) { index ->
                    when (val item = items[index]) {
                        is HistoryItem.Header -> DayHeader(item.dayStartEpochMs)
                        is HistoryItem.Row -> SubmissionRow(item.submission) {
                            navigator.navigate(JobDetailRoute(item.submission.id, item.submission.runtimeId))
                        }
                        null -> Unit
                    }
                }
                if (items.loadState.append is LoadState.Loading || (refresh is LoadState.Loading && items.itemCount == 0)) {
                    item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { OmniSpinner() } }
                }
            }
        }
    }
}

private fun HistoryItem.key(): String = when (this) {
    is HistoryItem.Header -> "day-$dayStartEpochMs"
    is HistoryItem.Row -> submission.id
}

@Composable
private fun StatsRow(state: HistoryUiState) {
    val colors = OmniTheme.colors
    val stats = state.stats
    Row(Modifier.fillMaxWidth().padding(16.dp)) {
        listOf(
            "Runs" to (stats?.totalJobs?.toString() ?: state.summary.total.toString()),
            "Accepted" to (stats?.let { "${(it.acceptanceRate * 100).roundToInt()}%" } ?: "—"),
            "Median time" to formatMillis(state.summary.medianTimeMs),
        ).forEach { (label, value) ->
            Column(Modifier.weight(1f)) {
                Text(label.uppercase(), style = OmniTheme.typography.overline, color = colors.textTertiary)
                Text(value, style = OmniTheme.typography.headline, color = colors.textPrimary)
            }
        }
    }
}

/** 7-day bars (today in accent), derived from cached history. */
@Composable
private fun WeekChart(week: List<DayActivity>) {
    if (week.isEmpty()) return
    val colors = OmniTheme.colors
    val max = week.maxOf { it.runs }.coerceAtLeast(1)
    val dayFormat = SimpleDateFormat("EEEEE", Locale.getDefault())
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            week.forEachIndexed { i, day ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction = (day.runs.toFloat() / max).coerceAtLeast(0.03f))
                        .background(if (i == week.lastIndex) colors.accent else colors.surfaceMuted),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            week.forEach { day ->
                Text(dayFormat.format(Date(day.dayStartEpochMs)), style = OmniTheme.typography.monoSmall, color = colors.textTertiary, modifier = Modifier.weight(1f))
            }
        }
        Text("${week.sumOf { it.runs }} runs in 7 days · from cached history", style = OmniTheme.typography.bodySmall, color = colors.textTertiary, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun VerdictFilters(state: HistoryUiState, onSelect: (Verdict?) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OmniChip("All", state.filter == null, { onSelect(null) }, count = state.summary.total.toString())
        listOf(Verdict.AC, Verdict.WA, Verdict.TLE, Verdict.CE, Verdict.RE, Verdict.MLE, Verdict.IE).forEach { verdict ->
            OmniChip(verdict.code, state.filter == verdict, { onSelect(verdict) }, count = (state.summary.verdictCounts[verdict] ?: 0).toString())
        }
    }
}

@Composable
private fun DayHeader(dayStart: Long) {
    val label = when {
        DateUtils.isToday(dayStart) -> "Today"
        DateUtils.isToday(dayStart + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
        else -> SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(dayStart))
    }
    Text(
        label.uppercase(),
        style = OmniTheme.typography.overline,
        color = OmniTheme.colors.textTertiary,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun SubmissionRow(submission: Submission, onClick: () -> Unit) {
    val colors = OmniTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .drawBehind { drawLine(colors.hairline, Offset(0f, size.height), Offset(size.width, size.height), 1f) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        submission.verdict?.let { VerdictBadge(it) } ?: Text(submission.status.name.lowercase(), style = OmniTheme.typography.monoSmall, color = colors.textTertiary)
        Column(Modifier.weight(1f)) {
            Text(submission.runtimeId, style = OmniTheme.typography.bodyStrong, color = colors.textPrimary)
            Text("job ${shortJobId(submission.id)}", style = OmniTheme.typography.monoSmall, color = colors.textTertiary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatMillis(submission.totalTimeMs), style = OmniTheme.typography.mono, color = colors.textSecondary)
            Text(
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(submission.createdAt.toEpochMilliseconds())),
                style = OmniTheme.typography.monoSmall,
                color = colors.textTertiary,
            )
        }
    }
}
