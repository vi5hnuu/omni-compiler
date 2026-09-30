package solutions.laxmi.omnicompiler.core.network.source

import solutions.laxmi.omnicompiler.core.model.AccountType
import solutions.laxmi.omnicompiler.core.model.AuthProvider
import solutions.laxmi.omnicompiler.core.model.BatchAccepted
import solutions.laxmi.omnicompiler.core.model.BatchRejected
import solutions.laxmi.omnicompiler.core.model.BatchResult
import solutions.laxmi.omnicompiler.core.model.BillingInfo
import solutions.laxmi.omnicompiler.core.model.CompileDiagnostic
import solutions.laxmi.omnicompiler.core.model.ExecutionRequest
import solutions.laxmi.omnicompiler.core.model.Job
import solutions.laxmi.omnicompiler.core.model.JobStatus
import solutions.laxmi.omnicompiler.core.model.LanguageStat
import solutions.laxmi.omnicompiler.core.model.Lane
import solutions.laxmi.omnicompiler.core.model.PlanInfo
import solutions.laxmi.omnicompiler.core.model.ReplayProof
import solutions.laxmi.omnicompiler.core.model.Runtime
import solutions.laxmi.omnicompiler.core.model.RuntimeStatus
import solutions.laxmi.omnicompiler.core.model.Submission
import solutions.laxmi.omnicompiler.core.model.TestResult
import solutions.laxmi.omnicompiler.core.model.UsageStats
import solutions.laxmi.omnicompiler.core.model.User
import solutions.laxmi.omnicompiler.core.model.Verdict
import solutions.laxmi.omnicompiler.core.model.Webhook
import solutions.laxmi.omnicompiler.core.network.dto.AgentFrameDto
import solutions.laxmi.omnicompiler.core.network.dto.AuthUserDto
import solutions.laxmi.omnicompiler.core.network.dto.BatchResultDto
import solutions.laxmi.omnicompiler.core.network.dto.BillingDto
import solutions.laxmi.omnicompiler.core.network.dto.ExecuteRequestDto
import solutions.laxmi.omnicompiler.core.network.dto.FileEntryDto
import solutions.laxmi.omnicompiler.core.network.dto.JobResponseDto
import solutions.laxmi.omnicompiler.core.network.dto.LanguageStatDto
import solutions.laxmi.omnicompiler.core.network.dto.PlanDto
import solutions.laxmi.omnicompiler.core.network.dto.ReplayResponseDto
import solutions.laxmi.omnicompiler.core.network.dto.RuntimeDto
import solutions.laxmi.omnicompiler.core.network.dto.StatsDto
import solutions.laxmi.omnicompiler.core.network.dto.StructuredErrorDto
import solutions.laxmi.omnicompiler.core.network.dto.SubmissionDto
import solutions.laxmi.omnicompiler.core.network.dto.TestCaseDto
import solutions.laxmi.omnicompiler.core.network.dto.TestResultDto
import solutions.laxmi.omnicompiler.core.network.dto.WebhookDto
import kotlin.time.Instant

internal fun ExecutionRequest.toDto() = ExecuteRequestDto(
    language = runtimeId,
    code = code,
    timeLimitMs = limits.timeMs,
    memLimitMb = limits.memMb,
    testCases = tests.map { TestCaseDto(it.stdin, it.expected) },
    files = files.takeIf { it.isNotEmpty() }?.map { FileEntryDto(it.name, it.content) },
    interactive = interactive.takeIf { it },
)

internal fun RuntimeDto.toModel() = Runtime(
    id = id,
    language = language,
    version = version,
    status = RuntimeStatus.fromWire(status),
    filename = filename,
    available = available,
    lane = Lane.fromWire(lane),
)

internal fun JobResponseDto.toModel() = Job(
    id = jobId,
    status = JobStatus.fromWire(status),
    verdict = Verdict.fromCode(verdict),
    totalTimeMs = totalTimeMs,
    results = results.orEmpty().map { it.toModel() }.sortedBy { it.index },
    code = code,
)

