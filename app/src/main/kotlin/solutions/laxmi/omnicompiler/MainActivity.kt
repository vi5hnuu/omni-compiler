package solutions.laxmi.omnicompiler

import javax.inject.Inject
import solutions.laxmi.omnicompiler.core.ads.LocalAds
import solutions.laxmi.omnicompiler.core.ads.Ads
import androidx.compose.runtime.CompositionLocalProvider
import solutions.laxmi.omnicompiler.core.model.AppTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.isSystemInDarkTheme
import android.graphics.Color
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

    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep the splash until the stored session is read, so the first frame is the right root.
        installSplashScreen().setKeepOnScreenCondition { viewModel.gate.value == AppGate.Loading }
        super.onCreate(savedInstanceState)
        // Once per launch: consent first (the form only shows where the law requires it), then the ads SDK.
        if (savedInstanceState == null) ads.start(this)
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
            CompositionLocalProvider(LocalAds provides ads) {
                OmniTheme(darkTheme = dark) {
                    val gate by viewModel.gate.collectAsStateWithLifecycle()
                    if (gate != AppGate.Loading) OmniNavHost(gate, BuildConfig.VERSION_NAME)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }
}
