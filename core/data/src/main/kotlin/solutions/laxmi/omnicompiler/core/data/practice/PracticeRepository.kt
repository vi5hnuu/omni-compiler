package solutions.laxmi.omnicompiler.core.data.practice

import solutions.laxmi.omnicompiler.core.catalog.LanguageCatalog
import solutions.laxmi.omnicompiler.core.model.Example
import solutions.laxmi.omnicompiler.core.model.Problem
import javax.inject.Inject

/** Practice content bundled from the ls-judge web playground (Examples gallery and Problems). */
interface PracticeRepository {
    suspend fun examples(): List<Example>
    suspend fun problems(): List<Problem>
    suspend fun problem(slug: String): Problem?
}

internal class CatalogPracticeRepository @Inject constructor(private val catalog: LanguageCatalog) : PracticeRepository {
    override suspend fun examples() = catalog.examples()
    override suspend fun problems() = catalog.problems()
    override suspend fun problem(slug: String) = catalog.problem(slug)
}
