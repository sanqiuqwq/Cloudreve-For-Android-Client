package com.cra.cloudreve.data.download

import com.cra.cloudreve.data.transfer.TransferDirection
import com.cra.cloudreve.data.transfer.TransferStatus
import com.cra.cloudreve.data.transfer.TransferTask

/**
 * 应用内下载在系统「下载」目录下的固定根目录。
 * 所有下载都收敛到这里，避免与其它应用写入的文件混淆。
 */
internal const val DOWNLOAD_ROOT_DIR = "cloudreve"

/** 下载引擎内部的任务状态，对外统一映射为 [TransferTask] */
internal data class DownloadTask(
    val id: String,
    val fileName: String,
    /** 目标的 cloudreve URI，每次（重新）开始下载时都会用它换取一份新的直链 */
    val uri: String,
    /** 相对「下载」目录的存放路径，例如 cloudreve 或 cloudreve/相册/2024 */
    val relativeDir: String = DOWNLOAD_ROOT_DIR,
    val status: TransferStatus = TransferStatus.PENDING,
    val bytesDownloaded: Long = 0L,
    /** 服务端未返回 Content-Length 时为 -1，代表总大小未知 */
    val totalBytes: Long = -1L,
    /** 落盘位置的可读描述，例如「下载/demo.zip」 */
    val savedLocation: String? = null,
    /** 落盘后可交给系统应用打开的 URI（MediaStore content:// 或 FileProvider URI） */
    val savedUri: String? = null,
    val error: String? = null
) {
    /** 进行中：可以暂停或取消 */
    val isActive: Boolean
        get() = status == TransferStatus.PENDING || status == TransferStatus.RUNNING

    /** 可以恢复 */
    val isPaused: Boolean
        get() = status == TransferStatus.PAUSED
}

internal fun DownloadTask.toTransferTask(): TransferTask = TransferTask(
    id = id,
    direction = TransferDirection.DOWNLOAD,
    fileName = fileName,
    status = status,
    bytesTransferred = bytesDownloaded,
    totalBytes = totalBytes,
    detail = savedLocation,
    localUri = savedUri,
    error = error
)