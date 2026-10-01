package com.cra.cloudreve.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.cra.cloudreve.ui.files.FilesScreen
import com.cra.cloudreve.ui.settings.SettingsScreen
import com.cra.cloudreve.ui.transfer.TransfersScreen

private data class MainTab(
    val label: String,
    val icon: ImageVector
)

private val tabs = listOf(
    MainTab(label = "文件", icon = Icons.Filled.Folder),
    MainTab(label = "传输", icon = Icons.Filled.SwapVert),
    MainTab(label = "设置", icon = Icons.Filled.Settings)
)

@Composable
fun MainScreen(
    onLoggedOut: () -> Unit,
    onOpenAbout: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Android 13+ 需要通知权限，下载的前台服务通知才能显示
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (tabs[selectedTab].label) {
                "文件" -> FilesScreen()
                "传输" -> TransfersScreen()
                else -> SettingsScreen(
                    onLoggedOut = onLoggedOut,
                    onOpenAbout = onOpenAbout
                )
            }
        }
    }
}