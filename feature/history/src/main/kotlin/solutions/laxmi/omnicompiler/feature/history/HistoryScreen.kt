package solutions.laxmi.omnicompiler.feature.history

import solutions.laxmi.omnicompiler.core.ads.AdBanner
import solutions.laxmi.omnicompiler.core.ui.formatDuration
import solutions.laxmi.omnicompiler.core.ui.R as CommonR
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
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
import solutions.laxmi.omnicompiler.core.ui.asString
import solutions.laxmi.omnicompiler.core.ui.toUiText
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Design W4: runs recorded by the judge for this account. */
@Composable
fun HistoryScreen(navigator: Navigator) {
    val viewModel = hiltViewModel<HistoryViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val runtimeNames by viewModel.runtimeNames.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems()
    val colors = OmniTheme.colors
    Column(Modifier.fillMaxSize().background(colors.background).navigationBarsPadding()) {
        OmniTopBar(stringResource(R.string.history_title), onBack = navigator::back, subtitle = stringResource(R.string.history_subtitle))
        if (!state.signedIn) {
            EmptyState(
                stringResource(R.string.history_signed_out_title),
                stringResource(R.string.history_signed_out_message),
                icon = OmniIcons.History,
                action = { OmniButton(stringResource(CommonR.string.common_sign_in), { navigator.navigate(WelcomeRoute) }) },
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
                            text = when {
                                error is AppError.Unauthorized -> stringResource(R.string.history_session_expired)
                                error != null -> error.toUiText().asString()
                                else -> stringResource(R.string.history_load_failed)
                            },
                            icon = OmniIcons.WifiOff,
                            modifier = Modifier.padding(16.dp),
                            action = { OmniTextButton(stringResource(CommonR.string.common_retry), items::retry) },
                        )
                    }
                }
                if (refresh is LoadState.NotLoading && items.itemCount == 0) {
                    item { EmptyState(stringResource(R.string.history_empty_title), stringResource(R.string.history_empty_message), icon = OmniIcons.History) }
                }
                items(items.itemCount, key = items.itemKey { it.key() }, contentType = items.itemContentType { it::class }) { index ->
                    when (val item = items[index]) {
                        is HistoryItem.Header -> DayHeader(item.dayStartEpochMs)
                        is HistoryItem.Row -> SubmissionRow(item.submission, runtimeNames[item.submission.runtimeId]) {
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
        AdBanner()
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
            stringResource(R.string.history_stat_runs) to (stats?.totalJobs?.toString() ?: state.summary.total.toString()),
            stringResource(R.string.history_stat_accepted) to (stats?.let { stringResource(R.string.history_percent, (it.acceptanceRate * 100).roundToInt()) } ?: formatDuration(null)),
            stringResource(R.string.history_stat_median) to formatDuration(state.summary.medianTimeMs),
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
    val locale = LocalConfiguration.current.locales[0]
    val dayFormat = remember(locale) { SimpleDateFormat("EEEEE", locale) }
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
        Text(week.sumOf { it.runs }.let { pluralStringResource(R.plurals.history_week_total, it, it) }, style = OmniTheme.typography.bodySmall, color = colors.textTertiary, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun VerdictFilters(state: HistoryUiState, onSelect: (Verdict?) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OmniChip(stringResource(R.string.history_filter_all), state.filter == null, { onSelect(null) }, count = state.summary.total.toString())
        listOf(Verdict.AC, Verdict.WA, Verdict.TLE, Verdict.CE, Verdict.RE, Verdict.MLE, Verdict.IE).forEach { verdict ->
            OmniChip(verdict.code, state.filter == verdict, { onSelect(verdict) }, count = (state.summary.verdictCounts[verdict] ?: 0).toString())
        }
    }
}

@Composable
private fun DayHeader(dayStart: Long) {
    val locale = LocalConfiguration.current.locales[0]
    val label = when {
        DateUtils.isToday(dayStart) -> stringResource(R.string.history_today)
        DateUtils.isToday(dayStart + DateUtils.DAY_IN_MILLIS) -> stringResource(R.string.history_yesterday)
        else -> SimpleDateFormat("EEE d MMM", locale).format(Date(dayStart))
    }
    Text(
        label.uppercase(),
        style = OmniTheme.typography.overline,
        color = OmniTheme.colors.textTertiary,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun SubmissionRow(submission: Submission, runtimeName: RuntimeName?, onClick: () -> Unit) {
    val colors = OmniTheme.colors
    val locale = LocalConfiguration.current.locales[0]
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
            Text(
                runtimeName?.let { stringResource(R.string.history_runtime_name, it.language, it.version) } ?: submission.runtimeId,
                style = OmniTheme.typography.bodyStrong,
                color = colors.textPrimary,
            )
            Text(stringResource(R.string.history_job, shortJobId(submission.id)), style = OmniTheme.typography.monoSmall, color = colors.textTertiary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatDuration(submission.totalTimeMs), style = OmniTheme.typography.mono, color = colors.textSecondary)
            Text(
                SimpleDateFormat("HH:mm", locale).format(Date(submission.createdAt.toEpochMilliseconds())),
                style = OmniTheme.typography.monoSmall,
                color = colors.textTertiary,
            )
        }
    }
}
