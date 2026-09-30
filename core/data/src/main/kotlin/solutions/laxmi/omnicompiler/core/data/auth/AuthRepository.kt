package solutions.laxmi.omnicompiler.core.data.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import solutions.laxmi.omnicompiler.core.common.ApplicationScope
import solutions.laxmi.omnicompiler.core.data.mapper.toModel
import solutions.laxmi.omnicompiler.core.data.mapper.toStored
import solutions.laxmi.omnicompiler.core.datastore.SessionStore
import solutions.laxmi.omnicompiler.core.datastore.StoredSession
import solutions.laxmi.omnicompiler.core.model.AppError
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.Session
import solutions.laxmi.omnicompiler.core.model.User
import solutions.laxmi.omnicompiler.core.model.onSuccess
import solutions.laxmi.omnicompiler.core.network.auth.TokenRefresher
import solutions.laxmi.omnicompiler.core.network.source.AuthNetworkDataSource
import solutions.laxmi.omnicompiler.core.network.source.JudgeNetworkDataSource
import solutions.laxmi.omnicompiler.core.network.source.RegistrationForm
import javax.inject.Inject
import javax.inject.Singleton

data class SignUpForm(
    val firstName: String,
    val lastName: String,
    val email: String,
    val password: String,
)

/** Every end-user flow of the auth service, plus the local session they produce. */
interface AuthRepository {
    val session: StateFlow<Session>

    suspend fun signIn(identifier: String, password: String): Outcome<User>
    suspend fun signInWithGoogle(idToken: String): Outcome<User>
    suspend fun continueAsGuest(): Outcome<User>

    /** Creates an unverified account; no session until the e-mail link is opened. */
    suspend fun register(form: SignUpForm): Outcome<String>

    /** Upgrades the current guest in place (same user id, so run history is kept). */
    suspend fun convertGuest(form: SignUpForm): Outcome<User>

    suspend fun resendVerification(email: String): Outcome<String>
    suspend fun requestPasswordReset(email: String): Outcome<String>

    suspend fun refreshProfile(): Outcome<User>
    suspend fun updateProfile(firstName: String, lastName: String, profileUrl: String?): Outcome<User>

    /** Succeeds by revoking every session, including this one; the caller is signed out afterwards. */
    suspend fun changePassword(currentPassword: String, newPassword: String): Outcome<String>

    suspend fun signOut()

    /** Erases the judge account (runs, keys, webhooks), then the identity, then all local data. */
    suspend fun deleteAccount(): Outcome<Unit>
}

@Singleton
internal class DefaultAuthRepository @Inject constructor(
    private val network: AuthNetworkDataSource,
    private val judge: JudgeNetworkDataSource,
    private val store: SessionStore,
    private val refresher: TokenRefresher,
    private val cleaner: LocalDataCleaner,
    @ApplicationScope private val scope: CoroutineScope,
) : AuthRepository {

    override val session: StateFlow<Session> = store.session
        .map { stored -> stored?.user?.let { Session.Active(it.toModel()) } ?: Session.SignedOut }
        .stateIn(scope, SharingStarted.Eagerly, Session.Loading)

    override suspend fun signIn(identifier: String, password: String) = establish(network.login(identifier, password))

    override suspend fun signInWithGoogle(idToken: String) = establish(network.loginWithGoogle(idToken))

    override suspend fun continueAsGuest() = establish(network.startGuest())

    override suspend fun register(form: SignUpForm) = network.register(form.toRegistration())

    override suspend fun convertGuest(form: SignUpForm): Outcome<User> {
        val result = network.convertGuest(form.toRegistration())
        if (result is Outcome.Success) {
            saveUser(result.value)
            // Access-token claims (account_type, email) stay stale until the next rotation.
            refresher.refresh(staleAccessToken = null)
        }
        return result
    }

    override suspend fun resendVerification(email: String) = network.resendVerification(email)

    override suspend fun requestPasswordReset(email: String) = network.forgotPassword(email)

    override suspend fun refreshProfile() = network.me().onSuccess { saveUser(it) }

    override suspend fun updateProfile(firstName: String, lastName: String, profileUrl: String?) =
        network.updateProfile(firstName.trim(), lastName.trim(), profileUrl?.trim()?.ifBlank { null })
            .onSuccess { saveUser(it) }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Outcome<String> {
        val result = network.changePassword(currentPassword, newPassword)
        if (result is Outcome.Success) clearLocalSession()
        return result
    }

    override suspend fun signOut() {
        val current = store.current()
        clearLocalSession()
        // Revoke server-side too, but never block or fail sign-out on it.
        if (current != null) scope.launch { network.logout(current.refreshToken) }
    }

    override suspend fun deleteAccount(): Outcome<Unit> {
        val judgeResult = judge.deleteAccount()
        // 404 = no judge account yet (never ran code); anything else must stop the erasure.
        if (judgeResult is Outcome.Failure && judgeResult.error !is AppError.NotFound) return judgeResult
        val authResult = network.deleteAccount()
        if (authResult is Outcome.Failure) return authResult
        store.clear()
        cleaner.clearEverything()
        return Outcome.Success(Unit)
    }

    private suspend fun establish(result: Outcome<StoredSession>): Outcome<User> = when (result) {
        is Outcome.Failure -> result
        is Outcome.Success -> {
            val previous = store.current()?.user?.id
            val user = result.value.user
            if (user == null) {
                Outcome.Failure(AppError.Unknown("Sign-in response had no user."))
            } else {
                if (previous != null && previous != user.id) cleaner.clearAccountScoped()
                store.save(result.value)
                Outcome.Success(user.toModel())
            }
        }
    }

    private suspend fun saveUser(user: User) {
        store.update { it.copy(user = user.toStored()) }
    }

    private suspend fun clearLocalSession() {
        store.clear()
        cleaner.clearAccountScoped()
    }

    private fun SignUpForm.toRegistration() = RegistrationForm(
        email = email,
        password = password,
        firstName = firstName,
        lastName = lastName,
        username = null,
    )
}
