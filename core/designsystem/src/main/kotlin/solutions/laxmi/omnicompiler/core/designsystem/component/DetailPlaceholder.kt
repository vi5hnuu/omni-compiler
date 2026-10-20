package solutions.laxmi.omnicompiler.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme

/** The detail pane of a two-pane layout before anything is picked in the list beside it. */
@Composable
fun DetailPlaceholder(title: String, message: String, icon: ImageVector) {
    Box(Modifier.fillMaxSize().background(OmniTheme.colors.background).statusBarsPadding(), contentAlignment = Alignment.Center) {
        EmptyState(title = title, message = message, icon = icon)
    }
}
