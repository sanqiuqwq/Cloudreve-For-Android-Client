package com.cra.cloudreve.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.cra.cloudreve.MainActivity
import com.cra.cloudreve.data.transfer.TransferDirection
import com.cra.cloudreve.data.transfer.TransferTask
import com.cra.cloudreve.data.transfer.formatBytes
import com.cra.cloudreve.data.upload.FileUploadManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 前台服务：仅负责「保活 + 展示常驻通知」。
 * 真正的传输跑在下载/上传引擎的协程里，服务通过共享的 StateFlow 观察进度。
 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var downloadManager: FileDownloadManager
    @Inject lateinit var uploadManager: FileUploadManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        startForegroundWithProgress(buildNotification(activeTasks()))

        if (observeJob?.isActive != true) {
            observeJob = scope.launch {
                combine(downloadManager.tasks, uploadManager.tasks) { downloads, uploads ->
                    downloads + uploads
                }.collect { tasks ->
                    val active = tasks.filter { it.isActive }
                    if (active.isEmpty()) {
                        stopForeground()
                        stopSelf()
                    } else {
                        notifyProgress(buildNotification(active))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun activeTasks(): List<TransferTask> =
        (downloadManager.tasks.value + uploadManager.tasks.value).filter { it.isActive }

    private fun startForegroundWithProgress(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun stopForeground() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun notifyProgress(notification: Notification) {
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(active: List<TransferTask>): Notification {
        val hasDownload = active.any { it.direction == TransferDirection.DOWNLOAD }
        val icon = if (hasDownload) {
            android.R.drawable.stat_sys_download
        } else {
            android.R.drawable.stat_sys_upload
        }
        val action = when {
            active.size > 1 -> "正在传输 ${active.size} 个文件"
            active.firstOrNull()?.direction == TransferDirection.UPLOAD -> "正在上传"
            active.firstOrNull()?.direction == TransferDirection.DOWNLOAD -> "正在下载"
            else -> "正在传输"
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(
                if (active.size > 1) action else active.firstOrNull()?.fileName ?: action
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())

        val totalBytes = active.sumOf { if (it.totalBytes > 0) it.totalBytes else 0L }
        val transferred = active.sumOf { it.bytesTransferred }
        if (totalBytes > 0L) {
            val percent = ((transferred * 100) / totalBytes).toInt().coerceIn(0, 100)
            builder.setProgress(100, percent, false)
            // 进度条本身不显示数字，百分比同时写进 subText 与正文，更直观
            builder.setSubText("$percent%")
            builder.setContentText(
                if (active.size > 1) {
                    "$percent%  ·  ${formatBytes(transferred)} / ${formatBytes(totalBytes)}"
                } else {
                    "${if (hasDownload) "已下载" else "已上传"} ${formatBytes(transferred)} / " +
                        "${formatBytes(totalBytes)}  ·  $percent%"
                }
            )
        } else {
            builder.setProgress(0, 0, true)
            builder.setContentText("已传输 ${formatBytes(transferred)}")
        }
        return builder.build()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "文件传输",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示文件上传/下载进度"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "cra_downloads"
        private const val NOTIFICATION_ID = 4101

        /** 沉寂地拉起前台服务；若系统限制后台启动则忽略，传输仍会在应用进程内继续 */
        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }
    }
}