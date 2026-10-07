package solutions.laxmi.omnicompiler.core.model

import kotlin.time.Instant

enum class AccountType { USER, GUEST }
enum class AuthProvider { LOCAL, GOOGLE, GUEST }

data class User(
    val id: String,
    val accountType: AccountType,
    val provider: AuthProvider,
    val email: String?,
    val username: String?,
    val firstName: String?,
    val lastName: String?,
    val profileUrl: String?,
    val verified: Boolean,
    val createdAt: Instant?,
) {
    val isGuest: Boolean get() = accountType == AccountType.GUEST
    val hasPassword: Boolean get() = provider == AuthProvider.LOCAL

    /** Name, else username, else e-mail; empty when the account has none (the UI supplies a localized label). */
    val displayName: String
        get() = listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { username ?: email.orEmpty() }

    val initials: String
        get() {
            val parts = displayName.split(' ', '.', '_').filter { it.isNotBlank() }
            return parts.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
        }
}

sealed interface Session {
    data object Loading : Session
    data object SignedOut : Session
    data class Active(val user: User) : Session
}

data class PlanInfo(
    val plan: String,
    val effectivePlan: String,
    val planSource: String?,
    val rateLimitRpm: Int,
    val expiresAt: String?,
    val expiryWarning: String?,
)

data class BillingInfo(
    val plan: String,
    val effectivePlan: String,
    val status: String,
    val provider: String?,
    val subscriptionId: String?,
    val periodStart: String?,
    val expiresAt: String?,
    val rateLimitRpm: Int,
    val executionsUsed: Long,
    val quotaLimit: Long,
    val quotaRemaining: Long,
    val testMode: Boolean,
) {
    val hasCancellableSubscription: Boolean
        get() = status.equals("active", true) || status.equals("past_due", true)
}

data class BillingSyncResult(val applied: Boolean, val providerStatus: String?, val reason: String?)

data class UsageStats(
    val totalJobs: Long,
    val acceptedJobs: Long,
    val acceptanceRate: Double,
    val plan: String,
    val effectivePlan: String,
    val rateLimitRpm: Int,
    val authenticated: Boolean,
    val expiryWarning: String?,
)

data class LanguageStat(val language: String, val totalJobs: Long, val acceptedJobs: Long, val acceptanceRate: Double)

/** Latest `X-RateLimit-*` values observed on execute calls. */
data class RateLimitSnapshot(val limit: Int, val remaining: Int, val resetEpochSeconds: Long)

data class Webhook(
    val id: String,
    val url: String,
    val enabled: Boolean,
    val createdAt: String?,
    val secret: String?,
)
