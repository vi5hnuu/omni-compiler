package solutions.laxmi.omnicompiler.core.network.source

import solutions.laxmi.omnicompiler.core.common.TimeSource
import solutions.laxmi.omnicompiler.core.datastore.StoredSession
import solutions.laxmi.omnicompiler.core.model.AppError
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.User
import solutions.laxmi.omnicompiler.core.model.map
import solutions.laxmi.omnicompiler.core.network.BuildConfig
import solutions.laxmi.omnicompiler.core.network.api.AuthAccountApi
import solutions.laxmi.omnicompiler.core.network.api.AuthPublicApi
import solutions.laxmi.omnicompiler.core.network.auth.toStoredSession
import solutions.laxmi.omnicompiler.core.network.dto.AuthEnvelope
import solutions.laxmi.omnicompiler.core.network.dto.AuthTokensDto
import solutions.laxmi.omnicompiler.core.network.dto.ChangePasswordRequestDto
import solutions.laxmi.omnicompiler.core.network.dto.EmailRequestDto
import solutions.laxmi.omnicompiler.core.network.dto.GoogleLoginRequestDto
import solutions.laxmi.omnicompiler.core.network.dto.LoginRequestDto
import solutions.laxmi.omnicompiler.core.network.dto.RefreshRequestDto
import solutions.laxmi.omnicompiler.core.network.dto.RegisterRequestDto
import solutions.laxmi.omnicompiler.core.network.dto.UpdateProfileRequestDto
import solutions.laxmi.omnicompiler.core.network.error.ApiCallRunner
import javax.inject.Inject

data class RegistrationForm(
    val email: String,
    val password: String,
    val firstName: String?,
    val lastName: String?,
    val username: String?,
)

/** Auth-service operations. Sign-in calls return a ready-to-persist session. */
interface AuthNetworkDataSource {
    suspend fun login(identifier: String, password: String): Outcome<StoredSession>
    suspend fun loginWithGoogle(idToken: String): Outcome<StoredSession>
    suspend fun startGuest(): Outcome<StoredSession>
    suspend fun register(form: RegistrationForm): Outcome<String>
    suspend fun resendVerification(email: String): Outcome<String>
    suspend fun forgotPassword(email: String): Outcome<String>
    suspend fun logout(refreshToken: String): Outcome<Unit>
    suspend fun convertGuest(form: RegistrationForm): Outcome<User>
    suspend fun me(): Outcome<User>
    suspend fun updateProfile(firstName: String?, lastName: String?, profileUrl: String?): Outcome<User>
    suspend fun changePassword(oldPassword: String, newPassword: String): Outcome<String>
    suspend fun deleteAccount(): Outcome<Unit>
}

internal class RetrofitAuthNetworkDataSource @Inject constructor(
    private val public: AuthPublicApi,
    private val account: AuthAccountApi,
    private val runner: ApiCallRunner,
    private val time: TimeSource,
) : AuthNetworkDataSource {

    private val audience get() = BuildConfig.JWT_AUDIENCE

    override suspend fun login(identifier: String, password: String) =
        session { public.login(audience, LoginRequestDto(identifier.trim(), password)) }

    override suspend fun loginWithGoogle(idToken: String) =
        session { public.loginWithGoogle(audience, GoogleLoginRequestDto(idToken)) }

    override suspend fun startGuest() = session { public.guest(audience) }

    override suspend fun register(form: RegistrationForm) =
        runner.auth { public.register(form.toDto()).message.orEmpty() }

    override suspend fun resendVerification(email: String) =
        runner.auth { public.resendVerification(EmailRequestDto(email.trim())).message.orEmpty() }

    override suspend fun forgotPassword(email: String) =
        runner.auth { public.forgotPassword(EmailRequestDto(email.trim())).message.orEmpty() }

    override suspend fun logout(refreshToken: String) =
        runner.auth { account.logout(RefreshRequestDto(refreshToken)) }.map { }

    override suspend fun convertGuest(form: RegistrationForm) =
        runner.auth { account.convertGuest(form.toDto()).requireData().toModel() }

    override suspend fun me() = runner.auth { account.me().requireData().toModel() }

    override suspend fun updateProfile(firstName: String?, lastName: String?, profileUrl: String?) =
        runner.auth { account.updateProfile(UpdateProfileRequestDto(firstName, lastName, profileUrl)).requireData().toModel() }

    override suspend fun changePassword(oldPassword: String, newPassword: String) =
        runner.auth { account.changePassword(ChangePasswordRequestDto(oldPassword, newPassword)).message.orEmpty() }

    override suspend fun deleteAccount() = runner.auth { account.deleteAccount() }.map { }

    private suspend fun session(call: suspend () -> AuthEnvelope<AuthTokensDto>): Outcome<StoredSession> =
        when (val result = runner.auth { call().requireData() }) {
            is Outcome.Success -> Outcome.Success(result.value.toStoredSession(time.now().toEpochMilliseconds(), previousUser = null))
            is Outcome.Failure -> result
        }

    private fun RegistrationForm.toDto() = RegisterRequestDto(
        email = email.trim(),
        password = password,
        firstName = firstName?.trim()?.ifBlank { null },
        lastName = lastName?.trim()?.ifBlank { null },
        username = username?.trim()?.ifBlank { null },
    )

    private fun <T> AuthEnvelope<T>.requireData(): T =
        data ?: throw MissingDataException(message ?: AppError.Unknown().message)
}

/** A 2xx envelope without `data` is a contract violation; surfaced like a malformed body. */
private class MissingDataException(message: String) : kotlinx.serialization.SerializationException(message)
