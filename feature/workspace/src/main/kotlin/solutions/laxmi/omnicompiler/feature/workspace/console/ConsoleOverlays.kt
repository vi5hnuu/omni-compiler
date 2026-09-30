package solutions.laxmi.omnicompiler.feature.workspace.console

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
import solutions.laxmi.omnicompiler.core.common.formatMillis
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
        SheetTitle(if (initial == null) "New test case" else "Edit test case", "Output must match exactly; trailing newlines are ignored.")
        OmniTextField(name, { name = it }, label = "Name", placeholder = "Sample 1")
        OmniTextField(
            stdin, { stdin = it }, label = "stdin", singleLine = false, minLines = 3, textStyle = OmniTheme.typography.code,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            labelAction = { OmniTextButton("From file", onLoadStdinFile) },
        )
        OmniTextField(
            expected, { expected = it }, label = "Expected output", singleLine = false, minLines = 3, textStyle = OmniTheme.typography.code,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
        )
        OmniButton("Save test", { onSave(name.trim(), stdin, expected) }, trailingIcon = OmniIcons.Check)
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
        SheetTitle("Run limits", "Applies to every test in this project.")
        LimitRow("Time limit / test", "${(time / 100).roundToInt() * 100} ms")
        Slider(time, { time = it }, valueRange = 500f..Limits.MAX_TIME_MS.toFloat(), colors = sliderColors)
        LimitRow("Memory / run", "${memory.roundToInt()} MB")
        Slider(memory, { memory = it }, valueRange = 32f..Limits.MAX_MEM_MB.toFloat(), colors = sliderColors)
        Text(
            "Runtime default: ${runtimeDefaults.timeMs} ms · ${runtimeDefaults.memMb} MB. The judge may lower memory to what the runtime allows.",
            style = OmniTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Skip result cache", style = OmniTheme.typography.bodyStrong, color = colors.textPrimary)
                Text("Identical submissions normally reuse a recent result.", style = OmniTheme.typography.bodySmall, color = colors.textTertiary)
            }
            OmniToggle(bypassCache, onBypassCache)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OmniButton("Use defaults", {
                time = runtimeDefaults.timeMs.toFloat()
                memory = runtimeDefaults.memMb.toFloat()
            }, Modifier.weight(1f), style = OmniButtonStyle.Secondary, trailingIcon = null)
            OmniButton("Save", { onSave(Limits((time / 100).roundToInt() * 100, memory.roundToInt())) }, Modifier.weight(1f), trailingIcon = OmniIcons.Check)
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
        snapshot?.let { Text("${it.limit} runs / minute", style = OmniTheme.typography.overline, color = colors.textTertiary) }
        SheetTitle("You're running too fast", "The judge limits how many runs you can start each minute.")
        Row(Modifier.fillMaxWidth().background(colors.background).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Runs are back in", style = OmniTheme.typography.body, color = colors.textSecondary, modifier = Modifier.weight(1f))
            Text(if (remaining > 0) "${remaining}s" else "now", style = OmniTheme.typography.title, color = colors.accentText)
        }
        OmniButton("OK", onDismiss, trailingIcon = null)
    }
}

