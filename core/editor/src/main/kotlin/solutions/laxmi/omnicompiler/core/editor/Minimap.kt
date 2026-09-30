package solutions.laxmi.omnicompiler.core.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * 34 dp code overview (design E1): one 2 px bar per line scaled by indent/length, the visible window
 * as a raised block, and error lines in the accent colour. Tap or drag to scroll.
 */
@Composable
internal fun Minimap(
    state: CodeEditorState,
    palette: EditorPalette,
    errorLines: Set<Int>,
    modifier: Modifier = Modifier,
) {
    val lines = state.minimap
    val description = stringResource(R.string.editor_minimap)
    val viewport = state.viewport
    Canvas(
        modifier = modifier
            .width(34.dp)
            .fillMaxHeight()
            .semantics { contentDescription = description }
            .pointerInput(state) { detectTapGestures { state.scrollToFraction(it.y / size.height - viewport.height / 2) } }
            .pointerInput(state) {
                detectVerticalDragGestures { change, _ -> state.scrollToFraction(change.position.y / size.height - state.viewport.height / 2) }
            },
    ) {
        val divider = 1.dp.toPx()
        drawRect(palette.indentGuide, size = Size(divider, size.height))
        val padding = 6.dp.toPx()
        val left = 3.dp.toPx() + divider
        val rowHeight = 4.dp.toPx()
        val barHeight = 2.dp.toPx()
        val charWidth = 0.8.dp.toPx()
        val maxWidth = size.width - left - 3.dp.toPx()
        // Long files compress rows so the whole document always fits.
        val available = size.height - padding * 2
        val scale = if (lines.isEmpty()) 1f else minOf(1f, available / (lines.size * rowHeight))
        val docHeight = lines.size * rowHeight * scale

        drawRect(
            color = palette.minimapViewport,
            topLeft = Offset(divider, padding + viewport.top * docHeight),
            size = Size(size.width - divider, maxOf(viewport.height * docHeight, 8.dp.toPx())),
        )
        lines.forEachIndexed { index, line ->
            if (line.length == 0) return@forEachIndexed
            val y = padding + index * rowHeight * scale
            val x = left + minOf(line.indent * charWidth, maxWidth / 2)
            val width = minOf(line.length * charWidth, maxWidth - (x - left))
            val color = if ((index + 1) in errorLines) palette.error else palette.minimapLine
            drawRect(color, Offset(x, y), Size(width, maxOf(barHeight * scale, 1f)))
        }
        errorLines.forEach { line ->
            val y = padding + (line - 1) * rowHeight * scale
            if (line in 1..lines.size) drawRect(palette.error, Offset(divider, y), Size(size.width - divider, barHeight))
        }
    }
}
