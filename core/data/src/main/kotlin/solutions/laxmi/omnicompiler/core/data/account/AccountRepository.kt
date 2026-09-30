package solutions.laxmi.omnicompiler.core.data.account

import kotlinx.coroutines.flow.StateFlow
import solutions.laxmi.omnicompiler.core.data.BuildConfig
import solutions.laxmi.omnicompiler.core.model.BillingInfo
import solutions.laxmi.omnicompiler.core.model.BillingSyncResult
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.PlanInfo
import solutions.laxmi.omnicompiler.core.model.RateLimitSnapshot
import solutions.laxmi.omnicompiler.core.model.UsageStats
import solutions.laxmi.omnicompiler.core.model.Webhook
import solutions.laxmi.omnicompiler.core.network.RateLimitTracker
import solutions.laxmi.omnicompiler.core.network.source.JudgeNetworkDataSource
import javax.inject.Inject

/** Plan, quota and billing state from ls-judge. Purchasing is intentionally not exposed in the app. */
interface AccountRepository {
    val rateLimit: StateFlow<RateLimitSnapshot?>
    suspend fun plan(): Outcome<PlanInfo>
    suspend fun billing(): Outcome<BillingInfo>
    suspend fun stats(): Outcome<UsageStats>
    suspend fun syncBilling(): Outcome<BillingSyncResult>
    suspend fun cancelSubscription(): Outcome<Unit>
}

internal class DefaultAccountRepository @Inject constructor(
    private val network: JudgeNetworkDataSource,
    tracker: RateLimitTracker,
) : AccountRepository {
    override val rateLimit = tracker.snapshot
    override suspend fun plan() = network.plan()
    override suspend fun billing() = network.billing()
    override suspend fun stats() = network.stats()
    override suspend fun syncBilling() = network.syncBilling()
    override suspend fun cancelSubscription() = network.cancelSubscription()
}

/** Developer access to ls-judge from outside the app: API key and job-completion webhooks. */
interface DeveloperRepository {
    /** Public API base shown in the quick-start snippet. */
    val apiBaseUrl: String

    /** Issues a new key and invalidates the previous one; the value is only ever shown once. */
    suspend fun rotateApiKey(): Outcome<String>
    suspend fun webhooks(): Outcome<List<Webhook>>
    suspend fun createWebhook(url: String, secret: String): Outcome<Webhook>
    suspend fun deleteWebhook(id: String): Outcome<Unit>
}

internal class DefaultDeveloperRepository @Inject constructor(
    private val network: JudgeNetworkDataSource,
) : DeveloperRepository {
    override val apiBaseUrl: String = solutions.laxmi.omnicompiler.core.network.BuildConfig.API_BASE_URL.trimEnd('/')
    override suspend fun rotateApiKey() = network.rotateApiKey()
    override suspend fun webhooks() = network.webhooks()
    override suspend fun createWebhook(url: String, secret: String) = network.createWebhook(url, secret)
    override suspend fun deleteWebhook(id: String) = network.deleteWebhook(id)
}

/** Links and switches baked in at build time. */
interface AppConfig {
    val privacyPolicyUrl: String
    val termsUrl: String
    val isGoogleSignInConfigured: Boolean
}

internal class BuildAppConfig @Inject constructor() : AppConfig {
    override val privacyPolicyUrl = BuildConfig.LEGAL_BASE_URL.trimEnd('/') + "/privacy-policy"
    override val termsUrl = BuildConfig.LEGAL_BASE_URL.trimEnd('/') + "/terms-of-service"
    override val isGoogleSignInConfigured = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
}
