package solutions.laxmi.omnicompiler.core.data.history

import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import solutions.laxmi.omnicompiler.core.common.TimeSource
import solutions.laxmi.omnicompiler.core.database.dao.SubmissionDao
import solutions.laxmi.omnicompiler.core.database.entity.SubmissionEntity
import solutions.laxmi.omnicompiler.core.model.JobStatus
import solutions.laxmi.omnicompiler.core.model.median
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.Submission
import solutions.laxmi.omnicompiler.core.model.UsageStats
import solutions.laxmi.omnicompiler.core.model.Verdict
import solutions.laxmi.omnicompiler.core.network.source.JudgeNetworkDataSource
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Runs started on one local calendar day. */
data class DayActivity(val dayStartEpochMs: Long, val runs: Int, val accepted: Int)

/** Summary derived from submissions cached on this device (the API has no aggregate endpoint). */
data class CachedHistorySummary(
    val verdictCounts: Map<Verdict, Int>,
    val total: Int,
    val medianTimeMs: Int?,
    /** Every page has been cached; until then the counts cover only the runs loaded so far. */
    val complete: Boolean = false,
)

/** Server-side run history (`/me/submissions`), cached for offline viewing and local filtering. */
interface HistoryRepository {
    /** [refreshOnStart] false reuses the cache fetched earlier in the same visit instead of refetching from the top. */
    fun submissions(verdict: Verdict?, refreshOnStart: Boolean = true): Flow<PagingData<Submission>>
    val summary: Flow<CachedHistorySummary>

    /** Last 7 local days, oldest first, from cached submissions. The window is computed each time collection starts. */
    fun observeLastWeek(): Flow<List<DayActivity>>

    suspend fun stats(): Outcome<UsageStats>
}

@Singleton
internal class DefaultHistoryRepository @Inject constructor(
    private val network: JudgeNetworkDataSource,
    private val dao: SubmissionDao,
    private val time: TimeSource,
) : HistoryRepository {

    @OptIn(ExperimentalPagingApi::class)
    override fun submissions(verdict: Verdict?, refreshOnStart: Boolean): Flow<PagingData<Submission>> = Pager(
        config = PagingConfig(pageSize = SubmissionsRemoteMediator.PAGE_SIZE, enablePlaceholders = false),
        remoteMediator = SubmissionsRemoteMediator(network, dao, refreshOnStart),
        pagingSourceFactory = { dao.pagingSource(verdict?.code) },
    ).flow.map { data -> data.map { it.toModel() } }

    override val summary: Flow<CachedHistorySummary> = combine(dao.observeSince(0), dao.observeCursor()) { rows, cursor ->
        val times = rows.mapNotNull { it.totalTimeMs }.sorted()
        CachedHistorySummary(
            verdictCounts = rows.mapNotNull { Verdict.fromCode(it.verdict) }.groupingBy { it }.eachCount(),
            total = rows.size,
            medianTimeMs = times.median(),
            complete = cursor?.endReached == true,
        )
    }

    override fun observeLastWeek(): Flow<List<DayActivity>> = flow {
        val now = time.now().toEpochMilliseconds()
        val days = (6 downTo 0).map { startOfDay(now, daysAgo = it) }
        emitAll(dao.observeSince(days.first()).map { rows -> weekOf(days, rows) })
    }

    private fun weekOf(days: List<Long>, rows: List<SubmissionEntity>): List<DayActivity> =
        days.map { start ->
            val end = start + 1.days.inWholeMilliseconds
            val inDay = rows.filter { it.createdAt in start until end }
            DayActivity(start, inDay.size, inDay.count { it.verdict == Verdict.AC.code })
        }

    override suspend fun stats() = network.stats()

    private fun startOfDay(nowMs: Long, daysAgo: Int): Long = Calendar.getInstance().apply {
        timeInMillis = nowMs
        add(Calendar.DAY_OF_YEAR, -daysAgo)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun SubmissionEntity.toModel() = Submission(
        id = id,
        runtimeId = runtimeId,
        status = JobStatus.fromWire(status),
        verdict = Verdict.fromCode(verdict),
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        totalTimeMs = totalTimeMs,
    )
}
