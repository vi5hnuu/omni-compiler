package solutions.laxmi.omnicompiler.core.data.history

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import solutions.laxmi.omnicompiler.core.data.AppErrorException
import solutions.laxmi.omnicompiler.core.database.dao.SubmissionDao
import solutions.laxmi.omnicompiler.core.database.entity.SubmissionCursorEntity
import solutions.laxmi.omnicompiler.core.database.entity.SubmissionEntity
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.network.source.JudgeNetworkDataSource

/** Pages `GET /me/submissions` (opaque `before` cursor) into the Room cache, newest first. */
@OptIn(ExperimentalPagingApi::class)
internal class SubmissionsRemoteMediator(
    private val network: JudgeNetworkDataSource,
    private val dao: SubmissionDao,
    /** False when the cache was already refreshed during this visit (e.g. only the verdict filter changed). */
    private val refreshOnStart: Boolean,
) : RemoteMediator<Int, SubmissionEntity>() {

    override suspend fun initialize() = if (refreshOnStart) InitializeAction.LAUNCH_INITIAL_REFRESH else InitializeAction.SKIP_INITIAL_REFRESH

    override suspend fun load(loadType: LoadType, state: PagingState<Int, SubmissionEntity>): MediatorResult {
        val before = when (loadType) {
            LoadType.REFRESH -> null
            LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            LoadType.APPEND -> {
                val cursor = dao.cursor() ?: return MediatorResult.Success(endOfPaginationReached = false)
                if (cursor.endReached || cursor.nextCursor == null) return MediatorResult.Success(endOfPaginationReached = true)
                cursor.nextCursor
            }
        }
        return when (val result = network.submissions(before, PAGE_SIZE)) {
            is Outcome.Failure -> MediatorResult.Error(AppErrorException(result.error))
            is Outcome.Success -> {
                val page = result.value
                val start = if (loadType == LoadType.REFRESH) 0L else dao.nextPosition()
                val entities = page.items.mapIndexed { i, s ->
                    SubmissionEntity(s.id, s.runtimeId, s.status.name, s.verdict?.code, s.createdAt.toEpochMilliseconds(), s.totalTimeMs, start + i)
                }
                val cursor = SubmissionCursorEntity(nextCursor = page.nextCursor, endReached = page.nextCursor == null)
                if (loadType == LoadType.REFRESH) dao.replaceFirstPage(entities, cursor) else dao.appendPage(entities, cursor)
                MediatorResult.Success(endOfPaginationReached = page.nextCursor == null)
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 50
    }
}
