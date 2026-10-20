package solutions.laxmi.omnicompiler.feature.workspace.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.feature.workspace.R

/** Wide windows (tablets, foldables, landscape) can show two files side by side. */
internal val SplitMinWidth = 600.dp

/**
 * Two panes with a draggable divider; [fraction] is the first pane's share of the width. Without a [second] pane the
 * first takes the whole width at the same place in the composition, so showing or hiding the second keeps the first's
 * state (an open editor's text, undo history and scroll).
 *
 * [minFirst] and [minSecond] keep each pane usable when the room shrinks (a sidebar opens, the window is resized): the
 * shown split is clamped to them while [fraction] keeps the user's choice for when there is room again. When both
 * can't fit, the room is shared in proportion to them.
 */
@Composable
internal fun SplitPanes(
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    first: @Composable (Modifier) -> Unit,
    second: (@Composable (Modifier) -> Unit)?,
    modifier: Modifier = Modifier,
    minFirst: Dp = 0.dp,
    minSecond: Dp = 0.dp,
) {
    val colors = OmniTheme.colors
    BoxWithConstraints(modifier) {
        val totalPx = with(LocalDensity.current) { maxWidth.toPx() }.coerceAtLeast(1f)
        val shown = shownFraction(fraction, maxWidth, minFirst, minSecond)
        val drag = rememberDraggableState { delta -> onFractionChange(shownFraction(shown + delta / totalPx, maxWidth, minFirst, minSecond)) }
        Row {
            first(Modifier.weight(if (second == null) 1f else shown).fillMaxHeight())
            if (second == null) return@Row
            val description = stringResource(R.string.editor_split_divider)
            Box(
                Modifier
                    .width(12.dp)
                    .fillMaxHeight()
                    .background(colors.surface)
                    .drawBehind { drawLine(colors.border, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1f) }
                    .draggable(drag, Orientation.Horizontal)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 4.dp, height = 28.dp).background(colors.dragHandle))
            }
            second(Modifier.weight(1f - shown).fillMaxHeight())
        }
    }
}

private fun shownFraction(fraction: Float, total: Dp, minFirst: Dp, minSecond: Dp): Float {
    if (total <= 0.dp) return fraction
    val low = maxOf(MIN_FRACTION, minFirst / total)
    val high = minOf(1f - MIN_FRACTION, 1f - minSecond / total)
    return if (low <= high) fraction.coerceIn(low, high) else minFirst / (minFirst + minSecond)
}

private const val MIN_FRACTION = 0.25f
