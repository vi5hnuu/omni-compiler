package solutions.laxmi.omnicompiler.feature.developer

import solutions.laxmi.omnicompiler.core.ui.asString
import androidx.compose.ui.platform.LocalResources
import solutions.laxmi.omnicompiler.core.ui.R as CommonR
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
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
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTextField
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTopBar
import solutions.laxmi.omnicompiler.core.designsystem.component.SectionLabel
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.model.Webhook
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.navigation.WelcomeRoute

/** Design U3 (adapted to one rotatable key): API access from your own apps, plus completion webhooks. */
@Composable
fun DeveloperScreen(navigator: Navigator) {
    val viewModel = hiltViewModel<DeveloperViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val clipboard = LocalClipboardManager.current
    var confirmRotate by rememberSaveable { mutableStateOf(false) }
    var addingWebhook by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Webhook?>(null) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it.asString(resources)) } }
    val colors = OmniTheme.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            OmniTopBar(stringResource(R.string.developer_title), onBack = navigator::back)
            if (!state.signedIn) {
                EmptyState(stringResource(R.string.developer_signed_out_title), stringResource(R.string.developer_signed_out_message), icon = OmniIcons.Key, action = { OmniButton(stringResource(CommonR.string.common_sign_in), { navigator.navigate(WelcomeRoute) }) })
                return@Column
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                SectionLabel(stringResource(R.string.developer_api_key))
                Text(
                    stringResource(R.string.developer_api_key_explainer),
                    style = OmniTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                state.revealedKey?.let { key ->
                    Column(Modifier.fillMaxWidth().padding(16.dp).background(colors.surfaceRaised).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OmniBadge(stringResource(R.string.developer_new_key), container = colors.accent, content = colors.onAccent)
                            Text("  " + stringResource(R.string.developer_copy_now), style = OmniTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                        Text(key, style = OmniTheme.typography.mono, color = colors.textPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            OmniTextButton(stringResource(R.string.developer_copy_key), { clipboard.setText(AnnotatedString(key)) })
                            OmniTextButton(stringResource(R.string.developer_hide), viewModel::hideKey, color = colors.textSecondary)
                        }
                    }
                }
                OmniButton(
                    stringResource(if (state.revealedKey == null) R.string.developer_generate else R.string.developer_generate_another),
                    { confirmRotate = true },
                    Modifier.padding(16.dp),
                    style = OmniButtonStyle.Outline,
                    leadingIcon = OmniIcons.Key,
                    loading = state.rotating,
                )
                SectionLabel(stringResource(R.string.developer_quick_start))
                val snippet = quickStart(state.apiBaseUrl)
                // Same shape as the console's code blocks: the snippet scrolls sideways and Copy stays in the corner.
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(colors.surfaceRaised)) {
                    Text(
                        snippet,
                        style = OmniTheme.typography.mono,
                        color = colors.textPrimary,
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 44.dp),
                    )
                    OmniIconButton(
                        OmniIcons.Copy,
                        stringResource(CommonR.string.common_copy),
                        { clipboard.setText(AnnotatedString(snippet)) },
                        modifier = Modifier.align(Alignment.TopEnd).background(colors.surfaceRaised),
                    )
                }

                SectionLabel(stringResource(R.string.developer_webhooks)) { OmniTextButton(stringResource(R.string.developer_add), { addingWebhook = true }) }
                Text(
                    stringResource(R.string.developer_webhooks_explainer),
                    style = OmniTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                state.createdSecret?.let { secret ->
                    InfoBanner(
                        stringResource(R.string.developer_secret_once, secret),
                        Modifier.padding(16.dp),
                        icon = OmniIcons.Lock,
                        action = {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                OmniTextButton(stringResource(CommonR.string.common_copy), { clipboard.setText(AnnotatedString(secret)) })
                                OmniTextButton(stringResource(CommonR.string.common_done), viewModel::dismissCreatedSecret, color = colors.textSecondary)
                            }
                        },
                    )
                }
                when {
                    state.webhooksLoading -> Box(Modifier.fillMaxWidth().padding(16.dp)) { OmniSpinner() }
                    state.webhooksError != null -> InfoBanner(state.webhooksError!!.asString(), Modifier.padding(16.dp), icon = OmniIcons.WifiOff, action = { OmniTextButton(stringResource(CommonR.string.common_retry), viewModel::loadWebhooks) })
                    state.webhooks.isEmpty() -> Text(stringResource(R.string.developer_no_webhooks), style = OmniTheme.typography.bodySmall, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
                    else -> state.webhooks.forEach { hook ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(hook.url, style = OmniTheme.typography.mono, color = colors.textPrimary, maxLines = 1)
                                Text(listOfNotNull(stringResource(if (hook.enabled) R.string.developer_enabled else R.string.developer_disabled), hook.createdAt?.take(10)).joinToString(" · "), style = OmniTheme.typography.monoSmall, color = colors.textTertiary)
                            }
                            OmniIconButton(OmniIcons.Trash, stringResource(R.string.developer_delete_webhook), { deleting = hook })
                        }
                    }
                }
            }
        }
        OmniSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    if (confirmRotate) {
        AlertDialog(
            onDismissRequest = { confirmRotate = false },
            shape = RectangleShape,
            containerColor = colors.surfaceRaised,
            title = { Text(stringResource(R.string.developer_rotate_title), style = OmniTheme.typography.title, color = colors.textPrimary) },
            text = { Text(stringResource(R.string.developer_rotate_message), style = OmniTheme.typography.body, color = colors.textSecondary) },
            confirmButton = {
                OmniTextButton(stringResource(R.string.developer_generate_confirm), {
                    confirmRotate = false
                    viewModel.rotateKey()
                })
            },
            dismissButton = { OmniTextButton(stringResource(CommonR.string.common_cancel), { confirmRotate = false }, color = colors.textSecondary) },
        )
    }
    if (addingWebhook) {
        var url by rememberSaveable { mutableStateOf("") }
        var secret by rememberSaveable { mutableStateOf(viewModel.newSecret()) }
        AlertDialog(
            onDismissRequest = { addingWebhook = false },
            shape = RectangleShape,
            containerColor = colors.surfaceRaised,
            title = { Text(stringResource(R.string.developer_add_webhook), style = OmniTheme.typography.title, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OmniTextField(url, { url = it }, label = stringResource(R.string.developer_url), placeholder = stringResource(R.string.developer_url_placeholder), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    OmniTextField(secret, { secret = it }, label = stringResource(R.string.developer_signing_secret), textStyle = OmniTheme.typography.mono)
                }
            },
            confirmButton = {
                OmniTextButton(stringResource(if (state.savingWebhook) R.string.developer_adding else R.string.developer_add), {
                    viewModel.createWebhook(url, secret) { addingWebhook = false }
                }, enabled = url.isNotBlank() && secret.isNotBlank() && !state.savingWebhook)
            },
            dismissButton = { OmniTextButton(stringResource(CommonR.string.common_cancel), { addingWebhook = false }, color = colors.textSecondary) },
        )
    }
    deleting?.let { hook ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            shape = RectangleShape,
            containerColor = colors.surfaceRaised,
            title = { Text(stringResource(R.string.developer_delete_webhook_title), style = OmniTheme.typography.title, color = colors.textPrimary) },
            text = { Text(hook.url, style = OmniTheme.typography.mono, color = colors.textSecondary) },
            confirmButton = {
                OmniTextButton(stringResource(CommonR.string.common_delete), {
                    deleting = null
                    viewModel.deleteWebhook(hook)
                })
            },
            dismissButton = { OmniTextButton(stringResource(CommonR.string.common_cancel), { deleting = null }, color = colors.textSecondary) },
        )
    }
}

private fun quickStart(base: String) = """
    curl -X POST $base/execute \
      -H "X-API-Key: ${'$'}OMNI_KEY" \
      -H "Content-Type: application/json" \
      -d '{"language":"python","code":"print(int(input())*2)","test_cases":[{"stdin":"21","expected":"42"}]}'
    # → {"job_id":"…"}  then  GET $base/jobs/{id}
""".trimIndent()
