package id.sch.mts241alikhlash.cbt.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.sch.mts241alikhlash.cbt.data.auth.AuthRepository
import id.sch.mts241alikhlash.cbt.data.auth.AuthUser
import id.sch.mts241alikhlash.cbt.data.auth.ROLE_STUDENT
import id.sch.mts241alikhlash.cbt.data.remote.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(val loading: Boolean = false, val error: String? = null)

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _loggedIn = MutableStateFlow<AuthUser?>(null)
    val loggedIn: StateFlow<AuthUser?> = _loggedIn.asStateFlow()

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun consumed() {
        _loggedIn.value = null
    }

    fun submit(identifier: String, password: String) {
        if (_state.value.loading) return
        if (identifier.isBlank() || password.isEmpty()) {
            _state.value = LoginUiState(error = "Akun dan kata sandi wajib diisi")
            return
        }
        _state.value = LoginUiState(loading = true)
        viewModelScope.launch {
            try {
                val user = auth.login(identifier, password, ROLE_STUDENT)
                _state.value = LoginUiState()
                _loggedIn.value = user
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _state.value = LoginUiState(error = error.userMessage())
            }
        }
    }
}
