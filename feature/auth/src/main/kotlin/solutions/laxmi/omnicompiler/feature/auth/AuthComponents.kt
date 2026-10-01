package solutions.laxmi.omnicompiler.feature.auth

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import solutions.laxmi.omnicompiler.core.designsystem.R as DesignR
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButton
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniButtonStyle
import solutions.laxmi.omnicompiler.core.designsystem.component.OmniDivider
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniDimens
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme

/** Red square `{}` mark + lowercase wordmark (design A1). */
@Composable
internal fun Wordmark() {
    val colors = OmniTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(22.dp).background(colors.accent), contentAlignment = Alignment.Center) {
            Text("{}", style = OmniTheme.typography.badge.copy(fontSize = 11.sp), color = colors.onAccent)
        }
        Text(stringResource(R.string.auth_wordmark), style = OmniTheme.typography.title.copy(fontSize = 15.sp), color = colors.textPrimary)
    }
}

/** Scrollable auth page scaffold with a snackbar host. */
@Composable
internal fun AuthScaffold(
    snackbar: SnackbarHostState,
    topBar: @Composable () -> Unit,
    /** Pinned under the scrolling content at the bottom of the screen (design A1's legal row). */
    footer: (@Composable () -> Unit)? = null,
    /** How content sits when it's shorter than the screen; e.g. SpaceBetween pushes the last block down. */
    contentArrangement: Arrangement.Vertical = Arrangement.spacedBy(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(OmniTheme.colors.background)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            topBar()
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                // At least viewport-tall so the arrangement can use the whole screen; scrolls when taller.
                // Capped and centred so landscape and tablets show a form, not edge-to-edge fields.
                Column(
                    Modifier
                        .widthIn(max = OmniDimens.formMaxWidth)
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight)
                        .padding(horizontal = OmniDimens.screenPadding),
                    verticalArrangement = contentArrangement,
                    content = content,
                )
            }
            if (footer != null) {
                val divider = OmniTheme.colors.divider
                Box(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind { drawLine(divider, Offset(0f, 0.5f), Offset(size.width, 0.5f), 1f) }
                        .padding(horizontal = OmniDimens.screenPadding),
                ) { footer() }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
internal fun AuthHeading(title: String, subtitle: String?) {
    Column(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = OmniTheme.typography.headline, color = OmniTheme.colors.textPrimary)
        if (subtitle != null) Text(subtitle, style = OmniTheme.typography.body, color = OmniTheme.colors.textSecondary)
    }
}

@Composable
internal fun GoogleButton(text: String, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    OmniButton(
        text = text,
        onClick = onClick,
        style = OmniButtonStyle.Light,
        leadingPainter = painterResource(DesignR.drawable.ic_google),
        loading = loading,
        enabled = enabled,
        height = OmniDimens.buttonHeightLarge,
    )
}

@Composable
internal fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OmniDivider(Modifier.weight(1f))
        Text(stringResource(R.string.auth_or), style = OmniTheme.typography.label, color = OmniTheme.colors.textTertiary)
        OmniDivider(Modifier.weight(1f))
    }
}

@Composable
internal fun StatusBarSpacer() = Box(Modifier.statusBarsPadding().height(OmniDimens.appBarHeight))

/** Opens the user's mail app (inbox), falling back silently when none is installed. */
internal fun Context.openEmailApp(): Boolean = try {
    startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    false
}

/** Finds the hosting Activity; Credential Manager must show its picker over one. */
internal tailrec fun Context.findActivityContext(): Context = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivityContext()
    else -> this
}
