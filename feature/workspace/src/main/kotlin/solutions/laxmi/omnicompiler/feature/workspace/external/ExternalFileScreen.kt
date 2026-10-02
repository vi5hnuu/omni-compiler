package solutions.laxmi.omnicompiler.feature.workspace.external

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import solutions.laxmi.omnicompiler.core.designsystem.component.EmptyState
import solutions.laxmi.omnicompiler.core.designsystem.component.InfoBanner
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniIconButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniSnackbarHost
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniSpinner
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTopBar
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.editor.CodeEditor
import solutions.laxmi.omnicompiler.core.editor.EditorDocument
import solutions.laxmi.omnicompiler.core.editor.rememberCodeEditorState
import solutions.laxmi.omnicompiler.core.navigation.EditorRoute
import solutions.laxmi.omnicompiler.core.navigation.ExternalFileRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.ui.asString
import solutions.laxmi.omnicompiler.feature.workspace.R
import solutions.laxmi.omnicompiler.feature.workspace.editor.FindReplaceBar

/**
 * A file opened from another app (a file manager's "Open with"), edited in place: changes are written back to that
 * file. Running needs a project, so "Open as project" copies it into one.
 */
@Composable
fun ExternalFileScreen(route: ExternalFileRoute, navigator: Navigator) {
    val viewModel = hiltViewModel<ExternalFileViewModel, ExternalFileViewModel.Factory>(key = route.uri) { it.create(route) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.editorSettings.collectAsStateWithLifecycle()
    val editorState = rememberCodeEditorState()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var searching by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is ExternalFileEvent.Message -> snackbar.showSnackbar(event.text.asString(resources))
                is ExternalFileEvent.OpenProject -> navigator.resetTo(EditorRoute(event.projectId))
            }
        }
    }
    BackHandler(enabled = searching) { searching = false }
    val colors = OmniTheme.colors
    val file = state.file
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            OmniTopBar(
                title = file?.name.orEmpty(),
                subtitle = file?.let {
                    stringResource(
                        when {
                            !it.writable -> R.string.external_read_only
                            state.save == SaveState.Saving -> R.string.external_saving
                            state.save == SaveState.Failed -> R.string.external_save_failed
                            else -> R.string.external_saved
                        },
                    )
                },
                onBack = {
                    editorState.flush()
                    navigator.back()
                },
            ) {
                if (file != null) {
                    OmniIconButton(OmniIcons.Search, stringResource(R.string.editor_menu_find_replace), { searching = !searching })
                    OmniTextButton(
                        stringResource(R.string.external_open_as_project),
                        {
                            editorState.flush()
                            viewModel.openAsProject()
                        },
                        enabled = !state.openingAsProject,
                    )
                }
            }
            when {
                state.loading -> Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) { OmniSpinner() }
                file == null -> EmptyState(
                    title = stringResource(R.string.external_open_failed),
                    message = state.loadError?.asString().orEmpty(),
                    modifier = Modifier.weight(1f),
                    icon = OmniIcons.File,
                )
                else -> {
                    if (!file.writable) {
                        InfoBanner(stringResource(R.string.external_read_only_message), Modifier.padding(16.dp), icon = OmniIcons.Lock)
                    }
                    if (searching) FindReplaceBar(editorState, startWithReplace = false, onClose = { searching = false })
                    CodeEditor(
                        state = editorState,
                        document = EditorDocument(file.uri, 0, file.text, file.name, languageBase = null, isEntry = false),
                        settings = settings,
                        onTextChange = { _, text -> viewModel.onTextChanged(text) },
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        readOnly = !file.writable,
                    )
                }
            }
        }
        OmniSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
