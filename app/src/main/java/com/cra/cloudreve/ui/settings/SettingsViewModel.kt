package com.cra.cloudreve.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cra.cloudreve.data.local.AppPreferences
import com.cra.cloudreve.data.repository.CloudreveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: CloudreveRepository
) : ViewModel() {

    data class UiState(
        val nickname: String = "",
        val email: String = "",
        val avatar: String = "",
        val serverUrl: String = "",
        val darkMode: Boolean = false,
        val dynamicColor: Boolean = true,
        val downloadConcurrency: Int = AppPreferences.DEFAULT_DOWNLOAD_CONCURRENCY,
        val showLogoutConfirm: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.sessionFlow.collect { session ->
                _uiState.update {
                    it.copy(
                        nickname = session.nickname,
                        email = session.email,
                        avatar = session.avatar,
                        serverUrl = session.serverUrl
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.preferencesFlow.collect { prefs ->
                _uiState.update {
                    it.copy(
                        darkMode = prefs.darkMode ?: false,
                        dynamicColor = prefs.dynamicColor,
                        downloadConcurrency = prefs.downloadConcurrency
                    )
                }
            }
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { repository.setDarkMode(enabled) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.setDynamicColor(enabled) }
    }

    /** 同时下载个数上限，取值范围 1-20 */
    fun setDownloadConcurrency(value: Int) {
        viewModelScope.launch { repository.setDownloadConcurrency(value) }
    }

    fun requestLogout() {
        _uiState.update { it.copy(showLogoutConfirm = true) }
    }

    fun dismissLogout() {
        _uiState.update { it.copy(showLogoutConfirm = false) }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            repository.logout()
            _uiState.update { it.copy(showLogoutConfirm = false) }
            onLoggedOut()
        }
    }
}
