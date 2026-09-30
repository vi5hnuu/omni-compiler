package solutions.laxmi.omnicompiler.feature.vcs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import solutions.laxmi.omnicompiler.core.data.git.GitRepository
import solutions.laxmi.omnicompiler.core.designsystem.component.EmptyState
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniBadge
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButtonStyle
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniListRow
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextField
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTopBar
import solutions.laxmi.omnicompiler.core.designsystem.component.SectionLabel
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.model.FileChange
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.SourceStatus
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.navigation.SourceControlRoute
import solutions.laxmi.omnicompiler.core.ui.UiText
import solutions.laxmi.omnicompiler.core.ui.asString
import solutions.laxmi.omnicompiler.core.ui.toUiText

data class SourceControlUiState(val status: SourceStatus? = null, val working: Boolean = false)

@HiltViewModel(assistedFactory = SourceControlViewModel.Factory::class)
class SourceControlViewModel @AssistedInject constructor(
    @Assisted private val route: SourceControlRoute,
    private val git: GitRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: SourceControlRoute): SourceControlViewModel
    }

    private val working = MutableStateFlow(false)
    private val messages = Channel<UiText>(Channel.BUFFERED)
    val messageFlow = messages.receiveAsFlow()

    val uiState: StateFlow<SourceControlUiState> = combine(git.observeStatus(route.projectId), working, ::SourceControlUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SourceControlUiState())

    fun commitAndPush(message: String, onDone: () -> Unit) = run(success = UiText.Res(R.string.git_pushed)) {
        git.commitAndPush(route.projectId, message).also { if (it is Outcome.Success) onDone() }
    }

    fun pull() {
        if (working.value) return
        viewModelScope.launch {
            working.value = true
            val text = when (val result = git.pull(route.projectId)) {
                is Outcome.Success -> when {
                    result.value.conflicts.isNotEmpty() -> UiText.Plural(R.plurals.git_pulled_conflicts, result.value.conflicts.size, result.value.conflicts.size)
                    result.value.updated == 0 -> UiText.Res(R.string.git_up_to_date)
                    else -> UiText.Plural(R.plurals.git_pulled, result.value.updated, result.value.updated)
                }
                is Outcome.Failure -> result.error.toUiText()
            }
            messages.send(text)
            working.value = false
        }
    }

    fun resolve(fileName: String, keepMine: Boolean) = run(success = null) { git.resolve(route.projectId, fileName, keepMine) }

    private fun run(success: UiText?, block: suspend () -> Outcome<Unit>) {
        if (working.value) return
        viewModelScope.launch {
            working.value = true
            when (val result = block()) {
                is Outcome.Success -> success?.let { messages.send(it) }
                is Outcome.Failure -> messages.send(result.error.toUiText())
            }
            working.value = false
        }
    }
}

/** Source control for a project imported from GitHub/GitLab: changes, conflicts, commit & push, pull. */
@Composable
fun SourceControlScreen(route: SourceControlRoute, navigator: Navigator) {
    val viewModel = hiltViewModel<SourceControlViewModel, SourceControlViewModel.Factory>(key = route.toString()) { it.create(route) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val colors = OmniTheme.colors
    var message by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(viewModel) { viewModel.messageFlow.collect { snackbar.showSnackbar(it.asString(resources)) } }
    val status = state.status
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            OmniTopBar(
                title = stringResource(R.string.git_source_control),
                subtitle = status?.remote?.let { "${it.repoName} · ${it.branch}" + if (it.path.isNotEmpty()) " · /${it.path}" else "" },
                onBack = navigator::back,
            ) {
                OmniTextButton(stringResource(R.string.git_pull), viewModel::pull, Modifier.padding(end = 12.dp), enabled = !state.working && status != null)
            }
            if (status == null) {
                EmptyState(stringResource(R.string.git_not_tracked_title), stringResource(R.string.git_not_tracked_message), Modifier.weight(1f), icon = OmniIcons.Link)
                return@Column
            }
            LazyColumn(Modifier.weight(1f)) {
                if (status.remote.conflicts.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.git_conflicts)) }
                    items(status.remote.conflicts.sorted()) { name ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(name, style = OmniTheme.typography.mono, color = colors.status.limit.text)
                            Text(stringResource(R.string.git_conflict_note), style = OmniTheme.typography.bodySmall, color = colors.textTertiary)
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                OmniTextButton(stringResource(R.string.git_keep_mine), { viewModel.resolve(name, keepMine = true) }, enabled = !state.working)
                                OmniTextButton(stringResource(R.string.git_take_theirs), { viewModel.resolve(name, keepMine = false) }, enabled = !state.working, color = colors.textSecondary)
                            }
                        }
                    }
                }
                item { SectionLabel(stringResource(R.string.git_changes)) }
                if (status.changes.isEmpty()) {
                    item { Text(stringResource(R.string.git_no_changes), style = OmniTheme.typography.bodySmall, color = colors.textTertiary, modifier = Modifier.padding(horizontal = 16.dp)) }
                }
                items(status.changes, key = { it.name }) { change ->
                    OmniListRow(title = change.name, trailing = { ChangeBadge(change.kind) })
                }
            }
            OmniTextField(
                value = message,
                onValueChange = { message = it },
                placeholder = stringResource(R.string.git_commit_message),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            OmniButton(
                stringResource(R.string.git_commit_push),
                { viewModel.commitAndPush(message) { message = "" } },
                Modifier.padding(16.dp),
                enabled = status.changes.isNotEmpty() && status.remote.conflicts.isEmpty() && !state.working,
                loading = state.working,
                leadingIcon = OmniIcons.Upload,
                trailingIcon = null,
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 140.dp))
    }
}

@Composable
private fun ChangeBadge(kind: FileChange.Kind) {
    val tone = when (kind) {
        FileChange.Kind.Added -> OmniTheme.colors.status.accepted
        FileChange.Kind.Modified -> OmniTheme.colors.status.limit
        FileChange.Kind.Deleted -> OmniTheme.colors.status.rejected
    }
    val label = when (kind) {
        FileChange.Kind.Added -> "A"
        FileChange.Kind.Modified -> "M"
        FileChange.Kind.Deleted -> "D"
    }
    OmniBadge(label, container = tone.fill, content = tone.onFill)
}
