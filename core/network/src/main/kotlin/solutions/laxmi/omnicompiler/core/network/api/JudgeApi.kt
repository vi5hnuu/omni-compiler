package solutions.laxmi.omnicompiler.core.network.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import solutions.laxmi.omnicompiler.core.network.dto.BatchResultDto
import solutions.laxmi.omnicompiler.core.network.dto.BillingDto
import solutions.laxmi.omnicompiler.core.network.dto.BillingSyncDto
import solutions.laxmi.omnicompiler.core.network.dto.CreateWebhookRequestDto
import solutions.laxmi.omnicompiler.core.network.dto.DeleteAccountRequestDto
import solutions.laxmi.omnicompiler.core.network.dto.ExecuteRequestDto
import solutions.laxmi.omnicompiler.core.network.dto.ExecuteResponseDto
import solutions.laxmi.omnicompiler.core.network.dto.JobResponseDto
import solutions.laxmi.omnicompiler.core.network.dto.LanguageStatDto
import solutions.laxmi.omnicompiler.core.network.dto.PlanDto
import solutions.laxmi.omnicompiler.core.network.dto.ReplayResponseDto
import solutions.laxmi.omnicompiler.core.network.dto.RotateKeyResponseDto
import solutions.laxmi.omnicompiler.core.network.dto.RuntimeDto
import solutions.laxmi.omnicompiler.core.network.dto.StatsDto
import solutions.laxmi.omnicompiler.core.network.dto.SubmissionsPageDto
import solutions.laxmi.omnicompiler.core.network.dto.WebhookDto

/** ls-judge public API. Admin routes are intentionally absent. */
internal interface JudgeApi {
    @GET("health")
    suspend fun health(): Response<Unit>

    @POST("execute")
    suspend fun execute(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Header("X-No-Cache") noCache: String?,
        @Body body: ExecuteRequestDto,
    ): Response<ExecuteResponseDto>

    /** 207 Multi-Status: each item is accepted or rejected independently. */
    @POST("batch/execute")
    suspend fun batchExecute(@Body body: List<ExecuteRequestDto>): BatchResultDto

    @GET("jobs/{id}")
    suspend fun job(@Path("id") id: String): JobResponseDto

    @DELETE("jobs/{id}")
    suspend fun cancelJob(@Path("id") id: String): Response<Unit>

    @POST("jobs/{id}/replay")
    suspend fun replay(@Path("id") id: String): ReplayResponseDto

    @GET("runtimes")
    suspend fun runtimes(): List<RuntimeDto>

    @GET("runtimes/{id}")
    suspend fun runtime(@Path("id") id: String): RuntimeDto

    @GET("stats")
    suspend fun stats(): StatsDto

    @GET("stats/languages")
    suspend fun languageStats(): List<LanguageStatDto>

    @GET("me/plan")
    suspend fun plan(): PlanDto

    @GET("me/billing")
    suspend fun billing(): BillingDto

    @POST("me/billing/sync")
    suspend fun syncBilling(): BillingSyncDto

    @POST("me/billing/cancel")
    suspend fun cancelBilling(): Response<Unit>

    @GET("me/submissions")
    suspend fun submissions(
        @Query("before") before: String?,
        @Query("limit") limit: Int,
    ): SubmissionsPageDto

    @HTTP(method = "DELETE", path = "me", hasBody = true)
    suspend fun deleteAccount(@Body body: DeleteAccountRequestDto): Response<Unit>

    @POST("users/rotate-key")
    suspend fun rotateApiKey(): RotateKeyResponseDto

    @POST("webhooks")
    suspend fun createWebhook(@Body body: CreateWebhookRequestDto): WebhookDto

    @GET("webhooks")
    suspend fun webhooks(): List<WebhookDto>

    @DELETE("webhooks/{id}")
    suspend fun deleteWebhook(@Path("id") id: String): Response<Unit>
}
