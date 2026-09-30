package solutions.laxmi.omnicompiler.core.common

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** Compact duration for verdict meta: `41 ms`, `1.21 s`. */
fun formatMillis(ms: Int?): String = when {
    ms == null -> "—"
    ms < 1_000 -> "$ms ms"
    else -> "%.2f s".format(ms / 1_000.0)
}

/** Short relative age used in lists: `2 m`, `1 h`, `Yesterday`, `3 d`. */
fun formatRelative(then: Instant, now: Instant): String {
    val age: Duration = now - then
    return when {
        age < 1.milliseconds * 60_000 -> "now"
        age.inWholeMinutes < 60 -> "${age.inWholeMinutes} m"
        age.inWholeHours < 24 -> "${age.inWholeHours} h"
        age.inWholeDays < 2 -> "Yesterday"
        else -> "${age.inWholeDays} d"
    }
}

fun shortJobId(id: String): String =
    if (id.length <= 10) id else "${id.take(4)}…${id.takeLast(3)}"
