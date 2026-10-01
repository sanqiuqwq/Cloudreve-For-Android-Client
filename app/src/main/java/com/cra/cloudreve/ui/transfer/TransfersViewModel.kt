package com.cra.cloudreve.ui.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cra.cloudreve.data.download.FileDownloadManager
import com.cra.cloudreve.data.transfer.TransferDirection
import com.cra.cloudreve.data.transfer.TransferTask
import com.cra.cloudreve.data.upload.FileUploadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TransfersViewModel @Inject constructor(
    private val downloadManager: FileDownloadManager,
    private val uploadManager: FileUploadManager
) : ViewModel() {

    /** 下载与上传任务合并展示，下载在上 */
    val tasks: StateFlow<List<TransferTask>> =
        combine(downloadManager.tasks, uploadManager.tasks) { downloads, uploads ->
            downloads + uploads
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun cancel(id: String) = when (directionOf(id)) {
        TransferDirection.UPLOAD -> uploadManager.cancel(id)
        TransferDirection.DOWNLOAD -> downloadManager.cancel(id)
        null -> Unit
    }

    fun pause(id: String) = downloadManager.pause(id)

    fun resume(id: String) = downloadManager.resume(id)

    fun retry(id: String) = when (directionOf(id)) {
        TransferDirection.UPLOAD -> uploadManager.retry(id)
        TransferDirection.DOWNLOAD -> downloadManager.retry(id)
        null -> Unit
    }

    fun remove(id: String) = when (directionOf(id)) {
        TransferDirection.UPLOAD -> uploadManager.remove(id)
        TransferDirection.DOWNLOAD -> downloadManager.remove(id)
        null -> Unit
    }

    fun clearFinished() {
        downloadManager.clearFinished()
        uploadManager.clearFinished()
    }

    private fun directionOf(id: String): TransferDirection? =
        tasks.value.firstOrNull { it.id == id }?.direction
}