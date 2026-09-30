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
import androidx.compose.ui.unit.dp
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.feature.workspace.R

/** Wide windows (tablets, foldables, landscape) can show two files side by side. */
internal val SplitMinWidth = 600.dp

/** Two panes with a draggable divider; [fraction] is the first pane's share of the width. */
@Composable
internal fun SplitPanes(
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OmniTheme.colors
    BoxWithConstraints(modifier) {
        val totalPx = with(LocalDensity.current) { maxWidth.toPx() }.coerceAtLeast(1f)
        val drag = rememberDraggableState { delta -> onFractionChange((fraction + delta / totalPx).coerceIn(MIN_FRACTION, 1f - MIN_FRACTION)) }
        Row {
            first(Modifier.weight(fraction).fillMaxHeight())
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
            second(Modifier.weight(1f - fraction).fillMaxHeight())
        }
    }
}

private const val MIN_FRACTION = 0.25f
