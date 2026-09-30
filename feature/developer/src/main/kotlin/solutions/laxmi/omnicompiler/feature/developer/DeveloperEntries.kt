package solutions.laxmi.omnicompiler.feature.developer

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import solutions.laxmi.omnicompiler.core.navigation.DeveloperRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator

fun EntryProviderScope<NavKey>.developerEntries(navigator: Navigator) {
    entry<DeveloperRoute> { DeveloperScreen(navigator) }
}
