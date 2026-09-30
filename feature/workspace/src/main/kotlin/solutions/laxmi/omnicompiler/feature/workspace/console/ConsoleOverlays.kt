package solutions.laxmi.omnicompiler.feature.workspace.console

import solutions.laxmi.omnicompiler.core.ui.testCount
import solutions.laxmi.omnicompiler.core.ui.formatDuration
import solutions.laxmi.omnicompiler.core.ui.R as CommonR
import solutions.laxmi.omnicompiler.feature.workspace.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import solutions.laxmi.omnicompiler.core.common.shortJobId
import solutions.laxmi.omnicompiler.core.designsystem.component.InfoBanner
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButtonStyle
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniSpinner
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextField
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniToggle
import solutions.laxmi.omnicompiler.core.designsystem.component.SheetHandle
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.model.BenchmarkResult
import solutions.laxmi.omnicompiler.core.model.Limits
import solutions.laxmi.omnicompiler.core.model.PendingRun
import solutions.laxmi.omnicompiler.core.model.RateLimitSnapshot
import solutions.laxmi.omnicompiler.core.model.ReplayProof
import solutions.laxmi.omnicompiler.core.model.TestCase
import solutions.laxmi.omnicompiler.core.ui.VerdictBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OmniSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RectangleShape,
        containerColor = OmniTheme.colors.surface,
        scrimColor = OmniTheme.colors.scrim,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) { content() }
    }
}

@Composable
private fun SheetTitle(title: String, subtitle: String? = null) {
    Column {
        Text(title, style = OmniTheme.typography.title, color = OmniTheme.colors.textPrimary)
        if (subtitle != null) Text(subtitle, style = OmniTheme.typography.bodySmall, color = OmniTheme.colors.textTertiary)
    }
}

/** Create or edit a test case; stdin can be loaded from a file. */
@Composable
internal fun TestEditorSheet(
    initial: TestCase?,
    loadedStdin: String?,
    onLoadStdinFile: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (name: String, stdin: String, expected: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var stdin by rememberSaveable { mutableStateOf(initial?.stdin.orEmpty()) }
    var expected by rememberSaveable { mutableStateOf(initial?.expected.orEmpty()) }
    LaunchedEffect(loadedStdin) { loadedStdin?.let { stdin = it } }
    OmniSheet(onDismiss) {
        SheetTitle(stringResource(if (initial == null) R.string.sheet_test_new else R.string.sheet_test_edit), stringResource(R.string.sheet_test_hint))
        OmniTextField(name, { name = it }, label = stringResource(R.string.sheet_test_name), placeholder = stringResource(R.string.sheet_test_name_placeholder))
        OmniTextField(
            stdin, { stdin = it }, label = stringResource(R.string.sheet_test_stdin), singleLine = false, minLines = 3, textStyle = OmniTheme.typography.code,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            labelAction = { OmniTextButton(stringResource(R.string.input_from_file), onLoadStdinFile) },
        )
        OmniTextField(
            expected, { expected = it }, label = stringResource(R.string.sheet_test_expected), singleLine = false, minLines = 3, textStyle = OmniTheme.typography.code,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
        )
        OmniButton(stringResource(R.string.sheet_test_save), { onSave(name.trim(), stdin, expected) }, trailingIcon = OmniIcons.Check)
    }
}

/** Per-project time/memory limits; the server clamps to 30 s / 1024 MB. */
@Composable
internal fun LimitsSheet(current: Limits, runtimeDefaults: Limits, bypassCache: Boolean, onBypassCache: (Boolean) -> Unit, onDismiss: () -> Unit, onSave: (Limits) -> Unit) {
    var time by rememberSaveable { mutableFloatStateOf(current.timeMs.toFloat()) }
    var memory by rememberSaveable { mutableFloatStateOf(current.memMb.toFloat()) }
    val colors = OmniTheme.colors
    val sliderColors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.surfaceMuted)
    OmniSheet(onDismiss) {
        SheetTitle(stringResource(R.string.sheet_limits_title), stringResource(R.string.sheet_limits_subtitle))
        LimitRow(stringResource(R.string.sheet_limits_time), stringResource(R.string.sheet_limits_time_value, (time / 100).roundToInt() * 100))
        Slider(time, { time = it }, valueRange = 500f..Limits.MAX_TIME_MS.toFloat(), colors = sliderColors)
        LimitRow(stringResource(R.string.sheet_limits_memory), stringResource(R.string.sheet_limits_memory_value, memory.roundToInt()))
        Slider(memory, { memory = it }, valueRange = 32f..Limits.MAX_MEM_MB.toFloat(), colors = sliderColors)
        Text(
            stringResource(R.string.sheet_limits_defaults_note, runtimeDefaults.timeMs, runtimeDefaults.memMb),
            style = OmniTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sheet_limits_skip_cache), style = OmniTheme.typography.bodyStrong, color = colors.textPrimary)
                Text(stringResource(R.string.sheet_limits_skip_cache_note), style = OmniTheme.typography.bodySmall, color = colors.textTertiary)
            }
            OmniToggle(bypassCache, onBypassCache)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OmniButton(stringResource(R.string.sheet_limits_use_defaults), {
                time = runtimeDefaults.timeMs.toFloat()
                memory = runtimeDefaults.memMb.toFloat()
            }, Modifier.weight(1f), style = OmniButtonStyle.Secondary, trailingIcon = null)
            OmniButton(stringResource(CommonR.string.common_save), { onSave(Limits((time / 100).roundToInt() * 100, memory.roundToInt())) }, Modifier.weight(1f), trailingIcon = OmniIcons.Check)
        }
    }
}

