package solutions.laxmi.omnicompiler.feature.account

import solutions.laxmi.omnicompiler.core.ui.shownInitials
import solutions.laxmi.omnicompiler.core.ui.shownName
import solutions.laxmi.omnicompiler.core.ui.asString
import androidx.compose.ui.platform.LocalResources
import solutions.laxmi.omnicompiler.core.ui.R as CommonR
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniSnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import solutions.laxmi.omnicompiler.core.designsystem.component.InfoBanner
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniBadge
import solutions.laxmi.omnicompiler.core.model.AuthProvider
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButtonStyle
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniPasswordField
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextField
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTopBar
import solutions.laxmi.omnicompiler.core.designsystem.component.SectionLabel
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.model.User
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.navigation.SignInRoute
import solutions.laxmi.omnicompiler.core.navigation.SignUpRoute
import solutions.laxmi.omnicompiler.core.navigation.WelcomeRoute
import solutions.laxmi.omnicompiler.core.ui.Avatar

/** Account details: profile edit, verification, password, sign-out and deletion. */
@Composable
fun ProfileScreen(navigator: Navigator) {
    val viewModel = hiltViewModel<ProfileViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var changingPassword by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is ProfileEvent.Message -> snackbar.showSnackbar(event.text.asString(resources))
                // The app's session gate returns to Welcome once the session is cleared.
                ProfileEvent.SignedOut, ProfileEvent.PasswordChanged -> Unit
            }
        }
    }
    val colors = OmniTheme.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            OmniTopBar(stringResource(R.string.profile_title), onBack = navigator::back)
            val user = state.user
            if (user == null) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.profile_not_signed_in), style = OmniTheme.typography.title, color = colors.textPrimary)
                    OmniButton(stringResource(CommonR.string.common_sign_in), { navigator.navigate(WelcomeRoute) })
                }
                return@Column
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Header(user)
                if (user.isGuest) GuestSection(onCreateAccount = { navigator.navigate(SignUpRoute(convertGuest = true)) }, onSignIn = { navigator.navigate(SignInRoute) })
                else ProfileForm(user, busy = state.busy == ProfileAction.Save, onSave = viewModel::save)
                if (!user.isGuest && !user.verified) {
                    InfoBanner(
                        stringResource(R.string.profile_verify_banner, user.email.orEmpty()),
                        Modifier.padding(16.dp),
                        icon = OmniIcons.Mail,
                        action = { OmniTextButton(stringResource(R.string.profile_resend_link), viewModel::resendVerification, enabled = state.busy == null) },
                    )
                }
                SectionLabel(stringResource(R.string.profile_security))
                if (user.hasPassword) {
                    OmniButton(stringResource(R.string.profile_change_password), { changingPassword = true }, Modifier.padding(horizontal = 16.dp), style = OmniButtonStyle.Outline, leadingIcon = OmniIcons.Lock)
                } else if (!user.isGuest) {
                    Text(stringResource(R.string.profile_google_no_password), style = OmniTheme.typography.bodySmall, color = colors.textTertiary, modifier = Modifier.padding(horizontal = 16.dp))
                }
                OmniButton(stringResource(R.string.profile_sign_out), viewModel::signOut, Modifier.padding(16.dp), style = OmniButtonStyle.Outline, leadingIcon = OmniIcons.LogOut, loading = state.busy == ProfileAction.SignOut)
                SectionLabel(stringResource(R.string.profile_danger_zone))
                Text(
                    stringResource(R.string.profile_delete_explainer),
                    style = OmniTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                OmniTextButton(stringResource(R.string.profile_delete_account), { confirmingDelete = true }, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
        OmniSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    if (changingPassword) {
        ChangePasswordDialog(
            busy = state.busy == ProfileAction.Password,
            onDismiss = { changingPassword = false },
            onSubmit = viewModel::changePassword,
        )
    }
    if (confirmingDelete) {
        DeleteAccountDialog(
            busy = state.busy == ProfileAction.Delete,
            onDismiss = { confirmingDelete = false },
            onConfirm = viewModel::deleteAccount,
        )
    }
}

@Composable
private fun Header(user: User) {
    val colors = OmniTheme.colors
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Avatar(user.profileUrl, user.shownInitials(), size = 56.dp)
        Column(Modifier.weight(1f)) {
            Text(user.shownName(), style = OmniTheme.typography.title, color = colors.textPrimary)
            // Guests have no e-mail and only a generated username, so the line explains where their data lives.
            Text(
                if (user.isGuest) stringResource(R.string.profile_guest_subtitle) else user.email ?: user.username.orEmpty(),
                style = OmniTheme.typography.bodySmall,
                color = colors.textTertiary,
            )
        }
        OmniBadge(
            stringResource(
                when {
                    user.isGuest -> R.string.profile_badge_guest
                    user.provider == AuthProvider.GOOGLE -> R.string.profile_badge_google
                    else -> R.string.profile_badge_email
                },
            ),
        )
    }
}

