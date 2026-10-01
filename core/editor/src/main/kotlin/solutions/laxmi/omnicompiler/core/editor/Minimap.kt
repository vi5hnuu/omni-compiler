package solutions.laxmi.omnicompiler.core.editor

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * 34 dp code overview (design E1): one 2 px bar per line scaled by indent/length, the visible window
 * as a raised block, and error lines in the accent colour. Tap or drag to scroll.
 *
 * The line bars are built into paths once per content change and kept on their own layer; scrolling only
 * redraws the viewport block, which reads the scroll position at draw time and so never recomposes.
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
    Box(
        modifier
            .width(34.dp)
            .fillMaxHeight()
            .semantics { contentDescription = description }
            .pointerInput(state) { detectTapGestures { state.scrollToFraction(it.y / size.height - state.viewport.height / 2) } }
            .pointerInput(state) {
                detectVerticalDragGestures { change, _ -> state.scrollToFraction(change.position.y / size.height - state.viewport.height / 2) }
            }
            .drawBehind {
                val geometry = MinimapGeometry(this, size, lines.size)
                drawRect(palette.indentGuide, size = Size(geometry.divider, size.height))
                val viewport = state.viewport
                drawRect(
                    color = palette.minimapViewport,
                    topLeft = Offset(geometry.divider, geometry.padding + viewport.top * geometry.docHeight),
                    size = Size(size.width - geometry.divider, maxOf(viewport.height * geometry.docHeight, 8.dp.toPx())),
                )
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawWithCache {
                    val geometry = MinimapGeometry(this, size, lines.size)
                    val normal = Path()
                    val errors = Path()
                    lines.forEachIndexed { index, line ->
                        if (line.length == 0) return@forEachIndexed
                        val x = geometry.left + minOf(line.indent * geometry.charWidth, geometry.maxWidth / 2)
                        val width = minOf(line.length * geometry.charWidth, geometry.maxWidth - (x - geometry.left))
                        val bar = Rect(Offset(x, geometry.rowTop(index)), Size(width, maxOf(geometry.barHeight * geometry.scale, 1f)))
                        (if ((index + 1) in errorLines) errors else normal).addRect(bar)
                    }
                    errorLines.filter { it in 1..lines.size }.forEach { line ->
                        errors.addRect(Rect(Offset(geometry.divider, geometry.rowTop(line - 1)), Size(size.width - geometry.divider, geometry.barHeight)))
                    }
                    onDrawBehind {
                        drawPath(normal, palette.minimapLine)
                        drawPath(errors, palette.error)
                    }
                },
        )
    }
}

/** Shared layout of the minimap; long files compress rows so the whole document always fits. */
private class MinimapGeometry(density: Density, size: Size, lineCount: Int) {
    val divider: Float
    val padding: Float
    val left: Float
    val barHeight: Float
    val charWidth: Float
    val maxWidth: Float
    val scale: Float
    val docHeight: Float
    private val rowHeight: Float

    init {
        with(density) {
            divider = 1.dp.toPx()
            padding = 6.dp.toPx()
            left = 3.dp.toPx() + divider
            rowHeight = 4.dp.toPx()
            barHeight = 2.dp.toPx()
            charWidth = 0.8.dp.toPx()
            maxWidth = size.width - left - 3.dp.toPx()
        }
        val available = size.height - padding * 2
        scale = if (lineCount == 0) 1f else minOf(1f, available / (lineCount * rowHeight))
        docHeight = lineCount * rowHeight * scale
    }

    fun rowTop(index: Int) = padding + index * rowHeight * scale
}
