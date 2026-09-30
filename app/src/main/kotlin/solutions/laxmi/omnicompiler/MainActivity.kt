package solutions.laxmi.omnicompiler

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

    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep the splash until the stored session is read, so the first frame is the right root.
        installSplashScreen().setKeepOnScreenCondition { viewModel.gate.value == AuthGate.Loading }
        // The UI is dark-only, so system bars always use light icons on the black ground.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            OmniTheme {
                val gate by viewModel.gate.collectAsStateWithLifecycle()
                if (gate != AuthGate.Loading) OmniNavHost(gate, BuildConfig.VERSION_NAME)
            }
        }
    }
}
