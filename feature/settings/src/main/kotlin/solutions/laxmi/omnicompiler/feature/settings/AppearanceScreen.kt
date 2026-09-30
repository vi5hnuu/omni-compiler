package solutions.laxmi.omnicompiler.feature.settings

import solutions.laxmi.omnicompiler.core.ui.descriptionRes
import solutions.laxmi.omnicompiler.core.ui.labelRes
import solutions.laxmi.omnicompiler.core.ui.displayName
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniListRow
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniSegmented
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniToggle
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTopBar
import solutions.laxmi.omnicompiler.core.designsystem.component.SectionLabel
import solutions.laxmi.omnicompiler.core.designsystem.theme.FiraCode
import solutions.laxmi.omnicompiler.core.designsystem.theme.IbmPlexMono
import solutions.laxmi.omnicompiler.core.designsystem.theme.JetBrainsMono
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.editor.EditorPalette
import solutions.laxmi.omnicompiler.core.editor.palette
import solutions.laxmi.omnicompiler.core.model.CodeFont
import solutions.laxmi.omnicompiler.core.model.EditorSettings
import solutions.laxmi.omnicompiler.core.model.EditorTheme
import solutions.laxmi.omnicompiler.core.model.LineSpacing
import solutions.laxmi.omnicompiler.core.navigation.Navigator

/** Design U5: editor theme, code font, size, ligatures, indent guides and line height. */
@Composable
fun AppearanceScreen(navigator: Navigator) {
    val viewModel = hiltViewModel<SettingsViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editor = state.editor
    // Segment labels are plain lambdas, so resolve the localized names up front.
    val spacingLabels = LineSpacing.entries.associateWith { stringResource(it.labelRes) }
    val colors = OmniTheme.colors
    Column(Modifier.fillMaxSize().background(colors.background).navigationBarsPadding()) {
        OmniTopBar(stringResource(R.string.appearance_title), onBack = navigator::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionLabel(stringResource(R.string.appearance_theme))
            EditorTheme.entries.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { theme ->
                        ThemeCard(theme, editor, selected = editor.theme == theme, modifier = Modifier.weight(1f)) {
                            viewModel.updateEditor { it.copy(theme = theme) }
                        }
                    }
                }
            }
            SectionLabel(stringResource(R.string.appearance_font))
            OmniSegmented(
                CodeFont.entries, editor.font, { it.displayName }, { font -> viewModel.updateEditor { it.copy(font = font) } },
                Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            )
            SectionLabel(stringResource(R.string.appearance_font_size)) { Text(stringResource(R.string.appearance_font_size_value, editor.fontSizeSp), style = OmniTheme.typography.mono, color = colors.textSecondary) }
            Slider(
                value = editor.fontSizeSp.toFloat(),
                onValueChange = { v -> viewModel.updateEditor { it.copy(fontSizeSp = v.toInt()) } },
                valueRange = EditorSettings.MIN_FONT_SP.toFloat()..EditorSettings.MAX_FONT_SP.toFloat(),
                steps = EditorSettings.MAX_FONT_SP - EditorSettings.MIN_FONT_SP - 1,
                colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.surfaceMuted),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            OmniListRow(stringResource(R.string.appearance_ligatures), subtitle = stringResource(R.string.appearance_ligatures_note), trailing = {
                OmniToggle(editor.ligatures, { v -> viewModel.updateEditor { it.copy(ligatures = v) } })
            })
            OmniListRow(stringResource(R.string.appearance_indent_guides), subtitle = stringResource(R.string.appearance_indent_guides_note), trailing = {
                OmniToggle(editor.indentGuides, { v -> viewModel.updateEditor { it.copy(indentGuides = v) } })
            })
            SectionLabel(stringResource(R.string.appearance_line_height))
            OmniSegmented(
                LineSpacing.entries, editor.lineSpacing, { spacingLabels.getValue(it) }, { spacing -> viewModel.updateEditor { it.copy(lineSpacing = spacing) } },
                Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            )
            SectionLabel(stringResource(R.string.appearance_preview))
            Preview(editor, Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun ThemeCard(theme: EditorTheme, settings: EditorSettings, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = OmniTheme.colors
    val palette = theme.palette()
    Column(
        modifier
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.border)
            .clickable(role = Role.RadioButton, onClick = onClick),
    ) {
        Column(Modifier.fillMaxWidth().height(64.dp).background(palette.background).padding(8.dp)) {
            Text(sampleLine(palette, 0), style = codeStyle(settings.font).copy(fontSize = 9.sp))
            Text(sampleLine(palette, 1), style = codeStyle(settings.font).copy(fontSize = 9.sp))
            Text(sampleLine(palette, 2), style = codeStyle(settings.font).copy(fontSize = 9.sp))
        }
        Column(Modifier.padding(8.dp)) {
            Text(stringResource(theme.labelRes), style = OmniTheme.typography.bodyStrong, color = colors.textPrimary)
            Text(stringResource(theme.descriptionRes), style = OmniTheme.typography.label, color = colors.textTertiary)
        }
    }
}

@Composable
private fun Preview(settings: EditorSettings, modifier: Modifier) {
    val palette = settings.theme.palette()
    val style = codeStyle(settings.font).copy(fontSize = settings.fontSizeSp.sp, lineHeight = (settings.fontSizeSp * settings.lineSpacing.multiplier).sp)
    Column(modifier.fillMaxWidth().background(palette.background).padding(12.dp)) {
        (0..3).forEach { Text(sampleLine(palette, it), style = style) }
    }
}

private fun codeStyle(font: CodeFont) = androidx.compose.ui.text.TextStyle(
    fontFamily = when (font) {
        CodeFont.JETBRAINS_MONO -> JetBrainsMono
        CodeFont.FIRA_CODE -> FiraCode
        CodeFont.IBM_PLEX_MONO -> IbmPlexMono
    },
)

/** A few syntax-coloured lines so each theme's roles are visible at a glance. */
private fun sampleLine(p: EditorPalette, index: Int) = buildAnnotatedString {
    fun kw(s: String) = withStyle(SpanStyle(color = p.keyword, fontWeight = FontWeight.SemiBold)) { append(s) }
    fun fn(s: String) = withStyle(SpanStyle(color = p.function, fontWeight = FontWeight.SemiBold)) { append(s) }
    fun ty(s: String) = withStyle(SpanStyle(color = p.type)) { append(s) }
    fun txt(s: String) = withStyle(SpanStyle(color = p.text)) { append(s) }
    fun num(s: String) = withStyle(SpanStyle(color = p.number)) { append(s) }
    fun str(s: String) = withStyle(SpanStyle(color = p.string)) { append(s) }
    fun com(s: String) = withStyle(SpanStyle(color = p.comment, fontStyle = FontStyle.Italic)) { append(s) }
    when (index) {
        0 -> { kw("int "); fn("main"); txt("() {") }
        1 -> { txt("  "); ty("long "); txt("n = "); num("42"); txt(";") }
        2 -> { txt("  print("); str("\"n >= 0\""); txt(");") }
        else -> { txt("  "); com("// return value -> exit code") }
    }
}
