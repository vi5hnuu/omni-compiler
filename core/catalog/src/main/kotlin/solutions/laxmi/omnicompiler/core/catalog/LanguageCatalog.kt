package solutions.laxmi.omnicompiler.core.catalog

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import solutions.laxmi.omnicompiler.core.common.Dispatcher
import solutions.laxmi.omnicompiler.core.common.OmniDispatcher
import solutions.laxmi.omnicompiler.core.model.Difficulty
import solutions.laxmi.omnicompiler.core.model.Example
import solutions.laxmi.omnicompiler.core.model.Lane
import solutions.laxmi.omnicompiler.core.model.LanguageCategory
import solutions.laxmi.omnicompiler.core.model.LanguageInfo
import solutions.laxmi.omnicompiler.core.model.Limits
import solutions.laxmi.omnicompiler.core.model.Problem
import solutions.laxmi.omnicompiler.core.model.ProblemExample
import solutions.laxmi.omnicompiler.core.model.Runtime
import solutions.laxmi.omnicompiler.core.model.RuntimeStatus
import solutions.laxmi.omnicompiler.core.model.Starter
import solutions.laxmi.omnicompiler.core.model.TestCaseDraft
import javax.inject.Inject
import javax.inject.Singleton

/** Bundled, offline metadata that the judge API does not serve. */
interface LanguageCatalog {
    suspend fun languages(): List<LanguageInfo>

    /** Metadata for a language family; unknown families get a derived fallback, never null. */
    suspend fun language(base: String): LanguageInfo

    /** Minimum limits a runtime needs for its starter to pass (JVM/CLR cold starts need more). */
    suspend fun defaultLimits(runtimeId: String): Limits

    /**
     * Runtimes known when the app was built. Only a first-launch stand-in until `GET /runtimes` succeeds;
     * the live list always replaces it.
     */
    suspend fun seedRuntimes(): List<Runtime>

    suspend fun examples(): List<Example>
    suspend fun problems(): List<Problem>
    suspend fun problem(slug: String): Problem?
}

@Singleton
internal class AssetLanguageCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(OmniDispatcher.IO) private val io: CoroutineDispatcher,
) : LanguageCatalog {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var loaded: Loaded? = null

    private class Loaded(
        val languages: List<LanguageInfo>,
        val byBase: Map<String, LanguageInfo>,
        val defaultLimits: Limits,
        val runtimeLimits: Map<String, Limits>,
        val seedRuntimes: List<Runtime>,
        val examples: List<Example>,
        val problems: List<Problem>,
    )

    override suspend fun languages() = load().languages

    override suspend fun language(base: String) = load().byBase[base] ?: fallback(base)

    override suspend fun defaultLimits(runtimeId: String): Limits {
        val data = load()
        return data.runtimeLimits[runtimeId] ?: data.defaultLimits
    }

    override suspend fun seedRuntimes() = load().seedRuntimes

    override suspend fun examples() = load().examples

    override suspend fun problems() = load().problems

    override suspend fun problem(slug: String) = load().problems.firstOrNull { it.slug == slug }

    private suspend fun load(): Loaded = loaded ?: mutex.withLock {
        loaded ?: withContext(io) { parse() }.also { loaded = it }
    }

    private fun parse(): Loaded {
        val raw = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        val data = json.decodeFromString(CatalogJson.serializer(), raw)
        val languages = data.languages.map { it.toModel() }.sortedBy { it.name.lowercase() }
        return Loaded(
            languages = languages,
            byBase = languages.associateBy { it.base },
            defaultLimits = data.defaultLimits.toModel(),
            runtimeLimits = data.runtimeLimits.mapValues { it.value.toModel() },
            seedRuntimes = data.runtimes.map { it.toModel() },
            examples = data.examples.map { it.toModel() },
            problems = data.problems.map { it.toModel() },
        )
    }

    private fun fallback(base: String) = LanguageInfo(
        base = base,
        name = base.replaceFirstChar { it.uppercase() },
        shortCode = base.take(2).replaceFirstChar { it.uppercase() },
        category = LanguageCategory.SCRIPTING,
        starter = null,
        importHint = "all files live together in /workspace (flat, import by bare name)",
        multiFileSupported = true,
        lineComment = "//",
        blockComment = null,
        tagline = null,
    )

    private companion object {
        const val ASSET = "catalog.json"
    }
}

private fun LimitsJson.toModel() = Limits(timeMs, memMb)
private fun RuntimeJson.toModel() = Runtime(
    id = id,
    language = language,
    version = version,
    status = RuntimeStatus.fromWire(status),
    filename = filename,
    available = true,
    lane = Lane.fromWire(lane),
)
private fun TestJson.toModel() = TestCaseDraft(stdin, expected)
private fun difficulty(value: String) = Difficulty.entries.firstOrNull { it.name == value } ?: Difficulty.EASY

private fun LanguageJson.toModel() = LanguageInfo(
    base = base,
    name = name,
    shortCode = shortCode,
    category = LanguageCategory.entries.firstOrNull { it.name == category } ?: LanguageCategory.SCRIPTING,
    starter = starter?.let { s -> Starter(s.code, s.tests.map { it.toModel() }) },
    importHint = importHint,
    multiFileSupported = multiFileSupported,
    lineComment = lineComment,
    blockComment = blockComment?.takeIf { it.size == 2 }?.let { it[0] to it[1] },
    tagline = tagline,
)

private fun ExampleJson.toModel() = Example(id, title, difficulty(difficulty), statement, tests.map { it.toModel() })

private fun ProblemJson.toModel() = Problem(
    slug = slug,
    title = title,
    difficulty = difficulty(difficulty),
    tags = tags,
    tagline = tagline,
    statement = statement,
    examples = examples.map { ProblemExample(it.input, it.output, it.explanation) },
    constraints = constraints,
    solutions = solutions,
    tests = tests.map { it.toModel() },
)

@Module
@InstallIn(SingletonComponent::class)
internal interface CatalogModule {
    @Binds
    fun bindsLanguageCatalog(impl: AssetLanguageCatalog): LanguageCatalog
}
