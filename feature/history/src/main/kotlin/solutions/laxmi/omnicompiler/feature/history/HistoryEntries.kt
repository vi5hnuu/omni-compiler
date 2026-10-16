package solutions.laxmi.omnicompiler.feature.history

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.ui.res.stringResource
import solutions.laxmi.omnicompiler.core.designsystem.component.DetailPlaceholder
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import solutions.laxmi.omnicompiler.core.navigation.HistoryRoute
import solutions.laxmi.omnicompiler.core.navigation.JobDetailRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.historyEntries(navigator: Navigator) {
    // Large windows: runs on the left, the picked run's details on the right.
    entry<HistoryRoute>(
        metadata = ListDetailSceneStrategy.listPane(
            detailPlaceholder = {
                DetailPlaceholder(stringResource(R.string.history_pick_title), stringResource(R.string.history_pick_message), OmniIcons.History)
            },
        ),
    ) { HistoryScreen(navigator) }
    entry<JobDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route -> JobDetailScreen(route, navigator) }
}
