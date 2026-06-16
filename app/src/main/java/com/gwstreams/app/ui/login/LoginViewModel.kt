package com.gwstreams.app.ui.login

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gwstreams.app.data.repo.XtreamRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val android.content.Context.dataStore by preferencesDataStore("gw_prefs")

data class LoginUiState(
    val host: String = "",
    val username: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

class LoginViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = XtreamRepository()
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state

    private val kHost = stringPreferencesKey("host")
    private val kUser = stringPreferencesKey("user")
    private val kPass = stringPreferencesKey("pass")

    init {
        viewModelScope.launch {
            val prefs = getApplication<Application>().dataStore.data.first()
            _state.value = _state.value.copy(
                host = prefs[kHost] ?: "",
                username = prefs[kUser] ?: "",
                password = prefs[kPass] ?: ""
            )
        }
    }

    fun onHost(v: String) { _state.value = _state.value.copy(host = v) }
    fun onUser(v: String) { _state.value = _state.value.copy(username = v) }
    fun onPass(v: String) { _state.value = _state.value.copy(password = v) }

    fun login() {
        val s = _state.value
        if (s.host.isBlank() || s.username.isBlank() || s.password.isBlank()) {
            _state.value = s.copy(error = "Fill in host, username and password.")
            return
        }
        _state.value = s.copy(loading = true, error = null)
        viewModelScope.launch {
            val result = repo.login(s.host, s.username, s.password)
            result.fold(
                onSuccess = {
                    persist()
                    _state.value = _state.value.copy(loading = false, success = true)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message ?: "Could not connect. Check the host and your network."
                    )
                }
            )
        }
    }

    private suspend fun persist() {
        val s = _state.value
        getApplication<Application>().dataStore.edit {
            it[kHost] = s.host; it[kUser] = s.username; it[kPass] = s.password
        }
    }
}
