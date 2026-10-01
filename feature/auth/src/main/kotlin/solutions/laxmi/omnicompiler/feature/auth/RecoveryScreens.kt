package solutions.laxmi.omnicompiler.feature.auth

import solutions.laxmi.omnicompiler.core.ui.asString
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    val resources = LocalResources.current
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it.asString(resources)) } }
    val colors = OmniTheme.colors
    AuthScaffold(
        snackbar,
        topBar = { AuthTopBar(navigator::back) },
        footer = { AccountSwitchRow(stringResource(R.string.inbox_wrong_address), stringResource(R.string.inbox_change_email), navigator::back) },
    ) {
        AuthHeading(stringResource(R.string.inbox_title), stringResource(R.string.inbox_subtitle, route.email))
        OmniButton(stringResource(R.string.inbox_open_email), {
            if (!context.openEmailApp()) { /* no mail app: the address is on screen */ }
        }, leadingIcon = OmniIcons.Mail)
        OmniButton(
            if (cooldown > 0) stringResource(R.string.inbox_resend_cooldown, cooldown) else stringResource(R.string.inbox_resend),
            { viewModel.resend(route.email) },
            style = OmniButtonStyle.Outline,
            enabled = cooldown == 0,
            trailingIcon = OmniIcons.Refresh,
        )
        Text(stringResource(R.string.inbox_while_you_wait).uppercase(), style = OmniTheme.typography.overline, color = colors.textTertiary, modifier = Modifier.padding(top = 8.dp))
        listOf(
            stringResource(R.string.inbox_step_projects),
            stringResource(if (signedIn) R.string.inbox_step_signed_in else R.string.inbox_step_signed_out),
        ).forEachIndexed { i, text ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("%02d".format(i + 1), style = OmniTheme.typography.mono, color = colors.accentText)
                Text(text, style = OmniTheme.typography.body, color = colors.textSecondary)
            }
        }
        if (signedIn) {
            OmniButton(stringResource(R.string.inbox_continue), { navigator.resetTo(EditorRoute()) }, modifier = Modifier.padding(top = 8.dp))
        } else {
            OmniButton(stringResource(R.string.inbox_verified_sign_in), { navigator.replace(SignInRoute) }, modifier = Modifier.padding(top = 8.dp))
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
    AuthScaffold(snackbar, topBar = { AuthTopBar(navigator::back) }) {
        AuthHeading(stringResource(R.string.reset_title), stringResource(R.string.reset_subtitle))
        OmniTextField(
            state.email, viewModel::setEmail, label = stringResource(R.string.auth_email), placeholder = stringResource(R.string.auth_email_placeholder), error = state.error?.asString(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onSend = { viewModel.send() }),
        )
        OmniButton(stringResource(if (state.sent) R.string.reset_send_again else R.string.reset_send), viewModel::send, loading = busy)
        if (state.sent) {
            InfoBanner(
                stringResource(R.string.reset_sent_message, state.email),
                title = stringResource(R.string.reset_sent_title),
                icon = OmniIcons.Mail,
                action = { OmniTextButton(stringResource(R.string.inbox_open_email), { context.openEmailApp() }) },
            )
            OmniButton(stringResource(R.string.reset_back_to_sign_in), { navigator.replace(SignInRoute) }, style = OmniButtonStyle.Outline, trailingIcon = null)
        }
    }
}
