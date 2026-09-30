package solutions.laxmi.omnicompiler.feature.vcs

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import solutions.laxmi.omnicompiler.core.designsystem.component.EmptyState
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniBadge
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniListRow
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniSpinner
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTab
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTabRow
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextField
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTopBar
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.navigation.EditorRoute
import solutions.laxmi.omnicompiler.core.navigation.GitAccountsRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.ui.asString

/** Pick a repository, branch and folder, and import that folder's files as a project that tracks it. */
@Composable
fun RepoImportScreen(navigator: Navigator) {
    val viewModel = hiltViewModel<RepoImportViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = OmniTheme.colors
    LaunchedEffect(viewModel) { viewModel.openedFlow.collect { navigator.resetTo(EditorRoute(it)) } }
    BackHandler(enabled = state.browse != null) { viewModel.up() }
    val browse = state.browse
    Column(Modifier.fillMaxSize().background(colors.background).navigationBarsPadding()) {
        OmniTopBar(
            title = browse?.repo?.fullName ?: stringResource(R.string.git_import_title),
            subtitle = browse?.let { "/" + it.path },
            onBack = { if (!viewModel.up()) navigator.back() },
        )
        if (state.hosts.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.git_no_accounts_title),
                message = stringResource(R.string.git_no_accounts_message),
                modifier = Modifier.weight(1f),
                icon = OmniIcons.Link,
                action = { OmniButton(stringResource(R.string.git_accounts_title), { navigator.navigate(GitAccountsRoute) }) },
            )
            return@Column
        }
        if (browse == null) {
            if (state.hosts.size > 1) {
                OmniTabRow(state.hosts.map { OmniTab(it.displayName()) }, state.hosts.indexOf(state.host), { viewModel.selectHost(state.hosts[it]) })
            }
            OmniTextField(
                state.query, viewModel::setQuery, placeholder = stringResource(R.string.git_search_repos), leadingIcon = OmniIcons.Search,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.weight(1f)) {
                items(state.visibleRepos, key = { it.host.name + it.id }) { repo ->
                    OmniListRow(
                        title = repo.fullName,
                        subtitle = repo.defaultBranch,
                        trailing = { if (repo.isPrivate) OmniBadge(stringResource(R.string.git_private)) },
                        onClick = { viewModel.openRepo(repo) },
                    )
                }
                if (state.canLoadMore && state.query.isBlank()) {
                    item { OmniTextButton(stringResource(R.string.git_load_more), viewModel::loadMore, Modifier.padding(16.dp), enabled = !state.loading) }
                }
            }
        } else {
            BranchBar(browse.branches.ifEmpty { listOf(browse.branch) }, browse.branch, viewModel::selectBranch)
            val files = browse.entries.count { !it.isFolder }
            LazyColumn(Modifier.weight(1f)) {
                items(browse.entries, key = { it.path }) { entry ->
                    OmniListRow(
                        title = entry.name,
                        leading = { Icon(if (entry.isFolder) OmniIcons.Folder else OmniIcons.File, null, tint = colors.textTertiary, modifier = Modifier.size(16.dp)) },
                        trailing = if (entry.isFolder) ({ Icon(OmniIcons.ChevronRight, null, tint = colors.textTertiary, modifier = Modifier.size(14.dp)) }) else null,
                        onClick = if (entry.isFolder) ({ viewModel.showFolder(entry.path) }) else null,
                    )
                }
            }
            Text(
                stringResource(R.string.git_import_note),
                style = OmniTheme.typography.bodySmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            OmniButton(
                if (files > 0) pluralStringResource(R.plurals.git_import_folder, files, files) else stringResource(R.string.git_import_empty),
                viewModel::importCurrent,
                Modifier.padding(16.dp),
                enabled = files > 0 && !state.importing,
                loading = state.importing,
                leadingIcon = OmniIcons.Download,
                trailingIcon = null,
            )
        }
        state.error?.let { Text(it.asString(), style = OmniTheme.typography.bodySmall, color = colors.accentText, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
        if (state.loading) Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { OmniSpinner() }
    }
}

@Composable
private fun BranchBar(branches: List<String>, selected: String, onSelect: (String) -> Unit) {
    val colors = OmniTheme.colors
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.git_branch), style = OmniTheme.typography.label, color = colors.textTertiary)
        Box {
            OmniTextButton(selected, { open = true })
            DropdownMenu(open, { open = false }, containerColor = colors.surfaceRaised, shape = RectangleShape) {
                branches.forEach { branch ->
                    DropdownMenuItem(text = { Text(branch, style = OmniTheme.typography.mono) }, onClick = {
                        open = false
                        onSelect(branch)
                    })
                }
            }
        }
    }
}
