package solutions.laxmi.omnicompiler.feature.languages

import solutions.laxmi.omnicompiler.core.ui.R as CommonR
import solutions.laxmi.omnicompiler.core.ui.labelRes
import solutions.laxmi.omnicompiler.core.ui.asString
import androidx.compose.ui.platform.LocalResources
import androidx.annotation.StringRes
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import solutions.laxmi.omnicompiler.core.designsystem.component.EmptyState
import solutions.laxmi.omnicompiler.core.designsystem.component.InfoBanner
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniBadge
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniCheckbox
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniChip
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniListRow
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniRadio
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniSpinner
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextField
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTopBar
import solutions.laxmi.omnicompiler.core.designsystem.component.SectionLabel
import solutions.laxmi.omnicompiler.core.designsystem.component.SheetHandle
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.model.Lane
import solutions.laxmi.omnicompiler.core.model.Language
import solutions.laxmi.omnicompiler.core.model.LanguageCategory
import solutions.laxmi.omnicompiler.core.model.Limits
import solutions.laxmi.omnicompiler.core.model.Runtime
import solutions.laxmi.omnicompiler.core.model.RuntimeStatus
import solutions.laxmi.omnicompiler.core.navigation.LanguagePickerRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.ui.LanguageTile
import kotlin.math.roundToInt

/** Design R1: full-screen language list with a runtime sheet for the chosen language. */
@Composable
fun LanguagePickerScreen(route: LanguagePickerRoute, navigator: Navigator) {
    val viewModel = hiltViewModel<LanguagePickerViewModel, LanguagePickerViewModel.Factory> { it.create(route.projectId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                LanguagePickerEvent.Done -> navigator.back()
                is LanguagePickerEvent.Message -> snackbar.showSnackbar(event.text.asString(resources))
            }
        }
    }
    val colors = OmniTheme.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            OmniTopBar(
                title = stringResource(R.string.languages_title),
                subtitle = stringResource(
                    R.string.languages_subtitle,
                    pluralStringResource(R.plurals.languages_count, state.languages.size, state.languages.size),
                    pluralStringResource(R.plurals.languages_runtime_count, state.totalRuntimes, state.totalRuntimes),
                ),
                onBack = navigator::back,
                navigationIcon = OmniIcons.Close,
            )
            OmniTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = stringResource(R.string.languages_search),
                leadingIcon = OmniIcons.Search,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            FilterChips(state.filter, viewModel::setFilter)
            state.refreshError?.let {
                InfoBanner(it.asString(), Modifier.padding(horizontal = 16.dp, vertical = 4.dp), icon = OmniIcons.WifiOff, action = { OmniTextButton(stringResource(CommonR.string.common_retry), viewModel::refresh) })
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { OmniSpinner() }
                state.languages.isEmpty() -> EmptyState(stringResource(R.string.languages_no_matches_title), stringResource(R.string.languages_no_matches_message), icon = OmniIcons.Search)
                else -> LazyColumn(Modifier.weight(1f)) {
                    if (state.recent.isNotEmpty()) {
                        item { SectionLabel(stringResource(R.string.languages_recent)) }
                        items(state.recent, key = { "recent-${it.base}" }) { LanguageRow(it, state.currentRuntimeId, viewModel::select) }
                        item { SectionLabel(stringResource(R.string.languages_all)) }
                    }
                    items(state.languages, key = { it.base }) { LanguageRow(it, state.currentRuntimeId, viewModel::select) }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
    state.selected?.let { language ->
        RuntimeSheet(
            language = language,
            currentRuntimeId = state.currentRuntimeId,
            defaultRuntimeId = state.defaultRuntimeId,
            limits = state.selectedLimits,
            onDismiss = viewModel::dismissSelection,
            onUse = viewModel::use,
        )
    }
}

@Composable
private fun FilterChips(selected: LanguageFilter, onSelect: (LanguageFilter) -> Unit) {
    val options = listOf<Pair<String, LanguageFilter>>(
        stringResource(R.string.languages_filter_all) to LanguageFilter.All,
        stringResource(R.string.languages_filter_recent) to LanguageFilter.Recent,
    ) + LanguageCategory.entries.map { stringResource(it.labelRes) to LanguageFilter.Category(it) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (label, filter) -> OmniChip(label, selected == filter, onClick = { onSelect(filter) }) }
    }
}

