package solutions.laxmi.omnicompiler.core.ads

import android.text.format.DateFormat
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniSpinner
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextButton
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import java.util.Date

private val passHours: Int get() = (BuildConfig.PASS_DURATION_MS / 3_600_000L).toInt()

/** The pass's state for its entry rows ("On until 3:45 PM", or the offer); null while the pass can't be offered. */
@Composable
fun adFreePassSummary(state: PassState): String? = when (state) {
    PassState.Unavailable -> null
    is PassState.Active -> stringResource(R.string.ads_pass_summary_active, formatTime(state.until))
    else -> pluralStringResource(R.plurals.ads_pass_summary, passHours, passHours)
}

/**
 * Offers the ad-free pass and plays its video when the user asks; the only place the rewarded video is shown, so
 * it never appears unprompted.
 */
@Composable
fun AdFreePassDialog(onDismiss: () -> Unit) {
    val pass = LocalAds.current.pass
    val state by pass.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val colors = OmniTheme.colors
    var outcome by rememberSaveable { mutableStateOf<PassResult?>(null) }

    val message = when (outcome) {
        PassResult.Offline -> R.string.ads_pass_offline
        PassResult.NotEarned -> R.string.ads_pass_not_earned
        PassResult.NotReady -> R.string.ads_pass_not_ready
        PassResult.Granted, null -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = colors.surfaceRaised,
        title = { Text(stringResource(R.string.ads_pass_title), style = OmniTheme.typography.title, color = colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val current = state
                val lead = if (current is PassState.Active) {
                    stringResource(R.string.ads_pass_active, formatTime(current.until))
                } else {
                    pluralStringResource(R.plurals.ads_pass_offer, passHours, passHours)
                }
                Text(lead, style = OmniTheme.typography.body, color = colors.textPrimary)
                Text(stringResource(R.string.ads_pass_scope), style = OmniTheme.typography.bodySmall, color = colors.textTertiary)
                when (current) {
                    // Unavailable is also the state's value before its first update; it reads as still loading.
                    PassState.Loading, PassState.Unavailable -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OmniSpinner()
                        Text(stringResource(R.string.ads_pass_loading), style = OmniTheme.typography.bodySmall, color = colors.textSecondary)
                    }
                    PassState.NoVideo -> Text(stringResource(R.string.ads_pass_no_video), style = OmniTheme.typography.bodySmall, color = colors.textSecondary)
                    else -> Unit
                }
                if (message != null && current !is PassState.Active) {
                    Text(stringResource(message), style = OmniTheme.typography.bodySmall, color = colors.accentText)
                }
            }
        },
        confirmButton = {
            when (state) {
                is PassState.Active -> OmniTextButton(stringResource(R.string.ads_pass_close), onDismiss)
                PassState.NoVideo -> OmniTextButton(stringResource(R.string.ads_pass_retry), {
                    outcome = null
                    pass.retry()
                })
                else -> OmniTextButton(stringResource(R.string.ads_pass_watch), {
                    outcome = null
                    activity?.let { pass.watch(it) { result -> outcome = result } }
                }, enabled = state == PassState.Ready)
            }
        },
        dismissButton = {
            if (state !is PassState.Active) OmniTextButton(stringResource(R.string.ads_pass_not_now), onDismiss, color = colors.textSecondary)
        },
    )
}

@Composable
private fun formatTime(epochMs: Long): String = DateFormat.getTimeFormat(LocalContext.current).format(Date(epochMs))
