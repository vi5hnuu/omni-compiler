package solutions.laxmi.omnicompiler.feature.vcs

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import solutions.laxmi.omnicompiler.core.navigation.GitAccountsRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.navigation.RepoImportRoute
import solutions.laxmi.omnicompiler.core.navigation.SourceControlRoute

fun EntryProviderScope<NavKey>.vcsEntries(navigator: Navigator) {
    entry<GitAccountsRoute> { GitAccountsScreen(navigator) }
    entry<RepoImportRoute> { RepoImportScreen(navigator) }
    entry<SourceControlRoute> { route -> SourceControlScreen(route, navigator) }
}
