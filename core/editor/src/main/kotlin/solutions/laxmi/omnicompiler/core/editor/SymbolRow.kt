package solutions.laxmi.omnicompiler.core.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniDimens
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme

/** Keys shown above the soft keyboard (design E2); Tab first, then the design's set, then extras on scroll. */
private val Symbols = listOf("{", "}", "(", ")", "[", "]", ";", "\"", "<", ">", "=", "&", "|", ":", ",", ".", "'", "+", "-", "*", "/", "%", "!", "#", "_", "\\")

@Composable
fun SymbolRow(state: CodeEditorState, modifier: Modifier = Modifier) {
    val colors = OmniTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(OmniDimens.symbolRowHeight)
            .background(colors.surface)
            .drawBehind { drawLine(colors.border, Offset(0f, 0f), Offset(size.width, 0f), 1f) }
            .horizontalScroll(rememberScrollState()),
    ) {
        SymbolKey("⇥", width = 44, highlighted = true, description = "Tab") { state.indent() }
        Symbols.forEach { symbol -> SymbolKey(symbol, width = 34) { state.insert(symbol) } }
    }
}

@Composable
private fun SymbolKey(label: String, width: Int, highlighted: Boolean = false, description: String = label, onClick: () -> Unit) {
    val colors = OmniTheme.colors
    Box(
        modifier = Modifier
            .width(width.dp)
            .fillMaxHeight()
            .drawBehind { drawLine(colors.hairline, Offset(size.width - 0.5f, 0f), Offset(size.width - 0.5f, size.height), 1f) }
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = OmniTheme.typography.code.copy(fontSize = if (highlighted) 15.sp else 14.sp),
            color = if (highlighted) colors.accentText else colors.textPrimary,
        )
    }
}
