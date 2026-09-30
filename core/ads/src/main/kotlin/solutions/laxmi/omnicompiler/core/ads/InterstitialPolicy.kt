package solutions.laxmi.omnicompiler.core.ads

/**
 * When a full-screen ad may appear. Only at natural breaks the caller already chose (opening another project,
 * starting from an example); never in the first day after install, never more often than [minIntervalMs],
 * and never while the user is busy (a run in progress or the keyboard up).
 */
class InterstitialPolicy(
    private val graceAfterInstallMs: Long = 24 * HOUR,
    private val minIntervalMs: Long = 5 * MINUTE,
) {
    fun mayShow(now: Long, firstLaunchAt: Long, lastShownAt: Long?, userBusy: Boolean): Boolean = when {
        userBusy -> false
        now - firstLaunchAt < graceAfterInstallMs -> false
        lastShownAt != null && now - lastShownAt < minIntervalMs -> false
        else -> true
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
