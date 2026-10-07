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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniDimens
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme

/** Keys shown above the soft keyboard (design E2): caret and line keys, Tab, then the design's symbols (scrolls). */
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
        // Soft keyboards have no arrow keys; precise caret moves and line edits are the most requested extras.
        SymbolKey("◀", width = 36, highlighted = true, description = stringResource(R.string.editor_key_left), repeats = true) { state.moveCaret(CaretDirection.Left) }
        SymbolKey("▶", width = 36, highlighted = true, description = stringResource(R.string.editor_key_right), repeats = true) { state.moveCaret(CaretDirection.Right) }
        SymbolKey("▲", width = 36, highlighted = true, description = stringResource(R.string.editor_key_up), repeats = true) { state.moveCaret(CaretDirection.Up) }
        SymbolKey("▼", width = 36, highlighted = true, description = stringResource(R.string.editor_key_down), repeats = true) { state.moveCaret(CaretDirection.Down) }
        SymbolKey("⇥", width = 40, highlighted = true, description = stringResource(R.string.editor_key_tab)) { state.indent() }
        SymbolKey("//", width = 40, highlighted = true, description = stringResource(R.string.editor_key_comment)) { state.toggleComment() }
        SymbolKey("⤒", width = 36, highlighted = true, description = stringResource(R.string.editor_key_line_up), repeats = true) { state.moveLines(up = true) }
        SymbolKey("⤓", width = 36, highlighted = true, description = stringResource(R.string.editor_key_line_down), repeats = true) { state.moveLines(up = false) }
        SymbolKey("⧉", width = 36, highlighted = true, description = stringResource(R.string.editor_key_duplicate)) { state.duplicateLine() }
        Symbols.forEach { symbol -> SymbolKey(symbol, width = 34) { state.insert(symbol) } }
    }
}

@Composable
private fun SymbolKey(label: String, width: Int, highlighted: Boolean = false, description: String = label, repeats: Boolean = false, onClick: () -> Unit) {
    val colors = OmniTheme.colors
    val currentOnClick by rememberUpdatedState(onClick)
    Box(
        modifier = Modifier
            .width(width.dp)
            .fillMaxHeight()
            .drawBehind { drawLine(colors.hairline, Offset(size.width - 0.5f, 0f), Offset(size.width - 0.5f, size.height), 1f) }
            .then(
                if (repeats) {
                    // Caret and line moves repeat while held, like a hardware key. Nothing happens on touch-down: a swipe
                    // that scrolls the row starts on a key too, and must not move the caret or a line.
                    Modifier
                        .pointerInput(Unit) {
                            coroutineScope {
                                var repeated = false
                                detectTapGestures(
                                    onPress = {
                                        repeated = false
                                        val repeater = launch {
                                            delay(viewConfiguration.longPressTimeoutMillis)
                                            repeated = true
                                            while (true) {
                                                currentOnClick()
                                                delay(REPEAT_INTERVAL_MS)
                                            }
                                        }
                                        tryAwaitRelease()
                                        repeater.cancel()
                                    },
                                    // A quick tap acts once; after a hold the repeats already acted.
                                    onTap = { if (!repeated) currentOnClick() },
                                )
                            }
                        }
                        .semantics {
                            role = Role.Button
                            onClick { currentOnClick(); true }
                        }
                } else {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                },
            )
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

private const val REPEAT_INTERVAL_MS = 60L
