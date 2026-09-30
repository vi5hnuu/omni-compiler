package solutions.laxmi.omnicompiler.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme

/** Square switch: red track when on, muted when off. */
@Composable
fun OmniToggle(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = OmniTheme.colors
    val track by animateColorAsState(if (checked) colors.accent else colors.surfaceMuted, label = "track")
    val knobOffset by animateDpAsState(if (checked) 16.dp else 0.dp, label = "knob")
    Box(
        modifier = modifier
            .size(width = 36.dp, height = 20.dp)
            .background(track)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                } else Modifier,
            )
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset(x = knobOffset)
                .size(16.dp)
                .background(if (checked) colors.onAccent else colors.textSecondary),
        )
    }
}

/** Square checkbox with a white fill and black check when selected. */
@Composable
fun OmniCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val colors = OmniTheme.colors
    Row(
        modifier = modifier.toggleable(checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(18.dp)
                .background(if (checked) colors.textPrimary else Color.Transparent)
                .border(1.5.dp, if (checked) colors.textPrimary else colors.borderStrong),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(OmniIcons.Check, null, tint = colors.background, modifier = Modifier.size(13.dp))
        }
        label()
    }
}

/** Square radio used in runtime and option pickers. */
@Composable
fun OmniRadio(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = OmniTheme.colors
    Box(
        modifier = modifier
            .size(18.dp)
            .border(1.5.dp, if (selected) colors.accent else colors.borderStrong),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(8.dp).background(colors.accent))
    }
}

/** Filter chip: selected chips invert to white-on-black like the design. */
@Composable
fun OmniChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: String? = null,
) {
    val colors = OmniTheme.colors
    Row(
        modifier = modifier
            .height(30.dp)
            .background(if (selected) colors.textPrimary else colors.surface)
            .border(1.dp, if (selected) colors.textPrimary else colors.border)
            .selectable(selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, style = OmniTheme.typography.label, color = if (selected) colors.background else colors.textSecondary)
        if (count != null) {
            Text(count, style = OmniTheme.typography.monoSmall, color = if (selected) colors.textDisabled else colors.textTertiary)
        }
    }
}

/** Two-to-four option segmented control (Monthly/Yearly, Tight/1.5/Loose…). */
@Composable
fun <T> OmniSegmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val colors = OmniTheme.colors
    Row(modifier.border(1.dp, colors.border)) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .background(if (isSelected) colors.textPrimary else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label(option), style = OmniTheme.typography.label, color = if (isSelected) colors.background else colors.textSecondary)
            }
        }
    }
}
