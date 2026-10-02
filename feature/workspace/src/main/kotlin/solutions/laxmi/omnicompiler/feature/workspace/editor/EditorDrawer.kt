package solutions.laxmi.omnicompiler.feature.workspace.editor

import androidx.annotation.StringRes
import solutions.laxmi.omnicompiler.feature.workspace.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniBadge
import solutions.laxmi.omnicompiler.core.designsystem.component.SectionLabel
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.model.ProjectSummary
import solutions.laxmi.omnicompiler.core.model.FileHeader
import solutions.laxmi.omnicompiler.core.model.User
import solutions.laxmi.omnicompiler.core.ui.Avatar
import solutions.laxmi.omnicompiler.core.ui.VerdictBadge
import solutions.laxmi.omnicompiler.core.ui.fileBadgeFor

/** Where drawer rows lead; the screen maps them to routes. */
enum class DrawerDestination(@StringRes val labelRes: Int, val icon: ImageVector) {
    Projects(R.string.drawer_all_projects, OmniIcons.Folder),
    ImportGit(R.string.drawer_import_git, OmniIcons.Download),
    Examples(R.string.drawer_examples, OmniIcons.Book),
    History(R.string.drawer_history, OmniIcons.History),
    Usage(R.string.drawer_usage, OmniIcons.Chart),
    Developer(R.string.drawer_developer, OmniIcons.Key),
    Settings(R.string.drawer_settings, OmniIcons.Settings),
}

/** Design W1: account header, project switcher, current project's files, navigation. */
@Composable
internal fun EditorDrawer(
    user: User?,
    projects: List<ProjectSummary>,
    usage: DrawerUsage?,
    destinations: List<DrawerDestination>,
    currentProjectId: String?,
    currentProjectName: String,
    files: List<FileHeader>,
    activeFileId: String?,
    entryShortCode: String?,
    onAccount: () -> Unit,
    onProject: (String) -> Unit,
    onNewProject: () -> Unit,
    onFile: (FileHeader) -> Unit,
    onDestination: (DrawerDestination) -> Unit,
) {
    val colors = OmniTheme.colors
    // Large projects would push navigation out of reach: show a few files (always including the open one).
    var showAllFiles by rememberSaveable { mutableStateOf(false) }
    val shownFiles = if (showAllFiles || files.size <= MAX_DRAWER_FILES + 1) files else {
        val first = files.take(MAX_DRAWER_FILES)
        first + files.filter { it.id == activeFileId && it !in first }
    }
    Column(
        Modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(colors.surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        AccountHeader(user, onAccount)
        LazyColumn(Modifier.weight(1f)) {
            item {
                SectionLabel(stringResource(R.string.drawer_projects)) {
                    Row(
                        Modifier.clickable(role = Role.Button, onClick = onNewProject).padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(OmniIcons.Plus, null, tint = colors.accentText, modifier = Modifier.size(12.dp))
                        Text(stringResource(R.string.drawer_new), style = OmniTheme.typography.label, color = colors.accentText)
                    }
                }
            }
            items(projects.take(MAX_DRAWER_PROJECTS), key = { it.project.id }) { summary ->
                DrawerRow(
                    selected = summary.project.id == currentProjectId,
                    onClick = { onProject(summary.project.id) },
                ) {
                    Text(
                        summary.project.name,
                        style = OmniTheme.typography.bodyStrong,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    summary.project.lastVerdict?.let { VerdictBadge(it) }
                }
            }
            item { SectionLabel(stringResource(R.string.drawer_project_files, currentProjectName)) }
            items(shownFiles, key = { it.id }) { file ->
                DrawerRow(selected = file.id == activeFileId, onClick = { onFile(file) }) {
                    Text(
                        fileBadgeFor(file.name, entryShortCode, file.isEntry),
                        style = OmniTheme.typography.badge,
                        color = colors.textTertiary,
                        modifier = Modifier.width(20.dp),
                    )
                    Text(file.name, style = OmniTheme.typography.bodySmall, color = colors.textPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                    fileRole(file)?.let { Text(stringResource(it), style = OmniTheme.typography.monoSmall, color = colors.textTertiary) }
                }
            }
            if (files.size > shownFiles.size || showAllFiles) {
                item(key = "files-toggle") {
                    DrawerRow(selected = false, onClick = { showAllFiles = !showAllFiles }) {
                        Text(
                            if (showAllFiles) stringResource(R.string.drawer_files_fewer)
                            else pluralStringResource(R.plurals.drawer_files_all, files.size, files.size),
                            style = OmniTheme.typography.bodySmall,
                            color = colors.accentText,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item { Box(Modifier.height(8.dp)) }
            items(destinations) { destination ->
                DrawerRow(selected = false, onClick = { onDestination(destination) }) {
                    Icon(destination.icon, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                    Text(stringResource(destination.labelRes), style = OmniTheme.typography.bodyStrong, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    Icon(OmniIcons.ChevronRight, null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                }
            }
        }
        usage?.let { UsageFooter(it) { onDestination(DrawerDestination.Usage) } }
    }
}

@Composable
private fun UsageFooter(usage: DrawerUsage, onClick: () -> Unit) {
    val colors = OmniTheme.colors
    val fraction = (usage.used.toFloat() / usage.limit).coerceIn(0f, 1f)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .drawBehind { drawLine(colors.divider, Offset(0f, 0.5f), Offset(size.width, 0.5f), 1f) }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.drawer_usage_period), style = OmniTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.drawer_usage_fraction, usage.used, usage.limit), style = OmniTheme.typography.monoSmall, color = colors.textPrimary)
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(colors.surfaceMuted)) {
            Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(if (fraction >= 0.9f) colors.accent else colors.textPrimary))
        }
    }
}

@Composable
private fun AccountHeader(user: User?, onClick: () -> Unit) {
    val colors = OmniTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .drawBehind { drawLine(colors.divider, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(imageUrl = user?.profileUrl, initials = user?.initials ?: "?", size = 36.dp)
        Column(Modifier.weight(1f)) {
            Text(user?.displayName ?: stringResource(R.string.drawer_not_signed_in), style = OmniTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1)
            Text(
                user?.email ?: stringResource(if (user?.isGuest == true) R.string.drawer_guest_subtitle else R.string.drawer_signed_out_subtitle),
                style = OmniTheme.typography.bodySmall,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // The plan lives on Usage & plan; only the guest state is known locally.
        if (user == null || user.isGuest) OmniBadge(stringResource(R.string.drawer_guest_badge))
    }
}

@Composable
private fun DrawerRow(selected: Boolean, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val colors = OmniTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(if (selected) colors.surfaceRaised else Color.Transparent)
            .drawBehind { if (selected) drawRect(colors.accent, size = size.copy(width = 2.dp.toPx())) }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@StringRes
private fun fileRole(file: FileHeader): Int? = when {
    file.isEntry -> R.string.drawer_file_entry
    file.name.substringAfterLast('.', "").lowercase() in DATA_EXTENSIONS -> R.string.drawer_file_data
    else -> null
}

private val DATA_EXTENSIONS = setOf("txt", "in", "dat", "csv")

private const val MAX_DRAWER_PROJECTS = 6
private const val MAX_DRAWER_FILES = 5