@Composable
private fun LanguageRow(language: Language, currentRuntimeId: String?, onSelect: (Language) -> Unit) {
    val colors = OmniTheme.colors
    val isCurrent = language.runtimes.any { it.id == currentRuntimeId }
    val subtitle = listOfNotNull(
        pluralStringResource(R.plurals.languages_runtime_count, language.runtimes.size, language.runtimes.size),
        language.defaultRuntime?.id,
        language.acceptanceRate?.let { stringResource(R.string.languages_accepted, (it * 100).roundToInt()) },
    ).joinToString(" · ")
    OmniListRow(
        title = language.info.name,
        subtitle = subtitle,
        selected = isCurrent,
        leading = { LanguageTile(language.info.shortCode, selected = isCurrent) },
        trailing = {
            if (language.runtimes.any { it.lane == Lane.HOT }) OmniBadge(stringResource(R.string.languages_fast), content = colors.textSecondary)
        },
        onClick = { onSelect(language) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuntimeSheet(
    language: Language,
    currentRuntimeId: String?,
    defaultRuntimeId: String?,
    limits: Map<String, Limits>,
    onDismiss: () -> Unit,
    onUse: (Runtime, Boolean) -> Unit,
) {
    val colors = OmniTheme.colors
    val initial = language.runtimes.firstOrNull { it.id == currentRuntimeId } ?: language.defaultRuntime
    var chosenId by rememberSaveable(language.base) { mutableStateOf(initial?.id) }
    var makeDefault by rememberSaveable(language.base) { mutableStateOf(false) }
    val chosen = language.runtimes.firstOrNull { it.id == chosenId }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RectangleShape,
        containerColor = colors.surface,
        scrimColor = colors.scrim,
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.languages_runtimes_title, language.info.name), style = OmniTheme.typography.title, color = colors.textPrimary)
                Text(pluralStringResource(R.plurals.languages_available, language.runtimes.size, language.runtimes.size), style = OmniTheme.typography.bodySmall, color = colors.textTertiary)
                language.info.tagline?.let { Text(it, style = OmniTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(top = 4.dp)) }
            }
            language.runtimes.forEach { runtime ->
                val selectable = runtime.isRunnable
                Row(
                    Modifier
                        .fillMaxWidth()
                        .alpha(if (runtime.status == RuntimeStatus.DEPRECATED || !selectable) 0.5f else 1f)
                        .clickable(enabled = selectable, role = Role.RadioButton) { chosenId = runtime.id }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OmniRadio(runtime.id == chosenId)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.languages_runtime_name, language.info.name, runtime.version), style = OmniTheme.typography.bodyStrong, color = colors.textPrimary)
                        Text(runtime.id, style = OmniTheme.typography.mono, color = colors.textTertiary)
                    }
                    OmniBadge(stringResource(runtime.statusTag(isDefault = runtime.id == defaultRuntimeId)))
                }
            }
            chosen?.let { runtime ->
                val lim = limits[runtime.id] ?: Limits.Default
                Row(Modifier.fillMaxWidth().padding(16.dp).background(colors.background).padding(12.dp)) {
                    LimitCell(stringResource(R.string.languages_limit_time), stringResource(R.string.languages_time_value, lim.timeMs), Modifier.weight(1f))
                    LimitCell(stringResource(R.string.languages_limit_memory), stringResource(R.string.languages_memory_value, lim.memMb), Modifier.weight(1f))
                    LimitCell(stringResource(R.string.languages_limit_lane), stringResource(if (runtime.lane == Lane.HOT) R.string.languages_lane_hot else R.string.languages_lane_cold), Modifier.weight(1f))
                }
                OmniCheckbox(
                    checked = makeDefault,
                    onCheckedChange = { makeDefault = it },
                    label = { Text(stringResource(R.string.languages_make_default), style = OmniTheme.typography.body, color = colors.textSecondary) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                OmniButton(
                    text = stringResource(R.string.languages_use, language.info.name, runtime.version),
                    onClick = { onUse(runtime, makeDefault) },
                    trailingIcon = OmniIcons.Check,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun LimitCell(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = OmniTheme.typography.overline, color = OmniTheme.colors.textTertiary)
        Text(value, style = OmniTheme.typography.mono, color = OmniTheme.colors.textPrimary)
    }
}

@StringRes
private fun Runtime.statusTag(isDefault: Boolean): Int = when {
    isDefault -> R.string.languages_status_default
    status == RuntimeStatus.DEPRECATED -> R.string.languages_status_deprecated
    status == RuntimeStatus.BUILDING -> R.string.languages_status_building
    !available || status == RuntimeStatus.FAILED -> R.string.languages_status_unavailable
    else -> R.string.languages_status_ready
}
