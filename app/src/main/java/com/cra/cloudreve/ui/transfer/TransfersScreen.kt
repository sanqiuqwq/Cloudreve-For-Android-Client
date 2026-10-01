package com.cra.cloudreve.ui.transfer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cra.cloudreve.data.transfer.TransferDirection
import com.cra.cloudreve.data.transfer.TransferStatus
import com.cra.cloudreve.data.transfer.TransferTask
import com.cra.cloudreve.data.transfer.formatBytes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersScreen(
    modifier: Modifier = Modifier,
    viewModel: TransfersViewModel = hiltViewModel()
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 已完成且已落盘的下载，点击整行交给系统应用打开
    val openTask: (TransferTask) -> Unit = { task ->
        val error = openLocalFile(context, task)
        if (error != null) scope.launch { snackbarHostState.showSnackbar(error) }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("传输") },
                actions = {
                    if (tasks.any { it.isFinished }) {
                        IconButton(onClick = viewModel::clearFinished) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "清除已完成")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (tasks.isEmpty()) {
            EmptyTransfers(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TransferRow(
                        task = task,
                        canOpen = task.status == TransferStatus.COMPLETED &&
                            task.direction == TransferDirection.DOWNLOAD &&
                            task.localUri != null,
                        onOpen = { openTask(task) },
                        onPause = { viewModel.pause(task.id) },
                        onResume = { viewModel.resume(task.id) },
                        onCancel = { viewModel.cancel(task.id) },
                        onRetry = { viewModel.retry(task.id) },
                        onRemove = { viewModel.remove(task.id) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun TransferRow(
    task: TransferTask,
    canOpen: Boolean,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (canOpen) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = statusIcon(task),
                contentDescription = null,
                tint = statusTint(task.status),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = statusText(task),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    task.isActive -> {
                        // 上传暂不支持暂停，仅提供取消
                        if (task.direction == TransferDirection.DOWNLOAD) {
                            IconButton(onClick = onPause) {
                                Icon(Icons.Filled.Pause, contentDescription = "暂停")
                            }
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Filled.Close, contentDescription = "取消")
                        }
                    }
                    task.isPaused -> {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "恢复")
                        }
                        IconButton(onClick = onRemove) {
                            Icon(Icons.Filled.Delete, contentDescription = "移除记录")
                        }
                    }
                    else -> {
                        if (task.status == TransferStatus.FAILED ||
                            task.status == TransferStatus.CANCELED
                        ) {
                            IconButton(onClick = onRetry) {
                                Icon(Icons.Filled.Refresh, contentDescription = "重试")
                            }
                        }
                        IconButton(onClick = onRemove) {
                            Icon(Icons.Filled.Delete, contentDescription = "移除记录")
                        }
                    }
                }
            }
        }

        if (task.isActive || task.isPaused) {
            Spacer(Modifier.height(8.dp))
            if (task.totalBytes > 0L) {
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun EmptyTransfers(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.SwapVert,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "暂无传输任务",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

private fun statusText(task: TransferTask): String = when (task.status) {
    TransferStatus.PENDING -> "等待中"
    TransferStatus.RUNNING -> progressText(task, if (task.direction == TransferDirection.UPLOAD) "上传中" else "下载中")
    TransferStatus.PAUSED -> progressText(task, "已暂停")
    TransferStatus.COMPLETED -> {
        val location = task.detail?.let { "保存到 $it" } ?: "已完成"
        if (task.direction == TransferDirection.DOWNLOAD && task.localUri != null) {
            "$location · 点击打开"
        } else {
            location
        }
    }
    TransferStatus.FAILED -> "失败：${task.error ?: "未知错误"}"
    TransferStatus.CANCELED -> "已取消"
}

private fun progressText(task: TransferTask, prefix: String): String =
    if (task.totalBytes > 0L) {
        val percent = (task.progress * 100).toInt()
        "$prefix $percent% · ${formatBytes(task.bytesTransferred)} / ${formatBytes(task.totalBytes)}"
    } else {
        val verb = if (task.direction == TransferDirection.UPLOAD) "已上传" else "已下载"
        "$prefix · $verb ${formatBytes(task.bytesTransferred)}"
    }

private fun statusIcon(task: TransferTask): ImageVector = when (task.status) {
    TransferStatus.PENDING -> Icons.Filled.Schedule
    TransferStatus.RUNNING -> if (task.direction == TransferDirection.UPLOAD) {
        Icons.Filled.Upload
    } else {
        Icons.Filled.Download
    }
    TransferStatus.PAUSED -> Icons.Filled.Pause
    TransferStatus.COMPLETED -> Icons.Filled.CheckCircle
    TransferStatus.FAILED -> Icons.Filled.ErrorOutline
    TransferStatus.CANCELED -> Icons.Filled.Cancel
}

@Composable
private fun statusTint(status: TransferStatus): Color = when (status) {
    TransferStatus.COMPLETED -> MaterialTheme.colorScheme.primary
    TransferStatus.FAILED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** 用系统应用打开已下载的本地文件；成功返回 null，失败返回提示文案 */
private fun openLocalFile(context: Context, task: TransferTask): String? {
    val uriString = task.localUri ?: return "该文件暂时无法打开"
    return try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriString.toUri(), mimeTypeOf(task.fileName))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        null
    } catch (e: ActivityNotFoundException) {
        "没有能打开“${task.fileName}”的应用"
    } catch (e: Exception) {
        e.message ?: "打开失败"
    }
}

private fun mimeTypeOf(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    if (ext.isBlank()) return "*/*"
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
}