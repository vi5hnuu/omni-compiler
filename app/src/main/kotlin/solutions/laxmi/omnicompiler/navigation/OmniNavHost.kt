package solutions.laxmi.omnicompiler.navigation

import solutions.laxmi.omnicompiler.core.navigation.ProjectFolderRoute
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import solutions.laxmi.omnicompiler.AppGate
import solutions.laxmi.omnicompiler.R
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.navigation.CheckInboxRoute
import solutions.laxmi.omnicompiler.core.navigation.EditorRoute
import solutions.laxmi.omnicompiler.core.navigation.ForgotPasswordRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.navigation.Route
import solutions.laxmi.omnicompiler.core.navigation.SignInRoute
import solutions.laxmi.omnicompiler.core.navigation.SignUpRoute
import solutions.laxmi.omnicompiler.core.navigation.WelcomeRoute
import solutions.laxmi.omnicompiler.feature.account.accountEntries
import solutions.laxmi.omnicompiler.feature.auth.authEntries
import solutions.laxmi.omnicompiler.feature.developer.developerEntries
import solutions.laxmi.omnicompiler.feature.history.historyEntries
import solutions.laxmi.omnicompiler.feature.languages.languagesEntries
import solutions.laxmi.omnicompiler.feature.projects.projectsEntries
import solutions.laxmi.omnicompiler.feature.settings.settingsEntries
import solutions.laxmi.omnicompiler.feature.workspace.workspaceEntries

@Composable
fun OmniNavHost(gate: AppGate, appVersion: String) {
    val backStack = rememberNavBackStack(
        when (gate) {
            AppGate.Ready -> EditorRoute()
            AppGate.NeedsFolder -> ProjectFolderRoute()
            else -> WelcomeRoute
        },
    )
    val navigator = remember(backStack) { BackStackNavigator(backStack) }

    // Session gate: signing out anywhere (or a dead refresh token) returns to Welcome; signing in
    // from an auth screen lands in the editor. Screens never route on session changes themselves.
    LaunchedEffect(gate) {
        val onAuthScreen = backStack.lastOrNull().isAuthRoute()
        when {
            gate == AppGate.SignedOut && !onAuthScreen -> navigator.resetTo(WelcomeRoute)
            // Projects live in a folder on the device; nothing past sign-in works until one is reachable.
            gate == AppGate.NeedsFolder && backStack.lastOrNull() !is ProjectFolderRoute -> navigator.resetTo(ProjectFolderRoute())
            gate == AppGate.Ready && (backStack.lastOrNull() == WelcomeRoute || backStack.lastOrNull() == ProjectFolderRoute()) ->
                navigator.resetTo(EditorRoute())
        }
    }

    NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxSize().background(OmniTheme.colors.background),
        onBack = navigator::back,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            authEntries(navigator, appVersion)
            workspaceEntries(navigator)
            languagesEntries(navigator)
            projectsEntries(navigator)
            historyEntries(navigator)
            accountEntries(navigator)
            developerEntries(navigator)
            settingsEntries(navigator, appVersion, R.raw.aboutlibraries)
        },
    )
}

private fun NavKey?.isAuthRoute() =
    this is WelcomeRoute || this is SignInRoute || this is SignUpRoute || this is CheckInboxRoute || this is ForgotPasswordRoute

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
