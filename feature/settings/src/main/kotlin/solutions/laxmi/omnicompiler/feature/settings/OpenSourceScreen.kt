package solutions.laxmi.omnicompiler.feature.settings

import androidx.annotation.RawRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.entity.License
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import solutions.laxmi.omnicompiler.core.designsystem.component.InfoBanner
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniListRow
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniSpinner
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTopBar
import solutions.laxmi.omnicompiler.core.designsystem.component.SheetHandle
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.ui.openUrl

/**
 * Third-party notices generated at build time by AboutLibraries (all Gradle dependencies plus the
 * manual entries in config/aboutlibraries). The LGPL section states the relinking/reverse-engineering
 * permissions that LGPL-2.1 §6 requires for the bundled Sora Editor.
 */
@Composable
fun OpenSourceScreen(navigator: Navigator, @RawRes licensesResId: Int) {
    val context = LocalContext.current
    val libraries by produceState<List<Library>?>(null, licensesResId) {
        value = withContext(Dispatchers.IO) {
            val json = context.resources.openRawResource(licensesResId).bufferedReader().use { it.readText() }
            Libs.Builder().withJson(json).build().libraries.sortedBy { it.name.lowercase() }
        }
    }
    var selected by remember { mutableStateOf<Library?>(null) }
    val colors = OmniTheme.colors
    Column(Modifier.fillMaxSize().background(colors.background).navigationBarsPadding()) {
        OmniTopBar("Open-source licenses", onBack = navigator::back, subtitle = libraries?.let { "${it.size} components" })
        val list = libraries
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { OmniSpinner() }
            return@Column
        }
        LazyColumn(Modifier.weight(1f)) {
            list.firstOrNull { it.uniqueId == SORA_EDITOR_ID }?.let { sora ->
                item { LgplNotice(sora.artifactVersion.orEmpty()) { context.openUrl(it) } }
            }
            items(list, key = { it.uniqueId }) { library ->
                OmniListRow(
                    title = library.name,
                    subtitle = listOfNotNull(
                        library.artifactVersion?.takeIf { it.isNotBlank() },
                        library.distinctLicenses().joinToString(", ") { it.spdxId ?: it.name }.ifBlank { null },
                    ).joinToString(" · "),
                    onClick = { selected = library },
                )
            }
        }
    }
    selected?.let { library -> LicenseSheet(library, onDismiss = { selected = null }) { context.openUrl(it) } }
}

@Composable
private fun LgplNotice(version: String, onOpen: (String) -> Unit) {
    val source = "https://github.com/Rosemoe/sora-editor/tree/$version"
    InfoBanner(
        title = "Sora Editor · LGPL-2.1",
        icon = OmniIcons.Info,
        text = "omni compiler uses Sora Editor $version without modifications. Its complete corresponding source code is " +
            "available at $source. You may replace the library with a modified version; it ships unobfuscated so the " +
            "app can be relinked against your build. Nothing in our Terms restricts your rights under the LGPL, including " +
            "modifying the library and reverse engineering the app to debug such modifications.",
        modifier = Modifier.padding(16.dp),
        action = { OmniTextButton("View source", { onOpen(source) }) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicenseSheet(library: Library, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    val colors = OmniTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RectangleShape,
        containerColor = colors.surface,
        scrimColor = colors.scrim,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(library.name, style = OmniTheme.typography.title, color = colors.textPrimary)
            library.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = OmniTheme.typography.bodySmall, color = colors.textSecondary) }
            val developers = library.developers.mapNotNull { it.name }.joinToString(", ")
            if (developers.isNotBlank()) Text(developers, style = OmniTheme.typography.bodySmall, color = colors.textTertiary)
            (library.scm?.url ?: library.website)?.let { url -> OmniTextButton("Source / website", { onOpen(url) }) }
            library.distinctLicenses().forEach { license ->
                Text(license.name, style = OmniTheme.typography.bodyStrong, color = colors.textPrimary)
                val content = license.licenseContent
                if (content.isNullOrBlank()) {
                    license.url?.let { url -> OmniTextButton("Read license", { onOpen(url) }) }
                } else {
                    Text(content, style = OmniTheme.typography.mono, color = colors.textSecondary)
                }
            }
        }
    }
}

/** A library can list the same license under an SPDX id and a raw POM hash; show it once. */
private fun Library.distinctLicenses(): List<License> = licenses.distinctBy { it.name.lowercase() }

private const val SORA_EDITOR_ID = "io.github.rosemoe:editor"
