package solutions.laxmi.omnicompiler.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import solutions.laxmi.omnicompiler.core.database.entity.PendingRunEntity
import solutions.laxmi.omnicompiler.core.database.entity.RunEntity
import solutions.laxmi.omnicompiler.core.database.entity.RunResultEntity
import solutions.laxmi.omnicompiler.core.database.entity.RuntimeEntity
import solutions.laxmi.omnicompiler.core.database.entity.SubmissionCursorEntity
import solutions.laxmi.omnicompiler.core.database.entity.SubmissionEntity
import solutions.laxmi.omnicompiler.core.database.entity.VerdictCount

@Dao
interface RunDao {
    @Query("SELECT * FROM runs WHERE project_id = :projectId ORDER BY started_at DESC LIMIT :limit")
    fun observeRecent(projectId: String, limit: Int): Flow<List<RunEntity>>

    @Query("SELECT * FROM run_results WHERE run_id IN (:runIds) ORDER BY test_index")
    fun observeResults(runIds: List<String>): Flow<List<RunResultEntity>>

    @Transaction
    suspend fun save(run: RunEntity, results: List<RunResultEntity>, keepPerProject: Int) {
        upsertRun(run)
        deleteResults(run.id)
        insertResults(results)
        trim(run.projectId, keepPerProject)
    }

    @Upsert
    suspend fun upsertRun(run: RunEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResults(results: List<RunResultEntity>)

    @Query("DELETE FROM run_results WHERE run_id = :runId")
    suspend fun deleteResults(runId: String)

    /** Keeps the console bounded: only the newest [keep] runs per project survive. */
    @Query(
        """
        DELETE FROM runs WHERE project_id = :projectId AND id NOT IN (
            SELECT id FROM runs WHERE project_id = :projectId ORDER BY started_at DESC LIMIT :keep
        )
        """,
    )
    suspend fun trim(projectId: String, keep: Int)

    @Query("DELETE FROM runs WHERE project_id = :projectId")
    suspend fun clear(projectId: String)
}

@Dao
interface RuntimeDao {
    @Query("SELECT * FROM runtimes ORDER BY language, version DESC")
    fun observeAll(): Flow<List<RuntimeEntity>>

    @Query("SELECT * FROM runtimes WHERE id = :id")
    suspend fun get(id: String): RuntimeEntity?

    @Transaction
    suspend fun replaceAll(runtimes: List<RuntimeEntity>) {
        deleteAll()
        upsertAll(runtimes)
    }

    @Upsert
    suspend fun upsertAll(runtimes: List<RuntimeEntity>)

    @Query("DELETE FROM runtimes")
    suspend fun deleteAll()
}

@Dao
interface SubmissionDao {
    @Query("SELECT * FROM submissions WHERE (:verdict IS NULL OR verdict = :verdict) ORDER BY position")
    fun pagingSource(verdict: String?): PagingSource<Int, SubmissionEntity>

    @Query("SELECT verdict, COUNT(*) AS count FROM submissions GROUP BY verdict")
    fun observeVerdictCounts(): Flow<List<VerdictCount>>

    @Query("SELECT * FROM submissions WHERE created_at >= :since ORDER BY created_at")
    fun observeSince(since: Long): Flow<List<SubmissionEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM submissions")
    suspend fun nextPosition(): Long

    @Query("SELECT * FROM submission_cursor WHERE `key` = 0")
    suspend fun cursor(): SubmissionCursorEntity?

    @Upsert
    suspend fun upsertAll(items: List<SubmissionEntity>)

    @Upsert
    suspend fun setCursor(cursor: SubmissionCursorEntity)

    @Transaction
    suspend fun replaceFirstPage(items: List<SubmissionEntity>, cursor: SubmissionCursorEntity) {
        clearAll()
        upsertAll(items)
        setCursor(cursor)
    }

    @Transaction
    suspend fun appendPage(items: List<SubmissionEntity>, cursor: SubmissionCursorEntity) {
        upsertAll(items)
        setCursor(cursor)
    }

    @Query("DELETE FROM submissions")
    suspend fun clearItems()

    @Query("DELETE FROM submission_cursor")
    suspend fun clearCursor()

    @Transaction
    suspend fun clearAll() {
        clearItems()
        clearCursor()
    }
}

@Dao
interface PendingRunDao {
    @Query("SELECT * FROM pending_runs ORDER BY created_at")
    fun observeAll(): Flow<List<PendingRunEntity>>

    @Query("SELECT * FROM pending_runs ORDER BY created_at")
    suspend fun all(): List<PendingRunEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(run: PendingRunEntity)

    @Query("DELETE FROM pending_runs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM pending_runs")
    suspend fun deleteAll()
}
