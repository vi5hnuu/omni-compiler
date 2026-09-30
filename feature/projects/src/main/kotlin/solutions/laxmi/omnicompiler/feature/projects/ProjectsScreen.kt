package solutions.laxmi.omnicompiler.feature.projects

import androidx.activity.compose.LocalActivity
import solutions.laxmi.omnicompiler.core.ads.LocalAds
import solutions.laxmi.omnicompiler.core.ads.AdBanner
import solutions.laxmi.omnicompiler.core.navigation.RepoImportRoute
import solutions.laxmi.omnicompiler.core.model.ProjectIssue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import solutions.laxmi.omnicompiler.core.ui.NewProjectSheet
import solutions.laxmi.omnicompiler.core.ui.formatAge
import solutions.laxmi.omnicompiler.core.ui.asString
import androidx.compose.ui.platform.LocalResources
import androidx.annotation.StringRes
import solutions.laxmi.omnicompiler.core.ui.R as CommonR
import androidx.compose.ui.res.stringResource
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(navigator: Navigator) {
    val viewModel = hiltViewModel<ProjectsViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    var searching by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ProjectRowUi?>(null) }
    var deleting by remember { mutableStateOf<ProjectRowUi?>(null) }
    var menu by remember { mutableStateOf(false) }
    val ads = LocalAds.current
    val activity = LocalActivity.current
    // The system pickers reach device storage and cloud providers (Drive, OneDrive, Dropbox) alike.
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.importFolder(uri.toString())
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFile(uri.toString())
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is ProjectsEvent.Open -> navigator.resetTo(EditorRoute(event.projectId))
                is ProjectsEvent.Share -> context.shareFile(event.file.uri, event.file.mimeType, resources.getString(R.string.projects_share_title, event.file.displayName))
                is ProjectsEvent.Message -> snackbar.showSnackbar(event.text.asString(resources))
            }
        }
    }
    val colors = OmniTheme.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            OmniTopBar(stringResource(R.string.projects_title), onBack = navigator::back) {
                OmniIconButton(OmniIcons.Search, stringResource(R.string.projects_search), { searching = !searching }, selected = searching)
                Box {
                    OmniIconButton(OmniIcons.MoreVertical, stringResource(R.string.projects_more), { menu = true })
                    DropdownMenu(menu, { menu = false }, containerColor = colors.surfaceRaised, shape = RectangleShape) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.projects_import_folder)) }, onClick = { menu = false; pickFolder.launch(null) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.projects_open_file)) }, onClick = { menu = false; pickFile.launch(arrayOf("*/*")) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.projects_import_git)) }, onClick = { menu = false; navigator.navigate(RepoImportRoute) })
                    }
                }
            }
            if (searching) {
                OmniTextField(
                    state.query, viewModel::setQuery, placeholder = stringResource(R.string.projects_search), leadingIcon = OmniIcons.Search,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            val filters = ProjectFilter.entries
            OmniTabRow(
                tabs = filters.map { OmniTab(stringResource(it.labelRes()), badge = state.counts[it]?.toString()) },
                selectedIndex = filters.indexOf(state.filter),
                onSelect = { viewModel.setFilter(filters[it]) },
            )
            if (state.projects.isEmpty()) {
                EmptyState(
                    title = stringResource(if (state.query.isBlank()) R.string.projects_empty_title else R.string.projects_no_matches),
                    message = stringResource(R.string.projects_empty_message),
                    icon = OmniIcons.Folder,
                    modifier = Modifier.weight(1f),
                )
            } else {
                PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.weight(1f)) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.projects, key = { it.summary.project.id }) { row ->
                        ProjectRow(
                            row = row,
                            age = formatAge(row.summary.project.updatedAt, state.now),
                            current = row.summary.project.id == state.currentProjectId,
                            onOpen = {
                                // Opening a different project is a natural break for a (rate-limited) interstitial.
                                if (row.summary.project.id != state.currentProjectId) activity?.let { ads.onNaturalBreak(it) }
                                navigator.resetTo(EditorRoute(row.summary.project.id))
                            },
                            onRename = { renaming = row },
                            onDuplicate = { viewModel.duplicate(row.summary.project.id) },
                            onExport = { viewModel.export(row.summary.project.id) },
                            onDelete = { deleting = row },
                        )
                    }
                }
                }
            }
            OmniButton(stringResource(R.string.projects_new), { creating = true }, Modifier.padding(16.dp), leadingIcon = OmniIcons.Plus, trailingIcon = null)
            AdBanner()
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
        TextPromptDialog(stringResource(R.string.projects_rename_title), row.summary.project.name, onDismiss = { renaming = null }) {
            viewModel.rename(row.summary.project.id, it)
            renaming = null
        }
    }
    deleting?.let { row ->
        ConfirmPrompt(
            title = stringResource(R.string.projects_delete_title, row.summary.project.name),
            message = stringResource(R.string.projects_delete_message),
            confirm = stringResource(CommonR.string.common_delete),
            onDismiss = { deleting = null },
        ) {
            viewModel.delete(row.summary.project.id)
            deleting = null
        }
    }
}

