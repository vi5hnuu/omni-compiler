package solutions.laxmi.omnicompiler.feature.account

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
import kotlinx.coroutines.launch
import solutions.laxmi.omnicompiler.core.data.auth.AuthRepository
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.Session
import solutions.laxmi.omnicompiler.core.model.User
import solutions.laxmi.omnicompiler.core.ui.UiText
import solutions.laxmi.omnicompiler.core.ui.toUiText
import javax.inject.Inject

/** Which long-running action is in flight, so only its button shows progress. */
enum class ProfileAction { Save, Password, Resend, Delete, SignOut }

data class ProfileUiState(val user: User? = null, val busy: ProfileAction? = null)

sealed interface ProfileEvent {
    data class Message(val text: UiText) : ProfileEvent
    /** Session ended (sign out, password change, deletion): go back to Welcome. */
    data object SignedOut : ProfileEvent
    data object PasswordChanged : ProfileEvent
}

@HiltViewModel
class ProfileViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {

    private val busy = MutableStateFlow<ProfileAction?>(null)
    private val events = Channel<ProfileEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val uiState: StateFlow<ProfileUiState> = combine(auth.session, busy) { session, action ->
        ProfileUiState((session as? Session.Active)?.user, action)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    init {
        // Pick up verification or profile changes made elsewhere (e.g. e-mail link opened in a browser).
        viewModelScope.launch { auth.refreshProfile() }
    }

    fun save(firstName: String, lastName: String, profileUrl: String) = run(ProfileAction.Save) {
        if (profileUrl.isNotBlank() && !profileUrl.startsWith("https://")) {
            events.send(ProfileEvent.Message(UiText.Res(R.string.profile_image_https)))
            return@run
        }
        when (val result = auth.updateProfile(firstName, lastName, profileUrl)) {
            is Outcome.Success -> events.send(ProfileEvent.Message(UiText.Res(R.string.profile_updated)))
            is Outcome.Failure -> events.send(ProfileEvent.Message(result.error.toUiText()))
        }
    }

    fun changePassword(current: String, new: String) = run(ProfileAction.Password) {
        when (val result = auth.changePassword(current, new)) {
            is Outcome.Success -> events.send(ProfileEvent.PasswordChanged)
            is Outcome.Failure -> events.send(ProfileEvent.Message(result.error.toUiText()))
        }
    }

    fun resendVerification() = run(ProfileAction.Resend) {
        val email = uiState.value.user?.email ?: return@run
        when (val result = auth.resendVerification(email)) {
            is Outcome.Success -> events.send(ProfileEvent.Message(if (result.value.isBlank()) UiText.Res(R.string.profile_verification_sent) else UiText.Raw(result.value)))
            is Outcome.Failure -> events.send(ProfileEvent.Message(result.error.toUiText()))
        }
    }

    fun signOut() = run(ProfileAction.SignOut) {
        auth.signOut()
        events.send(ProfileEvent.SignedOut)
    }

    fun deleteAccount() = run(ProfileAction.Delete) {
        when (val result = auth.deleteAccount()) {
            is Outcome.Success -> events.send(ProfileEvent.SignedOut)
            is Outcome.Failure -> events.send(ProfileEvent.Message(result.error.toUiText()))
        }
    }

    private fun run(action: ProfileAction, block: suspend () -> Unit) {
        if (busy.value != null) return
        viewModelScope.launch {
            busy.value = action
            try {
                block()
            } finally {
                busy.value = null
            }
        }
    }
}
