package solutions.laxmi.omnicompiler.feature.developer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import solutions.laxmi.omnicompiler.core.data.account.DeveloperRepository
import solutions.laxmi.omnicompiler.core.data.auth.AuthRepository
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.Session
import solutions.laxmi.omnicompiler.core.model.Webhook
import solutions.laxmi.omnicompiler.core.ui.UiText
import solutions.laxmi.omnicompiler.core.ui.toUiText
import java.security.SecureRandom
import javax.inject.Inject

data class DeveloperUiState(
    val signedIn: Boolean = true,
    val apiBaseUrl: String = "",
    /** Only held in memory right after generation; the server never returns it again. */
    val revealedKey: String? = null,
    val rotating: Boolean = false,
    val webhooks: List<Webhook> = emptyList(),
    val webhooksLoading: Boolean = true,
    val webhooksError: UiText? = null,
    val savingWebhook: Boolean = false,
    val createdSecret: String? = null,
)

@HiltViewModel
class DeveloperViewModel @Inject constructor(
    private val developer: DeveloperRepository,
    auth: AuthRepository,
) : ViewModel() {

    private val state = MutableStateFlow(DeveloperUiState(apiBaseUrl = developer.apiBaseUrl))
    private val events = Channel<UiText>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()

    val uiState: StateFlow<DeveloperUiState> = combine(state, auth.session) { s, session ->
        s.copy(signedIn = session is Session.Active)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), state.value)

    init {
        loadWebhooks()
    }

    fun rotateKey() {
        viewModelScope.launch {
            state.update { it.copy(rotating = true) }
            when (val result = developer.rotateApiKey()) {
                is Outcome.Success -> state.update { it.copy(rotating = false, revealedKey = result.value) }
                is Outcome.Failure -> {
                    state.update { it.copy(rotating = false) }
                    events.send(result.error.toUiText())
                }
            }
        }
    }

    fun hideKey() = state.update { it.copy(revealedKey = null) }

    fun loadWebhooks() {
        viewModelScope.launch {
            state.update { it.copy(webhooksLoading = true, webhooksError = null) }
            when (val result = developer.webhooks()) {
                is Outcome.Success -> state.update { it.copy(webhooksLoading = false, webhooks = result.value) }
                is Outcome.Failure -> state.update { it.copy(webhooksLoading = false, webhooksError = result.error.toUiText()) }
            }
        }
    }

    /** A random signing secret the receiver uses to verify `X-LS-Signature`. */
    fun newSecret(): String {
        val bytes = ByteArray(24).also(SecureRandom()::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun createWebhook(url: String, secret: String, onDone: () -> Unit) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("https://")) {
            viewModelScope.launch { events.send(UiText.Res(R.string.developer_https_required)) }
            return
        }
        viewModelScope.launch {
            state.update { it.copy(savingWebhook = true) }
            when (val result = developer.createWebhook(trimmed, secret)) {
                is Outcome.Success -> {
                    state.update { it.copy(savingWebhook = false, webhooks = it.webhooks + result.value, createdSecret = secret) }
                    onDone()
                }
                is Outcome.Failure -> {
                    state.update { it.copy(savingWebhook = false) }
                    events.send(result.error.toUiText())
                }
            }
        }
    }

    fun dismissCreatedSecret() = state.update { it.copy(createdSecret = null) }

    fun deleteWebhook(webhook: Webhook) {
        viewModelScope.launch {
            when (val result = developer.deleteWebhook(webhook.id)) {
                is Outcome.Success -> state.update { s -> s.copy(webhooks = s.webhooks.filter { it.id != webhook.id }) }
                is Outcome.Failure -> events.send(result.error.toUiText())
            }
        }
    }
}
