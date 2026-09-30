package solutions.laxmi.omnicompiler.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniCheckbox
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniPasswordField
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
import solutions.laxmi.omnicompiler.core.navigation.SignUpRoute
import solutions.laxmi.omnicompiler.core.ui.openUrl

@Composable
internal fun AuthEvents(viewModel: SignInMethodsViewModel, navigator: Navigator, snackbar: SnackbarHostState) {
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is AuthEvent.Message -> snackbar.showSnackbar(event.text)
                is AuthEvent.CheckInbox -> navigator.navigate(CheckInboxRoute(event.email))
                // New sessions are routed by the app's session gate; an existing one (guest) just returns.
                AuthEvent.SignedIn -> navigator.resetTo(EditorRoute())
                AuthEvent.Converted -> navigator.back()
            }
        }
    }
}

/** Design A2 (2-step verification notice removed: the auth service has no MFA). */
@Composable
fun SignInScreen(navigator: Navigator) {
    val viewModel = hiltViewModel<SignInViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    AuthEvents(viewModel, navigator, snackbar)
    val colors = OmniTheme.colors
    AuthScaffold(snackbar, topBar = { OmniTopBar("", onBack = navigator::back) }) {
        AuthHeading("Sign in", "Sync run history, API keys and plan across devices.")
        OmniTextField(
            state.identifier, viewModel::setIdentifier, label = "E-mail or username", placeholder = "ada@lovelace.dev",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
        )
        OmniPasswordField(
            state.password, viewModel::setPassword,
            labelAction = { OmniTextButton("Forgot password?", { navigator.navigate(ForgotPasswordRoute(state.identifier.takeIf { '@' in it }.orEmpty())) }) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.signIn() }),
            error = state.error,
        )
        state.unverifiedEmail?.let { email ->
            InfoBanner(
                "Open the link we sent to $email to finish creating your account.",
                icon = OmniIcons.Mail,
                title = "Verify your e-mail",
                action = { OmniTextButton(if (busy == AuthAction.Resend) "Sending…" else "Resend link", viewModel::resendVerification, enabled = busy == null) },
            )
        }
        OmniButton("Sign in", viewModel::signIn, loading = busy == AuthAction.Email, enabled = busy == null)
        if (viewModel.googleAvailable) {
            OrDivider()
            GoogleButton("Continue with Google", loading = busy == AuthAction.Google, enabled = busy == null) {
                viewModel.signInWithGoogle(context.findActivityContext())
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("New to omni? ", style = OmniTheme.typography.body, color = colors.textSecondary)
            OmniTextButton("Create account", { navigator.navigate(SignUpRoute()) })
        }
    }
}

/** Design A4. With [convertGuest], upgrades the current guest instead of creating a new account. */
@Composable
fun SignUpScreen(route: SignUpRoute, navigator: Navigator) {
    val viewModel = hiltViewModel<SignUpViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    AuthEvents(viewModel, navigator, snackbar)
    val colors = OmniTheme.colors
    val converting = route.convertGuest && state.isGuest
    AuthScaffold(snackbar, topBar = { OmniTopBar("", onBack = navigator::back) }) {
        AuthHeading(
            "Create account",
            if (converting) "Your guest run history comes with you. Projects already live on this device." else "Free plan. Projects stay on this device; run history syncs.",
        )
        OmniTextField(state.name, viewModel::setName, label = "Name", placeholder = "Ada Lovelace", keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
        OmniTextField(
            state.email, viewModel::setEmail, label = "E-mail", placeholder = "ada@lovelace.dev",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
        )
        OmniPasswordField(
            state.password, viewModel::setPassword,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        )
        StrengthMeter(state.password)
        OmniCheckbox(
            checked = state.acceptedTerms,
            onCheckedChange = viewModel::setAcceptedTerms,
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("I agree to the ", style = OmniTheme.typography.bodySmall, color = colors.textSecondary)
                    OmniTextButton("Terms", { context.openUrl(viewModel.config.termsUrl) })
                    Text(" and ", style = OmniTheme.typography.bodySmall, color = colors.textSecondary)
                    OmniTextButton("Privacy Policy", { context.openUrl(viewModel.config.privacyPolicyUrl) })
                }
            },
        )
        state.error?.let { Text(it, style = OmniTheme.typography.bodySmall, color = colors.accentText) }
        OmniButton("Create account", { viewModel.submit(route.convertGuest) }, enabled = state.canSubmit && busy == null, loading = busy == AuthAction.Register)
        if (viewModel.googleAvailable && !converting) {
            OrDivider()
            GoogleButton("Sign up with Google", loading = busy == AuthAction.Google, enabled = busy == null) {
                viewModel.signInWithGoogle(context.findActivityContext())
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Have an account? ", style = OmniTheme.typography.body, color = colors.textSecondary)
            OmniTextButton("Sign in", { navigator.replace(SignInRoute) })
        }
    }
}

@Composable
private fun StrengthMeter(password: String) {
    if (password.isEmpty()) return
    val colors = OmniTheme.colors
    val strength = PasswordStrength.of(password)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier.weight(1f).height(3.dp).background(
                        when {
                            i >= strength.segments -> colors.surfaceMuted
                            strength == PasswordStrength.TooShort || strength == PasswordStrength.Weak -> colors.accent
                            else -> colors.textPrimary
                        },
                    ),
                )
            }
        }
        Row {
            Text("${strength.label} · ${password.length} characters", style = OmniTheme.typography.label, color = colors.textSecondary, modifier = Modifier.weight(1f))
            PasswordStrength.hint(password)?.let { Text(it, style = OmniTheme.typography.label, color = colors.textTertiary) }
        }
    }
}
