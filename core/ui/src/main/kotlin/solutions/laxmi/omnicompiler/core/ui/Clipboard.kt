package solutions.laxmi.omnicompiler.core.ui

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch

/** A click handler that copies plain text, over Compose's suspending clipboard (the old ClipboardManager is deprecated). */
@Composable
fun rememberCopyToClipboard(): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope) {
        { text -> scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text))) } }
    }
}
