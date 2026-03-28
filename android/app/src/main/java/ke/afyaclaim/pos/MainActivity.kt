package ke.afyaclaim.pos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import ke.afyaclaim.pos.ui.navigation.AfyaRoot
import ke.afyaclaim.pos.ui.navigation.RootViewModel
import ke.afyaclaim.pos.ui.navigation.SessionUiState
import ke.afyaclaim.pos.ui.theme.AfyaTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val rootViewModel: RootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { rootViewModel.state.value is SessionUiState.Loading }
        enableEdgeToEdge()
        setContent {
            AfyaTheme {
                AfyaRoot(rootViewModel)
            }
        }
    }
}
