package ke.afyaclaim.pos.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ke.afyaclaim.pos.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = email.contains('@') && password.length >= 6 && !loading
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmail(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, error = null) }

    fun fillDemo() = _state.update { it.copy(email = DEMO_EMAIL, password = DEMO_PASSWORD, error = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            auth.login(current.email, current.password)
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
                .onSuccess { _state.update { it.copy(loading = false, password = "") } }
        }
    }

    companion object {
        // Seeded demo pharmacist from the API's db/seeds.rb, only offered in debug builds.
        const val DEMO_EMAIL = "pharmacist@afyaclaim.test"
        const val DEMO_PASSWORD = "password123"
    }
}
