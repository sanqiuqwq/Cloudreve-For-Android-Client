package com.cra.cloudreve.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cra.cloudreve.BuildConfig
import com.cra.cloudreve.data.local.AppPreferences
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onLoggedOut: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = state.nickname.ifBlank { "未登录" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (state.email.isNotBlank()) {
                        Text(
                            text = state.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(text = "服务器地址") },
                supportingContent = {
                    Text(
                        text = state.serverUrl.ifBlank { "未设置" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(text = "深色模式") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.DarkMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    Switch(
                        checked = state.darkMode,
                        onCheckedChange = { viewModel.setDarkMode(it) }
                    )
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(text = "动态取色") },
                supportingContent = {
                    Text(
                        text = "根据壁纸自动生成主题配色",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    Switch(
                        checked = state.dynamicColor,
                        onCheckedChange = { viewModel.setDynamicColor(it) }
                    )
                }
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(text = "同时下载个数") },
                supportingContent = {
                    Text(
                        text = "范围 1-20，默认 4；超出上限的下载会排队等待",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    Text(
                        text = "${state.downloadConcurrency}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            )
            Slider(
                value = state.downloadConcurrency.toFloat(),
                onValueChange = { viewModel.setDownloadConcurrency(it.roundToInt()) },
                valueRange = AppPreferences.MIN_DOWNLOAD_CONCURRENCY.toFloat()..
                    AppPreferences.MAX_DOWNLOAD_CONCURRENCY.toFloat(),
                steps = AppPreferences.MAX_DOWNLOAD_CONCURRENCY -
                    AppPreferences.MIN_DOWNLOAD_CONCURRENCY - 1,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(text = "关于") },
                supportingContent = {
                    Text(
                        text = "版本 ${BuildConfig.VERSION_NAME}、开源地址与开发者信息",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAbout)
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(text = "退出登录") },
                supportingContent = {
                    Text(
                        text = "退出后需要重新登录",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                trailingContent = {
                    TextButton(onClick = { viewModel.requestLogout() }) {
                        Text(text = "退出登录")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.size(8.dp))
    }

    if (state.showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLogout() },
            title = { Text(text = "退出登录") },
            text = { Text(text = "确定要退出当前账号吗？") },
            confirmButton = {
                TextButton(onClick = { viewModel.logout(onLoggedOut) }) {
                    Text(text = "确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissLogout() }) {
                    Text(text = "取消")
                }
            }
        )
    }
}
