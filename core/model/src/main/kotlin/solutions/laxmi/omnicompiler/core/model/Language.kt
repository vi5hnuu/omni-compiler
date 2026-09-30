package solutions.laxmi.omnicompiler.core.model

/** Static, bundled metadata for a language family (display name, starter, multi-file rules). */
data class LanguageInfo(
    val base: String,
    val name: String,
    val shortCode: String,
    val category: LanguageCategory,
    val starter: Starter?,
    val importHint: String,
    val multiFileSupported: Boolean,
    val lineComment: String?,
    val blockComment: Pair<String, String>?,
    val tagline: String?,
) {
    /** Placeholder source used when the family ships no reference sample. */
    fun placeholderCode(): String {
        val message = "$name — read input from stdin, print your answer"
        return when {
            starter != null -> starter.code
            blockComment != null -> "${blockComment.first} $message ${blockComment.second}\n"
            lineComment != null -> "$lineComment $message\n"
            else -> ""
        }
    }
}

data class Starter(val code: String, val tests: List<TestCaseDraft>)

/** A language family joined with its live runtimes. */
data class Language(
    val info: LanguageInfo,
    val runtimes: List<Runtime>,
    val acceptanceRate: Double?,
) {
    val base: String get() = info.base
    val defaultRuntime: Runtime? get() = runtimes.firstOrNull { it.status == RuntimeStatus.READY && it.available } ?: runtimes.firstOrNull()
}
