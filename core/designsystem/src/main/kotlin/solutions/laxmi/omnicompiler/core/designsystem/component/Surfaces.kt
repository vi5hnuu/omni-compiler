package solutions.laxmi.omnicompiler.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniDimens
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme

/** 1 px hairline divider. */
@Composable
fun OmniDivider(modifier: Modifier = Modifier, color: Color = OmniTheme.colors.divider) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** Grab handle on bottom sheets and the console peek. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 28.dp, height = 3.dp).background(OmniTheme.colors.dragHandle))
    }
}

/** Pushed-screen app bar: back arrow, 16 sp title, optional actions. */
@Composable
fun OmniTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: ImageVector = OmniIcons.ArrowLeft,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = OmniTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .statusBarsPadding()
            .height(OmniDimens.appBarHeight)
            .padding(start = 2.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            OmniIconButton(navigationIcon, "Back", onBack, size = OmniDimens.touchTarget, tint = colors.textPrimary, iconSize = 18.dp)
        } else {
            Box(Modifier.width(OmniDimens.space12))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = OmniTheme.typography.title, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = OmniTheme.typography.label, color = colors.textTertiary, maxLines = 1)
        }
        actions()
    }
}

/** Uppercase section label (e.g. "RECENT", "EDITOR"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = OmniDimens.screenPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), style = OmniTheme.typography.overline, color = OmniTheme.colors.textTertiary, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Info callout with the design's red left bar. */
@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = OmniIcons.Info,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = OmniTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .drawBehind { drawRect(colors.accent, size = size.copy(width = 2.dp.toPx())) }
            .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = colors.accentText, modifier = Modifier.padding(top = 1.dp).size(15.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (title != null) Text(title, style = OmniTheme.typography.bodyStrong, color = colors.textPrimary)
            Text(text, style = OmniTheme.typography.bodySmall, color = colors.textSecondary)
            action?.invoke()
        }
    }
}

/** Standard tappable list row: leading slot, title/subtitle, trailing slot, hairline bottom. */
@Composable
fun OmniListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    titleColor: Color = OmniTheme.colors.textPrimary,
    selected: Boolean = false,
    minHeight: Dp = OmniDimens.listRow,
    onClick: (() -> Unit)? = null,
) {
    val colors = OmniTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .background(if (selected) colors.surfaceRaised else Color.Transparent)
            .then(if (selected) Modifier.drawBehind { drawRect(colors.accent, size = size.copy(width = 2.dp.toPx())) } else Modifier)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .drawBehind {
                drawLine(colors.hairline, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
            }
            .padding(horizontal = OmniDimens.screenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = OmniTheme.typography.bodyStrong, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = OmniTheme.typography.bodySmall, color = colors.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

data class OmniTab(val title: String, val badge: String? = null, val badgeIsAlert: Boolean = false)

/** Underline tabs (2 dp accent indicator) used in the console sheet and list screens. */
@Composable
fun OmniTabRow(
    tabs: List<OmniTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = OmniDimens.sheetTabHeight,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = OmniTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind { drawLine(colors.divider, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(Modifier.weight(1f).fillMaxHeight(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
            itemsIndexed(tabs) { index, tab ->
                val selected = index == selectedIndex
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(min = 48.dp)
                        .selectableTab(selected) { onSelect(index) }
                        .drawBehind {
                            if (selected) drawRect(colors.accent, topLeft = Offset(0f, size.height - 2.dp.toPx()), size = size.copy(height = 2.dp.toPx()))
                        }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(tab.title, style = OmniTheme.typography.bodyStrong.copy(fontSize = OmniTheme.typography.label.fontSize * 1.1f), color = if (selected) colors.textPrimary else colors.textSecondary)
                    if (tab.badge != null) {
                        Text(tab.badge, style = OmniTheme.typography.monoSmall, color = if (tab.badgeIsAlert) colors.accentText else colors.textTertiary)
                    }
                }
            }
        }
        trailing()
    }
}

private fun Modifier.selectableTab(selected: Boolean, onClick: () -> Unit) =
    this.then(Modifier.clickable(role = Role.Tab, onClick = onClick))

/** Mono badge box (verdicts, tags like LIVE/FREE/DEFAULT). */
@Composable
fun OmniBadge(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = OmniTheme.colors.surfaceMuted,
    content: Color = OmniTheme.colors.textSecondary,
    outlined: Boolean = false,
) {
    Box(
        modifier = modifier
            .background(if (outlined) Color.Transparent else container)
            .then(if (outlined) Modifier.drawBehind { drawRect(content.copy(alpha = 0.4f), style = androidx.compose.ui.graphics.drawscope.Stroke(1f)) } else Modifier)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = OmniTheme.typography.badge, color = content)
    }
}

/** Centered message for empty/error states with an optional action. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = OmniIcons.Braces,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = OmniTheme.colors
    Column(
        modifier.fillMaxWidth().padding(OmniDimens.space32),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = colors.textTertiary, modifier = Modifier.size(22.dp))
        Text(title, style = OmniTheme.typography.title, color = colors.textPrimary)
        Text(message, style = OmniTheme.typography.bodySmall, color = colors.textSecondary)
        if (action != null) Box(Modifier.padding(top = 8.dp)) { action() }
    }
}
