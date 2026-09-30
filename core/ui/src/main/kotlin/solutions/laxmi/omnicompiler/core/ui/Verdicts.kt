package solutions.laxmi.omnicompiler.core.ui

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

private data class BadgeColors(val container: Color, val content: Color, val ring: Color?)

private fun OmniColors.badgeColors(state: VerdictState): BadgeColors = when (state) {
    is VerdictState.Done -> when (state.verdict) {
        Verdict.AC -> BadgeColors(textPrimary, background, null)
        Verdict.SK -> BadgeColors(Color.Transparent, textTertiary, border)
        else -> BadgeColors(accent, onAccent, null)
    }
    VerdictState.Pending -> BadgeColors(Color.Transparent, textTertiary, border)
    VerdictState.Running -> BadgeColors(Color.Transparent, accentText, accent)
}

/** Square mono verdict badge: AC inverted white, failures red, SK/pending outlined. */
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
                is VerdictState.Done -> when (state.verdict) {
                    Verdict.AC -> colors.textPrimary
                    Verdict.SK -> colors.surfaceMuted
                    else -> colors.accent
                }
                VerdictState.Pending -> colors.surfaceMuted
                VerdictState.Running -> colors.accentText
            }
            Box(Modifier.size(8.dp).background(fill))
        }
    }
}
