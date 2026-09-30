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
        WHERE (:query = '' OR p.name LIKE '%' || :query || '%')
        ORDER BY p.updated_at DESC
        """,
    )
    fun observeSummaries(query: String): Flow<List<ProjectSummaryRow>>

    @Transaction
    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeWithChildren(id: String): Flow<ProjectWithChildren?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: String): ProjectEntity?

    @Query("SELECT * FROM projects ORDER BY updated_at DESC LIMIT 1")
    suspend fun mostRecent(): ProjectEntity?

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

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM files WHERE project_id = :projectId")
    suspend fun nextPosition(projectId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(file: FileEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(files: List<FileEntity>)

    /** The editor's own save: the open buffer already has this text, so no reload is signalled. */
    @Query("UPDATE files SET content = :content WHERE id = :id")
    suspend fun updateContent(id: String, content: String)

    /** Content replaced from outside the editor; bumps [FileEntity.contentVersion] so an open editor reloads. */
    @Query("UPDATE files SET content = :content, content_version = content_version + 1 WHERE id = :id")
    suspend fun replaceContent(id: String, content: String)

    @Query("UPDATE files SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM files WHERE id = :id AND is_entry = 0")
    suspend fun deleteNonEntry(id: String)
}

@Dao
interface TestCaseDao {
    @Query("SELECT * FROM test_cases WHERE project_id = :projectId ORDER BY position")
    suspend fun list(projectId: String): List<TestCaseEntity>

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
