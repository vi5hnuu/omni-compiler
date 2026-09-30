package solutions.laxmi.omnicompiler.feature.account

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
import androidx.compose.material3.SnackbarHost
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
    var changingPassword by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is ProfileEvent.Message -> snackbar.showSnackbar(event.text)
                // The app's session gate returns to Welcome once the session is cleared.
                ProfileEvent.SignedOut, ProfileEvent.PasswordChanged -> Unit
            }
        }
    }
    val colors = OmniTheme.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            OmniTopBar("Account", onBack = navigator::back)
            val user = state.user
            if (user == null) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("You're not signed in.", style = OmniTheme.typography.title, color = colors.textPrimary)
                    OmniButton("Sign in", { navigator.navigate(WelcomeRoute) })
                }
                return@Column
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Header(user)
                if (user.isGuest) GuestSection(onCreateAccount = { navigator.navigate(SignUpRoute(convertGuest = true)) }, onSignIn = { navigator.navigate(SignInRoute) })
                else ProfileForm(user, busy = state.busy == ProfileAction.Save, onSave = viewModel::save)
                if (!user.isGuest && !user.verified) {
                    InfoBanner(
                        "Verify ${user.email} to sign in with a password on other devices.",
                        Modifier.padding(16.dp),
                        icon = OmniIcons.Mail,
                        action = { OmniTextButton("Resend link", viewModel::resendVerification, enabled = state.busy == null) },
                    )
                }
                SectionLabel("Security")
                if (user.hasPassword) {
                    OmniButton("Change password", { changingPassword = true }, Modifier.padding(horizontal = 16.dp), style = OmniButtonStyle.Secondary, leadingIcon = OmniIcons.Lock)
                } else if (!user.isGuest) {
                    Text("You sign in with Google, so there's no password to change.", style = OmniTheme.typography.bodySmall, color = colors.textTertiary, modifier = Modifier.padding(horizontal = 16.dp))
                }
                OmniButton("Sign out", viewModel::signOut, Modifier.padding(16.dp), style = OmniButtonStyle.Outline, leadingIcon = OmniIcons.LogOut, loading = state.busy == ProfileAction.SignOut)
                SectionLabel("Danger zone")
                Text(
                    "Deleting your account erases your run history, API key and webhooks on the judge, then your sign-in. Projects on this device are removed too.",
                    style = OmniTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                OmniTextButton("Delete account", { confirmingDelete = true }, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
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
        Avatar(user.profileUrl, user.initials, size = 56.dp)
        Column(Modifier.weight(1f)) {
            Text(user.displayName, style = OmniTheme.typography.title, color = colors.textPrimary)
            Text(user.email ?: user.username.orEmpty(), style = OmniTheme.typography.bodySmall, color = colors.textTertiary)
        }
        OmniBadge(if (user.isGuest) "GUEST" else user.provider.name)
    }
}

@Composable
private fun GuestSection(onCreateAccount: () -> Unit, onSignIn: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoBanner(
            "Guest accounts expire after a week without use. Create an account to keep your run history; your projects stay on this device either way.",
            icon = OmniIcons.Info,
        )
        OmniButton("Create account", onCreateAccount)
        OmniButton("I already have an account", onSignIn, style = OmniButtonStyle.Outline, trailingIcon = null)
    }
}

@Composable
private fun ProfileForm(user: User, busy: Boolean, onSave: (String, String, String) -> Unit) {
    var first by rememberSaveable(user.id) { mutableStateOf(user.firstName.orEmpty()) }
    var last by rememberSaveable(user.id) { mutableStateOf(user.lastName.orEmpty()) }
    var picture by rememberSaveable(user.id) { mutableStateOf(user.profileUrl.orEmpty()) }
    val changed = first != user.firstName.orEmpty() || last != user.lastName.orEmpty() || picture != user.profileUrl.orEmpty()
    SectionLabel("Profile")
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OmniTextField(first, { first = it.take(100) }, Modifier.weight(1f), label = "First name")
            OmniTextField(last, { last = it.take(100) }, Modifier.weight(1f), label = "Last name")
        }
        OmniTextField(
            picture, { picture = it.take(512) }, label = "Profile image URL", placeholder = "https://…",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        user.username?.let { OmniTextField(it, {}, label = "Username", enabled = false) }
        OmniButton("Save profile", { onSave(first, last, picture) }, enabled = changed, loading = busy, trailingIcon = OmniIcons.Check)
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
        title = { Text("Change password", style = OmniTheme.typography.title, color = OmniTheme.colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("You'll be signed out on every device, including this one.", style = OmniTheme.typography.bodySmall, color = OmniTheme.colors.textSecondary)
                OmniPasswordField(current, { current = it }, label = "Current password")
                OmniPasswordField(next, { next = it }, label = "New password", error = if (tooShort) "Use 8–72 characters." else null)
                OmniPasswordField(confirm, { confirm = it }, label = "Confirm", error = if (mismatch) "Doesn't match." else null)
            }
        },
        confirmButton = {
            OmniTextButton(
                if (busy) "Saving…" else "Change",
                { onSubmit(current, next) },
                enabled = !busy && current.isNotEmpty() && next.length in 8..72 && next == confirm,
            )
        },
        dismissButton = { OmniTextButton("Cancel", onDismiss, color = OmniTheme.colors.textSecondary) },
    )
}

@Composable
private fun DeleteAccountDialog(busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = OmniTheme.colors.surfaceRaised,
        title = { Text("Delete account?", style = OmniTheme.typography.title, color = OmniTheme.colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "This can't be undone. If you have an active paid plan, cancel it first. Type DELETE to confirm.",
                    style = OmniTheme.typography.body,
                    color = OmniTheme.colors.textSecondary,
                )
                OmniTextField(typed, { typed = it }, placeholder = "DELETE")
            }
        },
        confirmButton = { OmniTextButton(if (busy) "Deleting…" else "Delete", onConfirm, enabled = typed == "DELETE" && !busy) },
        dismissButton = { OmniTextButton("Cancel", onDismiss, color = OmniTheme.colors.textSecondary) },
    )
}
