package solutions.laxmi.omnicompiler.feature.developer

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
    val clipboard = LocalClipboardManager.current
    var confirmRotate by rememberSaveable { mutableStateOf(false) }
    var addingWebhook by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Webhook?>(null) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    val colors = OmniTheme.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            OmniTopBar("API key & webhooks", onBack = navigator::back)
            if (!state.signedIn) {
                EmptyState("Sign in first", "API keys and webhooks belong to your account.", icon = OmniIcons.Key, action = { OmniButton("Sign in", { navigator.navigate(WelcomeRoute) }) })
                return@Column
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                SectionLabel("API key")
                Text(
                    "Call the judge from scripts, CI or your own apps with an X-API-Key header. Generating a key replaces the previous one immediately.",
                    style = OmniTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                state.revealedKey?.let { key ->
                    Column(Modifier.fillMaxWidth().padding(16.dp).background(colors.surfaceRaised).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OmniBadge("NEW KEY", container = colors.accent, content = colors.onAccent)
                            Text("  Copy it now: it won't be shown again.", style = OmniTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                        Text(key, style = OmniTheme.typography.mono, color = colors.textPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            OmniTextButton("Copy key", { clipboard.setText(AnnotatedString(key)) })
                            OmniTextButton("Hide", viewModel::hideKey, color = colors.textSecondary)
                        }
                    }
                }
                OmniButton(
                    if (state.revealedKey == null) "Generate API key" else "Generate another",
                    { confirmRotate = true },
                    Modifier.padding(16.dp),
                    style = OmniButtonStyle.Secondary,
                    leadingIcon = OmniIcons.Key,
                    loading = state.rotating,
                )
                SectionLabel("Quick start")
                val snippet = quickStart(state.apiBaseUrl)
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(colors.surfaceRaised).padding(12.dp)) {
                    Text(snippet, style = OmniTheme.typography.mono, color = colors.textPrimary, modifier = Modifier.horizontalScroll(rememberScrollState()))
                    OmniTextButton("Copy", { clipboard.setText(AnnotatedString(snippet)) })
                }

                SectionLabel("Webhooks") { OmniTextButton("Add", { addingWebhook = true }) }
                Text(
                    "The judge POSTs job results to these URLs, signed with X-LS-Signature: sha256=<hmac of body with your secret>.",
                    style = OmniTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                state.createdSecret?.let { secret ->
                    InfoBanner(
                        "Signing secret (shown once): $secret",
                        Modifier.padding(16.dp),
                        icon = OmniIcons.Lock,
                        action = {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                OmniTextButton("Copy", { clipboard.setText(AnnotatedString(secret)) })
                                OmniTextButton("Done", viewModel::dismissCreatedSecret, color = colors.textSecondary)
                            }
                        },
                    )
                }
                when {
                    state.webhooksLoading -> Box(Modifier.fillMaxWidth().padding(16.dp)) { OmniSpinner() }
                    state.webhooksError != null -> InfoBanner(state.webhooksError!!, Modifier.padding(16.dp), icon = OmniIcons.WifiOff, action = { OmniTextButton("Retry", viewModel::loadWebhooks) })
                    state.webhooks.isEmpty() -> Text("No webhooks yet.", style = OmniTheme.typography.bodySmall, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
                    else -> state.webhooks.forEach { hook ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(hook.url, style = OmniTheme.typography.mono, color = colors.textPrimary, maxLines = 1)
                                Text(listOfNotNull(if (hook.enabled) "enabled" else "disabled", hook.createdAt?.take(10)).joinToString(" · "), style = OmniTheme.typography.monoSmall, color = colors.textTertiary)
                            }
                            OmniIconButton(OmniIcons.Trash, "Delete webhook", { deleting = hook })
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
    if (confirmRotate) {
        AlertDialog(
            onDismissRequest = { confirmRotate = false },
            shape = RectangleShape,
            containerColor = colors.surfaceRaised,
            title = { Text("Generate a new key?", style = OmniTheme.typography.title, color = colors.textPrimary) },
            text = { Text("Any key you issued before stops working right away.", style = OmniTheme.typography.body, color = colors.textSecondary) },
            confirmButton = {
                OmniTextButton("Generate", {
                    confirmRotate = false
                    viewModel.rotateKey()
                })
            },
            dismissButton = { OmniTextButton("Cancel", { confirmRotate = false }, color = colors.textSecondary) },
        )
    }
    if (addingWebhook) {
        var url by rememberSaveable { mutableStateOf("") }
        var secret by rememberSaveable { mutableStateOf(viewModel.newSecret()) }
        AlertDialog(
            onDismissRequest = { addingWebhook = false },
            shape = RectangleShape,
            containerColor = colors.surfaceRaised,
            title = { Text("Add webhook", style = OmniTheme.typography.title, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OmniTextField(url, { url = it }, label = "URL", placeholder = "https://example.com/hooks/omni", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    OmniTextField(secret, { secret = it }, label = "Signing secret", textStyle = OmniTheme.typography.mono)
                }
            },
            confirmButton = {
                OmniTextButton(if (state.savingWebhook) "Adding…" else "Add", {
                    viewModel.createWebhook(url, secret) { addingWebhook = false }
                }, enabled = url.isNotBlank() && secret.isNotBlank() && !state.savingWebhook)
            },
            dismissButton = { OmniTextButton("Cancel", { addingWebhook = false }, color = colors.textSecondary) },
        )
    }
    deleting?.let { hook ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            shape = RectangleShape,
            containerColor = colors.surfaceRaised,
            title = { Text("Delete webhook?", style = OmniTheme.typography.title, color = colors.textPrimary) },
            text = { Text(hook.url, style = OmniTheme.typography.mono, color = colors.textSecondary) },
            confirmButton = {
                OmniTextButton("Delete", {
                    deleting = null
                    viewModel.deleteWebhook(hook)
                })
            },
            dismissButton = { OmniTextButton("Cancel", { deleting = null }, color = colors.textSecondary) },
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
