package com.cra.cloudreve.data.upload

import android.net.Uri
import com.cra.cloudreve.data.transfer.TransferDirection
import com.cra.cloudreve.data.transfer.TransferStatus
import com.cra.cloudreve.data.transfer.TransferTask

/** 上传引擎内部的任务状态，对外统一映射为 [TransferTask] */
internal data class UploadTask(
    val id: String,
    val fileName: String,
    /** 本地待上传文件的 content URI */
    val sourceUri: Uri,
    /** 上传目标的 cloudreve URI（含文件名） */
    val targetUri: String,
    val mimeType: String,
    val size: Long,
    val lastModified: Long,
    val status: TransferStatus = TransferStatus.PENDING,
    val bytesUploaded: Long = 0L,
    val error: String? = null
) {
    val isActive: Boolean
        get() = status == TransferStatus.PENDING || status == TransferStatus.RUNNING
}

internal fun UploadTask.toTransferTask(): TransferTask = TransferTask(
    id = id,
    direction = TransferDirection.UPLOAD,
    fileName = fileName,
    status = status,
    bytesTransferred = bytesUploaded,
    totalBytes = size,
    detail = null,
    error = error
)