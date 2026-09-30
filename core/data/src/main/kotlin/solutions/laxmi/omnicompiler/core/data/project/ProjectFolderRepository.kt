package solutions.laxmi.omnicompiler.core.data.project

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import solutions.laxmi.omnicompiler.core.common.ApplicationScope
import solutions.laxmi.omnicompiler.core.datastore.PreferencesStore
import solutions.laxmi.omnicompiler.core.model.AppError
import solutions.laxmi.omnicompiler.core.model.ErrorReason
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.storage.ProjectFolderStore
import solutions.laxmi.omnicompiler.core.storage.ProjectRoot
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class FolderStatus {
    /** Not checked yet (app start). */
    Checking,

    /** No folder chosen: the user must pick one before projects can be opened. */
    NotChosen,
    Ready,

    /** A folder was chosen but can't be reached any more (grant revoked, folder deleted). */
    Lost,
}

/** Where projects live on the device, and whether the app can currently reach that folder. */
interface ProjectFolderRepository {
    val status: StateFlow<FolderStatus>

    /** Human-readable location of the chosen folder, e.g. `Documents/OmniCompiler`. */
    val displayPath: Flow<String?>

    /** The folder to read and write, or null when it isn't [FolderStatus.Ready]. */
    suspend fun root(): ProjectRoot?

    /** Adopts the user's pick from the system folder picker, then loads (and, first time, exports) projects. */
    suspend fun choose(treeUri: String): Outcome<Unit>

    /** Re-checks access, e.g. after a storage error or when the app returns to the foreground. */
    suspend fun recheck()
}

@Singleton
internal class DefaultProjectFolderRepository @Inject constructor(
    private val preferences: PreferencesStore,
    private val store: ProjectFolderStore,
    private val sync: ProjectSync,
    @ApplicationScope scope: CoroutineScope,
) : ProjectFolderRepository {

    private val state = MutableStateFlow(FolderStatus.Checking)
    override val status: StateFlow<FolderStatus> = state.asStateFlow()
    private val checkLock = Mutex()

    override val displayPath: Flow<String?> = preferences.projectsRoot.map { stored ->
        stored?.let { (tree, doc) -> store.displayPath(ProjectRoot(tree, doc)) }
    }

    init {
        scope.launch { recheck() }
    }

    override suspend fun root(): ProjectRoot? =
        if (state.value == FolderStatus.Ready) storedRoot() else null

    override suspend fun choose(treeUri: String): Outcome<Unit> = try {
        val root = store.adopt(treeUri)
        preferences.setProjectsRoot(root.treeUri, root.docId)
        state.value = FolderStatus.Ready
        sync.syncAll(root)
    } catch (e: IOException) {
        Outcome.Failure(AppError.Unknown(reason = ErrorReason.ProjectsFolderUnavailable))
    }

    override suspend fun recheck() = checkLock.withLock {
        val root = storedRoot()
        state.value = when {
            root == null -> FolderStatus.NotChosen
            store.isAccessible(root) -> FolderStatus.Ready
            else -> FolderStatus.Lost
        }
    }

    private suspend fun storedRoot(): ProjectRoot? = preferences.projectsRoot.first()?.let { (tree, doc) -> ProjectRoot(tree, doc) }
}
