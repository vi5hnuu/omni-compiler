package solutions.laxmi.omnicompiler.feature.projects

import solutions.laxmi.omnicompiler.core.ui.testCount
import solutions.laxmi.omnicompiler.core.ui.labelRes
import solutions.laxmi.omnicompiler.core.ui.asString
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniBadge
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButtonStyle
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniChip
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniListRow
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniSpinner
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTab
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTabRow
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTopBar
import solutions.laxmi.omnicompiler.core.designsystem.component.SectionLabel
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.model.Difficulty
import solutions.laxmi.omnicompiler.core.model.Example
import solutions.laxmi.omnicompiler.core.model.Language
import solutions.laxmi.omnicompiler.core.navigation.EditorRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.navigation.ProblemRoute

/** Examples (language-independent stdin → stdout drills) and full Problems, as in the web playground. */
@Composable
fun ExamplesScreen(navigator: Navigator) {
    val viewModel = hiltViewModel<ExamplesViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var chosen by remember { mutableStateOf<Example?>(null) }
    var replacingWith by remember { mutableStateOf<Example?>(null) }
    PracticeEvents(viewModel.eventFlow, navigator, snackbar)
    val colors = OmniTheme.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            OmniTopBar(stringResource(R.string.practice_title), onBack = navigator::back)
            OmniTabRow(listOf(OmniTab(stringResource(R.string.practice_tab_examples), state.examples.size.toString()), OmniTab(stringResource(R.string.practice_tab_problems), state.problems.size.toString())), tab, { tab = it })
            LazyColumn(Modifier.weight(1f)) {
                if (tab == 0) {
                    items(state.examples, key = { it.id }) { example ->
                        OmniListRow(
                            title = example.title,
                            subtitle = example.statement,
                            trailing = { DifficultyBadge(example.difficulty) },
                            onClick = { chosen = example },
                        )
                    }
                } else {
                    items(state.problems, key = { it.slug }) { problem ->
                        OmniListRow(
                            title = problem.title,
                            subtitle = problem.tagline,
                            trailing = { DifficultyBadge(problem.difficulty) },
                            onClick = { navigator.navigate(ProblemRoute(problem.slug)) },
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
    chosen?.let { example ->
        ExampleSheet(
            example = example,
            onDismiss = { chosen = null },
            onUse = { newProject ->
                chosen = null
                // Using an example in the current project overwrites its tests, so that path asks first.
                if (newProject) viewModel.useExample(example, inNewProject = true) else replacingWith = example
            },
        )
    }
    replacingWith?.let { example ->
        ConfirmPrompt(
            title = stringResource(R.string.practice_replace_tests_title),
            message = stringResource(R.string.practice_replace_tests_message),
            confirm = stringResource(R.string.practice_replace_tests_confirm),
            onDismiss = { replacingWith = null },
            onConfirm = {
                replacingWith = null
                viewModel.useExample(example, inNewProject = false)
            },
        )
    }
}

@Composable
fun ProblemScreen(route: ProblemRoute, navigator: Navigator) {
    val viewModel = hiltViewModel<ProblemViewModel, ProblemViewModel.Factory> { it.create(route.slug) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pickedBase by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmReplace by rememberSaveable { mutableStateOf(false) }
    PracticeEvents(viewModel.eventFlow, navigator, snackbar)
    val colors = OmniTheme.colors
    val problem = state.problem
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            OmniTopBar(problem?.title ?: stringResource(R.string.practice_problem), onBack = navigator::back)
            if (problem == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { OmniSpinner() }
                return@Column
            }
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DifficultyBadge(problem.difficulty)
                        problem.tags.forEach { OmniBadge(it.uppercase()) }
                    }
                }
                item { Text(problem.tagline, style = OmniTheme.typography.bodyStrong, color = colors.textPrimary) }
                items(problem.statement) { paragraph -> Text(paragraph.replace("`", ""), style = OmniTheme.typography.body, color = colors.textSecondary) }
                if (problem.examples.isNotEmpty()) item { SectionLabel(stringResource(R.string.practice_examples), Modifier.padding(horizontal = 0.dp)) }
                items(problem.examples) { example ->
                    Column(Modifier.fillMaxWidth().background(colors.surfaceRaised).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.practice_input).uppercase(), style = OmniTheme.typography.overline, color = colors.textTertiary)
                        Text(example.input, style = OmniTheme.typography.mono, color = colors.textPrimary)
                        Text(stringResource(R.string.practice_output).uppercase(), style = OmniTheme.typography.overline, color = colors.textTertiary)
                        Text(example.output, style = OmniTheme.typography.mono, color = colors.textPrimary)
                        example.explanation?.let { Text(it, style = OmniTheme.typography.bodySmall, color = colors.textSecondary) }
                    }
                }
                if (problem.constraints.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.practice_constraints), Modifier.padding(horizontal = 0.dp)) }
                    items(problem.constraints) { Text("·  ${it.replace("`", "")}", style = OmniTheme.typography.mono, color = colors.textSecondary) }
                }
                item {
                    Text(stringResource(R.string.practice_starters, pluralStringResource(R.plurals.practice_languages, state.solutionLanguages.size, state.solutionLanguages.size), testCount(problem.tests.size)), style = OmniTheme.typography.bodySmall, color = colors.textTertiary)
                }
            }
            if (state.solutionLanguages.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.solutionLanguages.forEach { language ->
                        OmniChip(language.info.name, pickedBase == language.base, onClick = { pickedBase = language.base.takeIf { it != pickedBase } })
                    }
                }
            }
            val picked: Language? = state.solutionLanguages.firstOrNull { it.base == pickedBase }
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OmniButton(stringResource(R.string.practice_tests_only), { confirmReplace = true }, Modifier.weight(1f), style = OmniButtonStyle.Secondary, trailingIcon = null)
                OmniButton(picked?.let { stringResource(R.string.practice_solve_in, it.info.name) } ?: stringResource(R.string.practice_solve), { viewModel.solve(picked) }, Modifier.weight(1f), trailingIcon = OmniIcons.ArrowRight)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
    if (confirmReplace) {
        ConfirmPrompt(
            title = stringResource(R.string.practice_replace_tests_title),
            message = stringResource(R.string.practice_replace_tests_message),
            confirm = stringResource(R.string.practice_replace_tests_confirm),
            onDismiss = { confirmReplace = false },
            onConfirm = {
                confirmReplace = false
                viewModel.useTestsInCurrentProject()
            },
        )
    }
}

@Composable
private fun PracticeEvents(events: kotlinx.coroutines.flow.Flow<PracticeEvent>, navigator: Navigator, snackbar: SnackbarHostState) {
    val resources = LocalResources.current
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is PracticeEvent.Open -> navigator.resetTo(EditorRoute(event.projectId))
                is PracticeEvent.Message -> snackbar.showSnackbar(event.text.asString(resources))
            }
        }
    }
}

@Composable
private fun DifficultyBadge(difficulty: Difficulty) {
    val colors = OmniTheme.colors
    OmniBadge(
        stringResource(difficulty.labelRes).uppercase(),
        container = if (difficulty == Difficulty.HARD) colors.accent else colors.surfaceMuted,
        content = if (difficulty == Difficulty.HARD) colors.onAccent else colors.textSecondary,
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ExampleSheet(example: Example, onDismiss: () -> Unit, onUse: (newProject: Boolean) -> Unit) {
    val colors = OmniTheme.colors
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = androidx.compose.ui.graphics.RectangleShape,
        containerColor = colors.surface,
        scrimColor = colors.scrim,
        dragHandle = { solutions.laxmi.omnicompiler.core.designsystem.component.SheetHandle() },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(example.title, style = OmniTheme.typography.title, color = colors.textPrimary)
            Text(example.statement, style = OmniTheme.typography.body, color = colors.textSecondary)
            Text(pluralStringResource(R.plurals.practice_test_cases, example.tests.size, example.tests.size), style = OmniTheme.typography.mono, color = colors.textTertiary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OmniButton(stringResource(R.string.practice_use_in_current), { onUse(false) }, Modifier.weight(1f), style = OmniButtonStyle.Secondary, trailingIcon = null)
                OmniButton(stringResource(R.string.projects_new), { onUse(true) }, Modifier.weight(1f), trailingIcon = OmniIcons.Plus)
            }
        }
    }
}
