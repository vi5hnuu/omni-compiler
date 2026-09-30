package solutions.laxmi.omnicompiler.feature.workspace.editor

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
import solutions.laxmi.omnicompiler.core.model.SourceFile
import solutions.laxmi.omnicompiler.core.model.User
import solutions.laxmi.omnicompiler.core.ui.LanguageTile
import solutions.laxmi.omnicompiler.core.ui.VerdictBadge
import solutions.laxmi.omnicompiler.core.ui.fileBadgeFor

/** Where drawer rows lead; the screen maps them to routes. */
enum class DrawerDestination(val label: String, val icon: ImageVector) {
    Projects("All projects", OmniIcons.Folder),
    Examples("Examples & problems", OmniIcons.Book),
    History("Run history", OmniIcons.History),
    Usage("Usage & plan", OmniIcons.Chart),
    Developer("API key & webhooks", OmniIcons.Key),
    Settings("Settings", OmniIcons.Settings),
}

/** Design W1: account header, project switcher, current project's files, navigation. */
@Composable
internal fun EditorDrawer(
    user: User?,
    projects: List<ProjectSummary>,
    currentProjectId: String?,
    currentProjectName: String,
    files: List<SourceFile>,
    activeFileId: String?,
    entryShortCode: String?,
    onAccount: () -> Unit,
    onProject: (String) -> Unit,
    onNewProject: () -> Unit,
    onFile: (SourceFile) -> Unit,
    onDestination: (DrawerDestination) -> Unit,
) {
    val colors = OmniTheme.colors
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
                SectionLabel("Projects") {
                    Row(
                        Modifier.clickable(role = Role.Button, onClick = onNewProject).padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(OmniIcons.Plus, null, tint = colors.accentText, modifier = Modifier.size(12.dp))
                        Text("New", style = OmniTheme.typography.label, color = colors.accentText)
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
            item { SectionLabel("$currentProjectName · files") }
            items(files, key = { it.id }) { file ->
                DrawerRow(selected = file.id == activeFileId, onClick = { onFile(file) }) {
                    Text(
                        fileBadgeFor(file.name, entryShortCode, file.isEntry),
                        style = OmniTheme.typography.badge,
                        color = colors.textTertiary,
                        modifier = Modifier.width(20.dp),
                    )
                    Text(file.name, style = OmniTheme.typography.bodySmall, color = colors.textPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                    fileRole(file)?.let { Text(it, style = OmniTheme.typography.monoSmall, color = colors.textTertiary) }
                }
            }
            item { Box(Modifier.height(8.dp)) }
            items(DrawerDestination.entries) { destination ->
                DrawerRow(selected = false, onClick = { onDestination(destination) }) {
                    Icon(destination.icon, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                    Text(destination.label, style = OmniTheme.typography.bodyStrong, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    Icon(OmniIcons.ChevronRight, null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                }
            }
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
        LanguageTile(code = user?.initials ?: "?", size = 36.dp, selected = true)
        Column(Modifier.weight(1f)) {
            Text(user?.displayName ?: "Not signed in", style = OmniTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1)
            Text(
                user?.email ?: if (user?.isGuest == true) "Guest · runs stay on this device" else "Sign in to sync history",
                style = OmniTheme.typography.bodySmall,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OmniBadge(if (user == null || user.isGuest) "GUEST" else "FREE")
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

private fun fileRole(file: SourceFile): String? = when {
    file.isEntry -> "entry"
    file.name.substringAfterLast('.', "").lowercase() in setOf("txt", "in", "dat", "csv") -> "data"
    else -> null
}

private const val MAX_DRAWER_PROJECTS = 6
