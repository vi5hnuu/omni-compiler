package solutions.laxmi.omnicompiler.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlin.time.Duration
import kotlin.time.Instant

/** Compact duration for verdict meta: `41 ms`, `1.21 s`, or an em dash when unknown. */
@Composable
@ReadOnlyComposable
fun formatDuration(ms: Int?): String = when {
    ms == null -> stringResource(R.string.common_duration_unknown)
    ms < 1_000 -> stringResource(R.string.common_duration_ms, ms)
    else -> stringResource(R.string.common_duration_seconds, ms / 1_000.0)
}

/** Short relative age used in lists: `now`, `2 m`, `1 h`, `Yesterday`, `3 d`. */
@Composable
@ReadOnlyComposable
fun formatAge(then: Instant, now: Instant): String {
    val age: Duration = now - then
    return when {
        age.inWholeMinutes < 1 -> stringResource(R.string.common_age_now)
        age.inWholeMinutes < 60 -> stringResource(R.string.common_age_minutes, age.inWholeMinutes)
        age.inWholeHours < 24 -> stringResource(R.string.common_age_hours, age.inWholeHours)
        age.inWholeDays < 2 -> stringResource(R.string.common_age_yesterday)
        else -> stringResource(R.string.common_age_days, age.inWholeDays)
    }
}

/** "N tests" style counts used across screens. */
@Composable
@ReadOnlyComposable
fun testCount(count: Int): String = pluralStringResource(R.plurals.common_tests, count, count)
