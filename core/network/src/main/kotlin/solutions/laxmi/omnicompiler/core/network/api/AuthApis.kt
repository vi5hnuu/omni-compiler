package solutions.laxmi.omnicompiler.core.network.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import solutions.laxmi.omnicompiler.core.network.humancheck.HumanCheck
import solutions.laxmi.omnicompiler.core.network.dto.AuthEnvelope
import solutions.laxmi.omnicompiler.core.network.dto.AuthTokensDto
import solutions.laxmi.omnicompiler.core.network.dto.AuthUserDto
import solutions.laxmi.omnicompiler.core.network.dto.ChangePasswordRequestDto
import solutions.laxmi.omnicompiler.core.network.dto.EmailRequestDto
import solutions.laxmi.omnicompiler.core.network.dto.GoogleLoginRequestDto
import solutions.laxmi.omnicompiler.core.network.dto.LoginRequestDto
import solutions.laxmi.omnicompiler.core.network.dto.RefreshRequestDto
import solutions.laxmi.omnicompiler.core.network.dto.RegisterRequestDto
import solutions.laxmi.omnicompiler.core.network.dto.UpdateProfileRequestDto
import kotlinx.serialization.json.JsonElement

/**
 * Auth endpoints that must never carry a bearer token. Kept on a separate client so a 401 here
 * (e.g. wrong password) is never mistaken for an expired session and never triggers a refresh.
 * Every auth call names the app (`X-Audience`, added by the client); [HumanCheck] calls also carry an attestation.
 */
internal interface AuthPublicApi {
    @HumanCheck("sign-in")
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): AuthEnvelope<AuthTokensDto>

    @POST("auth/login/google")
    suspend fun loginWithGoogle(@Body body: GoogleLoginRequestDto): AuthEnvelope<AuthTokensDto>

    @HumanCheck("guest")
    @POST("auth/guest")
    suspend fun guest(): AuthEnvelope<AuthTokensDto>

    @HumanCheck("sign-up")
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequestDto): AuthEnvelope<JsonElement>

    @HumanCheck("re-verify")
    @POST("auth/re-verify")
    suspend fun resendVerification(@Body body: EmailRequestDto): AuthEnvelope<JsonElement>

    @HumanCheck("forgot-password")
    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: EmailRequestDto): AuthEnvelope<JsonElement>

    /**
     * Revokes a refresh token. Called after the local session is already cleared, so the caller passes the
     * captured access token explicitly instead of going through the refreshing client.
     */
    @POST("auth/logout")
    suspend fun logout(@Header("Authorization") bearer: String, @Body body: RefreshRequestDto): AuthEnvelope<JsonElement>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequestDto): AuthEnvelope<AuthTokensDto>
}

/** Auth endpoints that require the current access token. */
internal interface AuthAccountApi {
    @HumanCheck("convert")
    @POST("auth/convert")
    suspend fun convertGuest(@Body body: RegisterRequestDto): AuthEnvelope<AuthUserDto>

    @GET("user/me")
    suspend fun me(): AuthEnvelope<AuthUserDto>

    @PATCH("user/me")
    suspend fun updateProfile(@Body body: UpdateProfileRequestDto): AuthEnvelope<AuthUserDto>

    @PATCH("user/me/password")
    suspend fun changePassword(@Body body: ChangePasswordRequestDto): AuthEnvelope<JsonElement>

    @DELETE("user/me")
    suspend fun deleteAccount(): AuthEnvelope<JsonElement>
}
