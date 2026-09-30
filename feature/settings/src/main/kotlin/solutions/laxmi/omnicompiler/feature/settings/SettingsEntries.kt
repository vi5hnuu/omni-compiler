package solutions.laxmi.omnicompiler.feature.settings

import androidx.annotation.RawRes
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import solutions.laxmi.omnicompiler.core.navigation.AppearanceRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.navigation.OpenSourceRoute
import solutions.laxmi.omnicompiler.core.navigation.SettingsRoute

/** [licensesResId] is the app's generated `R.raw.aboutlibraries` (only the app module can see it). */
fun EntryProviderScope<NavKey>.settingsEntries(navigator: Navigator, appVersion: String, @RawRes licensesResId: Int) {
    entry<SettingsRoute> { SettingsScreen(navigator, appVersion) }
    entry<AppearanceRoute> { AppearanceScreen(navigator) }
    entry<OpenSourceRoute> { OpenSourceScreen(navigator, licensesResId) }
}
