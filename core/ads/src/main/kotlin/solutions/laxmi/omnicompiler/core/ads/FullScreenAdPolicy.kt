package solutions.laxmi.omnicompiler.core.ads

/** What [FullScreenAdPolicy] weighs at a break; times are epoch milliseconds. */
data class FullScreenAdMoment(
    val now: Long,
    val firstLaunchAt: Long,
    val lastShownAt: Long?,
    val lastFailedRunAt: Long?,
    val passActive: Boolean,
    val userBusy: Boolean,
)

/**
 * When a full-screen ad (interstitial or app open) may appear. Only at breaks the caller already chose; never in the
 * first day after install, never within [minIntervalMs] of the previous one of either kind, never during an ad-free
 * pass, never while the user is busy (a run in progress or the keyboard up), and never just after a run failed, when
 * the user is busy fixing it.
 */
class FullScreenAdPolicy(
    private val graceAfterInstallMs: Long = 24 * HOUR,
    private val minIntervalMs: Long = 3 * MINUTE,
    private val quietAfterFailureMs: Long = MINUTE,
) {
    fun mayShow(moment: FullScreenAdMoment): Boolean = with(moment) {
        when {
            passActive || userBusy -> false
            now - firstLaunchAt < graceAfterInstallMs -> false
            lastShownAt != null && now - lastShownAt in 0 until minIntervalMs -> false
            lastFailedRunAt != null && now - lastFailedRunAt in 0 until quietAfterFailureMs -> false
            else -> true
        }
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
