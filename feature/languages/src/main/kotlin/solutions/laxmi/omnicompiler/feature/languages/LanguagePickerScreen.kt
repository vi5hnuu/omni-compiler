package solutions.laxmi.omnicompiler.feature.languages

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
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                LanguagePickerEvent.Done -> navigator.back()
                is LanguagePickerEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }
    val colors = OmniTheme.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            OmniTopBar(
                title = "Language",
                subtitle = "${state.languages.size} languages · ${state.totalRuntimes} runtimes",
                onBack = navigator::back,
                navigationIcon = OmniIcons.Close,
            )
            OmniTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = "Search languages or runtimes",
                leadingIcon = OmniIcons.Search,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            FilterChips(state.filter, viewModel::setFilter)
            state.refreshError?.let {
                InfoBanner(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), icon = OmniIcons.WifiOff, action = { OmniTextButton("Retry", viewModel::refresh) })
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { OmniSpinner() }
                state.languages.isEmpty() -> EmptyState("No matches", "Try another name, version or runtime id.", icon = OmniIcons.Search)
                else -> LazyColumn(Modifier.weight(1f)) {
                    if (state.recent.isNotEmpty()) {
                        item { SectionLabel("Recent") }
                        items(state.recent, key = { "recent-${it.base}" }) { LanguageRow(it, state.currentRuntimeId, viewModel::select) }
                        item { SectionLabel("All languages") }
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
    val options = listOf<Pair<String, LanguageFilter>>("All" to LanguageFilter.All, "Recent" to LanguageFilter.Recent) +
        LanguageCategory.entries.map { it.label to LanguageFilter.Category(it) }
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
    val subtitle = buildList {
        add("${language.runtimes.size} runtime${if (language.runtimes.size == 1) "" else "s"}")
        language.defaultRuntime?.let { add(it.id) }
        language.acceptanceRate?.let { add("${(it * 100).roundToInt()}% accepted") }
    }.joinToString(" · ")
    OmniListRow(
        title = language.info.name,
        subtitle = subtitle,
        selected = isCurrent,
        leading = { LanguageTile(language.info.shortCode, selected = isCurrent) },
        trailing = {
            if (language.runtimes.any { it.lane == Lane.HOT }) OmniBadge("FAST", content = colors.textSecondary)
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
                Text("${language.info.name} runtimes", style = OmniTheme.typography.title, color = colors.textPrimary)
                Text("${language.runtimes.size} available", style = OmniTheme.typography.bodySmall, color = colors.textTertiary)
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
                        Text("${language.info.name} ${runtime.version}", style = OmniTheme.typography.bodyStrong, color = colors.textPrimary)
                        Text(runtime.id, style = OmniTheme.typography.mono, color = colors.textTertiary)
                    }
                    OmniBadge(runtime.statusTag(isDefault = runtime.id == defaultRuntimeId))
                }
            }
            chosen?.let { runtime ->
                val lim = limits[runtime.id] ?: Limits.Default
                Row(Modifier.fillMaxWidth().padding(16.dp).background(colors.background).padding(12.dp)) {
                    LimitCell("Time", "${lim.timeMs} ms", Modifier.weight(1f))
                    LimitCell("Memory", "${lim.memMb} MB", Modifier.weight(1f))
                    LimitCell("Lane", if (runtime.lane == Lane.HOT) "hot" else "cold", Modifier.weight(1f))
                }
                OmniCheckbox(
                    checked = makeDefault,
                    onCheckedChange = { makeDefault = it },
                    label = { Text("Default for new projects", style = OmniTheme.typography.body, color = colors.textSecondary) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                OmniButton(
                    text = "Use ${language.info.name} ${runtime.version}",
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

private fun Runtime.statusTag(isDefault: Boolean): String = when {
    isDefault -> "DEFAULT"
    status == RuntimeStatus.DEPRECATED -> "DEPRECATED"
    status == RuntimeStatus.BUILDING -> "BUILDING"
    !available || status == RuntimeStatus.FAILED -> "UNAVAILABLE"
    else -> "READY"
}
