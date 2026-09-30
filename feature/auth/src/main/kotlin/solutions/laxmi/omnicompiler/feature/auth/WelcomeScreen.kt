package solutions.laxmi.omnicompiler.feature.auth

import solutions.laxmi.omnicompiler.core.ui.labelRes
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import solutions.laxmi.omnicompiler.core.designsystem.component.InfoBanner
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButtonStyle
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniCompactButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextButton
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.editor.palette
import solutions.laxmi.omnicompiler.core.model.EditorTheme
import solutions.laxmi.omnicompiler.core.model.Verdict
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.navigation.SignInRoute
import solutions.laxmi.omnicompiler.core.ui.VerdictBadge
import solutions.laxmi.omnicompiler.core.ui.openUrl

/** Design A1. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WelcomeScreen(navigator: Navigator, appVersion: String) {
    val viewModel = hiltViewModel<WelcomeViewModel>()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    AuthEvents(viewModel, navigator, snackbar)
    val colors = OmniTheme.colors
    AuthScaffold(
        snackbar = snackbar,
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Wordmark()
                Box(Modifier.weight(1f))
                OmniTextButton(stringResource(R.string.welcome_skip), viewModel::continueAsGuest, enabled = busy == null, color = colors.textSecondary)
            }
        },
        footer = {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OmniTextButton(stringResource(R.string.welcome_terms), { context.openUrl(viewModel.config.termsUrl) }, color = colors.textTertiary)
                Text(" · ", color = colors.textTertiary)
                OmniTextButton(stringResource(R.string.welcome_privacy), { context.openUrl(viewModel.config.privacyPolicyUrl) }, color = colors.textTertiary)
                Box(Modifier.weight(1f))
                Text(stringResource(R.string.welcome_version, appVersion), style = OmniTheme.typography.monoSmall, color = colors.textTertiary)
            }
        },
        // Hero at the top, sign-in choices just above the footer on tall screens (design A1).
        contentArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (viewModel.sessionExpired) InfoBanner(stringResource(R.string.welcome_session_expired), icon = OmniIcons.Info)
            DemoCard()
            Text(
                (
                    pluralStringResource(R.plurals.welcome_languages, counts.languages, counts.languages).let { languages ->
                        if (counts.runtimes > 0) stringResource(R.string.welcome_counts, languages, pluralStringResource(R.plurals.welcome_runtimes, counts.runtimes, counts.runtimes)) else languages
                    }
                ).uppercase(),
                style = OmniTheme.typography.overline,
                color = colors.accentText,
            )
            Text(stringResource(R.string.welcome_headline), style = OmniTheme.typography.display, color = colors.textPrimary)
            Text(
                stringResource(R.string.welcome_subtitle),
                style = OmniTheme.typography.body,
                color = colors.textSecondary,
            )
        }
        Column(Modifier.padding(top = 24.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (viewModel.googleAvailable) {
                GoogleButton(stringResource(R.string.welcome_google), loading = busy == AuthAction.Google, enabled = busy == null) {
                    viewModel.signInWithGoogle(context.findActivityContext())
                }
            }
            OmniButton(
                stringResource(R.string.welcome_email),
                { navigator.navigate(SignInRoute) },
                style = OmniButtonStyle.Outline,
                leadingIcon = OmniIcons.Mail,
                enabled = busy == null,
            )
            // Wraps under long translations instead of squeezing the note.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                OmniTextButton(stringResource(if (busy == AuthAction.Guest) R.string.welcome_guest_starting else R.string.welcome_guest), viewModel::continueAsGuest, enabled = busy == null)
                Text(stringResource(R.string.welcome_guest_note), style = OmniTheme.typography.bodySmall, color = colors.textTertiary)
            }
        }
    }
}

/** Static sample of what a run looks like (design A1 card). */
@Composable
private fun DemoCard() {
    val colors = OmniTheme.colors
    val p = EditorTheme.SIGNAL.palette()
    val code = listOf(
        buildAnnotatedString { withStyle(SpanStyle(color = p.preprocessor, fontWeight = FontWeight.SemiBold)) { append("#include ") }; withStyle(SpanStyle(color = p.string)) { append("<iostream>") } },
        buildAnnotatedString { withStyle(SpanStyle(color = p.keyword, fontWeight = FontWeight.SemiBold)) { append("int ") }; withStyle(SpanStyle(color = p.function, fontWeight = FontWeight.SemiBold)) { append("main") }; withStyle(SpanStyle(color = p.text)) { append("() {") } },
        buildAnnotatedString { withStyle(SpanStyle(color = p.keyword, fontWeight = FontWeight.SemiBold)) { append("    int ") }; withStyle(SpanStyle(color = p.text)) { append("a, b;") } },
        buildAnnotatedString { withStyle(SpanStyle(color = p.text)) { append("    std::") }; withStyle(SpanStyle(color = p.type)) { append("cin") }; withStyle(SpanStyle(color = p.operator)) { append(" >> ") }; withStyle(SpanStyle(color = p.text)) { append("a") }; withStyle(SpanStyle(color = p.operator)) { append(" >> ") }; withStyle(SpanStyle(color = p.text)) { append("b;") } },
        buildAnnotatedString { withStyle(SpanStyle(color = p.text)) { append("    std::") }; withStyle(SpanStyle(color = p.type)) { append("cout") }; withStyle(SpanStyle(color = p.operator)) { append(" << ") }; withStyle(SpanStyle(color = p.text)) { append("a + b;") } },
        buildAnnotatedString { withStyle(SpanStyle(color = p.text)) { append("}") } },
    )
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).background(colors.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("C+", style = OmniTheme.typography.badge, color = colors.accentText)
            Text(stringResource(R.string.welcome_demo_file), style = OmniTheme.typography.mono, color = colors.textSecondary, modifier = Modifier.weight(1f))
            OmniCompactButton(stringResource(R.string.welcome_run_demo), OmniIcons.Play, {}, height = 26.dp, enabled = true)
        }
        Column(Modifier.background(p.background).fillMaxWidth().padding(vertical = 8.dp)) {
            code.forEachIndexed { i, line ->
                Row {
                    Text("${i + 1}", style = OmniTheme.typography.mono, color = p.lineNumber, modifier = Modifier.width(28.dp).padding(end = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    Text(line, style = OmniTheme.typography.mono)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VerdictBadge(Verdict.AC)
            Text(stringResource(Verdict.AC.labelRes), style = OmniTheme.typography.bodyStrong, color = colors.textPrimary)
            Text(stringResource(R.string.welcome_demo_meta), style = OmniTheme.typography.monoSmall, color = colors.textTertiary, modifier = Modifier.weight(1f))
            repeat(3) { Box(Modifier.size(8.dp).background(colors.textPrimary)) }
        }
    }
}
