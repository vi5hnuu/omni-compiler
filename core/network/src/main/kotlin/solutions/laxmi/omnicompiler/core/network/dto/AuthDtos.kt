package solutions.laxmi.omnicompiler.core.network.dto

import kotlinx.serialization.Serializable

/** Every auth-service response is wrapped in this envelope, including errors (`success=false`). */
@Serializable
internal data class AuthEnvelope<T>(
    val success: Boolean = false,
    val message: String? = null,
    val data: T? = null,
)

@Serializable
internal data class AuthTokensDto(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresInSeconds: Long,
    val user: AuthUserDto? = null,
)

@Serializable
internal data class AuthUserDto(
    val id: String,
    val accountType: String,
    val authProvider: String,
    val email: String? = null,
    val username: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val profileUrl: String? = null,
    val roles: List<String> = emptyList(),
    val enabled: Boolean = false,
    val locked: Boolean = false,
    val createdAt: String? = null,
)

@Serializable
internal data class LoginRequestDto(val identifier: String, val password: String)

@Serializable
internal data class GoogleLoginRequestDto(val idToken: String)

@Serializable
internal data class RegisterRequestDto(
    val email: String,
    val password: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val username: String? = null,
)

@Serializable
internal data class RefreshRequestDto(val refreshToken: String)

@Serializable
internal data class EmailRequestDto(val email: String)

@Serializable
internal data class UpdateProfileRequestDto(
    val firstName: String? = null,
    val lastName: String? = null,
    val profileUrl: String? = null,
)

@Serializable
internal data class ChangePasswordRequestDto(val oldPassword: String, val newPassword: String)
