package solutions.laxmi.omnicompiler.core.data.auth

import solutions.laxmi.omnicompiler.core.database.dao.PendingRunDao
import solutions.laxmi.omnicompiler.core.database.dao.ProjectDao
import solutions.laxmi.omnicompiler.core.database.dao.SubmissionDao
import solutions.laxmi.omnicompiler.core.datastore.PreferencesStore
import solutions.laxmi.omnicompiler.core.network.RateLimitTracker
import javax.inject.Inject

/**
 * Decides what local data belongs to an account.
 * Projects are device-local work (like the web playground's local storage) and survive sign-out;
 * server-derived caches and queued runs are tied to the account and do not.
 */
internal class LocalDataCleaner @Inject constructor(
    private val submissions: SubmissionDao,
    private val pendingRuns: PendingRunDao,
    private val projects: ProjectDao,
    private val preferences: PreferencesStore,
    private val rateLimits: RateLimitTracker,
) {
    suspend fun clearAccountScoped() {
        submissions.clearAll()
        pendingRuns.deleteAll()
        rateLimits.clear()
    }

    suspend fun clearEverything() {
        clearAccountScoped()
        projects.deleteAll()
        preferences.clearUserScoped()
    }
}
