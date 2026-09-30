package solutions.laxmi.omnicompiler.feature.workspace.console

import solutions.laxmi.omnicompiler.feature.workspace.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
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

/** Collapsed console under the editor (design E1): latest verdict, meta and per-test strip. Tap or drag up to open. */
@Composable
internal fun ConsolePeek(latest: RunRecord?, onOpen: () -> Unit) {
    val colors = OmniTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .drawBehind { drawLine(colors.border, Offset(0f, 0f), Offset(size.width, 0f), 1f) }
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.console_open), onClick = onOpen)
            .pointerInput(Unit) { detectVerticalDragGestures { _, drag -> if (drag < -8f) onOpen() } },
    ) {
        Box(Modifier.fillMaxWidth().height(10.dp), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.padding(bottom = 1.dp).height(3.dp).fillMaxWidth(0.08f).background(colors.dragHandle))
        }
        Row(
            Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 12.dp),
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
