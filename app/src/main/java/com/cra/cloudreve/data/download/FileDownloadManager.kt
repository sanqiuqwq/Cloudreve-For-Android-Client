package com.cra.cloudreve.data.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.cra.cloudreve.data.local.AppPreferences
import com.cra.cloudreve.data.remote.AuthInterceptor
import com.cra.cloudreve.data.remote.await
import com.cra.cloudreve.data.repository.CloudreveRepository
import com.cra.cloudreve.data.transfer.TransferStatus
import com.cra.cloudreve.data.transfer.TransferTask
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * 应用内下载引擎：自行发起 HTTP 请求、实时汇报进度，并把文件写入系统「下载」目录。
 *
 * 不使用系统 DownloadManager —— 那样会把任务交给系统、且应用内拿不到进度。
 *
 * 所有下载都收敛到「下载/cloudreve」下：普通文件直接放在该目录，
 * 文件夹递归下载则按 cloudreve/<文件夹名>/... 保持原有层级。
 *
 * 下载过程先落到应用缓存目录的 .part 临时文件，完成后再发布到系统「下载」目录。
 * 这样暂停/恢复只需对普通文件做追加写入 + HTTP Range 续传，无需依赖 MediaStore 的追加能力。
 *
 * 同时进行的下载数受 [AppPreferences.downloadConcurrency] 限制，超出的任务进入队列等待。
 */
