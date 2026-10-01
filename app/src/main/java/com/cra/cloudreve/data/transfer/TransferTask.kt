package com.cra.cloudreve.data.transfer

/** 传输任务的统一状态，下载与上传共用，便于在「传输」页合并展示 */
enum class TransferStatus {
    PENDING,
    RUNNING,

    /** 已暂停（当前仅下载支持暂停/恢复） */
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELED
}

enum class TransferDirection { DOWNLOAD, UPLOAD }

/**
 * 面向 UI/通知的传输任务快照，由下载引擎与上传引擎各自映射后通过 StateFlow 暴露。
 */
data class TransferTask(
    val id: String,
    val direction: TransferDirection,
    val fileName: String,
    val status: TransferStatus = TransferStatus.PENDING,
    val bytesTransferred: Long = 0L,
    /** 总大小未知时为 -1 */
    val totalBytes: Long = -1L,
    /** 附加说明，例如下载完成后的保存位置 */
    val detail: String? = null,
    /** 已完成下载后本地文件的可打开 URI（content:// 或 FileProvider URI），用于交给系统应用打开 */
    val localUri: String? = null,
    val error: String? = null
) {
    val progress: Float
        get() = if (totalBytes > 0L) {
            (bytesTransferred.toFloat() / totalBytes).coerceIn(0f, 1f)
        } else {
            0f
        }

    /** 进行中：可以暂停或取消 */
    val isActive: Boolean
        get() = status == TransferStatus.PENDING || status == TransferStatus.RUNNING

    val isPaused: Boolean
        get() = status == TransferStatus.PAUSED

    val isFinished: Boolean
        get() = !isActive && !isPaused
}

/** 把字节数格式化为 B/KB/MB 等可读文本 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index == 0) "$bytes ${units[index]}" else "%.1f %s".format(value, units[index])
}