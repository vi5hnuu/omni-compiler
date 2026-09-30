package solutions.laxmi.omnicompiler.feature.languages

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import solutions.laxmi.omnicompiler.core.navigation.LanguagePickerRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator

fun EntryProviderScope<NavKey>.languagesEntries(navigator: Navigator) {
    entry<LanguagePickerRoute> { route -> LanguagePickerScreen(route, navigator) }
}
