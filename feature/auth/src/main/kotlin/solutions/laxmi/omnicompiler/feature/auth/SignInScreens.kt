package solutions.laxmi.omnicompiler.feature.auth

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.LinkAnnotation
import solutions.laxmi.omnicompiler.core.ui.asString
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.navigation.CheckInboxRoute
import solutions.laxmi.omnicompiler.core.navigation.ForgotPasswordRoute
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.navigation.SignInRoute
import solutions.laxmi.omnicompiler.core.navigation.SignUpRoute
import solutions.laxmi.omnicompiler.core.ui.openUrl

@Composable
internal fun AuthEvents(viewModel: SignInMethodsViewModel, navigator: Navigator, snackbar: SnackbarHostState) {
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is AuthEvent.Message -> snackbar.showSnackbar(event.text.asString(resources))
                is AuthEvent.CheckInbox -> navigator.navigate(CheckInboxRoute(event.email))
                // The app's session gate routes every completed sign-in (editor, or the folder picker first).
                AuthEvent.SignedIn -> Unit
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
    AuthScaffold(
        snackbar,
        topBar = { AuthTopBar(navigator::back) },
        footer = { AccountSwitchRow(stringResource(R.string.sign_in_new), stringResource(R.string.sign_in_create_account)) { navigator.navigate(SignUpRoute()) } },
    ) {
        AuthHeading(stringResource(R.string.sign_in_title), stringResource(R.string.sign_in_subtitle))
        OmniTextField(
            state.identifier, viewModel::setIdentifier, label = stringResource(R.string.sign_in_identifier), placeholder = stringResource(R.string.auth_email_placeholder),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
        )
        OmniPasswordField(
            state.password, viewModel::setPassword,
            labelAction = { OmniTextButton(stringResource(R.string.sign_in_forgot), { navigator.navigate(ForgotPasswordRoute(state.identifier.takeIf { '@' in it }.orEmpty())) }) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.signIn() }),
            error = state.error?.asString(),
        )
        state.unverifiedEmail?.let { email ->
            InfoBanner(
                stringResource(R.string.sign_in_verify_message, email),
                icon = OmniIcons.Mail,
                title = stringResource(R.string.sign_in_verify_title),
                action = { OmniTextButton(stringResource(if (busy == AuthAction.Resend) R.string.sign_in_sending else R.string.sign_in_resend), viewModel::resendVerification, enabled = busy == null) },
            )
        }
        OmniButton(stringResource(R.string.sign_in_button), viewModel::signIn, loading = busy == AuthAction.Email, enabled = busy == null)
        if (viewModel.googleAvailable) {
            OrDivider()
            GoogleButton(stringResource(R.string.welcome_google), loading = busy == AuthAction.Google, enabled = busy == null) {
                viewModel.signInWithGoogle(context.findActivityContext())
            }
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
    AuthScaffold(
        snackbar,
        topBar = { AuthTopBar(navigator::back) },
        footer = { AccountSwitchRow(stringResource(R.string.sign_up_have_account), stringResource(R.string.sign_in_button)) { navigator.replace(SignInRoute) } },
    ) {
        AuthHeading(
            stringResource(R.string.sign_up_title),
            stringResource(if (converting) R.string.sign_up_subtitle_convert else R.string.sign_up_subtitle),
        )
        OmniTextField(state.name, viewModel::setName, label = stringResource(R.string.sign_up_name), placeholder = stringResource(R.string.sign_up_name_placeholder), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
        OmniTextField(
            state.email, viewModel::setEmail, label = stringResource(R.string.auth_email), placeholder = stringResource(R.string.auth_email_placeholder),
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
                AgreementText(termsUrl = viewModel.config.termsUrl, privacyUrl = viewModel.config.privacyPolicyUrl)
            },
        )
        state.error?.let { Text(it.asString(), style = OmniTheme.typography.bodySmall, color = colors.accentText) }
        OmniButton(stringResource(R.string.sign_up_button), { viewModel.submit(route.convertGuest) }, enabled = state.canSubmit && busy == null, loading = busy == AuthAction.Register)
        if (viewModel.googleAvailable && !converting) {
            OrDivider()
            GoogleButton(stringResource(R.string.sign_up_google), loading = busy == AuthAction.Google, enabled = busy == null) {
                viewModel.signInWithGoogle(context.findActivityContext())
            }
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
            Text(stringResource(R.string.password_strength_summary, strength.labelRes?.let { stringResource(it) }.orEmpty(), pluralStringResource(R.plurals.password_characters, password.length, password.length)), style = OmniTheme.typography.label, color = colors.textSecondary, modifier = Modifier.weight(1f))
            PasswordStrength.hint(password)?.let { Text(it.asString(), style = OmniTheme.typography.label, color = colors.textTertiary) }
        }
    }
}

/** "I agree to the Terms and Privacy Policy" as one translatable sentence with two tappable links. */
@Composable
private fun AgreementText(termsUrl: String, privacyUrl: String) {
    val colors = OmniTheme.colors
    val terms = stringResource(R.string.sign_up_terms)
    val privacy = stringResource(R.string.sign_up_privacy)
    val sentence = stringResource(R.string.sign_up_agree, terms, privacy)
    val linkStyle = TextLinkStyles(SpanStyle(color = colors.accentText, fontWeight = FontWeight.SemiBold))
    val text = buildAnnotatedString {
        append(sentence)
        listOf(terms to termsUrl, privacy to privacyUrl).forEach { (label, url) ->
            val start = sentence.indexOf(label)
            if (start >= 0) addLink(LinkAnnotation.Url(url, linkStyle), start, start + label.length)
        }
    }
    Text(text, style = OmniTheme.typography.bodySmall, color = colors.textSecondary)
}