@Singleton
class FileDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val repository: CloudreveRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 权威任务表，保持插入顺序；对外统一映射为 [TransferTask] */
    private val lock = Any()
    private val internalTasks = LinkedHashMap<String, DownloadTask>()

    /** 已入队但尚未取得并发槽位的任务 id，保持入队顺序 */
    private val pendingQueue = ArrayDeque<String>()

    private val _tasks = MutableStateFlow<List<TransferTask>>(emptyList())
    val tasks: StateFlow<List<TransferTask>> = _tasks.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()

    /** 每个任务对应的 .part 临时文件，暂停时保留、结束后删除 */
    private val tempFiles = ConcurrentHashMap<String, File>()

    /** 正在下载的任务数，受 [maxConcurrent] 限制 */
    private var runningCount = 0
    private var maxConcurrent = AppPreferences.DEFAULT_DOWNLOAD_CONCURRENCY

    init {
        scope.launch {
            repository.preferencesFlow.collect { prefs ->
                synchronized(lock) { maxConcurrent = prefs.downloadConcurrency }
                // 上限调大后立即把等待中的任务放出来
                pump()
            }
        }
    }

    fun enqueue(fileName: String, uri: String, relativeDir: String = DOWNLOAD_ROOT_DIR) {
        val task = DownloadTask(
            id = UUID.randomUUID().toString(),
            fileName = sanitizeName(fileName),
            uri = uri,
            relativeDir = sanitizeDir(relativeDir)
        )
        synchronized(lock) {
            internalTasks[task.id] = task
            pendingQueue.addLast(task.id)
            publishLocked()
        }
        pump()
        DownloadService.start(context)
    }

    /**
     * 递归下载一个文件夹：逐层列出子目录，把其中的文件按相对路径入队，保持原有目录层级。
     * 目标落点为「下载/cloudreve/<folderName>/...」，返回入队的文件数量。
     */
    suspend fun enqueueFolder(folderUri: String, folderName: String): Int {
        val rootDir = "$DOWNLOAD_ROOT_DIR/$folderName"
        var count = 0
        val queue = ArrayDeque<Pair<String, String>>()
        queue.addLast(folderUri to rootDir)
        while (queue.isNotEmpty()) {
            val (uri, dir) = queue.removeFirst()
            val children = repository.listAllChildren(uri).getOrElse { throw it }
            for (child in children) {
                val childUri = child.uriKey()
                if (childUri.isBlank()) continue
                if (child.isFolder) {
                    queue.addLast(childUri to "$dir/${child.name}")
                } else {
                    enqueue(child.name, childUri, dir)
                    count++
                }
            }
        }
        return count
    }

    /** 暂停：中断当前请求，但保留已下载的部分，恢复时续传 */
    fun pause(id: String) {
        val task = findTask(id) ?: return
        if (!task.isActive) return
        val onDisk = tempFiles[id]?.takeIf { it.exists() }?.length()
        synchronized(lock) { pendingQueue.remove(id) }
        mark(id) {
            it.copy(
                status = TransferStatus.PAUSED,
                bytesDownloaded = onDisk ?: it.bytesDownloaded
            )
        }
        jobs.remove(id)?.cancel()
    }

    /** 恢复：从已下载的字节位置继续 */
    fun resume(id: String) {
        val task = findTask(id) ?: return
        if (!task.isPaused) return
        mark(id) { it.copy(status = TransferStatus.PENDING, error = null) }
        synchronized(lock) { pendingQueue.addLast(id) }
        pump()
        DownloadService.start(context)
    }

    /** 重试：丢弃已下载部分，从头开始 */
    fun retry(id: String) {
        val task = findTask(id) ?: return
        if (task.isActive || task.isPaused) return
        tempFiles.remove(id)?.let { runCatching { it.delete() } }
        mark(id) {
            it.copy(
                status = TransferStatus.PENDING,
                bytesDownloaded = 0L,
                totalBytes = -1L,
                savedLocation = null,
                savedUri = null,
                error = null
            )
        }
        synchronized(lock) { pendingQueue.addLast(id) }
        pump()
        DownloadService.start(context)
    }

    fun cancel(id: String) {
        val task = findTask(id) ?: return
        if (!task.isActive && !task.isPaused) return
        synchronized(lock) { pendingQueue.remove(id) }
        mark(id) { it.copy(status = TransferStatus.CANCELED) }
        jobs.remove(id)?.cancel()
        tempFiles.remove(id)?.let { runCatching { it.delete() } }
    }

    /** 移除一条任务记录（进行中的会先取消） */
    fun remove(id: String) {
        synchronized(lock) { pendingQueue.remove(id) }
        jobs.remove(id)?.cancel()
        tempFiles.remove(id)?.let { runCatching { it.delete() } }
        synchronized(lock) {
            internalTasks.remove(id)
            publishLocked()
        }
    }

    /** 清空所有已结束（完成/失败/取消）的任务记录 */
    fun clearFinished() {
        synchronized(lock) {
            internalTasks.entries.removeAll { !(it.value.isActive || it.value.isPaused) }
            publishLocked()
        }
    }

    private fun findTask(id: String): DownloadTask? = synchronized(lock) { internalTasks[id] }

    private fun publishLocked() {
        _tasks.value = internalTasks.values.map { it.toTransferTask() }
    }

    /**
     * 按并发上限尽可能启动等待中的任务。
     * 返回时要么已无空余槽位，要么队列已空。
     */
    private fun pump() {
        while (true) {
            val nextId = synchronized(lock) {
                if (runningCount >= maxConcurrent) return
                var candidate: String? = null
                while (pendingQueue.isNotEmpty()) {
                    val id = pendingQueue.removeFirst()
                    if (internalTasks[id]?.status == TransferStatus.PENDING) {
                        candidate = id
                        break
                    }
                }
                val id = candidate ?: return
                runningCount++
                id
            }
            launchJob(nextId)
        }
    }

    private fun launchJob(id: String) {
        val job = scope.launch { runTask(id) }
        jobs[id] = job
        // 用完成回调而非协程体里的 finally：即使协程在启动前就被取消，也能归还槽位
        job.invokeOnCompletion { onJobFinished(id) }
    }

    private fun onJobFinished(id: String) {
        jobs.remove(id)
        // 只有暂停需要保留临时文件，其余终态都清理掉
        if (findTask(id)?.status != TransferStatus.PAUSED) {
            tempFiles.remove(id)?.let { runCatching { it.delete() } }
        }
        synchronized(lock) { if (runningCount > 0) runningCount-- }
        pump()
    }

    private suspend fun runTask(id: String) {
        val task = findTask(id) ?: return
        mark(id) { it.copy(status = TransferStatus.RUNNING) }
        try {
            val url = repository.getDownloadUrl(task.uri).getOrElse { error ->
                throw IOException(error.message ?: "获取下载链接失败")
            }
            download(task, url)
            mark(id) { it.copy(status = TransferStatus.COMPLETED) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            when {
                // 暂停：已下载的部分保留，等待用户恢复，不记为失败
                findTask(id)?.status == TransferStatus.PAUSED -> Unit
                !coroutineContext.isActive -> throw CancellationException("download canceled")
                else -> mark(id) {
                    it.copy(status = TransferStatus.FAILED, error = e.message ?: "下载失败")
                }
            }
        }
    }

    private suspend fun download(task: DownloadTask, url: String) {
        val temp = tempFiles.getOrPut(task.id) {
            File(tempDir(), "${task.id}.part")
        }
        val existing = if (temp.exists()) temp.length() else 0L

        val requestBuilder = Request.Builder()
            .url(url)
            // 直链无需鉴权，跳过 AuthInterceptor 注入的 token
            .header(AuthInterceptor.HEADER_NO_AUTH, "1")
        if (existing > 0L) {
            requestBuilder.header("Range", "bytes=$existing-")
        }

        val call = okHttpClient.newCall(requestBuilder.build())
        val job = coroutineContext[Job]
        // 通过 await 桥接：暂停/取消/移除发生的瞬间即中断底层请求
        call.await { response ->
            if (response.code != HTTP_OK && response.code != HTTP_PARTIAL) {
                throw IOException("服务器返回 HTTP ${response.code}")
            }
            // 服务端支持 Range 才会返回 206，否则只能从头重下
            val append = existing > 0L && response.code == HTTP_PARTIAL
            val startAt = if (append) existing else 0L
            val body = response.body ?: throw IOException("响应内容为空")
            val remaining = body.contentLength()
            val total = if (remaining > 0L) startAt + remaining else -1L
            mark(task.id) { it.copy(totalBytes = total, bytesDownloaded = startAt) }

            FileOutputStream(temp, append).use { output ->
                copyWithProgress(body.byteStream(), output, task, startAt, job)
            }
        }
        // 已被取消/移除时不要落盘
        coroutineContext.ensureActive()
        val published = publish(temp, task.fileName, task.relativeDir)
        mark(task.id) {
            it.copy(savedLocation = published.location, savedUri = published.uri)
        }
    }

    private fun copyWithProgress(
        input: InputStream,
        output: OutputStream,
        task: DownloadTask,
        startAt: Long,
        job: Job?
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var downloaded = startAt
        var lastEmit = 0L
        while (true) {
            // 兜底：即使 socket 中断不及时，也能尽快退出循环
            job?.ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            downloaded += read
            val now = System.currentTimeMillis()
            if (now - lastEmit >= PROGRESS_INTERVAL_MS) {
                lastEmit = now
                mark(task.id) { it.copy(bytesDownloaded = downloaded) }
            }
        }
        output.flush()
        mark(task.id) { it.copy(bytesDownloaded = downloaded) }
    }

    private fun mark(id: String, transform: (DownloadTask) -> DownloadTask) {
        synchronized(lock) {
            val current = internalTasks[id] ?: return
            internalTasks[id] = transform(current)
            publishLocked()
        }
    }

    private fun tempDir(): File =
        File(context.cacheDir, "downloads").apply { if (!exists()) mkdirs() }

    // ---------------- 发布到系统「下载」目录：Android 10+ 走 MediaStore，更早版本回退到公共目录 ----------------

    /** 落盘结果：可读位置描述 + 可交给系统应用打开的 URI */
    private data class Published(val location: String, val uri: String)

    private fun publish(temp: File, fileName: String, relativeDir: String): Published {
        if (!temp.exists()) throw IOException("临时文件缺失")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishToMediaStore(temp, fileName, relativeDir)
        } else {
            publishToLegacy(temp, fileName, relativeDir)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun publishToMediaStore(temp: File, fileName: String, relativeDir: String): Published {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        // MediaStore 要求相对路径以公共目录名开头，如 Download/cloudreve/子目录
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$relativeDir"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, guessMime(fileName))
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            // 复制完成前标记为 pending，避免其他应用读到半截文件
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri: Uri = resolver.insert(collection, values)
            ?: throw IOException("无法在系统下载目录创建文件")
        try {
            val output = resolver.openOutputStream(uri)
                ?: throw IOException("无法写入系统下载目录")
            output.use { out ->
                temp.inputStream().use { it.copyTo(out) }
            }
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return Published("下载/$relativeDir/$fileName", uri.toString())
    }

    @Suppress("DEPRECATION")
    private fun publishToLegacy(temp: File, fileName: String, relativeDir: String): Published {
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(base, relativeDir)
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("无法创建下载目录")
        }
        val target = uniqueFile(dir, fileName)
        temp.inputStream().use { input ->
            FileOutputStream(target).use { input.copyTo(it) }
        }
        // Android 7+ 直接暴露 file:// 会抛 FileUriExposedException，需经 FileProvider 转换
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            target
        )
        return Published(target.absolutePath, uri.toString())
    }

    private fun uniqueFile(dir: File, name: String): File {
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var candidate = File(dir, name)
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($index)$ext")
            index++
        }
        return candidate
    }

    private fun guessMime(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isBlank()) return DEFAULT_MIME
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: DEFAULT_MIME
    }

    private fun sanitizeName(fileName: String): String =
        sanitizeSegment(fileName).ifBlank { "download" }

    /** 逐段清洗相对目录，保证始终以 [DOWNLOAD_ROOT_DIR] 开头且不含非法字符 */
    private fun sanitizeDir(dir: String): String {
        val segments = dir.split('/').map { sanitizeSegment(it) }.filter { it.isNotBlank() }
        return segments.ifEmpty { listOf(DOWNLOAD_ROOT_DIR) }.joinToString("/")
    }

    private fun sanitizeSegment(segment: String): String =
        segment.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_INTERVAL_MS = 150L
        const val DEFAULT_MIME = "application/octet-stream"
        const val HTTP_OK = 200
        const val HTTP_PARTIAL = 206
    }
}