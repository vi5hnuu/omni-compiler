package solutions.laxmi.omnicompiler.core.data.auth

import kotlinx.coroutines.flow.first
import solutions.laxmi.omnicompiler.core.data.project.ProjectRepository
import solutions.laxmi.omnicompiler.core.database.dao.RunDao
import solutions.laxmi.omnicompiler.core.datastore.PreferencesStore
import solutions.laxmi.omnicompiler.core.model.Session
import javax.inject.Inject

/** What a guest would keep by creating an account (design X3). */
data class KeepWorkOffer(val projectName: String, val files: Int, val tests: Int, val runs: Int)

/** Decides when a guest is invited to turn their guest account into a real one. */
interface GuestPromptRepository {
    /**
     * Returns an offer for [projectId] when the current user is a guest who hasn't been asked about it,
     * and records that they have been, so each project prompts at most once.
     */
    suspend fun claimOffer(projectId: String): KeepWorkOffer?
}

internal class DefaultGuestPromptRepository @Inject constructor(
    private val auth: AuthRepository,
    private val projects: ProjectRepository,
    private val runs: RunDao,
    private val preferences: PreferencesStore,
) : GuestPromptRepository {

    override suspend fun claimOffer(projectId: String): KeepWorkOffer? {
        val user = (auth.session.value as? Session.Active)?.user ?: return null
        if (!user.isGuest || projectId in preferences.guestPromptedProjects()) return null
        val workspace = projects.observeWorkspace(projectId).first() ?: return null
        preferences.markGuestPrompted(projectId)
        return KeepWorkOffer(workspace.project.name, workspace.files.size, workspace.tests.size, runs.countForProject(projectId))
    }
}
