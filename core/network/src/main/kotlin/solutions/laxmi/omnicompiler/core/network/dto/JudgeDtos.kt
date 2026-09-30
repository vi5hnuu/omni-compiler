package solutions.laxmi.omnicompiler.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** `{"error":{code,message,request_id,details}}` returned by every non-2xx judge response. */
@Serializable
internal data class JudgeErrorEnvelope(val error: JudgeErrorDto? = null)

@Serializable
internal data class JudgeErrorDto(
    val code: String? = null,
    val message: String? = null,
    @SerialName("request_id") val requestId: String? = null,
    val details: JsonElement? = null,
)

@Serializable
internal data class ExecuteRequestDto(
    val language: String,
    val code: String,
    @SerialName("time_limit_ms") val timeLimitMs: Int,
    @SerialName("mem_limit_mb") val memLimitMb: Int,
    @SerialName("test_cases") val testCases: List<TestCaseDto>,
    val files: List<FileEntryDto>? = null,
    val interactive: Boolean? = null,
)

@Serializable
internal data class TestCaseDto(val stdin: String, val expected: String)

@Serializable
internal data class FileEntryDto(val name: String, val content: String)

@Serializable
internal data class ExecuteResponseDto(@SerialName("job_id") val jobId: String)

@Serializable
internal data class JobResponseDto(
    @SerialName("job_id") val jobId: String,
    val status: String,
    val verdict: String? = null,
    @SerialName("total_time_ms") val totalTimeMs: Int? = null,
    val results: List<TestResultDto>? = null,
    val code: String? = null,
)

@Serializable
internal data class TestResultDto(
    val index: Int,
    val verdict: String,
    @SerialName("time_ms") val timeMs: Int? = null,
    val stdin: String? = null,
    val expected: String? = null,
    val stdout: String? = null,
    val stderr: String? = null,
)

/** One WebSocket frame: a finished test, or the terminal `done` frame. */
@Serializable
internal data class AgentFrameDto(
    val index: Int = 0,
    val verdict: String? = null,
    @SerialName("time_ms") val timeMs: Int? = null,
    val stdout: String? = null,
    val stderr: String? = null,
    @SerialName("structured_error") val structuredError: StructuredErrorDto? = null,
    val done: Boolean = false,
)

@Serializable
internal data class StructuredErrorDto(val line: Int? = null, val col: Int? = null, val message: String = "")

@Serializable
internal data class RuntimeDto(
    val id: String,
    val language: String,
    val version: String,
    val status: String? = null,
    val filename: String = "",
    val available: Boolean = false,
    val lane: String? = null,
)

@Serializable
internal data class BatchResultDto(
    val accepted: List<BatchAcceptedDto>? = null,
    val errors: List<BatchErrorDto>? = null,
)

@Serializable
internal data class BatchAcceptedDto(val index: Int, @SerialName("job_id") val jobId: String)

@Serializable
internal data class BatchErrorDto(val index: Int, val error: String)

@Serializable
internal data class ReplayResponseDto(
    val verdict: String? = null,
    @SerialName("total_time_ms") val totalTimeMs: Int? = null,
    val proof: ReplayProofDto,
)

@Serializable
internal data class ReplayProofDto(
    val version: String = "",
    @SerialName("runtime_id") val runtimeId: String = "",
    @SerialName("ext4_hash") val ext4Hash: String = "",
    @SerialName("code_hash") val codeHash: String = "",
    @SerialName("input_hash") val inputHash: String = "",
    val verdict: String? = null,
    @SerialName("total_time_ms") val totalTimeMs: Int? = null,
    val signature: String = "",
    @SerialName("signed_at") val signedAt: String = "",
)

@Serializable
internal data class StatsDto(
    @SerialName("total_jobs") val totalJobs: Long = 0,
    @SerialName("ac_count") val acCount: Long = 0,
    @SerialName("ac_rate") val acRate: Double = 0.0,
    val plan: String = "",
    @SerialName("effective_plan") val effectivePlan: String = "",
    @SerialName("rate_limit_rpm") val rateLimitRpm: Int = 0,
    val authenticated: Boolean = false,
    @SerialName("plan_expiry_warning") val planExpiryWarning: String? = null,
)

@Serializable
internal data class LanguageStatDto(
    val language: String,
    @SerialName("total_jobs") val totalJobs: Long = 0,
    @SerialName("ac_count") val acCount: Long = 0,
    @SerialName("ac_rate") val acRate: Double = 0.0,
)

@Serializable
internal data class PlanDto(
    val plan: String = "",
    @SerialName("plan_source") val planSource: String? = null,
    @SerialName("rate_limit_rpm") val rateLimitRpm: Int = 0,
    @SerialName("effective_plan") val effectivePlan: String = "",
    @SerialName("plan_expires_at") val planExpiresAt: String? = null,
    @SerialName("plan_expiry_warning") val planExpiryWarning: String? = null,
)

@Serializable
internal data class BillingDto(
    val plan: String = "",
    @SerialName("effective_plan") val effectivePlan: String = "",
    @SerialName("plan_source") val planSource: String? = null,
    @SerialName("plan_expires_at") val planExpiresAt: String? = null,
    @SerialName("rate_limit_rpm") val rateLimitRpm: Int = 0,
    @SerialName("billing_provider") val billingProvider: String? = null,
    @SerialName("billing_subscription_id") val billingSubscriptionId: String? = null,
    @SerialName("billing_status") val billingStatus: String = "",
    @SerialName("current_period_start") val currentPeriodStart: String? = null,
    @SerialName("executions_used") val executionsUsed: Long = 0,
    @SerialName("quota_limit") val quotaLimit: Long = 0,
    @SerialName("quota_remaining") val quotaRemaining: Long = 0,
    @SerialName("test_mode") val testMode: Boolean = false,
)

@Serializable
internal data class BillingSyncDto(
    val status: String = "",
    @SerialName("provider_status") val providerStatus: String? = null,
    val reason: String? = null,
)

@Serializable
internal data class SubmissionsPageDto(
    val submissions: List<SubmissionDto> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
internal data class SubmissionDto(
    val id: String,
    @SerialName("runtime_id") val runtimeId: String,
    val status: String,
    val verdict: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("total_time_ms") val totalTimeMs: Int? = null,
)

@Serializable
internal data class DeleteAccountRequestDto(val confirm: String)

@Serializable
internal data class RotateKeyResponseDto(@SerialName("api_key") val apiKey: String)

@Serializable
internal data class CreateWebhookRequestDto(val url: String, val secret: String)

@Serializable
internal data class WebhookDto(
    val id: String,
    val url: String,
    val enabled: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    val secret: String? = null,
)
