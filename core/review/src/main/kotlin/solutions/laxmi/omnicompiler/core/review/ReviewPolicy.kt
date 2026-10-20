package solutions.laxmi.omnicompiler.core.review

import solutions.laxmi.omnicompiler.core.datastore.Engagement

/**
 * When the Google Play review card may be requested. Google asks for it only once someone has used the app enough
 * to judge it, and Play itself shows it at most about once a month; so only after a few days, a few launches and a few
 * runs that passed, and never again within [minIntervalMs] of the last request.
 */
class ReviewPolicy(
    private val minAgeMs: Long = 3 * DAY,
    private val minLaunches: Int = 5,
    private val minAcceptedRuns: Int = 3,
    private val minIntervalMs: Long = 30 * DAY,
) {
    fun mayRequest(now: Long, engagement: Engagement): Boolean {
        val lastRequest = engagement.lastReviewRequestAt
        return when {
            engagement.firstLaunchAt == 0L || now - engagement.firstLaunchAt < minAgeMs -> false
            engagement.launches < minLaunches -> false
            engagement.acceptedRuns < minAcceptedRuns -> false
            lastRequest != null && now - lastRequest < minIntervalMs -> false
            else -> true
        }
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1_000L
    }
}
