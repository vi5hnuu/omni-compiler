package solutions.laxmi.omnicompiler.feature.auth

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import solutions.laxmi.omnicompiler.core.navigation.CheckInboxRoute
import solutions.laxmi.omnicompiler.core.navigation.ForgotPasswordRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.navigation.SignInRoute
import solutions.laxmi.omnicompiler.core.navigation.SignUpRoute
import solutions.laxmi.omnicompiler.core.navigation.WelcomeRoute

fun EntryProviderScope<NavKey>.authEntries(navigator: Navigator, appVersion: String) {
    entry<WelcomeRoute> { WelcomeScreen(navigator, appVersion) }
    entry<SignInRoute> { SignInScreen(navigator) }
    entry<SignUpRoute> { route -> SignUpScreen(route, navigator) }
    entry<CheckInboxRoute> { route -> CheckInboxScreen(route, navigator) }
    entry<ForgotPasswordRoute> { route -> ForgotPasswordScreen(route, navigator) }
}
