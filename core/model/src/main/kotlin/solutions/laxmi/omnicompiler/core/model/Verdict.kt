package solutions.laxmi.omnicompiler.core.model

/**
 * Judge verdicts as returned by ls-judge. [SK] marks tests skipped after an earlier failure.
 * [code] is the judge's own short code (shown as-is); display names live in `core/ui`.
 */
enum class Verdict(val code: String) {
    AC("AC"),
    WA("WA"),
    TLE("TLE"),
    MLE("MLE"),
    RE("RE"),
    CE("CE"),
    IE("IE"),
    SK("SK");

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
