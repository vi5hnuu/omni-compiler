package solutions.laxmi.omnicompiler.feature.projects

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.ui.res.stringResource
import solutions.laxmi.omnicompiler.core.designsystem.component.DetailPlaceholder
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import solutions.laxmi.omnicompiler.core.navigation.ExamplesRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.navigation.ProblemRoute
import solutions.laxmi.omnicompiler.core.navigation.ProjectFolderRoute
import solutions.laxmi.omnicompiler.core.navigation.ProjectsRoute

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.projectsEntries(navigator: Navigator) {
    entry<ProjectsRoute> { ProjectsScreen(navigator) }
    // Large windows: the practice list on the left, the picked problem on the right.
    entry<ExamplesRoute>(
        metadata = ListDetailSceneStrategy.listPane(
            detailPlaceholder = {
                DetailPlaceholder(stringResource(R.string.practice_pick_title), stringResource(R.string.practice_pick_message), OmniIcons.Book)
            },
        ),
    ) { ExamplesScreen(navigator) }
    entry<ProblemRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route -> ProblemScreen(route, navigator) }
    entry<ProjectFolderRoute> { route -> ProjectFolderScreen(route, navigator) }
}
