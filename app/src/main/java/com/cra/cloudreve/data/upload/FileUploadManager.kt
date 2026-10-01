package com.cra.cloudreve.data.upload

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.cra.cloudreve.data.download.DownloadService
import com.cra.cloudreve.data.remote.AuthInterceptor
import com.cra.cloudreve.data.remote.await
import com.cra.cloudreve.data.remote.dto.UploadSession
import com.cra.cloudreve.data.repository.CloudreveRepository
import com.cra.cloudreve.data.transfer.TransferStatus
import com.cra.cloudreve.data.transfer.TransferTask
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * 应用内上传引擎：自行创建上传会话、按分片推送并实时汇报进度。
 *
 * 支持两类服务端策略（由会话响应的 upload_urls 是否为空判定）：
 *  - 预签名直传（如 OneDrive）：用 Content-Range 复用一个预签名地址逐片 PUT，最后调用服务端回调收尾；
 *  - 中转分片：把每个分片 POST 给站点，服务端收齐后自动完成。
 */
@Singleton
class FileUploadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val repository: CloudreveRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val lock = Any()
    private val internalTasks = LinkedHashMap<String, UploadTask>()
    private val lastEmit = ConcurrentHashMap<String, Long>()

    private val _tasks = MutableStateFlow<List<TransferTask>>(emptyList())
    val tasks: StateFlow<List<TransferTask>> = _tasks.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()

    /** 每个任务对应的上传会话，取消/失败时用于清理服务端占位文件 */
    private val sessions = ConcurrentHashMap<String, UploadSession>()

    fun enqueue(source: Uri, targetDirUri: String) {
        val meta = queryMeta(source)
        val name = meta.name.ifBlank { "upload" }
        val task = UploadTask(
            id = UUID.randomUUID().toString(),
            fileName = name,
            sourceUri = source,
            targetUri = joinUri(targetDirUri, name),
            mimeType = meta.mimeType,
            size = meta.size,
            lastModified = meta.lastModified
        )
        synchronized(lock) {
            internalTasks[task.id] = task
            publishLocked()
        }
        launch(task)
        DownloadService.start(context)
    }

    fun cancel(id: String) {
        val task = findTask(id) ?: return
        if (!task.isActive) return
        mark(id) { it.copy(status = TransferStatus.CANCELED) }
        jobs.remove(id)?.cancel()
    }

    /** 重试：从头重新创建上传会话 */
    fun retry(id: String) {
        val task = findTask(id) ?: return
        if (task.isActive) return
        val next = task.copy(
            status = TransferStatus.PENDING,
            bytesUploaded = 0L,
            error = null
        )
        mark(id) { next }
        launch(next)
        DownloadService.start(context)
    }

    fun remove(id: String) {
        jobs.remove(id)?.cancel()
        synchronized(lock) {
            internalTasks.remove(id)
            lastEmit.remove(id)
            publishLocked()
        }
    }

    fun clearFinished() {
        synchronized(lock) {
            internalTasks.entries.removeAll { !it.value.isActive }
            publishLocked()
        }
    }

    private fun launch(task: UploadTask) {
        jobs[task.id] = scope.launch {
            mark(task.id) { it.copy(status = TransferStatus.RUNNING) }
            try {
                if (task.size < 0L) throw IOException("无法获取文件大小")
                val session = repository.createUploadSession(
                    uri = task.targetUri,
                    size = task.size,
                    lastModified = task.lastModified,
                    mimeType = task.mimeType
                ).getOrElse { throw IOException(it.message ?: "创建上传会话失败") }
                sessions[task.id] = session
                upload(task, session)
                mark(task.id) { it.copy(status = TransferStatus.COMPLETED, bytesUploaded = task.size) }
            } catch (e: CancellationException) {
                cleanupSession(task.id)
                throw e
            } catch (e: Exception) {
                if (!isActive) {
                    cleanupSession(task.id)
                    throw CancellationException("upload canceled")
                }
                cleanupSession(task.id)
                mark(task.id) {
                    it.copy(status = TransferStatus.FAILED, error = e.message ?: "上传失败")
                }
            } finally {
                jobs.remove(task.id)
                sessions.remove(task.id)
                lastEmit.remove(task.id)
            }
        }
    }

    private suspend fun upload(task: UploadTask, session: UploadSession) {
        val total = task.size
        val presignedUrl = session.uploadUrls.firstOrNull()
        val chunkSize = if (session.chunkSize > 0L) session.chunkSize else total
        val resolver = context.contentResolver
        val input = resolver.openInputStream(task.sourceUri)
            ?: throw IOException("无法读取本地文件")
        input.use { stream ->
            val onProgress: (Long) -> Unit = { read -> addProgress(task.id, read) }
            if (total == 0L) {
                // 空文件：中转上传发送一个 0 长度分片
                if (presignedUrl == null) {
                    repository.uploadChunk(
                        sessionId = session.sessionId,
                        index = 0,
                        body = ChunkRequestBody(stream, 0L, OCTET_STREAM, onProgress)
                    ).getOrElse { throw IOException(it.message ?: "上传分片失败") }
                }
            } else {
                var offset = 0L
                var index = 0
                while (offset < total) {
                    // 取消后不要继续推送后续分片
                    coroutineContext.ensureActive()
                    val length = minOf(chunkSize, total - offset)
                    if (presignedUrl != null) {
                        putChunk(presignedUrl, offset, length, total, stream, onProgress)
                    } else {
                        repository.uploadChunk(
                            sessionId = session.sessionId,
                            index = index,
                            body = ChunkRequestBody(stream, length, OCTET_STREAM, onProgress)
                        ).getOrElse { throw IOException(it.message ?: "上传分片失败") }
                    }
                    offset += length
                    index++
                    mark(task.id) { it.copy(bytesUploaded = offset) }
                }
            }
        }
        // 预签名直传需要额外调用服务端回调完成收尾；中转上传由服务端自动完成
        if (presignedUrl != null) {
            completeOnedrive(session)
        }
    }

    private suspend fun putChunk(
        url: String,
        start: Long,
        length: Long,
        total: Long,
        input: InputStream,
        onProgress: (Long) -> Unit
    ) {
        val end = start + length - 1
        val body = ChunkRequestBody(input, length, null, onProgress)
        val request = Request.Builder()
            .url(url)
            .put(body)
            // 预签名地址不能携带 Authorization，也不能带 Content-Type
            .header(AuthInterceptor.HEADER_NO_AUTH, "1")
            .header("Content-Range", "bytes $start-$end/$total")
            .build()
        val call = okHttpClient.newCall(request)
        call.await { response ->
            if (!response.isSuccessful) {
                throw IOException("直传失败：HTTP ${response.code}")
            }
        }
    }

    private suspend fun completeOnedrive(session: UploadSession) {
        val base = repository.serverUrl()
        if (base.isBlank()) throw IOException("服务器地址缺失")
        val url = "${base.trimEnd('/')}/api/v4/callback/onedrive/" +
            "${session.sessionId}/${session.callbackSecret}"
        val request = Request.Builder()
            .url(url)
            .post(EMPTY_BODY)
            .header(AuthInterceptor.HEADER_NO_AUTH, "1")
            .build()
        val call = okHttpClient.newCall(request)
        call.await { response ->
            if (!response.isSuccessful) {
                throw IOException("完成上传失败：HTTP ${response.code}")
            }
        }
    }

    /** 取消/失败时尽力删除服务端上传会话（占位文件），网络失败忽略 */
    private suspend fun cleanupSession(taskId: String) {
        val session = sessions.remove(taskId) ?: return
        withContext(NonCancellable) {
            runCatching { repository.deleteUploadSession(session.sessionId, session.uri) }
        }
    }

    private fun addProgress(id: String, delta: Long) {
        synchronized(lock) {
            val current = internalTasks[id] ?: return
            internalTasks[id] = current.copy(bytesUploaded = current.bytesUploaded + delta)
            val now = System.currentTimeMillis()
            val last = lastEmit[id] ?: 0L
            if (now - last >= PROGRESS_INTERVAL_MS) {
                lastEmit[id] = now
                publishLocked()
            }
        }
    }

    private fun mark(id: String, transform: (UploadTask) -> UploadTask) {
        synchronized(lock) {
            val current = internalTasks[id] ?: return
            internalTasks[id] = transform(current)
            publishLocked()
        }
    }

    private fun findTask(id: String): UploadTask? = synchronized(lock) { internalTasks[id] }

    private fun publishLocked() {
        _tasks.value = internalTasks.values.map { it.toTransferTask() }
    }

    private data class LocalFileMeta(
        val name: String,
        val size: Long,
        val mimeType: String,
        val lastModified: Long
    )

    private fun queryMeta(uri: Uri): LocalFileMeta {
        val resolver = context.contentResolver
        var name = ""
        var size = -1L
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) cursor.getString(nameIndex)?.let { name = it }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }
        if (size < 0L) {
            size = runCatching {
                resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            }.getOrDefault(-1L)
        }
        val mime = resolver.getType(uri) ?: DEFAULT_MIME
        if (name.isBlank()) {
            name = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
        }
        return LocalFileMeta(
            name = name,
            size = size,
            mimeType = mime,
            lastModified = System.currentTimeMillis()
        )
    }

    private fun joinUri(parent: String, name: String): String =
        parent.trimEnd('/') + "/" + name.trimStart('/')

    /** 从共享输入流中顺序读取固定长度的一段并写入请求体，避免整片载入内存 */
    private class ChunkRequestBody(
        private val input: InputStream,
        private val length: Long,
        private val mediaType: MediaType?,
        private val onProgress: (Long) -> Unit
    ) : RequestBody() {

        override fun contentType(): MediaType? = mediaType

        override fun contentLength(): Long = length

        // 输入流不可回退，禁止 OkHttp 重试
        override fun isOneShot(): Boolean = true

        override fun writeTo(sink: BufferedSink) {
            val buffer = ByteArray(BUFFER_SIZE)
            var remaining = length
            while (remaining > 0L) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val read = input.read(buffer, 0, toRead)
                if (read < 0) throw IOException("文件内容提前结束")
                sink.write(buffer, 0, read)
                remaining -= read
                onProgress(read.toLong())
            }
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_INTERVAL_MS = 150L
        const val DEFAULT_MIME = "application/octet-stream"
        val OCTET_STREAM: MediaType = DEFAULT_MIME.toMediaType()
        val EMPTY_BODY: RequestBody = ByteArray(0).toRequestBody(null)
    }
}