@StringRes
private fun ProjectFilter.labelRes() = when (this) {
    ProjectFilter.ALL -> R.string.projects_filter_all
    ProjectFilter.MULTI_FILE -> R.string.projects_filter_multi_file
    ProjectFilter.SCRATCH -> R.string.projects_filter_scratch
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
                // What the last folder check found, e.g. a skipped binary file or a rebuilt manifest.
                project.issues.firstOrNull()?.let { issue ->
                    val more = project.issues.size - 1
                    Text(
                        issueText(issue) + if (more > 0) " " + stringResource(R.string.projects_issue_more, more) else "",
                        style = OmniTheme.typography.bodySmall,
                        color = colors.status.limit.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(age, style = OmniTheme.typography.monoSmall, color = colors.textTertiary)
            OmniIconButton(OmniIcons.MoreVertical, stringResource(R.string.projects_actions), { menu = true }, iconSize = 15.dp)
        }
        DropdownMenu(menu, { menu = false }, containerColor = colors.surfaceRaised, shape = RectangleShape) {
            listOf(
                R.string.projects_open to onOpen,
                R.string.projects_rename to onRename,
                R.string.projects_duplicate to onDuplicate,
                R.string.projects_share_zip to onExport,
                CommonR.string.common_delete to onDelete,
            ).forEach { (labelRes, action) ->
                val destructive = labelRes == CommonR.string.common_delete
                DropdownMenuItem(
                    text = { Text(stringResource(labelRes), style = OmniTheme.typography.bodyStrong, color = if (destructive) colors.accentText else colors.textPrimary) },
                    onClick = {
                        menu = false
                        action()
                    },
                )
            }
        }
    }
}

@Composable
private fun issueText(issue: ProjectIssue): String = when (issue) {
    ProjectIssue.Imported -> stringResource(R.string.projects_issue_imported)
    ProjectIssue.ManifestRebuilt -> stringResource(R.string.projects_issue_manifest_rebuilt)
    ProjectIssue.DuplicateId -> stringResource(R.string.projects_issue_duplicate_id)
    ProjectIssue.EntryMissing -> stringResource(R.string.projects_issue_entry_missing)
    is ProjectIssue.UnknownRuntime -> stringResource(R.string.projects_issue_unknown_runtime, issue.runtimeId.ifBlank { "?" })
    is ProjectIssue.TooManyFiles -> stringResource(R.string.projects_issue_too_many_files, issue.limit)
    is ProjectIssue.FileSkipped -> stringResource(
        when (issue.reason) {
            ProjectIssue.SkipReason.TOO_LARGE -> R.string.projects_issue_skipped_large
            ProjectIssue.SkipReason.BINARY -> R.string.projects_issue_skipped_binary
            ProjectIssue.SkipReason.BAD_NAME -> R.string.projects_issue_skipped_name
            ProjectIssue.SkipReason.FOLDER -> R.string.projects_issue_skipped_folder
        },
        issue.name,
    )
}
