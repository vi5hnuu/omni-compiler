package solutions.laxmi.omnicompiler.feature.projects

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import solutions.laxmi.omnicompiler.core.navigation.ExamplesRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.navigation.ProblemRoute
import solutions.laxmi.omnicompiler.core.navigation.ProjectsRoute

fun EntryProviderScope<NavKey>.projectsEntries(navigator: Navigator) {
    entry<ProjectsRoute> { ProjectsScreen(navigator) }
    entry<ExamplesRoute> { ExamplesScreen(navigator) }
    entry<ProblemRoute> { route -> ProblemScreen(route, navigator) }
}
