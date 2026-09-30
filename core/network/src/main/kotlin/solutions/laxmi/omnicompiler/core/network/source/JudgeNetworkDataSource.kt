package solutions.laxmi.omnicompiler.core.network.source

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import solutions.laxmi.omnicompiler.core.model.BatchResult
import solutions.laxmi.omnicompiler.core.model.BillingInfo
import solutions.laxmi.omnicompiler.core.model.BillingSyncResult
import solutions.laxmi.omnicompiler.core.model.ExecutionRequest
import solutions.laxmi.omnicompiler.core.model.Job
import solutions.laxmi.omnicompiler.core.model.JobEvent
import solutions.laxmi.omnicompiler.core.model.LanguageStat
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.PlanInfo
import solutions.laxmi.omnicompiler.core.model.ReplayProof
import solutions.laxmi.omnicompiler.core.model.Runtime
import solutions.laxmi.omnicompiler.core.model.Submission
import solutions.laxmi.omnicompiler.core.model.UsageStats
import solutions.laxmi.omnicompiler.core.model.Verdict
import solutions.laxmi.omnicompiler.core.model.Webhook
import solutions.laxmi.omnicompiler.core.model.getOrNull
import solutions.laxmi.omnicompiler.core.model.map
import kotlinx.serialization.SerializationException
import solutions.laxmi.omnicompiler.core.network.api.JudgeApi
import solutions.laxmi.omnicompiler.core.network.dto.CreateWebhookRequestDto
import solutions.laxmi.omnicompiler.core.network.dto.DeleteAccountRequestDto
import solutions.laxmi.omnicompiler.core.network.error.ApiCallRunner
import solutions.laxmi.omnicompiler.core.network.error.requireSuccess
import solutions.laxmi.omnicompiler.core.network.stream.JobSocket
import javax.inject.Inject

data class SubmitResult(val jobId: String, val servedFromCache: Boolean)

data class SubmissionsPage(val items: List<Submission>, val nextCursor: String?)

/** ls-judge operations available to signed-in, guest and anonymous callers (no admin routes). */
interface JudgeNetworkDataSource {
    suspend fun isHealthy(): Boolean
    suspend fun submit(request: ExecutionRequest): Outcome<SubmitResult>
    suspend fun submitBatch(requests: List<ExecutionRequest>): Outcome<BatchResult>
    suspend fun job(id: String): Outcome<Job>
    suspend fun cancel(id: String): Outcome<Unit>
    suspend fun replay(id: String): Outcome<ReplayProof>

    /** Live events for a job. Throws [solutions.laxmi.omnicompiler.core.network.stream.JobStreamException] if the socket drops. */
    fun stream(id: String): Flow<JobEvent>

    suspend fun runtimes(): Outcome<List<Runtime>>
    suspend fun runtime(id: String): Outcome<Runtime>
    suspend fun stats(): Outcome<UsageStats>
    suspend fun languageStats(): Outcome<List<LanguageStat>>
    suspend fun plan(): Outcome<PlanInfo>
    suspend fun billing(): Outcome<BillingInfo>
    suspend fun syncBilling(): Outcome<BillingSyncResult>
    suspend fun cancelSubscription(): Outcome<Unit>
    suspend fun submissions(before: String?, limit: Int): Outcome<SubmissionsPage>
    suspend fun deleteAccount(): Outcome<Unit>
    suspend fun rotateApiKey(): Outcome<String>
    suspend fun webhooks(): Outcome<List<Webhook>>
    suspend fun createWebhook(url: String, secret: String): Outcome<Webhook>
    suspend fun deleteWebhook(id: String): Outcome<Unit>
}

internal class RetrofitJudgeNetworkDataSource @Inject constructor(
    private val api: JudgeApi,
    private val socket: JobSocket,
    private val runner: ApiCallRunner,
) : JudgeNetworkDataSource {

    override suspend fun isHealthy(): Boolean = runner.judge { api.health().isSuccessful }.getOrNull() == true

    override suspend fun submit(request: ExecutionRequest) = runner.judge {
        val response = api.execute(
            idempotencyKey = request.idempotencyKey,
            noCache = if (request.bypassCache) "1" else null,
            body = request.toDto(),
        ).requireSuccess()
        val body = response.body() ?: throw SerializationException("Empty execute response")
        SubmitResult(body.jobId, servedFromCache = response.headers()["X-LS-Cache"] == "hit")
    }

    override suspend fun submitBatch(requests: List<ExecutionRequest>) =
        runner.judge { api.batchExecute(requests.map { it.toDto() }).toModel() }

    override suspend fun job(id: String) = runner.judge { api.job(id).toModel() }

    override suspend fun cancel(id: String) = runner.judge { api.cancelJob(id).requireSuccess() }.map { }

    override suspend fun replay(id: String) = runner.judge { api.replay(id).toModel() }

    override fun stream(id: String): Flow<JobEvent> = socket.frames(id).map { frame ->
        if (frame.done) {
            JobEvent.Completed(
                verdict = Verdict.fromCode(frame.verdict),
                totalTimeMs = frame.timeMs,
                stderr = frame.stderr,
                diagnostic = frame.structuredError?.toModel(),
                job = null,
            )
        } else {
            JobEvent.TestFinished(frame.toTestResult())
        }
    }

    override suspend fun runtimes() = runner.judge { api.runtimes().map { it.toModel() } }

    override suspend fun runtime(id: String) = runner.judge { api.runtime(id).toModel() }

    override suspend fun stats() = runner.judge { api.stats().toModel() }

    override suspend fun languageStats() = runner.judge { api.languageStats().map { it.toModel() } }

    override suspend fun plan() = runner.judge { api.plan().toModel() }

    override suspend fun billing() = runner.judge { api.billing().toModel() }

    override suspend fun syncBilling() = runner.judge {
        val dto = api.syncBilling()
        BillingSyncResult(applied = dto.status == "applied", providerStatus = dto.providerStatus, reason = dto.reason)
    }

    override suspend fun cancelSubscription() = runner.judge { api.cancelBilling().requireSuccess() }.map { }

    override suspend fun submissions(before: String?, limit: Int) = runner.judge {
        val page = api.submissions(before, limit)
        SubmissionsPage(page.submissions.map { it.toModel() }, page.nextCursor?.takeIf { it.isNotBlank() })
    }

    override suspend fun deleteAccount() =
        runner.judge { api.deleteAccount(DeleteAccountRequestDto(DELETE_CONFIRMATION)).requireSuccess() }.map { }

    override suspend fun rotateApiKey() = runner.judge { api.rotateApiKey().apiKey }

    override suspend fun webhooks() = runner.judge { api.webhooks().map { it.toModel() } }

    override suspend fun createWebhook(url: String, secret: String) =
        runner.judge { api.createWebhook(CreateWebhookRequestDto(url.trim(), secret)).toModel() }

    override suspend fun deleteWebhook(id: String) = runner.judge { api.deleteWebhook(id).requireSuccess() }.map { }

    private companion object {
        /** Literal phrase the server requires to confirm erasure. */
        const val DELETE_CONFIRMATION = "delete my account"
    }
}
