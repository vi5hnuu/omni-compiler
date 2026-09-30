package solutions.laxmi.omnicompiler.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniListRow
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniSegmented
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniToggle
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTopBar
import solutions.laxmi.omnicompiler.core.designsystem.component.SectionLabel
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.model.EditorSettings
import solutions.laxmi.omnicompiler.core.model.Limits
import solutions.laxmi.omnicompiler.core.navigation.AppearanceRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.navigation.ProfileRoute
import solutions.laxmi.omnicompiler.core.navigation.WelcomeRoute
import solutions.laxmi.omnicompiler.core.ui.LanguageTile
import solutions.laxmi.omnicompiler.core.ui.openUrl

/** Design U4 (2-step verification row removed: the auth service has no MFA). */
@Composable
fun SettingsScreen(navigator: Navigator, appVersion: String) {
    val viewModel = hiltViewModel<SettingsViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pickingDefault by rememberSaveable { mutableStateOf(false) }
    var editingLimits by rememberSaveable { mutableStateOf(false) }
    var showingLicenses by rememberSaveable { mutableStateOf(false) }
    val colors = OmniTheme.colors
    val editor = state.editor
    val defaultRuntime = state.run.defaultRuntimeId
    Column(Modifier.fillMaxSize().background(colors.background).navigationBarsPadding()) {
        OmniTopBar("Settings", onBack = navigator::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionLabel("Editor")
            OmniListRow(
                "Theme · font",
                subtitle = "${editor.theme.label} · ${editor.font.label} ${editor.fontSizeSp}px",
                trailing = { Chevron() },
                onClick = { navigator.navigate(AppearanceRoute) },
            )
            ToggleRow("Minimap", "Code overview beside the editor", editor.minimap) { v -> viewModel.updateEditor { it.copy(minimap = v) } }
            ToggleRow("Symbol row above keyboard", "{ } ( ) ; and more, one tap away", editor.symbolRow) { v -> viewModel.updateEditor { it.copy(symbolRow = v) } }
            ToggleRow("Autocomplete", "Suggest identifiers and keywords while typing", editor.autocomplete) { v -> viewModel.updateEditor { it.copy(autocomplete = v) } }
            ToggleRow("Word wrap", "Long lines scroll sideways when off", editor.wordWrap) { v -> viewModel.updateEditor { it.copy(wordWrap = v) } }
            OmniListRow("Tab size", trailing = {
                OmniSegmented(EditorSettings.TabSizes, editor.tabSize, { "$it" }, { size -> viewModel.updateEditor { it.copy(tabSize = size) } }, Modifier.width(132.dp))
            })

            SectionLabel("Run")
            OmniListRow(
                "Default language",
                subtitle = defaultRuntime ?: "Chosen automatically (Python, C++, …)",
                trailing = { Chevron() },
                onClick = { pickingDefault = true },
            )
            OmniListRow(
                "Default limits",
                subtitle = "${state.run.defaultLimits.timeMs} ms · ${state.run.defaultLimits.memMb} MB for new projects",
                trailing = { Chevron() },
                onClick = { editingLimits = true },
            )
            ToggleRow("Run on Ctrl + Enter", "With a hardware keyboard", state.run.runOnCtrlEnter) { v -> viewModel.updateRun { it.copy(runOnCtrlEnter = v) } }
            ToggleRow("Skip result cache", "Always execute, even for an identical recent submission", state.run.bypassCache) { v -> viewModel.updateRun { it.copy(bypassCache = v) } }
            ToggleRow("Send queued runs when back online", "Runs started offline go out automatically", state.run.sendQueuedWhenOnline) { v -> viewModel.updateRun { it.copy(sendQueuedWhenOnline = v) } }

            SectionLabel("Account")
            val user = state.user
            OmniListRow(
                title = user?.let { if (it.isGuest) "Guest account" else it.email ?: it.displayName } ?: "Not signed in",
                subtitle = if (user == null) "Sign in to sync run history" else "Profile, password, delete account",
                trailing = { Chevron() },
                onClick = { navigator.navigate(if (user == null) WelcomeRoute else ProfileRoute) },
            )
            if (user != null) {
                OmniListRow("Sign out", titleColor = colors.accentText, onClick = viewModel::signOut)
            }

            SectionLabel("About")
            OmniListRow("Privacy policy", trailing = { Icon(OmniIcons.ExternalLink, null, tint = colors.textTertiary) }, onClick = { context.openUrl(viewModel.config.privacyPolicyUrl) })
            OmniListRow("Terms of service", trailing = { Icon(OmniIcons.ExternalLink, null, tint = colors.textTertiary) }, onClick = { context.openUrl(viewModel.config.termsUrl) })
            OmniListRow("Open-source notices", trailing = { Chevron() }, onClick = { showingLicenses = true })
            OmniListRow("Version", trailing = { Text(appVersion, style = OmniTheme.typography.mono, color = colors.textTertiary) })
        }
    }
    if (pickingDefault) {
        AlertDialog(
            onDismissRequest = { pickingDefault = false },
            shape = RectangleShape,
            containerColor = colors.surfaceRaised,
            title = { Text("Default language", style = OmniTheme.typography.title, color = colors.textPrimary) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    item {
                        OmniListRow("Automatic", subtitle = "Python, then C++, JavaScript, Java", selected = defaultRuntime == null, onClick = {
                            viewModel.setDefaultRuntime(null)
                            pickingDefault = false
                        })
                    }
                    items(state.languages, key = { it.base }) { language ->
                        val runtime = language.defaultRuntime!!
                        OmniListRow(
                            language.info.name,
                            subtitle = runtime.id,
                            selected = runtime.id == defaultRuntime,
                            leading = { LanguageTile(language.info.shortCode, selected = runtime.id == defaultRuntime) },
                            onClick = {
                                viewModel.setDefaultRuntime(runtime.id)
                                pickingDefault = false
                            },
                        )
                    }
                }
            },
            confirmButton = { OmniTextButton("Close", { pickingDefault = false }, color = colors.textSecondary) },
        )
    }
    if (editingLimits) {
        LimitsDialog(state.run.defaultLimits, onDismiss = { editingLimits = false }) {
            viewModel.setDefaultLimits(it)
            editingLimits = false
        }
    }
    if (showingLicenses) {
        AlertDialog(
            onDismissRequest = { showingLicenses = false },
            shape = RectangleShape,
            containerColor = colors.surfaceRaised,
            title = { Text("Open-source notices", style = OmniTheme.typography.title, color = colors.textPrimary) },
            text = {
                Text(
                    OPEN_SOURCE_NOTICES,
                    style = OmniTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { OmniTextButton("Close", { showingLicenses = false }, color = colors.textSecondary) },
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    OmniListRow(title, subtitle = subtitle, trailing = { OmniToggle(checked, onChange) }, onClick = { onChange(!checked) })
}

@Composable
private fun Chevron() = Icon(OmniIcons.ChevronRight, null, tint = OmniTheme.colors.textTertiary)

@Composable
private fun LimitsDialog(current: Limits, onDismiss: () -> Unit, onSave: (Limits) -> Unit) {
    var time by rememberSaveable { mutableStateOf(current.timeMs.toString()) }
    var memory by rememberSaveable { mutableStateOf(current.memMb.toString()) }
    val timeValue = time.toIntOrNull()
    val memValue = memory.toIntOrNull()
    val valid = timeValue in 1..Limits.MAX_TIME_MS && memValue in 1..Limits.MAX_MEM_MB
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = OmniTheme.colors.surfaceRaised,
        title = { Text("Default limits", style = OmniTheme.typography.title, color = OmniTheme.colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextField(time, { time = it.filter(Char::isDigit) }, label = "Time limit per test (ms, ≤ 30000)")
                solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextField(memory, { memory = it.filter(Char::isDigit) }, label = "Memory per run (MB, ≤ 1024)")
            }
        },
        confirmButton = { OmniTextButton("Save", { onSave(Limits(timeValue!!, memValue!!)) }, enabled = valid) },
        dismissButton = { OmniTextButton("Cancel", onDismiss, color = OmniTheme.colors.textSecondary) },
    )
}

private val OPEN_SOURCE_NOTICES = """
    Fonts: Archivo, JetBrains Mono, Fira Code and IBM Plex Mono are licensed under the SIL Open Font License 1.1.

    Code editor: Sora Editor (LGPL-2.1) with TextMate support from Eclipse tm4e (EPL-2.0).

    Syntax grammars: from the tm-grammars collection; each grammar keeps its original MIT, BSD or Apache-2.0 license.

    Libraries: AndroidX, Jetpack Compose, Kotlin, kotlinx, OkHttp, Retrofit, Dagger/Hilt, Tink and Coil are licensed under Apache-2.0.
""".trimIndent()
