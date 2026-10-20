package solutions.laxmi.omnicompiler

import javax.inject.Inject
import solutions.laxmi.omnicompiler.core.ads.LocalAds
import solutions.laxmi.omnicompiler.core.ads.Ads
import solutions.laxmi.omnicompiler.core.review.AppReview
import solutions.laxmi.omnicompiler.core.review.LocalAppReview
import androidx.compose.runtime.CompositionLocalProvider
import solutions.laxmi.omnicompiler.core.model.AppTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.isSystemInDarkTheme
import android.graphics.Color
import android.content.ContentResolver
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme
import solutions.laxmi.omnicompiler.navigation.OmniNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var ads: Ads
    @Inject lateinit var review: AppReview

    // Set when a file from another app brought us to the front; that return never gets an app-open ad.
    private var openedForFile = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep the splash until the stored session is read, so the first frame is the right root.
        installSplashScreen().setKeepOnScreenCondition { viewModel.gate.value == AppGate.Loading }
        super.onCreate(savedInstanceState)
        // Once per launch: consent first (the form only shows where the law requires it), then the ads SDK.
        if (savedInstanceState == null) {
            review.onLaunch()
            ads.start(this)
            openedForFile = openSharedFile(intent)
        }
        setContent {
            val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
            val dark = when (appTheme) {
                AppTheme.SYSTEM -> isSystemInDarkTheme()
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
            }
            // System bar icons follow the app theme, not the device's, so they stay readable on our ground.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            CompositionLocalProvider(LocalAds provides ads, LocalAppReview provides review) {
                OmniTheme(darkTheme = dark) {
                    val gate by viewModel.gate.collectAsStateWithLifecycle()
                    val userId by viewModel.userId.collectAsStateWithLifecycle()
                    val externalFile by viewModel.externalFile.collectAsStateWithLifecycle()
                    if (gate != AppGate.Loading) {
                        OmniNavHost(gate, userId, BuildConfig.VERSION_NAME, externalFile, viewModel::onExternalFileOpened)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openedForFile = openSharedFile(intent)
    }

    /** "Open with" from a file manager: edit that file in place (only content URIs; file:// needs storage access). */
    private fun openSharedFile(intent: Intent?): Boolean {
        val uri = intent?.data?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT } ?: return false
        if (intent.action != Intent.ACTION_VIEW && intent.action != Intent.ACTION_EDIT) return false
        viewModel.openExternalFile(uri.toString())
        return true
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }

    // onResume rather than onStart: a new intent ("Open with") is delivered between the two.
    override fun onResume() {
        super.onResume()
        ads.onForeground(this, openedForContent = openedForFile)
        openedForFile = false
    }

    override fun onStop() {
        super.onStop()
        ads.onBackground()
    }
}
