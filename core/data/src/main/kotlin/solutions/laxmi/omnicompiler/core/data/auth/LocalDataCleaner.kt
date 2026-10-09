package solutions.laxmi.omnicompiler.core.data.auth

import solutions.laxmi.omnicompiler.core.database.dao.PendingRunDao
import solutions.laxmi.omnicompiler.core.database.dao.ProjectDao
import solutions.laxmi.omnicompiler.core.database.dao.SubmissionDao
import solutions.laxmi.omnicompiler.core.datastore.GitCredentialStore
import solutions.laxmi.omnicompiler.core.datastore.PreferencesStore
import solutions.laxmi.omnicompiler.core.network.RateLimitTracker
import javax.inject.Inject

/**
 * Decides what local data belongs to an account.
 * Projects are device-local work (like the web playground's local storage) and survive sign-out;
 * server-derived caches, queued runs and connected GitHub/GitLab tokens are tied to the account and do not, so
 * the next person signing in on this device can't push with someone else's token.
 */
internal class LocalDataCleaner @Inject constructor(
    private val submissions: SubmissionDao,
    private val pendingRuns: PendingRunDao,
    private val projects: ProjectDao,
    private val preferences: PreferencesStore,
    private val rateLimits: RateLimitTracker,
    private val gitCredentials: GitCredentialStore,
) {
    suspend fun clearAccountScoped() {
        submissions.clearAll()
        pendingRuns.deleteAll()
        rateLimits.clear()
        gitCredentials.clear()
    }

    suspend fun clearEverything() {
        clearAccountScoped()
        projects.deleteAll()
        preferences.clearUserScoped()
    }
}
