package ke.afyaclaim.pos.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ke.afyaclaim.pos.ui.login.LoginScreen
import ke.afyaclaim.pos.ui.theme.Afya

@Composable
fun AfyaRoot(rootViewModel: RootViewModel) {
    val state by rootViewModel.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(Afya.colors.bg)) {
        AnimatedContent(
            targetState = state::class,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
            label = "root",
        ) { kind ->
            when (kind) {
                SessionUiState.SignedIn::class -> {
                    val session = (state as? SessionUiState.SignedIn)?.session
                    if (session != null) MainShell(session = session, onSignOut = rootViewModel::signOut)
                }
                SessionUiState.SignedOut::class -> LoginScreen()
                else -> Box(Modifier.fillMaxSize())
            }
        }
    }
}
