package solutions.laxmi.omnicompiler.core.model

/** A concrete toolchain (e.g. `cpp-23`, `python-3.13`) served by `GET /runtimes`. */
data class Runtime(
    val id: String,
    val language: String,
    val version: String,
    val status: RuntimeStatus,
    val filename: String,
    val available: Boolean,
    val lane: Lane,
) {
    val isRunnable: Boolean get() = available && (status == RuntimeStatus.READY || status == RuntimeStatus.DEPRECATED)
    val extension: String get() = filename.substringAfterLast('.', missingDelimiterValue = "")
}

enum class RuntimeStatus {
    BUILDING, READY, DEPRECATED, FAILED, UNKNOWN;

    companion object {
        fun fromWire(value: String?): RuntimeStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

/** Hot lanes keep warm VMs (fast start); cold lanes boot on demand. */
enum class Lane {
    HOT, COLD;

    companion object {
        fun fromWire(value: String?): Lane = if (value.equals("hot", ignoreCase = true)) HOT else COLD
    }
}

/** Picker filter groups; display names live in `core/ui`. */
enum class LanguageCategory { COMPILED, SCRIPTING, JVM, FUNCTIONAL, ESOTERIC }

/** Resource limits for a run. Values are clamped by the server; the app mirrors its bounds. */
data class Limits(val timeMs: Int, val memMb: Int) {
    companion object {
        const val DEFAULT_TIME_MS = 5_000
        const val DEFAULT_MEM_MB = 256
        const val MAX_TIME_MS = 30_000
        const val MAX_MEM_MB = 1_024
        val Default = Limits(DEFAULT_TIME_MS, DEFAULT_MEM_MB)
    }

    /** Never lower user-chosen limits when a runtime needs more (JVM/CLR cold starts). */
    fun raisedTo(minimum: Limits) = Limits(maxOf(timeMs, minimum.timeMs), maxOf(memMb, minimum.memMb))

    fun clamped() = Limits(timeMs.coerceIn(1, MAX_TIME_MS), memMb.coerceIn(1, MAX_MEM_MB))
}
