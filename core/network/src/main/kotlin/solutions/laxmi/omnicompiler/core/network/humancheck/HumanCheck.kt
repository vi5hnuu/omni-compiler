package solutions.laxmi.omnicompiler.core.network.humancheck

/**
 * Marks an auth call the server protects against bots (sign-in, sign-up, guest sessions, e-mails, guest conversion).
 * [HumanCheckInterceptor] attaches an attestation bound to the exact request; [action] must match the action the auth
 * service expects for that endpoint (see the auth service's docs/BOT_PROTECTION.md).
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class HumanCheck(val action: String)

/**
 * Produces attestation tokens for protected calls. The network layer owns the contract; the implementation (Play
 * Integrity) lives in its own module so nothing here depends on Google Play.
 */
interface HumanCheckTokens {
    /**
     * A token bound to [requestHash], or null when none can be produced (no Play services, quota, offline). The
     * request then goes out without one and the server's policy decides: the client never blocks a sign-in itself.
     */
    suspend fun token(requestHash: String): String?
}
