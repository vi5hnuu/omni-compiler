package solutions.laxmi.omnicompiler.feature.settings

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import solutions.laxmi.omnicompiler.core.navigation.AppearanceRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.navigation.SettingsRoute

fun EntryProviderScope<NavKey>.settingsEntries(navigator: Navigator, appVersion: String) {
    entry<SettingsRoute> { SettingsScreen(navigator, appVersion) }
    entry<AppearanceRoute> { AppearanceScreen(navigator) }
}
