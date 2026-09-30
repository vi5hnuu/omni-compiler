package solutions.laxmi.omnicompiler.core.model

enum class Difficulty { EASY, MEDIUM, HARD }

/** Language-independent stdin → stdout practice problem (web playground "Examples"). */
data class Example(
    val id: String,
    val title: String,
    val difficulty: Difficulty,
    val statement: String,
    val tests: List<TestCaseDraft>,
)

data class ProblemExample(val input: String, val output: String, val explanation: String?)

/** A full problem with statement, constraints and per-language starting solutions. */
data class Problem(
    val slug: String,
    val title: String,
    val difficulty: Difficulty,
    val tags: List<String>,
    val tagline: String,
    val statement: List<String>,
    val examples: List<ProblemExample>,
    val constraints: List<String>,
    val solutions: Map<String, String>,
    val tests: List<TestCaseDraft>,
)
