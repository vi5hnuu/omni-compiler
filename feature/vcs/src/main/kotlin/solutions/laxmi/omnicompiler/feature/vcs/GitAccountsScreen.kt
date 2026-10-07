package solutions.laxmi.omnicompiler.feature.vcs

import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.graphics.RectangleShape
import solutions.laxmi.omnicompiler.core.ui.R as CommonR
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import solutions.laxmi.omnicompiler.core.data.git.GitRepository
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButtonStyle
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniPasswordField
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTopBar
import solutions.laxmi.omnicompiler.core.designsystem.component.SectionLabel
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.model.GitAccount
import solutions.laxmi.omnicompiler.core.model.GitHost
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.ui.UiText
import solutions.laxmi.omnicompiler.core.ui.asString
import solutions.laxmi.omnicompiler.core.ui.openUrl
import solutions.laxmi.omnicompiler.core.ui.toUiText
import javax.inject.Inject

data class GitAccountsUiState(
    val accounts: Map<GitHost, GitAccount> = emptyMap(),
    val connecting: GitHost? = null,
    val errors: Map<GitHost, UiText> = emptyMap(),
)

@HiltViewModel
class GitAccountsViewModel @Inject constructor(private val git: GitRepository) : ViewModel() {
    private val connecting = MutableStateFlow<GitHost?>(null)
    private val errors = MutableStateFlow<Map<GitHost, UiText>>(emptyMap())

    val uiState: StateFlow<GitAccountsUiState> = combine(git.accounts, connecting, errors) { accounts, busy, failures ->
        GitAccountsUiState(accounts.associateBy { it.host }, busy, failures)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GitAccountsUiState())

    fun connect(host: GitHost, token: String) {
        if (connecting.value != null) return
        viewModelScope.launch {
            connecting.value = host
            errors.update { it - host }
            val result = git.connect(host, token)
            if (result is Outcome.Failure) errors.update { it + (host to result.error.toUiText()) }
            connecting.value = null
        }
    }

    fun disconnect(host: GitHost) {
        viewModelScope.launch { git.disconnect(host) }
    }
}

/** Connect GitHub / GitLab with a personal access token (stored encrypted on this device only). */
@Composable
fun GitAccountsScreen(navigator: Navigator) {
    val viewModel = hiltViewModel<GitAccountsViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = OmniTheme.colors
    Column(Modifier.fillMaxSize().background(colors.background).navigationBarsPadding().imePadding()) {
        OmniTopBar(stringResource(R.string.git_accounts_title), onBack = navigator::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.git_accounts_intro),
                style = OmniTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(16.dp),
            )
            GitHost.entries.forEach { host ->
                HostSection(
                    host = host,
                    account = state.accounts[host],
                    busy = state.connecting == host,
                    error = state.errors[host],
                    onConnect = { viewModel.connect(host, it) },
                    onDisconnect = { viewModel.disconnect(host) },
                )
            }
        }
    }
}

@Composable
private fun HostSection(host: GitHost, account: GitAccount?, busy: Boolean, error: UiText?, onConnect: (String) -> Unit, onDisconnect: () -> Unit) {
    val colors = OmniTheme.colors
    val context = LocalContext.current
    var token by rememberSaveable(host) { mutableStateOf("") }
    SectionLabel(host.displayName())
    // Bottom padding separates one provider's button from the next provider's heading.
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (account != null) {
            Text(stringResource(R.string.git_connected_as, account.login), style = OmniTheme.typography.body, color = colors.textPrimary)
            var confirming by rememberSaveable(host) { mutableStateOf(false) }
            OmniButton(stringResource(R.string.git_disconnect), { confirming = true }, style = OmniButtonStyle.Secondary, trailingIcon = null)
            if (confirming) {
                AlertDialog(
                    onDismissRequest = { confirming = false },
                    shape = RectangleShape,
                    containerColor = colors.surfaceRaised,
                    title = { Text(stringResource(R.string.git_disconnect_title, host.displayName()), style = OmniTheme.typography.title, color = colors.textPrimary) },
                    text = { Text(stringResource(R.string.git_disconnect_message), style = OmniTheme.typography.body, color = colors.textSecondary) },
                    confirmButton = {
                        OmniTextButton(stringResource(R.string.git_disconnect), {
                            confirming = false
                            onDisconnect()
                        })
                    },
                    dismissButton = { OmniTextButton(stringResource(CommonR.string.common_cancel), { confirming = false }, color = colors.textSecondary) },
                )
            }
        } else {
            OmniPasswordField(
                value = token,
                onValueChange = { token = it },
                label = stringResource(R.string.git_token_label),
                error = error?.asString(),
                supporting = stringResource(host.scopeHint()),
            )
            OmniTextButton(stringResource(R.string.git_create_token), { context.openUrl(host.tokenPage()) })
            OmniButton(
                stringResource(R.string.git_connect),
                { onConnect(token) },
                enabled = token.isNotBlank() && !busy,
                loading = busy,
                leadingIcon = OmniIcons.Link,
                trailingIcon = null,
            )
        }
    }
}

internal fun GitHost.displayName() = when (this) {
    GitHost.GITHUB -> "GitHub"
    GitHost.GITLAB -> "GitLab"
}

/** Token pages with the needed scopes pre-selected where the host supports it. */
private fun GitHost.tokenPage() = when (this) {
    GitHost.GITHUB -> "https://github.com/settings/tokens/new?scopes=repo&description=Omni%20Compiler"
    GitHost.GITLAB -> "https://gitlab.com/-/user_settings/personal_access_tokens?name=Omni%20Compiler&scopes=api"
}

private fun GitHost.scopeHint() = when (this) {
    GitHost.GITHUB -> R.string.git_scope_github
    GitHost.GITLAB -> R.string.git_scope_gitlab
}
