package solutions.laxmi.omnicompiler.feature.workspace

import solutions.laxmi.omnicompiler.core.navigation.PreviewRoute
import solutions.laxmi.omnicompiler.core.navigation.ExternalFileRoute
import solutions.laxmi.omnicompiler.feature.workspace.external.ExternalFileScreen
import solutions.laxmi.omnicompiler.feature.workspace.preview.PreviewScreen
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import solutions.laxmi.omnicompiler.core.navigation.EditorRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.feature.workspace.editor.EditorScreen

/** Registers the workspace destinations with the app's NavDisplay. */
fun EntryProviderScope<NavKey>.workspaceEntries(navigator: Navigator) {
    entry<EditorRoute> { route -> EditorScreen(route, navigator) }
    entry<PreviewRoute> { route -> PreviewScreen(route, navigator) }
    entry<ExternalFileRoute> { route -> ExternalFileScreen(route, navigator) }
}
