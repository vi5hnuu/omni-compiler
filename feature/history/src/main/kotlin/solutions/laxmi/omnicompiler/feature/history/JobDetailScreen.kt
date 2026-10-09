package solutions.laxmi.omnicompiler.feature.history

import solutions.laxmi.omnicompiler.core.model.JobStatus
import solutions.laxmi.omnicompiler.core.ui.clipForDisplay
import solutions.laxmi.omnicompiler.core.ui.labelRes
import solutions.laxmi.omnicompiler.core.ui.formatDuration
import solutions.laxmi.omnicompiler.core.ui.asString
import androidx.compose.ui.platform.LocalResources
import solutions.laxmi.omnicompiler.core.ui.R as CommonR
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniSnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import solutions.laxmi.omnicompiler.core.common.shortJobId
import solutions.laxmi.omnicompiler.core.designsystem.component.EmptyState
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButtonStyle
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniIconButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniSpinner
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTopBar
import solutions.laxmi.omnicompiler.core.designsystem.component.SectionLabel
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.model.EditorSettings
import solutions.laxmi.omnicompiler.core.model.TestResult
import solutions.laxmi.omnicompiler.core.editor.CodeEditor
import solutions.laxmi.omnicompiler.core.editor.EditorDocument
import solutions.laxmi.omnicompiler.core.editor.rememberCodeEditorState
import solutions.laxmi.omnicompiler.core.navigation.EditorRoute
import solutions.laxmi.omnicompiler.core.navigation.JobDetailRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.ui.VerdictBadge

@Composable
fun JobDetailScreen(route: JobDetailRoute, navigator: Navigator) {
    val viewModel = hiltViewModel<JobDetailViewModel, JobDetailViewModel.Factory> { it.create(route) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is JobDetailEvent.OpenProject -> navigator.resetTo(EditorRoute(event.projectId))
                is JobDetailEvent.Message -> snackbar.showSnackbar(event.text.asString(resources))
            }
        }
    }
    val colors = OmniTheme.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            val runtimeName by viewModel.runtimeName.collectAsStateWithLifecycle()
            OmniTopBar(
                stringResource(R.string.job_title, shortJobId(route.jobId)),
                onBack = navigator::back,
                subtitle = runtimeName?.let { stringResource(R.string.history_runtime_name, it.language, it.version) } ?: route.runtimeId,
            ) {
                OmniIconButton(OmniIcons.Copy, stringResource(R.string.job_copy_id), { clipboard.setText(AnnotatedString(route.jobId)) })
            }
            val job = state.job
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { OmniSpinner() }
                job == null -> EmptyState(stringResource(R.string.job_load_failed), state.error?.asString().orEmpty(), icon = OmniIcons.Alert, action = { OmniButton(stringResource(CommonR.string.common_retry), viewModel::load) })
                else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        job.verdict?.let { VerdictBadge(it) }
                        Text(job.verdict?.let { stringResource(it.labelRes) } ?: stringResource(job.status.labelRes()), style = OmniTheme.typography.title, color = colors.textPrimary, modifier = Modifier.weight(1f))
                        Text(formatDuration(job.totalTimeMs), style = OmniTheme.typography.mono, color = colors.textSecondary)
                    }
                    job.code?.let { code ->
                        SectionLabel(stringResource(R.string.job_code))
                        val editorState = rememberCodeEditorState()
                        CodeEditor(
                            state = editorState,
                            document = EditorDocument(route.jobId, 0, code, "code", route.runtimeId?.substringBefore('-'), isEntry = true),
                            settings = EditorSettings(minimap = false),
                            onTextChange = { _, _ -> },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
                        )
                    }
                    if (job.results.isNotEmpty()) SectionLabel(stringResource(R.string.job_tests))
                    job.results.forEach { ResultCard(it) }
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OmniButton(stringResource(R.string.job_verify), viewModel::verify, Modifier.weight(1f), style = OmniButtonStyle.Secondary, loading = state.verifying, leadingIcon = OmniIcons.ShieldCheck, trailingIcon = null)
                        OmniButton(stringResource(R.string.job_open_as_project), viewModel::openAsProject, Modifier.weight(1f), enabled = job.code != null, trailingIcon = OmniIcons.ArrowRight)
                    }
                    state.proof?.let { proof ->
                        SectionLabel(stringResource(R.string.job_replay_proof))
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                stringResource(R.string.job_proof_image) to proof.imageHash,
                                stringResource(R.string.job_proof_code) to proof.codeHash,
                                stringResource(R.string.job_proof_input) to proof.inputHash,
                                stringResource(R.string.job_proof_signature) to proof.signature,
                                stringResource(R.string.job_proof_signed_at) to proof.signedAt,
                            )
                                .forEach { (label, value) ->
                                    Text(label.uppercase(), style = OmniTheme.typography.overline, color = colors.textTertiary)
                                    Text(value, style = OmniTheme.typography.mono, color = colors.textPrimary)
                                }
                        }
                    }
                }
            }
        }
        OmniSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ResultCard(result: TestResult) {
    val colors = OmniTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).background(colors.surfaceRaised).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.job_index, result.index), style = OmniTheme.typography.mono, color = colors.textTertiary)
            VerdictBadge(result.verdict)
            Box(Modifier.weight(1f))
            Text(formatDuration(result.timeMs), style = OmniTheme.typography.monoSmall, color = colors.textTertiary)
        }
        listOf(
            R.string.job_stdin to result.stdin,
            R.string.job_expected to result.expected,
            R.string.job_stdout to result.stdout,
            R.string.job_stderr to result.stderr,
        ).forEach { (labelRes, value) ->
            if (!value.isNullOrEmpty()) {
                val isError = labelRes == R.string.job_stderr
                Text(stringResource(labelRes).uppercase(), style = OmniTheme.typography.overline, color = if (isError) colors.accentText else colors.textTertiary)
                // Very long output shows its beginning; the full text is in the console of the run that produced it.
                val shown = remember(value) { clipForDisplay(value.trimEnd())?.let { it + "\n…" } ?: value.trimEnd() }
                Text(shown, style = OmniTheme.typography.mono, color = colors.textPrimary, modifier = Modifier.horizontalScroll(rememberScrollState()))
            }
        }
    }
}

@androidx.annotation.StringRes
private fun JobStatus.labelRes(): Int = when (this) {
    JobStatus.PENDING -> R.string.job_status_pending
    JobStatus.RUNNING -> R.string.job_status_running
    JobStatus.DONE -> R.string.job_status_done
    JobStatus.FAILED -> R.string.job_status_failed
}
