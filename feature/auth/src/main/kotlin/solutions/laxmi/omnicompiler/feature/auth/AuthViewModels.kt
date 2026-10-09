package solutions.laxmi.omnicompiler.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import solutions.laxmi.omnicompiler.core.data.account.AppConfig
import solutions.laxmi.omnicompiler.core.data.auth.AuthRepository
import solutions.laxmi.omnicompiler.core.data.auth.GoogleIdTokenProvider
import solutions.laxmi.omnicompiler.core.data.auth.GoogleIdTokenResult
import solutions.laxmi.omnicompiler.core.data.auth.SignUpForm
import solutions.laxmi.omnicompiler.core.data.runtime.RuntimeRepository
import solutions.laxmi.omnicompiler.core.model.AppError
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.Session
import solutions.laxmi.omnicompiler.core.ui.UiText
import solutions.laxmi.omnicompiler.core.ui.toUiText
import javax.inject.Inject

/** Which sign-in path is running, so only that button shows progress. */
enum class AuthAction { Email, Google, Guest, Register, Resend, Reset }

sealed interface AuthEvent {
    data class Message(val text: UiText) : AuthEvent
    data class CheckInbox(val email: String) : AuthEvent
    /** Signed in: the app's session gate takes over navigation; this only closes auth screens. */
    data object SignedIn : AuthEvent
    data object Converted : AuthEvent
}

/**
 * Sign-in methods shared by Welcome, Sign in and Create account. Google needs an Activity context
 * for the account picker; it is passed per call and never stored.
 */
abstract class SignInMethodsViewModel(
    protected val auth: AuthRepository,
    private val google: GoogleIdTokenProvider,
) : ViewModel() {

    protected val busyState = MutableStateFlow<AuthAction?>(null)
    val busy: StateFlow<AuthAction?> = busyState.asStateFlow()

    protected val events = Channel<AuthEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val googleAvailable: Boolean get() = google.isAvailable

    fun signInWithGoogle(activityContext: Context) = perform(AuthAction.Google) {
        when (val token = google.requestIdToken(activityContext)) {
            GoogleIdTokenResult.Cancelled -> Unit
            is GoogleIdTokenResult.Failed -> events.send(AuthEvent.Message(token.error.toUiText()))
            is GoogleIdTokenResult.Token -> report(auth.signInWithGoogle(token.idToken))
        }
    }

    fun continueAsGuest() = perform(AuthAction.Guest) { report(auth.continueAsGuest()) }

    protected suspend fun report(result: Outcome<*>) = when (result) {
        is Outcome.Success -> events.send(AuthEvent.SignedIn)
        is Outcome.Failure -> events.send(AuthEvent.Message(result.error.toUiText()))
    }

    protected fun perform(action: AuthAction, block: suspend () -> Unit) {
        if (busyState.value != null) return
        viewModelScope.launch {
            busyState.value = action
            try {
                block()
            } finally {
                busyState.value = null
            }
        }
    }
}

data class WelcomeCounts(val languages: Int, val runtimes: Int)

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    auth: AuthRepository,
    google: GoogleIdTokenProvider,
    runtimes: RuntimeRepository,
    val config: AppConfig,
) : SignInMethodsViewModel(auth, google) {

    private val catalogCount = MutableStateFlow(0)

    /** Live counts when runtimes are cached; bundled catalog size otherwise. */
    val counts: StateFlow<WelcomeCounts> = combine(runtimes.languages, catalogCount) { languages, catalog ->
        if (languages.isEmpty()) WelcomeCounts(catalog, 0) else WelcomeCounts(languages.size, languages.sumOf { it.runtimes.size })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WelcomeCounts(0, 0))

    /** Why the user landed here, captured once so it survives rotation but isn't shown again later. */
    val sessionExpired: Boolean = auth.sessionExpired.value.also { if (it) auth.acknowledgeSessionExpired() }

    init {
        viewModelScope.launch {
            catalogCount.value = runtimes.allLanguageInfo().size
            runtimes.refresh()
        }
    }
}

data class SignInUiState(
    val identifier: String = "",
    val password: String = "",
    /** Set when the account exists but its e-mail isn't verified yet (403 from the auth service). */
    val unverifiedEmail: String? = null,
    val error: UiText? = null,
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    auth: AuthRepository,
    google: GoogleIdTokenProvider,
) : SignInMethodsViewModel(auth, google) {

    private val state = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = state

    fun setIdentifier(value: String) = state.update { it.copy(identifier = value, error = null) }
    fun setPassword(value: String) = state.update { it.copy(password = value, error = null) }

    fun signIn() = perform(AuthAction.Email) {
        val s = state.value
        if (s.identifier.isBlank() || s.password.isEmpty()) {
            state.update { it.copy(error = UiText.Res(R.string.sign_in_missing_fields)) }
            return@perform
        }
        when (val result = auth.signIn(s.identifier, s.password)) {
            is Outcome.Success -> events.send(AuthEvent.SignedIn)
            is Outcome.Failure -> {
                val error = result.error
                state.update {
                    it.copy(
                        error = error.toUiText(),
                        unverifiedEmail = if (error is AppError.EmailNotVerified) s.identifier.trim().takeIf { id -> '@' in id } else null,
                    )
                }
            }
        }
    }

    fun resendVerification() = perform(AuthAction.Resend) {
        val email = state.value.unverifiedEmail ?: return@perform
        when (val result = auth.resendVerification(email)) {
            is Outcome.Success -> events.send(AuthEvent.CheckInbox(email))
            is Outcome.Failure -> events.send(AuthEvent.Message(result.error.toUiText()))
        }
    }
}

