package solutions.laxmi.omnicompiler.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import solutions.laxmi.omnicompiler.core.database.entity.FileEntity
import solutions.laxmi.omnicompiler.core.database.entity.ProjectEntity
import solutions.laxmi.omnicompiler.core.database.entity.ProjectSummaryRow
import solutions.laxmi.omnicompiler.core.database.entity.ProjectOutlineRow
import solutions.laxmi.omnicompiler.core.database.entity.ProjectWithChildren
import solutions.laxmi.omnicompiler.core.database.entity.TestCaseEntity

@Dao
interface ProjectDao {
    /** File names are joined with '\n' (never valid in a file name) to keep this a single query. */
    @Query(
        """
        SELECT p.*,
               (SELECT GROUP_CONCAT(name, char(10)) FROM (SELECT name FROM files WHERE project_id = p.id ORDER BY is_entry DESC, position)) AS file_names,
               (SELECT COUNT(*) FROM test_cases WHERE project_id = p.id) AS test_count
        FROM projects p
        WHERE (:query = '' OR p.name LIKE '%' || :query || '%' ESCAPE '\')
        ORDER BY p.updated_at DESC
        """,
    )
    fun observeSummaries(query: String): Flow<List<ProjectSummaryRow>>

    @Transaction
    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeOutline(id: String): Flow<ProjectOutlineRow?>

    /** Everything, contents included, in one transaction: for runs, git, export. */
    @Transaction
    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun withChildren(id: String): ProjectWithChildren?

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: String): ProjectEntity?

    @Query("SELECT * FROM projects")
    suspend fun all(): List<ProjectEntity>

    @Query("UPDATE projects SET manifest_modified = :modified WHERE id = :id")
    suspend fun setManifestModified(id: String, modified: Long)

    @Query("UPDATE projects SET folder_doc_id = :folderDocId WHERE id = :id")
    suspend fun setFolder(id: String, folderDocId: String)

    @Query("SELECT * FROM projects ORDER BY updated_at DESC LIMIT 1")
    suspend fun mostRecent(): ProjectEntity?

    /** Projects linked to a document (duplicates share it), so its grant is kept while one still uses it. */
    @Query("SELECT COUNT(*) FROM projects WHERE origin_uri = :uri")
    suspend fun countWithOrigin(uri: String): Int

    @Query("SELECT name FROM projects")
    suspend fun names(): List<String>

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(project: ProjectEntity)

    @Query("UPDATE projects SET updated_at = :at WHERE id = :id")
    suspend fun touch(id: String, at: Long)

    @Query("UPDATE projects SET updated_at = :at WHERE id = (SELECT project_id FROM files WHERE id = :fileId)")
    suspend fun touchForFile(fileId: String, at: Long)

    @Query("UPDATE projects SET last_verdict = :verdict, updated_at = :at WHERE id = :id")
    suspend fun setLastVerdict(id: String, verdict: String?, at: Long)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM projects")
    suspend fun deleteAll()
}

@Dao
interface FileDao {
    @Query("SELECT * FROM files WHERE project_id = :projectId ORDER BY is_entry DESC, position")
    suspend fun list(projectId: String): List<FileEntity>

    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun get(id: String): FileEntity?

    /** Re-queried on every `files` write but reads one integer; the content is fetched only when it changes. */
    @Query("SELECT content_version FROM files WHERE id = :id")
    fun observeContentVersion(id: String): Flow<Int?>

    @Query("SELECT content FROM files WHERE id = :id")
    suspend fun content(id: String): String?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM files WHERE project_id = :projectId")
    suspend fun nextPosition(projectId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(file: FileEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(files: List<FileEntity>)

    /**
     * The editor's own save: the open buffer already has this text, so no reload is signalled. The disk copy is now
     * behind until [markWritten].
     */
    @Query("UPDATE files SET content = :content, disk_dirty = 1 WHERE id = :id")
    suspend fun updateContent(id: String, content: String)

    /**
     * Content replaced from outside the editor (and already written to disk with it); bumps
     * [FileEntity.contentVersion] so an open editor reloads.
     */
    @Query("UPDATE files SET content = :content, content_version = content_version + 1, disk_dirty = 0 WHERE id = :id")
    suspend fun replaceContent(id: String, content: String)

    /**
     * [content] reached the disk. Only clears the flag while the file still holds that text: a save made meanwhile
     * keeps it set and gets written by its own disk write. Returns the number of rows updated.
     */
    @Query("UPDATE files SET doc_id = :docId, last_modified = :lastModified, size = :size, disk_dirty = 0 WHERE id = :id AND content = :content")
    suspend fun markWritten(id: String, content: String, docId: String, lastModified: Long, size: Long): Int

    /**
     * Takes an edit found on disk, unless the text changed since it was compared ([expected]): a save that landed in
     * between wins and is written over the disk copy. Returns the number of rows updated.
     */
    @Query(
        """
        UPDATE files SET content = :content, content_version = content_version + 1, doc_id = :docId,
            last_modified = :lastModified, size = :size, disk_dirty = 0
        WHERE id = :id AND content = :expected
        """,
    )
    suspend fun replaceIfUnchanged(id: String, expected: String, content: String, docId: String, lastModified: Long, size: Long): Int

    /** Name, role, order and disk state from a rescan; leaves the text (and any pending save) alone. */
    @Query("UPDATE files SET name = :name, is_entry = :isEntry, position = :position, doc_id = :docId, last_modified = :lastModified, size = :size WHERE id = :id")
    suspend fun updateLayout(id: String, name: String, isEntry: Boolean, position: Int, docId: String, lastModified: Long, size: Long)

    @Query("UPDATE files SET disk_dirty = 0 WHERE id = :id")
    suspend fun clearDiskDirty(id: String)

    /** Records what the file looks like on disk after the app wrote it (or found it unchanged). */
    @Query("UPDATE files SET doc_id = :docId, last_modified = :lastModified, size = :size WHERE id = :id")
    suspend fun setDiskState(id: String, docId: String, lastModified: Long, size: Long)

    @Upsert
    suspend fun upsertAll(files: List<FileEntity>)

    @Query("DELETE FROM files WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<String>)

    @Query("UPDATE files SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM files WHERE id = :id AND is_entry = 0")
    suspend fun deleteNonEntry(id: String)
}

@Dao
interface TestCaseDao {
    @Query("SELECT * FROM test_cases WHERE project_id = :projectId ORDER BY position")
    suspend fun list(projectId: String): List<TestCaseEntity>

    @Query("SELECT * FROM test_cases WHERE id = :id")
    suspend fun get(id: String): TestCaseEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM test_cases WHERE project_id = :projectId")
    suspend fun nextPosition(projectId: String): Int

    @Upsert
    suspend fun upsert(test: TestCaseEntity)

    @Upsert
    suspend fun upsertAll(tests: List<TestCaseEntity>)

    @Query("DELETE FROM test_cases WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM test_cases WHERE project_id = :projectId")
    suspend fun deleteForProject(projectId: String)

    @Transaction
    suspend fun replaceAll(projectId: String, tests: List<TestCaseEntity>) {
        deleteForProject(projectId)
        upsertAll(tests)
    }
}
