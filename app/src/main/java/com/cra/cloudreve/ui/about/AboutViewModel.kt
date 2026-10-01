package com.cra.cloudreve.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cra.cloudreve.BuildConfig
import com.cra.cloudreve.data.remote.dto.UpdateInfo
import com.cra.cloudreve.data.update.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val updateChecker: UpdateChecker
) : ViewModel() {

    data class UiState(
        val versionName: String = BuildConfig.VERSION_NAME,
        val versionCode: Int = BuildConfig.VERSION_CODE,
        val checking: Boolean = false,
        val update: UpdateInfo? = null,
        val message: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun checkUpdate() {
        if (_uiState.value.checking) return
        _uiState.update { it.copy(checking = true, update = null, message = null) }
        viewModelScope.launch {
            updateChecker.check()
                .onSuccess { info ->
                    val newer = info.isNewerThan(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)
                    _uiState.update {
                        it.copy(
                            checking = false,
                            update = info.takeIf { newer },
                            message = if (newer) null else "已是最新版本"
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(checking = false, message = e.message ?: "检查更新失败")
                    }
                }
        }
    }

    fun dismissUpdate() {
        _uiState.update { it.copy(update = null) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }
}