@Composable
internal fun BenchmarkSheet(defaultCopies: Int, busy: Boolean, result: BenchmarkResult?, onDismiss: () -> Unit, onStart: (Int) -> Unit) {
    val colors = OmniTheme.colors
    var copies by rememberSaveable { mutableFloatStateOf(defaultCopies.toFloat()) }
    OmniSheet(onDismiss) {
        SheetTitle("Benchmark", "Runs all tests N times in parallel to measure timing variance.")
        if (result == null) {
            LimitRow("Copies", copies.roundToInt().toString())
            Slider(
                copies, { copies = it }, valueRange = 1f..20f, steps = 18, enabled = !busy,
                colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.surfaceMuted),
            )
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OmniSpinner()
                    Text("Running ${copies.roundToInt()} copies…", style = OmniTheme.typography.body, color = colors.textSecondary)
                }
            } else {
                OmniButton("Start benchmark", { onStart(copies.roundToInt()) }, trailingIcon = OmniIcons.Zap)
            }
        } else {
            Row(Modifier.fillMaxWidth()) {
                listOf("Min" to result.minMs, "Median" to result.medianMs, "Max" to result.maxMs).forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(label.uppercase(), style = OmniTheme.typography.overline, color = colors.textTertiary)
                        Text(formatMillis(value), style = OmniTheme.typography.title, color = colors.textPrimary)
                    }
                }
            }
            result.runs.forEachIndexed { i, run ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("#${i + 1}", style = OmniTheme.typography.mono, color = colors.textTertiary)
                    run.verdict?.let { VerdictBadge(it) }
                    Text(shortJobId(run.jobId), style = OmniTheme.typography.mono, color = colors.textSecondary, modifier = Modifier.weight(1f))
                    Text(formatMillis(run.totalTimeMs), style = OmniTheme.typography.mono, color = colors.textPrimary)
                }
            }
            result.rejected.forEach { Text("#${it.index + 1} rejected: ${it.error}", style = OmniTheme.typography.bodySmall, color = colors.accentText) }
            OmniButton("Done", onDismiss, trailingIcon = null)
        }
    }
}

/** Signed replay proof from `POST /jobs/{id}/replay`. */
@Composable
internal fun ProofSheet(busy: Boolean, proof: ReplayProof?, onDismiss: () -> Unit) {
    val colors = OmniTheme.colors
    val clipboard = LocalClipboardManager.current
    OmniSheet(onDismiss) {
        SheetTitle("Verified run", "A signed record tying this verdict to the exact runtime image, code and input.")
        if (busy || proof == null) {
            OmniSpinner()
            return@OmniSheet
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            proof.verdict?.let { VerdictBadge(it) }
            Text(formatMillis(proof.totalTimeMs), style = OmniTheme.typography.mono, color = colors.textSecondary)
            Text(proof.runtimeId, style = OmniTheme.typography.mono, color = colors.textSecondary)
        }
        listOf(
            "Runtime image" to proof.imageHash,
            "Code" to proof.codeHash,
            "Input" to proof.inputHash,
            "Signature" to proof.signature,
            "Signed at" to proof.signedAt,
            "Proof version" to proof.version,
        ).forEach { (label, value) ->
            Column {
                Text(label.uppercase(), style = OmniTheme.typography.overline, color = colors.textTertiary)
                Text(value, style = OmniTheme.typography.mono, color = colors.textPrimary)
            }
        }
        OmniButton("Copy proof", {
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
            InfoBanner(icon = OmniIcons.WifiOff, text = "You're offline. Edits save on this device. Runs need a connection.")
        }
        state.pending.forEach { run ->
            Row(
                Modifier.fillMaxWidth().background(colors.surfaceRaised).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("1 run waiting", style = OmniTheme.typography.bodyStrong, color = colors.textPrimary)
                    Text(
                        "${timeFormat.get()!!.format(Date(run.createdAt.toEpochMilliseconds()))} · ${run.request.tests.size} tests",
                        style = OmniTheme.typography.monoSmall,
                        color = colors.textTertiary,
                    )
                }
                if (state.online) OmniTextButton("Send now", onSendNow)
                OmniTextButton("Cancel", { onCancel(run) }, color = colors.textSecondary)
            }
        }
        if (state.pending.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Send when back online", style = OmniTheme.typography.bodyStrong, color = colors.textPrimary, modifier = Modifier.weight(1f))
                OmniToggle(state.runSettings.sendQueuedWhenOnline, onSendWhenOnline)
            }
        }
    }
}

private val timeFormat = ThreadLocal.withInitial { SimpleDateFormat("HH:mm", Locale.getDefault()) }
