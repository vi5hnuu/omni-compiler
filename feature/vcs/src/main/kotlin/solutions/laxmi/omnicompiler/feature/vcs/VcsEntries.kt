package solutions.laxmi.omnicompiler.feature.vcs

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import solutions.laxmi.omnicompiler.core.navigation.GitAccountsRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.navigation.RepoImportRoute
import solutions.laxmi.omnicompiler.core.navigation.SourceControlRoute

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.vcsEntries(navigator: Navigator) {
    // A Settings page: beside Settings on large windows, on its own when opened from repository import.
    entry<GitAccountsRoute>(metadata = ListDetailSceneStrategy.detailPane()) { GitAccountsScreen(navigator) }
    entry<RepoImportRoute> { RepoImportScreen(navigator) }
    entry<SourceControlRoute> { route -> SourceControlScreen(route, navigator) }
}