@Composable
private fun LimitRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = OmniTheme.typography.bodyStrong, color = OmniTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        Text(value, style = OmniTheme.typography.mono, color = OmniTheme.colors.textSecondary)
    }
}

/** Design X2, adapted: ls-judge limits runs per minute (429), so this shows when runs are back. */
@Composable
internal fun RateLimitSheet(retryAfterSeconds: Long?, snapshot: RateLimitSnapshot?, onDismiss: () -> Unit) {
    val colors = OmniTheme.colors
    var remaining by rememberSaveable {
        mutableLongStateOf(
            retryAfterSeconds ?: snapshot?.let { (it.resetEpochSeconds - System.currentTimeMillis() / 1000).coerceAtLeast(0) } ?: 60,
        )
    }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1_000)
            remaining--
        }
    }
    OmniSheet(onDismiss) {
        snapshot?.let { Text(pluralStringResource(R.plurals.sheet_rate_limit_per_minute, it.limit, it.limit).uppercase(), style = OmniTheme.typography.overline, color = colors.textTertiary) }
        SheetTitle(stringResource(R.string.sheet_rate_limit_title), stringResource(R.string.sheet_rate_limit_subtitle))
        Row(Modifier.fillMaxWidth().background(colors.background).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sheet_rate_limit_back_in), style = OmniTheme.typography.body, color = colors.textSecondary, modifier = Modifier.weight(1f))
            Text(if (remaining > 0) stringResource(R.string.sheet_rate_limit_seconds, remaining.toInt()) else stringResource(R.string.sheet_rate_limit_now), style = OmniTheme.typography.title, color = colors.accentText)
        }
        OmniButton(stringResource(CommonR.string.common_ok), onDismiss, trailingIcon = null)
    }
}

@Composable
internal fun BenchmarkSheet(defaultCopies: Int, busy: Boolean, result: BenchmarkResult?, onDismiss: () -> Unit, onStart: (Int) -> Unit) {
    val colors = OmniTheme.colors
    var copies by rememberSaveable { mutableFloatStateOf(defaultCopies.toFloat()) }
    OmniSheet(onDismiss) {
        SheetTitle(stringResource(R.string.sheet_benchmark_title), stringResource(R.string.sheet_benchmark_subtitle))
        if (result == null) {
            LimitRow(stringResource(R.string.sheet_benchmark_copies), copies.roundToInt().toString())
            Slider(
                copies, { copies = it }, valueRange = 1f..20f, steps = 18, enabled = !busy,
                colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.surfaceMuted),
            )
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OmniSpinner()
                    Text(copies.roundToInt().let { pluralStringResource(R.plurals.sheet_benchmark_running, it, it) }, style = OmniTheme.typography.body, color = colors.textSecondary)
                }
            } else {
                OmniButton(stringResource(R.string.sheet_benchmark_start), { onStart(copies.roundToInt()) }, trailingIcon = OmniIcons.Zap)
            }
        } else {
            Row(Modifier.fillMaxWidth()) {
                listOf(R.string.sheet_benchmark_min to result.minMs, R.string.sheet_benchmark_median to result.medianMs, R.string.sheet_benchmark_max to result.maxMs).forEach { (labelRes, value) ->
                    val label = stringResource(labelRes)
                    Column(Modifier.weight(1f)) {
                        Text(label.uppercase(), style = OmniTheme.typography.overline, color = colors.textTertiary)
                        Text(formatDuration(value), style = OmniTheme.typography.title, color = colors.textPrimary)
                    }
                }
            }
            result.runs.forEachIndexed { i, run ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.sheet_benchmark_index, i + 1), style = OmniTheme.typography.mono, color = colors.textTertiary)
                    run.verdict?.let { VerdictBadge(it) }
                    Text(shortJobId(run.jobId), style = OmniTheme.typography.mono, color = colors.textSecondary, modifier = Modifier.weight(1f))
                    Text(formatDuration(run.totalTimeMs), style = OmniTheme.typography.mono, color = colors.textPrimary)
                }
            }
            result.rejected.forEach { Text(stringResource(R.string.sheet_benchmark_rejected, it.index + 1, it.error), style = OmniTheme.typography.bodySmall, color = colors.accentText) }
            OmniButton(stringResource(CommonR.string.common_done), onDismiss, trailingIcon = null)
        }
    }
}

