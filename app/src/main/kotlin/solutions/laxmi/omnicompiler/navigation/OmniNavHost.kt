package solutions.laxmi.omnicompiler.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import solutions.laxmi.omnicompiler.core.designsystem.component.EmptyState
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTopBar
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.navigation.EditorRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.navigation.Route
import solutions.laxmi.omnicompiler.feature.workspace.workspaceEntries

@Composable
fun OmniNavHost() {
    val backStack = rememberNavBackStack(EditorRoute())
    val navigator = remember(backStack) { BackStackNavigator(backStack) }
    NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxSize().background(OmniTheme.colors.background),
        onBack = navigator::back,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider(fallback = { key -> unavailableEntry(key, navigator) }) {
            workspaceEntries(navigator)
        },
    )
}

/** Destination whose feature module has not registered an entry yet; keeps navigation from crashing. */
private fun unavailableEntry(key: NavKey, navigator: Navigator) = NavEntry(key) {
    Box(Modifier.fillMaxSize().background(OmniTheme.colors.background)) {
        OmniTopBar(title = "", onBack = navigator::back)
        EmptyState(
            title = "Not available yet",
            message = "This screen is part of a later build step.",
            modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
        )
    }
}

private class BackStackNavigator(private val backStack: NavBackStack<NavKey>) : Navigator {
    override fun navigate(route: Route) {
        backStack.add(route)
    }

    override fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    override fun resetTo(route: Route) {
        backStack.clear()
        backStack.add(route)
    }

    override fun replace(route: Route) {
        if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex)
        backStack.add(route)
    }
}