@Composable
private fun GuestSection(onCreateAccount: () -> Unit, onSignIn: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoBanner(
            stringResource(R.string.profile_guest_notice),
            icon = OmniIcons.Info,
        )
        OmniButton(stringResource(R.string.profile_create_account), onCreateAccount)
        OmniButton(stringResource(R.string.profile_have_account), onSignIn, style = OmniButtonStyle.Outline, trailingIcon = null)
    }
}

@Composable
private fun ProfileForm(user: User, busy: Boolean, onSave: (String, String, String) -> Unit) {
    var first by rememberSaveable(user.id) { mutableStateOf(user.firstName.orEmpty()) }
    var last by rememberSaveable(user.id) { mutableStateOf(user.lastName.orEmpty()) }
    var picture by rememberSaveable(user.id) { mutableStateOf(user.profileUrl.orEmpty()) }
    val changed = first != user.firstName.orEmpty() || last != user.lastName.orEmpty() || picture != user.profileUrl.orEmpty()
    SectionLabel(stringResource(R.string.profile_section))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OmniTextField(first, { first = it.take(100) }, Modifier.weight(1f), label = stringResource(R.string.profile_first_name))
            OmniTextField(last, { last = it.take(100) }, Modifier.weight(1f), label = stringResource(R.string.profile_last_name))
        }
        OmniTextField(
            picture, { picture = it.take(512) }, label = stringResource(R.string.profile_image_url), placeholder = stringResource(R.string.profile_image_url_placeholder),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        user.username?.let { OmniTextField(it, {}, label = stringResource(R.string.profile_username), enabled = false) }
        OmniButton(stringResource(R.string.profile_save), { onSave(first, last, picture) }, enabled = changed, loading = busy, trailingIcon = OmniIcons.Check)
    }
}

@Composable
private fun ChangePasswordDialog(busy: Boolean, onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var current by rememberSaveable { mutableStateOf("") }
    var next by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val tooShort = next.isNotEmpty() && next.length !in 8..72
    val mismatch = confirm.isNotEmpty() && confirm != next
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = OmniTheme.colors.surfaceRaised,
        title = { Text(stringResource(R.string.profile_change_password), style = OmniTheme.typography.title, color = OmniTheme.colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.profile_password_warning), style = OmniTheme.typography.bodySmall, color = OmniTheme.colors.textSecondary)
                OmniPasswordField(current, { current = it }, label = stringResource(R.string.profile_current_password))
                OmniPasswordField(next, { next = it }, label = stringResource(R.string.profile_new_password), error = if (tooShort) stringResource(R.string.profile_password_length) else null)
                OmniPasswordField(confirm, { confirm = it }, label = stringResource(R.string.profile_confirm_password), error = if (mismatch) stringResource(R.string.profile_password_mismatch) else null)
            }
        },
        confirmButton = {
            OmniTextButton(
                stringResource(if (busy) R.string.profile_saving else R.string.profile_change),
                { onSubmit(current, next) },
                enabled = !busy && current.isNotEmpty() && next.length in 8..72 && next == confirm,
            )
        },
        dismissButton = { OmniTextButton(stringResource(CommonR.string.common_cancel), onDismiss, color = OmniTheme.colors.textSecondary) },
    )
}

@Composable
private fun DeleteAccountDialog(busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val confirmWord = stringResource(R.string.profile_delete_confirm_word)
    var typed by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = OmniTheme.colors.surfaceRaised,
        title = { Text(stringResource(R.string.profile_delete_title), style = OmniTheme.typography.title, color = OmniTheme.colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.profile_delete_message, confirmWord),
                    style = OmniTheme.typography.body,
                    color = OmniTheme.colors.textSecondary,
                )
                OmniTextField(typed, { typed = it }, placeholder = confirmWord)
            }
        },
        confirmButton = { OmniTextButton(stringResource(if (busy) R.string.profile_deleting else CommonR.string.common_delete), onConfirm, enabled = typed.trim() == confirmWord && !busy) },
        dismissButton = { OmniTextButton(stringResource(CommonR.string.common_cancel), onDismiss, color = OmniTheme.colors.textSecondary) },
    )
}
