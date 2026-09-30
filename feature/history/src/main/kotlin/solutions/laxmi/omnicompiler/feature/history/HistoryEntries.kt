package solutions.laxmi.omnicompiler.feature.history

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import solutions.laxmi.omnicompiler.core.navigation.HistoryRoute
import solutions.laxmi.omnicompiler.core.navigation.JobDetailRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator

fun EntryProviderScope<NavKey>.historyEntries(navigator: Navigator) {
    entry<HistoryRoute> { HistoryScreen(navigator) }
    entry<JobDetailRoute> { route -> JobDetailScreen(route, navigator) }
}
