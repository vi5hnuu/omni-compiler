package solutions.laxmi.omnicompiler.core.ui

import solutions.laxmi.omnicompiler.core.designsystem.theme.StatusTone
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniColors
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.model.Verdict

/** Visual state of a verdict chip: finished verdicts plus the two in-flight states from the design. */
sealed interface VerdictState {
    data class Done(val verdict: Verdict) : VerdictState
    data object Pending : VerdictState
    data object Running : VerdictState
}

/**
 * The one place a verdict becomes a colour family: AC green, WA/RE/IE red, TLE/MLE amber, CE violet.
 * SK has no family (it's drawn neutral).
 */
fun Verdict.tone(colors: OmniColors): StatusTone? = when (this) {
    Verdict.AC -> colors.status.accepted
    Verdict.WA, Verdict.RE, Verdict.IE -> colors.status.rejected
    Verdict.TLE, Verdict.MLE -> colors.status.limit
    Verdict.CE -> colors.status.compile
    Verdict.SK -> null
}

private data class BadgeColors(val container: Color, val content: Color, val ring: Color?)

private fun OmniColors.badgeColors(state: VerdictState): BadgeColors = when (state) {
    is VerdictState.Done -> state.verdict.tone(this)?.let { BadgeColors(it.fill, it.onFill, null) }
        ?: BadgeColors(Color.Transparent, textTertiary, border)
    VerdictState.Pending -> BadgeColors(Color.Transparent, textTertiary, border)
    VerdictState.Running -> BadgeColors(Color.Transparent, accentText, accent)
}

/** Square mono verdict badge filled with its status colour; SK/pending outlined, running ringed in accent. */
@Composable
fun VerdictBadge(state: VerdictState, modifier: Modifier = Modifier) {
    val colors = OmniTheme.colors.badgeColors(state)
    val label = when (state) {
        is VerdictState.Done -> state.verdict.code
        VerdictState.Pending, VerdictState.Running -> "…"
    }
    val description = stringResource(
        when (state) {
            is VerdictState.Done -> state.verdict.labelRes
            VerdictState.Pending -> R.string.verdict_pending
            VerdictState.Running -> R.string.verdict_running
        },
    )
    Box(
        modifier = modifier
            .background(colors.container)
            .then(if (colors.ring != null) Modifier.border(1.dp, colors.ring) else Modifier)
            .padding(horizontal = 5.dp, vertical = 1.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = OmniTheme.typography.badge, color = colors.content)
    }
}

@Composable
fun VerdictBadge(verdict: Verdict, modifier: Modifier = Modifier) = VerdictBadge(VerdictState.Done(verdict), modifier)

/** Row of 8 dp squares, one per test (design E1 console peek). */
@Composable
fun VerdictStrip(states: List<VerdictState>, modifier: Modifier = Modifier) {
    val colors = OmniTheme.colors
    val passed = states.count { it is VerdictState.Done && it.verdict == Verdict.AC }
    val summary = pluralStringResource(R.plurals.common_tests_passed_of, states.size, passed, states.size)
    Row(modifier.clearAndSetSemantics { contentDescription = summary }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        states.forEach { state ->
            val fill = when (state) {
                is VerdictState.Done -> state.verdict.tone(colors)?.fill ?: colors.surfaceMuted
                VerdictState.Pending -> colors.surfaceMuted
                VerdictState.Running -> colors.accentText
            }
            Box(Modifier.size(8.dp).background(fill))
        }
    }
}
