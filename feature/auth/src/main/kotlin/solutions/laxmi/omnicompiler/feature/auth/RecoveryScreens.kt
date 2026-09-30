package solutions.laxmi.omnicompiler.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import solutions.laxmi.omnicompiler.core.designsystem.component.InfoBanner
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButtonStyle
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextField
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTopBar
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.navigation.CheckInboxRoute
import solutions.laxmi.omnicompiler.core.navigation.EditorRoute
import solutions.laxmi.omnicompiler.core.navigation.ForgotPasswordRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.navigation.SignInRoute

/**
 * Design A5. Verification finishes in the browser (the auth service renders the result page and
 * has no app link), so this screen explains that and offers resend + sign-in.
 */
@Composable
fun CheckInboxScreen(route: CheckInboxRoute, navigator: Navigator) {
    val viewModel = hiltViewModel<CheckInboxViewModel>()
    val cooldown by viewModel.resendCooldown.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    val colors = OmniTheme.colors
    AuthScaffold(snackbar, topBar = { OmniTopBar("", onBack = navigator::back) }) {
        AuthHeading("Check your inbox", "We sent a verification link to ${route.email}. It expires in 24 hours.")
        OmniButton("Open e-mail app", {
            if (!context.openEmailApp()) { /* no mail app: the address is on screen */ }
        }, leadingIcon = OmniIcons.Mail)
        OmniButton(
            if (cooldown > 0) "Resend link · 0:%02d".format(cooldown) else "Resend link",
            { viewModel.resend(route.email) },
            style = OmniButtonStyle.Outline,
            enabled = cooldown == 0,
            trailingIcon = OmniIcons.Refresh,
        )
        Text("WHILE YOU WAIT", style = OmniTheme.typography.overline, color = colors.textTertiary, modifier = Modifier.padding(top = 8.dp))
        listOf(
            "Your projects are saved on this device and stay put.",
            if (signedIn) "You can keep coding; password sign-in on other devices works once you verify." else "After opening the link, come back and sign in with your password.",
        ).forEachIndexed { i, text ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("%02d".format(i + 1), style = OmniTheme.typography.mono, color = colors.accentText)
                Text(text, style = OmniTheme.typography.body, color = colors.textSecondary)
            }
        }
        if (signedIn) {
            OmniButton("Continue to editor", { navigator.resetTo(EditorRoute()) }, modifier = Modifier.padding(top = 8.dp))
        } else {
            OmniButton("I've verified · Sign in", { navigator.replace(SignInRoute) }, modifier = Modifier.padding(top = 8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Wrong address? ", style = OmniTheme.typography.body, color = colors.textSecondary)
            OmniTextButton("Change e-mail", navigator::back)
        }
    }
}

/** Design A6. The reset itself happens on the auth service's web page opened from the e-mail. */
@Composable
fun ForgotPasswordScreen(route: ForgotPasswordRoute, navigator: Navigator) {
    val viewModel = hiltViewModel<ForgotPasswordViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(route.email) { viewModel.init(route.email) }
    AuthScaffold(snackbar, topBar = { OmniTopBar("", onBack = navigator::back) }) {
        AuthHeading("Reset password", "Enter the e-mail you signed up with and we'll send you a reset link.")
        OmniTextField(
            state.email, viewModel::setEmail, label = "E-mail", placeholder = "ada@lovelace.dev", error = state.error,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onSend = { viewModel.send() }),
        )
        OmniButton(if (state.sent) "Send again" else "Send reset link", viewModel::send, loading = busy)
        if (state.sent) {
            InfoBanner(
                "If ${state.email} has an account, a link is on its way. Open it to choose a new password, then sign in here. Resetting signs you out on every device.",
                title = "Link sent",
                icon = OmniIcons.Mail,
                action = { OmniTextButton("Open e-mail app", { context.openEmailApp() }) },
            )
            OmniButton("Back to sign in", { navigator.replace(SignInRoute) }, style = OmniButtonStyle.Outline, trailingIcon = null)
        }
    }
}
