package ke.afyaclaim.pos.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ke.afyaclaim.pos.data.repository.AuthRepository
import ke.afyaclaim.pos.domain.model.Session
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SessionUiState {
    data object Loading : SessionUiState
    data object SignedOut : SessionUiState
    data class SignedIn(val session: Session) : SessionUiState
}

@HiltViewModel
class RootViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {
    val state: StateFlow<SessionUiState> = auth.session
        .map { if (it == null) SessionUiState.SignedOut else SessionUiState.SignedIn(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionUiState.Loading)

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }
}
