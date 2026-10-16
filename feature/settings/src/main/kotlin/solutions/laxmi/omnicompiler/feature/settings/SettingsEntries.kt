package solutions.laxmi.omnicompiler.feature.settings

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.annotation.RawRes
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.ui.res.stringResource
import solutions.laxmi.omnicompiler.core.designsystem.component.DetailPlaceholder
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import solutions.laxmi.omnicompiler.core.navigation.AppearanceRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.navigation.OpenSourceRoute
import solutions.laxmi.omnicompiler.core.navigation.SettingsRoute

/** [licensesResId] is the app's generated `R.raw.aboutlibraries` (only the app module can see it). */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.settingsEntries(navigator: Navigator, appVersion: String, @RawRes licensesResId: Int) {
    // Large windows: settings on the left, the page picked from them on the right. Opened from elsewhere (e.g. the
    // editor's menu) a detail page shows on its own.
    entry<SettingsRoute>(
        metadata = ListDetailSceneStrategy.listPane(
            detailPlaceholder = {
                DetailPlaceholder(stringResource(R.string.settings_pick_title), stringResource(R.string.settings_pick_message), OmniIcons.Settings)
            },
        ),
    ) { SettingsScreen(navigator, appVersion) }
    entry<AppearanceRoute>(metadata = ListDetailSceneStrategy.detailPane()) { AppearanceScreen(navigator) }
    entry<OpenSourceRoute>(metadata = ListDetailSceneStrategy.detailPane()) { OpenSourceScreen(navigator, licensesResId) }
}
