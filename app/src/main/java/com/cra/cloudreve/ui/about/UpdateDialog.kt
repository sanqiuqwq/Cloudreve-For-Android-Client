package com.cra.cloudreve.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cra.cloudreve.data.remote.dto.UpdateInfo

/** 发现新版本时的提示弹窗；[UpdateInfo.forceUpdate] 为真时不提供关闭入口 */
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    currentVersion: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!info.forceUpdate) onDismiss() },
        title = { Text(text = "发现新版本 ${info.versionName}") },
        text = {
            Column {
                Text(
                    text = "当前版本 $currentVersion",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (info.changelog.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = info.changelog)
                }
                if (info.forceUpdate) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "该版本为强制更新，请更新后继续使用",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(text = "前往更新") }
        },
        dismissButton = {
            if (!info.forceUpdate) {
                TextButton(onClick = onDismiss) { Text(text = "以后再说") }
            }
        }
    )
}