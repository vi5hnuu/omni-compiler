package solutions.laxmi.omnicompiler.feature.workspace.preview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewFeature
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import kotlinx.coroutines.delay
import solutions.laxmi.omnicompiler.core.designsystem.component.EmptyState
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniIconButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniTopBar
import solutions.laxmi.omnicompiler.core.designsystem.icon.OmniIcons
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.core.navigation.Navigator
import solutions.laxmi.omnicompiler.core.navigation.PreviewRoute
import solutions.laxmi.omnicompiler.feature.workspace.R
import java.io.ByteArrayInputStream

/**
 * Preview for web files: Markdown rendered natively, HTML (or a JavaScript file in a blank page) in a WebView
 * that serves the project's own files, with a console that shows `console.*` and evaluates expressions.
 * The page reloads shortly after the editor saves a change.
 */
@Composable
fun PreviewScreen(route: PreviewRoute, navigator: Navigator) {
    val viewModel = hiltViewModel<PreviewViewModel, PreviewViewModel.Factory>(key = route.toString()) { it.create(route) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = OmniTheme.colors
    var webView by remember { mutableStateOf<WebView?>(null) }
    Column(Modifier.fillMaxSize().background(colors.background).navigationBarsPadding().imePadding()) {
        OmniTopBar(state.title.ifEmpty { stringResource(R.string.preview_title) }, onBack = navigator::back) {
            if (state.kind == PreviewKind.Html || state.kind == PreviewKind.JavaScript) {
                OmniIconButton(OmniIcons.Refresh, stringResource(R.string.preview_reload), { webView?.reload() })
            }
        }
        when (state.kind) {
            null -> EmptyState(stringResource(R.string.preview_unsupported_title), stringResource(R.string.preview_unsupported_message), Modifier.weight(1f), icon = OmniIcons.File)
            PreviewKind.Markdown -> Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Markdown(
                    content = state.markdown,
                    colors = markdownColor(text = colors.textPrimary, codeBackground = colors.surfaceRaised),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PreviewKind.Html, PreviewKind.JavaScript -> {
                ProjectPage(
                    files = state.files,
                    pageName = state.pageName,
                    onConsole = viewModel::log,
                    onWebView = { webView = it },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                BrowserConsole(
                    lines = state.console,
                    onEvaluate = { expression ->
                        viewModel.log(ConsoleLine(ConsoleLine.Level.Input, expression))
                        webView?.evaluateJavascript(expression) { result -> viewModel.log(ConsoleLine(ConsoleLine.Level.Result, result ?: "undefined")) }
                    },
                    onClear = viewModel::clearConsole,
                    modifier = Modifier.fillMaxWidth().height(if (state.kind == PreviewKind.JavaScript) 320.dp else 200.dp),
                )
            }
        }
    }
}

/** Serves [files] at `https://appassets.androidplatform.net/project/…` so pages load their own CSS, JS and images. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ProjectPage(
    files: Map<String, String>,
    pageName: String,
    onConsole: (ConsoleLine) -> Unit,
    onWebView: (WebView?) -> Unit,
    modifier: Modifier,
) {
    val currentFiles by rememberUpdatedState(files)
    var view by remember { mutableStateOf<WebView?>(null) }
    val currentConsole by rememberUpdatedState(onConsole)
    val pageUrl = "https://${WebViewAssetLoader.DEFAULT_DOMAIN}$PROJECT_PATH${Uri.encode(pageName)}"
    val loader = remember {
        WebViewAssetLoader.Builder()
            .addPathHandler(PROJECT_PATH) { path ->
                currentFiles[Uri.decode(path)]?.let { content ->
                    WebResourceResponse(mimeTypeOf(path), "utf-8", ByteArrayInputStream(content.encodeToByteArray()))
                }
            }
            .build()
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Pages only see their own project files through the loader; no file:// or content:// access.
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) WebSettingsCompat.setSafeBrowsingEnabled(settings, true)
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        loader.shouldInterceptRequest(request.url)

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (request.url.host == WebViewAssetLoader.DEFAULT_DOMAIN) return false
                        openExternally(ctx, request.url)
                        return true
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        val level = when (message.messageLevel()) {
                            ConsoleMessage.MessageLevel.ERROR -> ConsoleLine.Level.Error
                            ConsoleMessage.MessageLevel.WARNING -> ConsoleLine.Level.Warn
                            else -> ConsoleLine.Level.Log
                        }
                        val where = message.sourceId().substringAfterLast('/').takeIf { it.isNotEmpty() }?.let { " ($it:${message.lineNumber()})" }.orEmpty()
                        currentConsole(ConsoleLine(level, message.message() + if (level == ConsoleLine.Level.Error) where else ""))
                        return true
                    }
                }
                view = this
                onWebView(this)
                loadUrl(pageUrl)
            }
        },
        onRelease = { released ->
            view = null
            onWebView(null)
            released.destroy()
        },
    )
    // Autosave updates the served files; reload once edits settle (the first load is the factory's).
    var firstFiles by remember { mutableStateOf(true) }
    LaunchedEffect(files) {
        if (firstFiles) {
            firstFiles = false
            return@LaunchedEffect
        }
        delay(RELOAD_DEBOUNCE_MS)
        view?.reload()
    }
}

@Composable
private fun BrowserConsole(lines: List<ConsoleLine>, onEvaluate: (String) -> Unit, onClear: () -> Unit, modifier: Modifier) {
    val colors = OmniTheme.colors
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex) }
    Column(modifier.background(colors.surface).drawBehind { drawLine(colors.borderStrong, Offset(0f, 0f), Offset(size.width, 0f), 1f) }) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(stringResource(R.string.preview_console), style = OmniTheme.typography.overline, color = colors.textTertiary, modifier = Modifier.weight(1f))
            OmniIconButton(OmniIcons.Trash, stringResource(R.string.console_clear), onClear, size = 32.dp, iconSize = 14.dp)
        }
        SelectionContainer(Modifier.weight(1f)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                items(lines) { line ->
                    val color = when (line.level) {
                        ConsoleLine.Level.Error -> colors.status.rejected.text
                        ConsoleLine.Level.Warn -> colors.status.limit.text
                        ConsoleLine.Level.Input -> colors.textTertiary
                        ConsoleLine.Level.Result -> colors.accentText
                        ConsoleLine.Level.Log -> colors.textPrimary
                    }
                    val prefix = when (line.level) {
                        ConsoleLine.Level.Input -> "› "
                        ConsoleLine.Level.Result -> "‹ "
                        else -> ""
                    }
                    Text(prefix + line.text, style = OmniTheme.typography.mono, color = color, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(40.dp).drawBehind { drawLine(colors.divider, Offset(0f, 0f), Offset(size.width, 0f), 1f) }.padding(horizontal = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text("› ", style = OmniTheme.typography.mono, color = colors.accentText)
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                textStyle = OmniTheme.typography.mono.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    if (input.isNotBlank()) {
                        onEvaluate(input)
                        input = ""
                    }
                }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (input.isEmpty()) Text(stringResource(R.string.preview_console_hint), style = OmniTheme.typography.mono, color = colors.textTertiary)
                    inner()
                },
            )
        }
    }
}

private fun openExternally(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun mimeTypeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "html", "htm" -> "text/html"
    "css" -> "text/css"
    "js", "mjs" -> "text/javascript"
    "json" -> "application/json"
    "svg" -> "image/svg+xml"
    "xml" -> "application/xml"
    "md" -> "text/markdown"
    else -> "text/plain"
}

private const val PROJECT_PATH = "/project/"
private const val RELOAD_DEBOUNCE_MS = 800L
