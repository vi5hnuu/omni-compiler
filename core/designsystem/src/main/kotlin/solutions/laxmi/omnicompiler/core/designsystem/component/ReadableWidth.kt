package solutions.laxmi.omnicompiler.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniDimens

/**
 * Centres a screen's content column at a readable width on tablets and in landscape; on phones it is the full
 * width. Applied under the app bar, which keeps spanning the window.
 */
fun Modifier.readableWidth(): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = OmniDimens.contentMaxWidth).fillMaxWidth()

/** A screen's body under its app bar: the rest of the height, at [readableWidth]. */
@Composable
fun ColumnScope.ReadableContent(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.weight(1f).readableWidth(), content = content)
}