internal fun TestResultDto.toModel() = TestResult(
    index = index,
    verdict = Verdict.fromCode(verdict) ?: Verdict.IE,
    timeMs = timeMs,
    stdin = stdin,
    expected = expected,
    stdout = stdout,
    stderr = stderr,
)

internal fun AgentFrameDto.toTestResult() = TestResult(
    index = index,
    verdict = Verdict.fromCode(verdict) ?: Verdict.IE,
    timeMs = timeMs,
    stdin = null,
    expected = null,
    stdout = stdout,
    stderr = stderr,
)

internal fun StructuredErrorDto.toModel() = CompileDiagnostic(
    line = line?.takeIf { it > 0 },
    column = col?.takeIf { it > 0 },
    message = message,
)

internal fun SubmissionDto.toModel() = Submission(
    id = id,
    runtimeId = runtimeId,
    status = JobStatus.fromWire(status),
    verdict = Verdict.fromCode(verdict),
    createdAt = parseInstant(createdAt) ?: Instant.fromEpochMilliseconds(0),
    totalTimeMs = totalTimeMs,
)

internal fun BatchResultDto.toModel() = BatchResult(
    accepted = accepted.orEmpty().map { BatchAccepted(it.index, it.jobId) },
    rejected = errors.orEmpty().map { BatchRejected(it.index, it.error) },
)

internal fun ReplayResponseDto.toModel() = ReplayProof(
    verdict = Verdict.fromCode(verdict ?: proof.verdict),
    totalTimeMs = totalTimeMs ?: proof.totalTimeMs,
    version = proof.version,
    runtimeId = proof.runtimeId,
    imageHash = proof.ext4Hash,
    codeHash = proof.codeHash,
    inputHash = proof.inputHash,
    signature = proof.signature,
    signedAt = proof.signedAt,
)

internal fun StatsDto.toModel() = UsageStats(
    totalJobs = totalJobs,
    acceptedJobs = acCount,
    acceptanceRate = acRate,
    plan = plan,
    effectivePlan = effectivePlan,
    rateLimitRpm = rateLimitRpm,
    authenticated = authenticated,
    expiryWarning = planExpiryWarning,
)

internal fun LanguageStatDto.toModel() = LanguageStat(language, totalJobs, acCount, acRate)

internal fun PlanDto.toModel() = PlanInfo(
    plan = plan,
    effectivePlan = effectivePlan,
    planSource = planSource,
    rateLimitRpm = rateLimitRpm,
    expiresAt = planExpiresAt,
    expiryWarning = planExpiryWarning,
)

internal fun BillingDto.toModel() = BillingInfo(
    plan = plan,
    effectivePlan = effectivePlan,
    status = billingStatus,
    provider = billingProvider,
    subscriptionId = billingSubscriptionId,
    periodStart = currentPeriodStart,
    expiresAt = planExpiresAt,
    rateLimitRpm = rateLimitRpm,
    executionsUsed = executionsUsed,
    quotaLimit = quotaLimit,
    quotaRemaining = quotaRemaining,
    testMode = testMode,
)

internal fun WebhookDto.toModel() = Webhook(id = id, url = url, enabled = enabled, createdAt = createdAt, secret = secret)

internal fun AuthUserDto.toModel() = User(
    id = id,
    accountType = if (accountType.equals("GUEST", true)) AccountType.GUEST else AccountType.USER,
    provider = AuthProvider.entries.firstOrNull { it.name.equals(authProvider, true) } ?: AuthProvider.LOCAL,
    email = email,
    username = username,
    firstName = firstName,
    lastName = lastName,
    profileUrl = profileUrl,
    verified = enabled,
    createdAt = parseInstant(createdAt),
)

internal fun parseInstant(value: String?): Instant? = value?.let { runCatching { Instant.parse(it) }.getOrNull() }
