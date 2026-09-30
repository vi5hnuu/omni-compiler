package solutions.laxmi.omnicompiler.feature.settings

import solutions.laxmi.omnicompiler.core.navigation.ProjectFolderRoute
import solutions.laxmi.omnicompiler.core.ui.labelRes
import solutions.laxmi.omnicompiler.core.ui.displayName
import solutions.laxmi.omnicompiler.core.ui.R as CommonR
import androidx.compose.ui.res.stringResource
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
import solutions.laxmi.omnicompiler.core.navigation.OpenSourceRoute
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
    val colors = OmniTheme.colors
    val editor = state.editor
    val defaultRuntime = state.run.defaultRuntimeId
    Column(Modifier.fillMaxSize().background(colors.background).navigationBarsPadding()) {
        OmniTopBar(stringResource(R.string.settings_title), onBack = navigator::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionLabel(stringResource(R.string.settings_editor))
            OmniListRow(
                stringResource(R.string.settings_theme_font),
                subtitle = stringResource(R.string.settings_theme_font_value, stringResource(editor.theme.labelRes), editor.font.displayName, editor.fontSizeSp),
                trailing = { Chevron() },
                onClick = { navigator.navigate(AppearanceRoute) },
            )
            ToggleRow(stringResource(R.string.settings_minimap), stringResource(R.string.settings_minimap_note), editor.minimap) { v -> viewModel.updateEditor { it.copy(minimap = v) } }
            ToggleRow(stringResource(R.string.settings_symbol_row), stringResource(R.string.settings_symbol_row_note), editor.symbolRow) { v -> viewModel.updateEditor { it.copy(symbolRow = v) } }
            ToggleRow(stringResource(R.string.settings_autocomplete), stringResource(R.string.settings_autocomplete_note), editor.autocomplete) { v -> viewModel.updateEditor { it.copy(autocomplete = v) } }
            ToggleRow(stringResource(R.string.settings_word_wrap), stringResource(R.string.settings_word_wrap_note), editor.wordWrap) { v -> viewModel.updateEditor { it.copy(wordWrap = v) } }
            ToggleRow(stringResource(R.string.settings_sticky_scroll), stringResource(R.string.settings_sticky_scroll_note), editor.stickyScroll) { v -> viewModel.updateEditor { it.copy(stickyScroll = v) } }
            ToggleRow(stringResource(R.string.settings_invisibles), stringResource(R.string.settings_invisibles_note), editor.showInvisibles) { v -> viewModel.updateEditor { it.copy(showInvisibles = v) } }
            ToggleRow(stringResource(R.string.settings_hardware_keyboard), stringResource(R.string.settings_hardware_keyboard_note), editor.hardwareKeyboardOnly) { v -> viewModel.updateEditor { it.copy(hardwareKeyboardOnly = v) } }
            OmniListRow(stringResource(R.string.settings_tab_size), trailing = {
                OmniSegmented(EditorSettings.TabSizes, editor.tabSize, { "$it" }, { size -> viewModel.updateEditor { it.copy(tabSize = size) } }, Modifier.width(132.dp))
            })

            SectionLabel(stringResource(R.string.settings_storage))
            OmniListRow(
                stringResource(R.string.settings_projects_folder),
                subtitle = state.projectsFolder ?: stringResource(R.string.settings_projects_folder_none),
                trailing = { Chevron() },
                onClick = { navigator.navigate(ProjectFolderRoute(change = true)) },
            )
            SectionLabel(stringResource(R.string.settings_run))
            OmniListRow(
                stringResource(R.string.settings_default_language),
                subtitle = defaultRuntime ?: stringResource(R.string.settings_default_language_auto),
                trailing = { Chevron() },
                onClick = { pickingDefault = true },
            )
            OmniListRow(
                stringResource(R.string.settings_default_limits),
                subtitle = stringResource(R.string.settings_default_limits_value, state.run.defaultLimits.timeMs, state.run.defaultLimits.memMb),
                trailing = { Chevron() },
                onClick = { editingLimits = true },
            )
            ToggleRow(stringResource(R.string.settings_ctrl_enter), stringResource(R.string.settings_ctrl_enter_note), state.run.runOnCtrlEnter) { v -> viewModel.updateRun { it.copy(runOnCtrlEnter = v) } }
            ToggleRow(stringResource(R.string.settings_skip_cache), stringResource(R.string.settings_skip_cache_note), state.run.bypassCache) { v -> viewModel.updateRun { it.copy(bypassCache = v) } }
            ToggleRow(stringResource(R.string.settings_send_queued), stringResource(R.string.settings_send_queued_note), state.run.sendQueuedWhenOnline) { v -> viewModel.updateRun { it.copy(sendQueuedWhenOnline = v) } }

            SectionLabel(stringResource(R.string.settings_account))
            val user = state.user
            OmniListRow(
                title = user?.let { if (it.isGuest) stringResource(R.string.settings_guest_account) else it.email ?: it.displayName } ?: stringResource(R.string.settings_not_signed_in),
                subtitle = stringResource(if (user == null) R.string.settings_sign_in_note else R.string.settings_account_note),
                trailing = { Chevron() },
                onClick = { navigator.navigate(if (user == null) WelcomeRoute else ProfileRoute) },
            )
            if (user != null) {
                OmniListRow(stringResource(R.string.settings_sign_out), titleColor = colors.accentText, onClick = viewModel::signOut)
            }

            SectionLabel(stringResource(R.string.settings_about))
            OmniListRow(stringResource(R.string.settings_privacy), trailing = { Icon(OmniIcons.ExternalLink, null, tint = colors.textTertiary) }, onClick = { context.openUrl(viewModel.config.privacyPolicyUrl) })
            OmniListRow(stringResource(R.string.settings_terms), trailing = { Icon(OmniIcons.ExternalLink, null, tint = colors.textTertiary) }, onClick = { context.openUrl(viewModel.config.termsUrl) })
            OmniListRow(stringResource(R.string.settings_open_source), trailing = { Chevron() }, onClick = { navigator.navigate(OpenSourceRoute) })
            OmniListRow(stringResource(R.string.settings_version), trailing = { Text(appVersion, style = OmniTheme.typography.mono, color = colors.textTertiary) })
        }
    }
    if (pickingDefault) {
        AlertDialog(
            onDismissRequest = { pickingDefault = false },
            shape = RectangleShape,
            containerColor = colors.surfaceRaised,
            title = { Text(stringResource(R.string.settings_default_language), style = OmniTheme.typography.title, color = colors.textPrimary) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    item {
                        OmniListRow(stringResource(R.string.settings_automatic), subtitle = stringResource(R.string.settings_automatic_note), selected = defaultRuntime == null, onClick = {
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
            confirmButton = { OmniTextButton(stringResource(CommonR.string.common_close), { pickingDefault = false }, color = colors.textSecondary) },
        )
    }
    if (editingLimits) {
        LimitsDialog(state.run.defaultLimits, onDismiss = { editingLimits = false }) {
            viewModel.setDefaultLimits(it)
            editingLimits = false
        }
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
        title = { Text(stringResource(R.string.settings_default_limits), style = OmniTheme.typography.title, color = OmniTheme.colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextField(time, { time = it.filter(Char::isDigit) }, label = stringResource(R.string.settings_limit_time_field, Limits.MAX_TIME_MS))
                solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextField(memory, { memory = it.filter(Char::isDigit) }, label = stringResource(R.string.settings_limit_memory_field, Limits.MAX_MEM_MB))
            }
        },
        confirmButton = { OmniTextButton(stringResource(CommonR.string.common_save), { onSave(Limits(timeValue!!, memValue!!)) }, enabled = valid) },
        dismissButton = { OmniTextButton(stringResource(CommonR.string.common_cancel), onDismiss, color = OmniTheme.colors.textSecondary) },
    )
}
