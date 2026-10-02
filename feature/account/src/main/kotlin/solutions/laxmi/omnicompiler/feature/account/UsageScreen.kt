package solutions.laxmi.omnicompiler.feature.account

import solutions.laxmi.omnicompiler.core.model.Limits
import solutions.laxmi.omnicompiler.core.ui.asString
import androidx.compose.ui.platform.LocalResources
import solutions.laxmi.omnicompiler.core.ui.R as CommonR
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniSnackbarHost
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
    val resources = LocalResources.current
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it.asString(resources)) } }
    val colors = OmniTheme.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            OmniTopBar(stringResource(R.string.usage_title), onBack = navigator::back) {
                OmniIconButton(OmniIcons.Refresh, stringResource(R.string.usage_refresh), viewModel::load)
            }
            when {
                !state.signedIn -> EmptyState(stringResource(R.string.usage_signed_out_title), stringResource(R.string.usage_signed_out_message), icon = OmniIcons.Chart, action = { OmniButton(stringResource(CommonR.string.common_sign_in), { navigator.navigate(WelcomeRoute) }) })
                state.loading && state.plan == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { OmniSpinner() }
                state.plan == null -> EmptyState(stringResource(R.string.usage_load_failed), state.error?.asString().orEmpty(), icon = OmniIcons.WifiOff, action = { OmniButton(stringResource(CommonR.string.common_retry), viewModel::load) })
                else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    val plan = state.plan!!
                    plan.expiryWarning?.let { InfoBanner(it, Modifier.padding(16.dp), icon = OmniIcons.Alert) }
                    if (state.billing?.testMode == true) InfoBanner(stringResource(R.string.usage_test_mode), Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), icon = OmniIcons.Info)
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.usage_current_plan).uppercase(), style = OmniTheme.typography.overline, color = colors.textTertiary)
                            Text(plan.effectivePlan.replaceFirstChar { it.uppercase() }, style = OmniTheme.typography.headline, color = colors.textPrimary)
                            plan.expiresAt?.let { Text(stringResource(R.string.usage_renews_or_ends, it.take(10)), style = OmniTheme.typography.bodySmall, color = colors.textTertiary) }
                        }
                        plan.planSource?.let(::planSourceLabel)?.let { OmniBadge(stringResource(it).uppercase()) }
                    }
                    state.billing?.let { billing ->
                        SectionLabel(stringResource(R.string.usage_billing_period))
                        // A quota of 0 is unmetered on the judge: show the count without a meter or "remaining".
                        if (billing.quotaLimit > 0) {
                            Meter(
                                label = stringResource(R.string.usage_billed_runs),
                                value = stringResource(R.string.usage_fraction, billing.executionsUsed.toInt(), billing.quotaLimit.toInt()),
                                fraction = billing.executionsUsed.toFloat() / billing.quotaLimit,
                            )
                            Text(billing.quotaRemaining.toInt().let { pluralStringResource(R.plurals.usage_runs_remaining, it, it) }, style = OmniTheme.typography.bodySmall, color = colors.textTertiary, modifier = Modifier.padding(horizontal = 16.dp))
                        } else {
                            KeyValue(stringResource(R.string.usage_billed_runs), stringResource(R.string.usage_unlimited_count, billing.executionsUsed.toInt()))
                        }
                    }
                    SectionLabel(stringResource(R.string.usage_rate_limit))
                    // The judge omits rate headers for unlimited buckets and reports an rpm of 0.
                    val limit = state.rateLimit?.takeIf { it.limit > 0 }
                    when {
                        limit != null -> Meter(
                            label = stringResource(R.string.usage_runs_this_minute),
                            value = stringResource(R.string.usage_fraction, limit.limit - limit.remaining, limit.limit),
                            fraction = (limit.limit - limit.remaining).toFloat() / limit.limit,
                        )
                        plan.rateLimitRpm > 0 -> KeyValue(stringResource(R.string.usage_runs_this_minute), stringResource(R.string.usage_per_minute, plan.rateLimitRpm))
                        else -> KeyValue(stringResource(R.string.usage_runs_this_minute), stringResource(R.string.usage_unlimited))
                    }
                    SectionLabel(stringResource(R.string.usage_your_limits))
                    listOf(
                        stringResource(R.string.usage_limit_time) to stringResource(R.string.usage_limit_time_value, Limits.MAX_TIME_MS / 1_000),
                        stringResource(R.string.usage_limit_memory) to stringResource(R.string.usage_limit_memory_value, Limits.MAX_MEM_MB),
                        stringResource(R.string.usage_limit_tests) to JUDGE_MAX_TESTS.toString(),
                        stringResource(R.string.usage_limit_files) to stringResource(R.string.usage_limit_files_value, JUDGE_MAX_FILES, JUDGE_MAX_FILE_KB),
                        stringResource(R.string.usage_queue) to stringResource(if (plan.effectivePlan.equals("free", true)) R.string.usage_queue_normal else R.string.usage_queue_priority),
                    ).forEach { (k, v) -> KeyValue(k, v) }
                    state.stats?.let { stats ->
                        SectionLabel(stringResource(R.string.usage_all_time))
                        KeyValue(stringResource(R.string.usage_runs), stats.totalJobs.toString())
                        KeyValue(stringResource(R.string.usage_accepted), stringResource(R.string.usage_percent, (stats.acceptanceRate * 100).roundToInt()))
                    }
                    state.billing?.let { billing ->
                        SectionLabel(stringResource(R.string.usage_billing))
                        KeyValue(stringResource(R.string.usage_status), billing.status.humanized().ifBlank { stringResource(R.string.usage_status_none) })
                        billing.provider?.let { KeyValue(stringResource(R.string.usage_provider), it.humanized()) }
                        billing.periodStart?.let { KeyValue(stringResource(R.string.usage_period_started), it.take(10)) }
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OmniButton(stringResource(R.string.usage_sync), viewModel::sync, Modifier.weight(1f), style = OmniButtonStyle.Secondary, loading = state.busy, trailingIcon = OmniIcons.Refresh)
                            if (billing.hasCancellableSubscription) {
                                OmniButton(stringResource(R.string.usage_cancel_plan), { confirmCancel = true }, Modifier.weight(1f), style = OmniButtonStyle.Outline, enabled = !state.busy, trailingIcon = null)
                            }
                        }
                    }
                }
            }
        }
        OmniSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    if (confirmCancel) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmCancel = false },
            shape = androidx.compose.ui.graphics.RectangleShape,
            containerColor = colors.surfaceRaised,
            title = { Text(stringResource(R.string.usage_cancel_title), style = OmniTheme.typography.title, color = colors.textPrimary) },
            text = { Text(stringResource(R.string.usage_cancel_message), style = OmniTheme.typography.body, color = colors.textSecondary) },
            confirmButton = {
                OmniTextButton(stringResource(R.string.usage_cancel_plan), {
                    confirmCancel = false
                    viewModel.cancelSubscription()
                })
            },
            dismissButton = { OmniTextButton(stringResource(R.string.usage_keep), { confirmCancel = false }, color = colors.textSecondary) },
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

/** Judge-enforced per-run caps (ls-judge MAX_TEST_CASES / MAX_FILES / MAX_CODE_BYTES defaults). */
private const val JUDGE_MAX_TESTS = 100
private const val JUDGE_MAX_FILES = 20
private const val JUDGE_MAX_FILE_KB = 64

/** Judge enum values ("past_due", "razorpay") as display text ("Past due", "Razorpay"). */
private fun String.humanized(): String = replace('_', ' ').replaceFirstChar { it.uppercase() }

/** Readable label for the judge's `plan_source`; the schema default needs no badge. */
@androidx.annotation.StringRes
private fun planSourceLabel(source: String): Int? = when (source) {
    "admin_grant" -> R.string.usage_source_admin_grant
    "billing" -> R.string.usage_source_billing
    "trial" -> R.string.usage_source_trial
    else -> null
}
