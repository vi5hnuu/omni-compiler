package solutions.laxmi.omnicompiler.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import solutions.laxmi.omnicompiler.core.designsystem.component.EmptyState
import solutions.laxmi.omnicompiler.core.designsystem.component.InfoBanner
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniBadge
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButtonStyle
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniIconButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniSpinner
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTopBar
import solutions.laxmi.omnicompiler.core.designsystem.component.SectionLabel
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.navigation.WelcomeRoute
import kotlin.math.roundToInt

/** Design U1 (adapted): plan, quota, rate limit and billing status. No in-app purchase by product decision. */
@Composable
fun UsageScreen(navigator: Navigator) {
    val viewModel = hiltViewModel<UsageViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    val colors = OmniTheme.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            OmniTopBar("Usage & plan", onBack = navigator::back) {
                OmniIconButton(OmniIcons.Refresh, "Refresh", viewModel::load)
            }
            when {
                !state.signedIn -> EmptyState("Sign in to see usage", "Plans and quotas belong to an account.", icon = OmniIcons.Chart, action = { OmniButton("Sign in", { navigator.navigate(WelcomeRoute) }) })
                state.loading && state.plan == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { OmniSpinner() }
                state.plan == null -> EmptyState("Couldn't load usage", state.error.orEmpty(), icon = OmniIcons.WifiOff, action = { OmniButton("Retry", viewModel::load) })
                else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    val plan = state.plan!!
                    plan.expiryWarning?.let { InfoBanner(it, Modifier.padding(16.dp), icon = OmniIcons.Alert) }
                    if (state.billing?.testMode == true) InfoBanner("Billing is in test mode on this server.", Modifier.padding(horizontal = 16.dp), icon = OmniIcons.Info)
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("CURRENT PLAN", style = OmniTheme.typography.overline, color = colors.textTertiary)
                            Text(plan.effectivePlan.replaceFirstChar { it.uppercase() }, style = OmniTheme.typography.headline, color = colors.textPrimary)
                            plan.expiresAt?.let { Text("Renews or ends ${it.take(10)}", style = OmniTheme.typography.bodySmall, color = colors.textTertiary) }
                        }
                        plan.planSource?.let { OmniBadge(it.uppercase()) }
                    }
                    state.billing?.let { billing ->
                        SectionLabel("This billing period")
                        Meter(
                            label = "Runs",
                            value = "${billing.executionsUsed} / ${billing.quotaLimit}",
                            fraction = if (billing.quotaLimit > 0) billing.executionsUsed.toFloat() / billing.quotaLimit else 0f,
                        )
                        Text("${billing.quotaRemaining} runs remaining", style = OmniTheme.typography.bodySmall, color = colors.textTertiary, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    SectionLabel("Rate limit")
                    val limit = state.rateLimit
                    Meter(
                        label = "Runs this minute",
                        value = limit?.let { "${it.limit - it.remaining} / ${it.limit}" } ?: "${plan.rateLimitRpm} / min",
                        fraction = limit?.let { (it.limit - it.remaining).toFloat() / it.limit.coerceAtLeast(1) } ?: 0f,
                    )
                    SectionLabel("Your limits")
                    listOf(
                        "Time limit / test" to "up to 30 s",
                        "Memory / run" to "up to 1024 MB",
                        "Tests / run" to "100",
                        "Extra files / run" to "20 × 64 KB",
                        "Queue" to if (plan.effectivePlan.equals("free", true)) "Normal" else "Priority",
                    ).forEach { (k, v) -> KeyValue(k, v) }
                    state.stats?.let { stats ->
                        SectionLabel("All time")
                        KeyValue("Runs", stats.totalJobs.toString())
                        KeyValue("Accepted", "${(stats.acceptanceRate * 100).roundToInt()}%")
                    }
                    state.billing?.let { billing ->
                        SectionLabel("Billing")
                        KeyValue("Status", billing.status.ifBlank { "none" })
                        billing.provider?.let { KeyValue("Provider", it) }
                        billing.periodStart?.let { KeyValue("Period started", it.take(10)) }
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OmniButton("Sync billing", viewModel::sync, Modifier.weight(1f), style = OmniButtonStyle.Secondary, loading = state.busy, trailingIcon = OmniIcons.Refresh)
                            if (billing.hasCancellableSubscription) {
                                OmniButton("Cancel plan", { confirmCancel = true }, Modifier.weight(1f), style = OmniButtonStyle.Outline, enabled = !state.busy, trailingIcon = null)
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
    if (confirmCancel) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmCancel = false },
            shape = androidx.compose.ui.graphics.RectangleShape,
            containerColor = colors.surfaceRaised,
            title = { Text("Cancel subscription?", style = OmniTheme.typography.title, color = colors.textPrimary) },
            text = { Text("Your plan stays active until the end of the current period.", style = OmniTheme.typography.body, color = colors.textSecondary) },
            confirmButton = {
                OmniTextButton("Cancel plan", {
                    confirmCancel = false
                    viewModel.cancelSubscription()
                })
            },
            dismissButton = { OmniTextButton("Keep", { confirmCancel = false }, color = colors.textSecondary) },
        )
    }
}

@Composable
internal fun Meter(label: String, value: String, fraction: Float) {
    val colors = OmniTheme.colors
    val high = fraction >= 0.9f
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(label, style = OmniTheme.typography.bodyStrong, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text(value, style = OmniTheme.typography.mono, color = if (high) colors.accentText else colors.textSecondary)
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(colors.surfaceMuted)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(if (high) colors.accent else colors.textPrimary))
        }
    }
}

@Composable
internal fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(key, style = OmniTheme.typography.body, color = OmniTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = OmniTheme.typography.mono, color = OmniTheme.colors.textPrimary)
    }
}
