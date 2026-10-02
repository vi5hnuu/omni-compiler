package solutions.laxmi.omnicompiler.core.designsystem.component

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Snackbar host that stays visible above the navigation bar and the on-screen keyboard. Insets an ancestor already
 * consumed (e.g. a screen column with navigationBarsPadding) add nothing here, so it is safe anywhere in a screen.
 */
@Composable
fun OmniSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier.navigationBarsPadding().imePadding())
}