data class SignUpUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val acceptedTerms: Boolean = false,
    val isGuest: Boolean = false,
    val error: UiText? = null,
) {
    val strength: PasswordStrength get() = PasswordStrength.of(password)
    val canSubmit: Boolean
        get() = email.contains('@') && password.length in PasswordStrength.MIN..PasswordStrength.MAX && acceptedTerms
}

@HiltViewModel
class SignUpViewModel @Inject constructor(
    auth: AuthRepository,
    google: GoogleIdTokenProvider,
    val config: AppConfig,
) : SignInMethodsViewModel(auth, google) {

    private val form = MutableStateFlow(SignUpUiState())

    val uiState: StateFlow<SignUpUiState> = combine(form, auth.session) { f, session ->
        f.copy(isGuest = (session as? Session.Active)?.user?.isGuest == true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SignUpUiState())

    fun setName(value: String) = form.update { it.copy(name = value.take(201), error = null) }
    fun setEmail(value: String) = form.update { it.copy(email = value.trim().take(254), error = null) }
    fun setPassword(value: String) = form.update { it.copy(password = value.take(PasswordStrength.MAX), error = null) }
    fun setAcceptedTerms(value: Boolean) = form.update { it.copy(acceptedTerms = value) }

    /** Registers, or — for a guest — upgrades the guest in place so run history carries over. */
    fun submit(convertGuest: Boolean) = perform(AuthAction.Register) {
        val f = uiState.value
        if (!f.canSubmit) return@perform
        val (first, last) = splitName(f.name)
        val signUp = SignUpForm(first, last, f.email, f.password)
        if (convertGuest && f.isGuest) {
            when (val result = auth.convertGuest(signUp)) {
                is Outcome.Success -> events.send(AuthEvent.CheckInbox(f.email))
                is Outcome.Failure -> form.update { it.copy(error = result.error.toUiText()) }
            }
        } else {
            when (val result = auth.register(signUp)) {
                is Outcome.Success -> events.send(AuthEvent.CheckInbox(f.email))
                is Outcome.Failure -> form.update { it.copy(error = result.error.toUiText()) }
            }
        }
    }

    private fun splitName(name: String): Pair<String, String> {
        val parts = name.trim().split(Regex("\\s+"), limit = 2)
        return parts.getOrElse(0) { "" }.take(100) to parts.getOrElse(1) { "" }.take(100)
    }
}

@HiltViewModel
class CheckInboxViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {

    private val cooldown = MutableStateFlow(RESEND_COOLDOWN_SECONDS)
    val resendCooldown: StateFlow<Int> = cooldown

    private val events = Channel<UiText>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()

    /** Signed-in (converted guest) users continue straight to the editor once they've checked mail. */
    val signedIn: StateFlow<Boolean> = auth.session
        .map { it is Session.Active }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        startCooldown()
    }

    fun resend(email: String) {
        if (cooldown.value > 0) return
        viewModelScope.launch {
            when (val result = auth.resendVerification(email)) {
                is Outcome.Success -> events.send(if (result.value.isBlank()) UiText.Res(R.string.inbox_sent) else UiText.Raw(result.value))
                is Outcome.Failure -> events.send(result.error.toUiText())
            }
            startCooldown()
        }
    }

    private fun startCooldown() {
        viewModelScope.launch {
            cooldown.value = RESEND_COOLDOWN_SECONDS
            while (cooldown.value > 0) {
                delay(1_000)
                cooldown.update { it - 1 }
            }
        }
    }

    private companion object {
        /** The auth service rate-limits resend (10/min per IP); a client cooldown keeps users well below it. */
        const val RESEND_COOLDOWN_SECONDS = 60
    }
}

data class ForgotPasswordUiState(val email: String = "", val sent: Boolean = false, val error: UiText? = null)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val state = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = state
    private val busyState = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = busyState

    fun init(email: String) {
        if (state.value.email.isEmpty() && email.isNotBlank()) state.update { it.copy(email = email) }
    }

    fun setEmail(value: String) = state.update { it.copy(email = value.trim(), error = null) }

    fun send() {
        // One request at a time: repeated taps would send several reset e-mails.
        if (busyState.value) return
        val email = state.value.email
        if ('@' !in email) {
            state.update { it.copy(error = UiText.Res(R.string.reset_email_required)) }
            return
        }
        viewModelScope.launch {
            busyState.value = true
            when (val result = auth.requestPasswordReset(email)) {
                is Outcome.Success -> state.update { it.copy(sent = true) }
                is Outcome.Failure -> state.update { it.copy(error = result.error.toUiText()) }
            }
            busyState.value = false
        }
    }
}
