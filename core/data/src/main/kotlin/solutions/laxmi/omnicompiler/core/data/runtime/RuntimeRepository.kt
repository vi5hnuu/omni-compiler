package solutions.laxmi.omnicompiler.core.data.runtime

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import solutions.laxmi.omnicompiler.core.common.ApplicationScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import solutions.laxmi.omnicompiler.core.catalog.LanguageCatalog
import solutions.laxmi.omnicompiler.core.data.mapper.toEntity
import solutions.laxmi.omnicompiler.core.data.mapper.toModel
import solutions.laxmi.omnicompiler.core.database.dao.RuntimeDao
import solutions.laxmi.omnicompiler.core.datastore.PreferencesStore
import solutions.laxmi.omnicompiler.core.model.Language
import solutions.laxmi.omnicompiler.core.model.LanguageInfo
import solutions.laxmi.omnicompiler.core.model.LanguageStat
import solutions.laxmi.omnicompiler.core.model.Limits
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.Runtime
import solutions.laxmi.omnicompiler.core.model.RuntimeStatus
import solutions.laxmi.omnicompiler.core.model.map
import solutions.laxmi.omnicompiler.core.network.source.JudgeNetworkDataSource
import javax.inject.Inject
import javax.inject.Singleton

/** Live runtimes (cached for offline use) joined with bundled language metadata. */
interface RuntimeRepository {
    val languages: Flow<List<Language>>
    val recentRuntimeIds: Flow<List<String>>

    fun observeRuntime(id: String): Flow<Runtime?>
    suspend fun refresh(): Outcome<Unit>
    suspend fun runtime(id: String): Runtime?
    suspend fun languageInfo(base: String): LanguageInfo
    suspend fun allLanguageInfo(): List<LanguageInfo>

    /** Limits that let the runtime's own sample pass (never lower than the global defaults). */
    suspend fun defaultLimits(runtimeId: String): Limits

    /** The runtime new projects start with: user default, else a ready Python/C++, else any ready runtime. */
    suspend fun defaultRuntime(): Runtime?

    suspend fun markUsed(runtimeId: String)
}

@Singleton
internal class DefaultRuntimeRepository @Inject constructor(
    private val network: JudgeNetworkDataSource,
    private val dao: RuntimeDao,
    private val catalog: LanguageCatalog,
    private val preferences: PreferencesStore,
    @ApplicationScope private val appScope: CoroutineScope,
) : RuntimeRepository {

    private val stats = MutableStateFlow<Map<String, LanguageStat>>(emptyMap())

    /** Guards [inFlight] so concurrent callers can't both see "no request" and start two. */
    private val inFlightLock = Any()
    private var inFlight: Deferred<Outcome<Unit>>? = null

    private val seedLock = Mutex()
    @Volatile private var seeded = false

    private val runtimes: Flow<List<Runtime>> = dao.observeAll()
        .onStart { ensureSeeded() }
        .map { rows -> rows.map { it.toModel() } }

    override val languages: Flow<List<Language>> = combine(
        runtimes,
        stats,
        flow { emit(catalog.languages().associateBy { it.base }) },
    ) { all, statsByLanguage, infoByBase ->
        all.groupBy { it.language }.map { (base, list) ->
            val info = infoByBase[base] ?: catalog.language(base)
            Language(
                info = info,
                runtimes = list.sortedWith(runtimeOrder(info.versionOrder)),
                acceptanceRate = statsByLanguage[base]?.acceptanceRate,
            )
        }.sortedBy { it.info.name.lowercase() }
    }

    override val recentRuntimeIds: Flow<List<String>> = preferences.recentRuntimeIds

    override fun observeRuntime(id: String): Flow<Runtime?> = runtimes.map { list -> list.firstOrNull { it.id == id } }

    /** Screens and startup may ask at the same moment; they all share one in-flight request. */
    override suspend fun refresh(): Outcome<Unit> {
        val request = synchronized(inFlightLock) {
            inFlight?.takeIf { it.isActive } ?: appScope.async { fetch() }.also { inFlight = it }
        }
        return request.await()
    }

    private suspend fun fetch(): Outcome<Unit> {
        val result = network.runtimes()
        if (result is Outcome.Failure) return result
        dao.replaceAll((result as Outcome.Success).value.map { it.toEntity() })
        // Acceptance rates are decorative: fetched in the background, never failing the refresh.
        appScope.launch { network.languageStats().map { list -> stats.value = list.associateBy { it.language } } }
        return Outcome.Success(Unit)
    }

    override suspend fun runtime(id: String): Runtime? {
        ensureSeeded()
        return dao.get(id)?.toModel()
    }

    /** A fresh install offline still gets pickers and new projects from the bundled snapshot. */
    private suspend fun ensureSeeded() {
        if (seeded) return
        seedLock.withLock {
            if (!seeded) {
                dao.seedIfEmpty(catalog.seedRuntimes().map { it.toEntity() })
                seeded = true
            }
        }
    }

    override suspend fun languageInfo(base: String): LanguageInfo = catalog.language(base)

    override suspend fun allLanguageInfo(): List<LanguageInfo> = catalog.languages()

    override suspend fun defaultLimits(runtimeId: String): Limits = catalog.defaultLimits(runtimeId)

    override suspend fun defaultRuntime(): Runtime? {
        var all = runtimes.first()
        if (all.isEmpty()) {
            refresh()
            all = runtimes.first()
        }
        val ready = all.filter { it.isRunnable && it.status == RuntimeStatus.READY }
        val preferred = preferences.runSettings.first().defaultRuntimeId
        return ready.firstOrNull { it.id == preferred }
            ?: PREFERRED_LANGUAGES.firstNotNullOfOrNull { base ->
                ready.filter { it.language == base }.sortedWith(runtimeOrder(catalog.language(base).versionOrder)).firstOrNull()
            }
            ?: ready.firstOrNull()
    }

    override suspend fun markUsed(runtimeId: String) = preferences.markRuntimeUsed(runtimeId)

    private companion object {
        val PREFERRED_LANGUAGES = listOf("python", "cpp", "javascript", "java")
    }
}

/**
 * Ready before deprecated/others, then the catalog's newest-first order, then (for versions the catalog doesn't
 * list) newest first numerically.
 */
internal fun runtimeOrder(versionOrder: List<String>): Comparator<Runtime> {
    val rank = versionOrder.withIndex().associate { (i, v) -> v to i }
    return compareBy<Runtime> { statusRank(it.status) }
        .thenBy { rank[it.version] ?: Int.MAX_VALUE }
        .then { a, b -> compareVersions(b.version, a.version) }
}

private fun statusRank(status: RuntimeStatus) = when (status) {
    RuntimeStatus.READY -> 0
    RuntimeStatus.DEPRECATED -> 1
    RuntimeStatus.BUILDING -> 2
    else -> 3
}

internal fun compareVersions(a: String, b: String): Int {
    val left = a.split('.', '-')
    val right = b.split('.', '-')
    for (i in 0 until maxOf(left.size, right.size)) {
        val l = left.getOrNull(i)
        val r = right.getOrNull(i)
        if (l == null) return -1
        if (r == null) return 1
        val ln = l.toIntOrNull()
        val rn = r.toIntOrNull()
        val cmp = if (ln != null && rn != null) ln.compareTo(rn) else l.compareTo(r)
        if (cmp != 0) return cmp
    }
    return 0
}
