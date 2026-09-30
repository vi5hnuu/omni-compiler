package solutions.laxmi.omnicompiler.feature.workspace.console

import solutions.laxmi.omnicompiler.feature.workspace.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniSpinner
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.model.RunPhase
import solutions.laxmi.omnicompiler.core.model.RunRecord
import solutions.laxmi.omnicompiler.core.ui.VerdictBadge
import solutions.laxmi.omnicompiler.core.ui.VerdictState
import solutions.laxmi.omnicompiler.core.ui.VerdictStrip

/** Height of the summary row; with the sheet's drag handle it is the console's collapsed (peek) height. */
internal val ConsolePeekHeight = 38.dp

/**
 * Summary row at the top of the console sheet (design E1): latest verdict, meta and per-test strip.
 * It is all that shows while the sheet is collapsed; tapping it toggles the sheet.
 */
@Composable
internal fun ConsolePeek(latest: RunRecord?, expanded: Boolean, onToggle: () -> Unit) {
    val colors = OmniTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(if (expanded) R.string.console_close else R.string.console_open),
                onClick = onToggle,
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().height(ConsolePeekHeight).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (latest == null) {
                Text(stringResource(R.string.console_title), style = OmniTheme.typography.bodyStrong, color = colors.textPrimary)
                Text(stringResource(R.string.console_empty_peek), style = OmniTheme.typography.mono, color = colors.textTertiary)
                return@Row
            }
            when {
                latest.phase.isActive -> {
                    OmniSpinner(size = 12.dp)
                    VerdictBadge(if (latest.phase == RunPhase.RUNNING) VerdictState.Running else VerdictState.Pending)
                }
                latest.displayVerdict() != null -> VerdictBadge(latest.displayVerdict()!!)
            }
            Text(latest.headline(), style = OmniTheme.typography.bodyStrong, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text(latest.meta(), style = OmniTheme.typography.monoSmall, color = colors.textTertiary, maxLines = 1)
            Box(Modifier.weight(1f))
            VerdictStrip(latest.stripStates().take(MAX_STRIP))
        }
    }
}

private const val MAX_STRIP = 16
