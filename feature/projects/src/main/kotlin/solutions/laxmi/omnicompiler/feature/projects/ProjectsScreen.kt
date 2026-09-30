package solutions.laxmi.omnicompiler.feature.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import solutions.laxmi.omnicompiler.core.common.formatRelative
import solutions.laxmi.omnicompiler.core.designsystem.component.EmptyState
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniIconButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTab
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTabRow
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextField
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTopBar
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.model.ProjectFilter
import solutions.laxmi.omnicompiler.core.navigation.EditorRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.ui.LanguageTile
import solutions.laxmi.omnicompiler.core.ui.VerdictBadge
import solutions.laxmi.omnicompiler.core.ui.shareFile

/** Design W3: all local projects with search, filters and per-project actions. */
@Composable
fun ProjectsScreen(navigator: Navigator) {
    val viewModel = hiltViewModel<ProjectsViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var searching by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ProjectRowUi?>(null) }
    var deleting by remember { mutableStateOf<ProjectRowUi?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is ProjectsEvent.Open -> navigator.resetTo(EditorRoute(event.projectId))
                is ProjectsEvent.Share -> context.shareFile(event.file.uri, event.file.mimeType, "Share ${event.file.displayName}")
                is ProjectsEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }
    val colors = OmniTheme.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            OmniTopBar("Projects", onBack = navigator::back) {
                OmniIconButton(OmniIcons.Search, "Search projects", { searching = !searching }, selected = searching)
            }
            if (searching) {
                OmniTextField(
                    state.query, viewModel::setQuery, placeholder = "Search projects", leadingIcon = OmniIcons.Search,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            val filters = ProjectFilter.entries
            OmniTabRow(
                tabs = filters.map { OmniTab(it.label(), badge = state.counts[it]?.toString()) },
                selectedIndex = filters.indexOf(state.filter),
                onSelect = { viewModel.setFilter(filters[it]) },
            )
            if (state.projects.isEmpty()) {
                EmptyState(
                    title = if (state.query.isBlank()) "No projects" else "No matches",
                    message = "Projects live on this device. Create one to start coding.",
                    icon = OmniIcons.Folder,
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(state.projects, key = { it.summary.project.id }) { row ->
                        ProjectRow(
                            row = row,
                            age = formatRelative(row.summary.project.updatedAt, state.now),
                            current = row.summary.project.id == state.currentProjectId,
                            onOpen = { navigator.resetTo(EditorRoute(row.summary.project.id)) },
                            onRename = { renaming = row },
                            onDuplicate = { viewModel.duplicate(row.summary.project.id) },
                            onExport = { viewModel.export(row.summary.project.id) },
                            onDelete = { deleting = row },
                        )
                    }
                }
            }
            OmniButton("New project", { creating = true }, Modifier.padding(16.dp), leadingIcon = OmniIcons.Plus, trailingIcon = null)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp))
    }
    if (creating) {
        NewProjectSheet(state.languages, onDismiss = { creating = false }) { name, language ->
            creating = false
            viewModel.create(name, language)
        }
    }
    renaming?.let { row ->
        TextPromptDialog("Rename project", row.summary.project.name, onDismiss = { renaming = null }) {
            viewModel.rename(row.summary.project.id, it)
            renaming = null
        }
    }
    deleting?.let { row ->
        ConfirmPrompt(
            title = "Delete ${row.summary.project.name}?",
            message = "Its files, tests and console history are removed from this device. Runs already sent stay in your run history.",
            confirm = "Delete",
            onDismiss = { deleting = null },
        ) {
            viewModel.delete(row.summary.project.id)
            deleting = null
        }
    }
}

private fun ProjectFilter.label() = when (this) {
    ProjectFilter.ALL -> "All"
    ProjectFilter.MULTI_FILE -> "Multi-file"
    ProjectFilter.SCRATCH -> "Scratch"
}

@Composable
private fun ProjectRow(
    row: ProjectRowUi,
    age: String,
    current: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = OmniTheme.colors
    var menu by remember { mutableStateOf(false) }
    val project = row.summary.project
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(role = Role.Button, onClick = onOpen, onLongClick = { menu = true })
                .drawBehind { drawLine(colors.hairline, Offset(0f, size.height), Offset(size.width, size.height), 1f) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LanguageTile(row.shortCode, selected = current)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(project.name, style = OmniTheme.typography.bodyStrong, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    project.lastVerdict?.let { VerdictBadge(it) }
                }
                Text(row.summary.fileNames.joinToString(" · "), style = OmniTheme.typography.monoSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(age, style = OmniTheme.typography.monoSmall, color = colors.textTertiary)
            OmniIconButton(OmniIcons.MoreVertical, "Project actions", { menu = true }, iconSize = 15.dp)
        }
        DropdownMenu(menu, { menu = false }, containerColor = colors.surfaceRaised, shape = RectangleShape) {
            listOf("Open" to onOpen, "Rename" to onRename, "Duplicate" to onDuplicate, "Share as .zip" to onExport, "Delete" to onDelete).forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label, style = OmniTheme.typography.bodyStrong, color = if (label == "Delete") colors.accentText else colors.textPrimary) },
                    onClick = {
                        menu = false
                        action()
                    },
                )
            }
        }
    }
}
