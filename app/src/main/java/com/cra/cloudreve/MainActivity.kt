package com.cra.cloudreve

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cra.cloudreve.data.local.AppPreferences
import com.cra.cloudreve.data.local.Session
import com.cra.cloudreve.data.remote.dto.UpdateInfo
import com.cra.cloudreve.data.repository.CloudreveRepository
import com.cra.cloudreve.data.update.UpdateChecker
import com.cra.cloudreve.ui.about.AboutInfo
import com.cra.cloudreve.ui.about.UpdateDialog
import com.cra.cloudreve.ui.about.openUrl
import com.cra.cloudreve.ui.navigation.CraNavHost
import com.cra.cloudreve.ui.navigation.Routes
import com.cra.cloudreve.ui.theme.CraTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CraRoot()
        }
    }
}

@HiltViewModel
class RootViewModel @Inject constructor(
    repository: CloudreveRepository,
    updateChecker: UpdateChecker
) : ViewModel() {

    data class RootState(
        val session: Session = Session(),
        val preferences: AppPreferences = AppPreferences(),
        val ready: Boolean = false
    )

    val state: StateFlow<RootState> = combine(
        repository.sessionFlow,
        repository.preferencesFlow
    ) { session, preferences ->
        RootState(session = session, preferences = preferences, ready = true)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RootState()
    )

    private val _pendingUpdate = MutableStateFlow<UpdateInfo?>(null)
    val pendingUpdate: StateFlow<UpdateInfo?> = _pendingUpdate.asStateFlow()

    init {
        // 应用启动时自动检查一次，发现新版本才提示
        viewModelScope.launch {
            updateChecker.check().onSuccess { info ->
                if (info.isNewerThan(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)) {
                    _pendingUpdate.value = info
                }
            }
        }
    }

    fun dismissUpdate() {
        _pendingUpdate.value = null
    }
}

@Composable
fun CraRoot(viewModel: RootViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingUpdate by viewModel.pendingUpdate.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (!state.ready) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val darkTheme = state.preferences.darkMode ?: isSystemInDarkTheme()

    CraTheme(
        darkTheme = darkTheme,
        dynamicColor = state.preferences.dynamicColor
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            CraNavHost(
                startDestination = if (state.session.isLoggedIn) Routes.MAIN else Routes.LOGIN
            )
        }

        pendingUpdate?.let { info ->
            UpdateDialog(
                info = info,
                currentVersion = BuildConfig.VERSION_NAME,
                onDismiss = { viewModel.dismissUpdate() },
                onConfirm = {
                    viewModel.dismissUpdate()
                    openUrl(context, info.downloadUrl.ifBlank { AboutInfo.REPO_URL })
                }
            )
        }
    }
}
