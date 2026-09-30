package solutions.laxmi.omnicompiler.feature.history

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
import androidx.compose.material3.SnackbarHost
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
import solutions.laxmi.omnicompiler.core.common.formatMillis
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
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is JobDetailEvent.OpenProject -> navigator.resetTo(EditorRoute(event.projectId))
                is JobDetailEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }
    val colors = OmniTheme.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            OmniTopBar("Job ${shortJobId(route.jobId)}", onBack = navigator::back, subtitle = route.runtimeId) {
                OmniIconButton(OmniIcons.Copy, "Copy job id", { clipboard.setText(AnnotatedString(route.jobId)) })
            }
            val job = state.job
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { OmniSpinner() }
                job == null -> EmptyState("Couldn't load this run", state.error.orEmpty(), icon = OmniIcons.Alert, action = { OmniButton("Retry", viewModel::load) })
                else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        job.verdict?.let { VerdictBadge(it) }
                        Text(job.verdict?.label ?: job.status.name.lowercase(), style = OmniTheme.typography.title, color = colors.textPrimary, modifier = Modifier.weight(1f))
                        Text(formatMillis(job.totalTimeMs), style = OmniTheme.typography.mono, color = colors.textSecondary)
                    }
                    job.code?.let { code ->
                        SectionLabel("Code")
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
                    if (job.results.isNotEmpty()) SectionLabel("Tests")
                    job.results.forEach { ResultCard(it) }
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OmniButton("Verify run", viewModel::verify, Modifier.weight(1f), style = OmniButtonStyle.Secondary, loading = state.verifying, leadingIcon = OmniIcons.ShieldCheck, trailingIcon = null)
                        OmniButton("Open as project", viewModel::openAsProject, Modifier.weight(1f), enabled = job.code != null, trailingIcon = OmniIcons.ArrowRight)
                    }
                    state.proof?.let { proof ->
                        SectionLabel("Replay proof")
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Runtime image" to proof.imageHash, "Code" to proof.codeHash, "Input" to proof.inputHash, "Signature" to proof.signature, "Signed at" to proof.signedAt)
                                .forEach { (label, value) ->
                                    Text(label.uppercase(), style = OmniTheme.typography.overline, color = colors.textTertiary)
                                    Text(value, style = OmniTheme.typography.mono, color = colors.textPrimary)
                                }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
private fun ResultCard(result: TestResult) {
    val colors = OmniTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).background(colors.surfaceRaised).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("#${result.index}", style = OmniTheme.typography.mono, color = colors.textTertiary)
            VerdictBadge(result.verdict)
            Box(Modifier.weight(1f))
            Text(formatMillis(result.timeMs), style = OmniTheme.typography.monoSmall, color = colors.textTertiary)
        }
        listOf("stdin" to result.stdin, "expected" to result.expected, "stdout" to result.stdout, "stderr" to result.stderr).forEach { (label, value) ->
            if (!value.isNullOrEmpty()) {
                Text(label.uppercase(), style = OmniTheme.typography.overline, color = if (label == "stderr") colors.accentText else colors.textTertiary)
                Text(value.trimEnd(), style = OmniTheme.typography.mono, color = colors.textPrimary, modifier = Modifier.horizontalScroll(rememberScrollState()))
            }
        }
    }
}
