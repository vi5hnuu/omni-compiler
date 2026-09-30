package solutions.laxmi.omnicompiler.core.model

/** Judge verdicts as returned by ls-judge. [SK] marks tests skipped after an earlier failure. */
enum class Verdict(val code: String, val label: String) {
    AC("AC", "Accepted"),
    WA("WA", "Wrong Answer"),
    TLE("TLE", "Time Limit Exceeded"),
    MLE("MLE", "Memory Limit Exceeded"),
    RE("RE", "Runtime Error"),
    CE("CE", "Compile Error"),
    IE("IE", "Internal Error"),
    SK("SK", "Skipped");

    val isFailure: Boolean get() = this != AC && this != SK

    companion object {
        fun fromCode(code: String?): Verdict? = code?.let { c -> entries.firstOrNull { it.code == c } }
    }
}

enum class JobStatus {
    PENDING, RUNNING, DONE, FAILED;

    val isTerminal: Boolean get() = this == DONE || this == FAILED

    companion object {
        fun fromWire(value: String?): JobStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PENDING
    }
}
