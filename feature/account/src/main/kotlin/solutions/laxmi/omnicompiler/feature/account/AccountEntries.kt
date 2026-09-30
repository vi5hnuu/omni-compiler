package solutions.laxmi.omnicompiler.feature.account

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.navigation.ProfileRoute
import solutions.laxmi.omnicompiler.core.navigation.UsageRoute

fun EntryProviderScope<NavKey>.accountEntries(navigator: Navigator) {
    entry<UsageRoute> { UsageScreen(navigator) }
    entry<ProfileRoute> { ProfileScreen(navigator) }
}