/** Signed replay proof from `POST /jobs/{id}/replay`. */
@Composable
internal fun ProofSheet(busy: Boolean, proof: ReplayProof?, onDismiss: () -> Unit) {
    val colors = OmniTheme.colors
    val clipboard = LocalClipboardManager.current
    OmniSheet(onDismiss) {
        SheetTitle(stringResource(R.string.sheet_proof_title), stringResource(R.string.sheet_proof_subtitle))
        if (busy || proof == null) {
            OmniSpinner()
            return@OmniSheet
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            proof.verdict?.let { VerdictBadge(it) }
            Text(formatDuration(proof.totalTimeMs), style = OmniTheme.typography.mono, color = colors.textSecondary)
            Text(proof.runtimeId, style = OmniTheme.typography.mono, color = colors.textSecondary)
        }
        listOf(
            stringResource(R.string.sheet_proof_image) to proof.imageHash,
            stringResource(R.string.sheet_proof_code) to proof.codeHash,
            stringResource(R.string.sheet_proof_input) to proof.inputHash,
            stringResource(R.string.sheet_proof_signature) to proof.signature,
            stringResource(R.string.sheet_proof_signed_at) to proof.signedAt,
            stringResource(R.string.sheet_proof_version) to proof.version,
        ).forEach { (label, value) ->
            Column {
                Text(label.uppercase(), style = OmniTheme.typography.overline, color = colors.textTertiary)
                Text(value, style = OmniTheme.typography.mono, color = colors.textPrimary)
            }
        }
        OmniButton(stringResource(R.string.sheet_proof_copy), {
            clipboard.setText(AnnotatedString(listOf(proof.version, proof.runtimeId, proof.imageHash, proof.codeHash, proof.inputHash, proof.verdict?.code, proof.totalTimeMs, proof.signature, proof.signedAt).joinToString("\n")))
        }, style = OmniButtonStyle.Secondary, leadingIcon = OmniIcons.Copy, trailingIcon = null)
    }
}

/** Design X1: queued runs waiting for connectivity. */
@Composable
internal fun OfflineQueueCard(
    state: ConsoleUiState,
    onCancel: (PendingRun) -> Unit,
    onSendNow: () -> Unit,
    onSendWhenOnline: (Boolean) -> Unit,
) {
    val colors = OmniTheme.colors
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!state.online) {
            InfoBanner(icon = OmniIcons.WifiOff, text = stringResource(R.string.offline_banner))
        }
        state.pending.forEach { run ->
            Row(
                Modifier.fillMaxWidth().background(colors.surfaceRaised).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.offline_run_waiting), style = OmniTheme.typography.bodyStrong, color = colors.textPrimary)
                    Text(
                        stringResource(R.string.offline_run_meta, timeFormat.get()!!.format(Date(run.createdAt.toEpochMilliseconds())), testCount(run.request.tests.size)),
                        style = OmniTheme.typography.monoSmall,
                        color = colors.textTertiary,
                    )
                }
                if (state.online) OmniTextButton(stringResource(R.string.offline_send_now), onSendNow)
                OmniTextButton(stringResource(CommonR.string.common_cancel), { onCancel(run) }, color = colors.textSecondary)
            }
        }
        if (state.pending.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.offline_send_when_online), style = OmniTheme.typography.bodyStrong, color = colors.textPrimary, modifier = Modifier.weight(1f))
                OmniToggle(state.runSettings.sendQueuedWhenOnline, onSendWhenOnline)
            }
        }
    }
}

private val timeFormat = ThreadLocal.withInitial { SimpleDateFormat("HH:mm", Locale.getDefault()) }
