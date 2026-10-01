package com.cra.cloudreve.ui.files

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cra.cloudreve.data.download.FileDownloadManager
import com.cra.cloudreve.data.remote.api.CloudreveApi
import com.cra.cloudreve.data.remote.dto.FileObject
import com.cra.cloudreve.data.repository.CloudreveRepository
import com.cra.cloudreve.data.transfer.TransferStatus
import com.cra.cloudreve.data.upload.FileUploadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FileSortOption(val label: String, val orderBy: String, val direction: String) {
    NAME_ASC("名称升序", "name", "asc"),
    NAME_DESC("名称降序", "name", "desc"),
    SIZE_ASC("大小升序", "size", "asc"),
    SIZE_DESC("大小降序", "size", "desc"),
    TIME_DESC("修改时间降序", "updated_at", "desc"),
    TIME_ASC("修改时间升序", "updated_at", "asc")
}

data class Crumb(val name: String, val uri: String)

/** 分享链接 / 直链的展示结果 */
data class LinkResult(val title: String, val content: String)

/**
 * 创建分享时的可选限制，各字段取默认值即表示不开启该限制。
 *
 * @param password 访问密码，空串表示不加密
 * @param expireSeconds 有效期（秒），0 表示永久有效
 * @param downloadLimit 可下载次数，0 表示不限次数
 */
data class ShareOptions(
    val password: String = "",
    val expireSeconds: Int = 0,
    val downloadLimit: Int = 0
)

/** 批量移动时的目标目录浏览状态 */
data class MoveDialogState(
    val browseUri: String = CloudreveApi.ROOT_URI,
    val crumbs: List<Crumb> = listOf(Crumb("我的文件", CloudreveApi.ROOT_URI)),
    val folders: List<FileObject> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val repository: CloudreveRepository,
    private val downloadManager: FileDownloadManager,
    private val uploadManager: FileUploadManager
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val currentUri: String = CloudreveApi.ROOT_URI,
        val crumbs: List<Crumb> = listOf(Crumb("我的文件", CloudreveApi.ROOT_URI)),
        val files: List<FileObject> = emptyList(),
        val sort: FileSortOption = FileSortOption.NAME_ASC,
        /** 多选模式下已选中的文件 cloudreve URI 集合 */
        val selection: Set<String> = emptySet(),
        /** 分享链接 / 直链的结果弹窗 */
        val linkResult: LinkResult? = null,
        /** 批量移动的目录浏览弹窗，非空即表示正在选择目标目录 */
        val moveDialog: MoveDialogState? = null,
        val errorMessage: String? = null,
        val infoMessage: String? = null
    ) {
        val isEmpty: Boolean
            get() = !loading && !refreshing && files.isEmpty()

        val selectionMode: Boolean
            get() = selection.isNotEmpty()

        /** 当前浏览的目标目录不能是待移动对象本身 */
        val canMoveHere: Boolean
            get() = moveDialog?.let { it.browseUri !in selection } ?: false
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 已完成过的上传任务，避免重复触发刷新 */
    private val completedUploads = mutableSetOf<String>()

    init {
        load()
        viewModelScope.launch {
            uploadManager.tasks.collect { tasks ->
                val finished = tasks.filter { it.status == TransferStatus.COMPLETED }
                val newlyDone = finished.filter { completedUploads.add(it.id) }
                if (newlyDone.isNotEmpty()) load(refreshing = true)
            }
        }
    }

    fun refresh() {
        load(refreshing = true)
    }

    fun retry() {
        load()
    }

    fun openFolder(folder: FileObject) {
        if (!folder.isFolder) return
        val uri = folder.path
        if (uri.isBlank()) return
        val current = _uiState.value
        _uiState.update {
            it.copy(
                currentUri = uri,
                crumbs = current.crumbs + Crumb(folder.name, uri),
                files = emptyList(),
                selection = emptySet()
            )
        }
        load()
    }

    fun navigateToCrumb(index: Int) {
        val state = _uiState.value
        if (index < 0 || index >= state.crumbs.size - 1) return
        val crumb = state.crumbs[index]
        _uiState.update {
            it.copy(
                currentUri = crumb.uri,
                crumbs = state.crumbs.subList(0, index + 1).toList(),
                files = emptyList(),
                selection = emptySet()
            )
        }
        load()
    }

    fun navigateUp(): Boolean {
        val state = _uiState.value
        if (state.crumbs.size <= 1) return false
        navigateToCrumb(state.crumbs.size - 2)
        return true
    }

    fun changeSort(option: FileSortOption) {
        if (_uiState.value.sort == option) return
        _uiState.update { it.copy(sort = option) }
        load()
    }

    /** 多选模式：点按切换某个文件/文件夹的选中状态 */
    fun toggleSelection(file: FileObject) {
        val uri = file.uriKey()
        if (uri.isBlank()) return
        _uiState.update { state ->
            val next = if (uri in state.selection) {
                state.selection - uri
            } else {
                state.selection + uri
            }
            state.copy(selection = next)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selection = emptySet()) }
    }

    fun dismissLinkResult() {
        _uiState.update { it.copy(linkResult = null) }
    }

    /** 为单个文件/文件夹创建 Cloudreve 分享链接（一个分享只能绑定一个 uri） */
    fun share(file: FileObject, options: ShareOptions) {
        val uri = file.uriKey()
        if (uri.isBlank()) return
        viewModelScope.launch {
            repository.createShare(
                uri = uri,
                password = options.password,
                expireSeconds = options.expireSeconds,
                downloadLimit = options.downloadLimit
            ).fold(
                onSuccess = { url ->
                    val content = if (options.password.isNotEmpty()) {
                        "链接：$url\n密码：${options.password}"
                    } else {
                        url
                    }
                    _uiState.update {
                        it.copy(linkResult = LinkResult(title = "分享链接", content = content))
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(errorMessage = error.message ?: "创建分享失败") }
                }
            )
        }
    }

    /** 获取单个文件的 Cloudreve 直链 */
    fun getDirectLink(file: FileObject) {
        if (file.isFolder) {
            _uiState.update { it.copy(infoMessage = "文件夹不支持获取直链") }
            return
        }
        val uri = file.uriKey()
        if (uri.isBlank()) return
        viewModelScope.launch {
            repository.getDirectLink(uri).fold(
                onSuccess = { link ->
                    _uiState.update {
                        it.copy(linkResult = LinkResult(title = "直链", content = link))
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(errorMessage = error.message ?: "获取直链失败") }
                }
            )
        }
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(errorMessage = "文件夹名称不能为空") }
            return
        }
        viewModelScope.launch {
            repository.createFolder(_uiState.value.currentUri, trimmed).fold(
                onSuccess = {
                    _uiState.update { it.copy(infoMessage = "已新建文件夹") }
                    load()
                },
                onFailure = { error ->
                    _uiState.update { it.copy(errorMessage = error.message ?: "新建文件夹失败") }
                }
            )
        }
    }

    fun rename(file: FileObject, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(errorMessage = "名称不能为空") }
            return
        }
        val uri = file.path
        if (uri.isBlank()) return
        viewModelScope.launch {
            repository.rename(uri, trimmed).fold(
                onSuccess = {
                    _uiState.update { it.copy(infoMessage = "已重命名") }
                    load()
                },
                onFailure = { error ->
                    _uiState.update { it.copy(errorMessage = error.message ?: "重命名失败") }
                }
            )
        }
    }

    /** 交给应用内下载引擎，直链由引擎在开始时现取；进度会出现在「传输」页 */
    fun download(file: FileObject) {
        val uri = file.uriKey()
        if (uri.isBlank()) return
        if (!file.isFolder) {
            downloadManager.enqueue(file.name, uri)
            _uiState.update { it.copy(infoMessage = "已开始下载：${file.name}") }
            return
        }
        // 文件夹：递归列出全部文件后按原有目录层级入队
        viewModelScope.launch {
            _uiState.update { it.copy(infoMessage = "正在获取「${file.name}」的内容…") }
            runCatching { downloadManager.enqueueFolder(uri, file.name) }.fold(
                onSuccess = { count ->
                    _uiState.update {
                        it.copy(
                            infoMessage = if (count == 0) {
                                "「${file.name}」中没有可下载的文件"
                            } else {
                                "已加入下载队列：$count 个文件"
                            }
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(errorMessage = error.message ?: "文件夹下载失败") }
                }
            )
        }
    }

    /** 批量下载：文件夹递归展开，文件直接入队 */
    fun downloadSelection() {
        val state = _uiState.value
        val selected = state.files.filter { it.uriKey() in state.selection }
        if (selected.isEmpty()) return
        clearSelection()
        viewModelScope.launch {
            var fileCount = 0
            var failedFolders = 0
            for (item in selected) {
                val uri = item.uriKey()
                if (uri.isBlank()) continue
                if (item.isFolder) {
                    runCatching { downloadManager.enqueueFolder(uri, item.name) }
                        .onSuccess { fileCount += it }
                        .onFailure { failedFolders++ }
                } else {
                    downloadManager.enqueue(item.name, uri)
                    fileCount++
                }
            }
            val suffix = if (failedFolders > 0) "，$failedFolders 个文件夹读取失败" else ""
            _uiState.update {
                it.copy(infoMessage = "已加入下载队列：$fileCount 个文件$suffix")
            }
        }
    }

    /** 交给应用内上传引擎，上传到当前目录；进度会出现在「传输」页 */
    fun upload(source: Uri) {
        uploadManager.enqueue(source, _uiState.value.currentUri)
        _uiState.update { it.copy(infoMessage = "已开始上传") }
    }

    fun delete(file: FileObject) {
        val uri = file.path.ifBlank { file.id }
        if (uri.isBlank()) return
        viewModelScope.launch {
            repository.delete(listOf(uri)).fold(
                onSuccess = {
                    _uiState.update { it.copy(infoMessage = "已删除") }
                    load()
                },
                onFailure = { error ->
                    _uiState.update { it.copy(errorMessage = error.message ?: "删除失败") }
                }
            )
        }
    }

    /** 批量删除当前选中的文件/文件夹 */
    fun deleteSelection() {
        val uris = _uiState.value.selection.toList()
        if (uris.isEmpty()) return
        viewModelScope.launch {
            repository.delete(uris).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(selection = emptySet(), infoMessage = "已删除 ${uris.size} 项")
                    }
                    load(refreshing = true)
                },
                onFailure = { error ->
                    _uiState.update { it.copy(errorMessage = error.message ?: "删除失败") }
                }
            )
        }
    }

    // ---------------- 批量移动：弹窗内浏览目标目录 ----------------

    /** 打开移动弹窗，并从根目录开始浏览 */
    fun startMove() {
        if (_uiState.value.selection.isEmpty()) return
        _uiState.update { it.copy(moveDialog = MoveDialogState()) }
        loadMoveFolders()
    }

    fun dismissMove() {
        _uiState.update { it.copy(moveDialog = null) }
    }

    /** 进入弹窗中的某个子目录 */
    fun enterMoveFolder(folder: FileObject) {
        val uri = folder.uriKey()
        if (uri.isBlank()) return
        _uiState.update { state ->
            val dialog = state.moveDialog ?: return@update state
            state.copy(
                moveDialog = dialog.copy(
                    browseUri = uri,
                    crumbs = dialog.crumbs + Crumb(folder.name, uri),
                    folders = emptyList()
                )
            )
        }
        loadMoveFolders()
    }

    /** 返回上一级目录 */
    fun navigateMoveUp() {
        _uiState.update { state ->
            val dialog = state.moveDialog ?: return@update state
            if (dialog.crumbs.size <= 1) return@update state
            state.copy(
                moveDialog = dialog.copy(
                    browseUri = dialog.crumbs[dialog.crumbs.size - 2].uri,
                    crumbs = dialog.crumbs.subList(0, dialog.crumbs.size - 1).toList(),
                    folders = emptyList()
                )
            )
        }
        loadMoveFolders()
    }

    /** 跳转到面包屑中的某一级 */
    fun jumpMoveCrumb(index: Int) {
        _uiState.update { state ->
            val dialog = state.moveDialog ?: return@update state
            if (index < 0 || index >= dialog.crumbs.size - 1) return@update state
            state.copy(
                moveDialog = dialog.copy(
                    browseUri = dialog.crumbs[index].uri,
                    crumbs = dialog.crumbs.subList(0, index + 1).toList(),
                    folders = emptyList()
                )
            )
        }
        loadMoveFolders()
    }

    /** 把选中的条目移动到弹窗当前所在目录 */
    fun confirmMove() {
        val state = _uiState.value
        val dialog = state.moveDialog ?: return
        val uris = state.selection.toList()
        if (uris.isEmpty()) return
        if (dialog.browseUri in state.selection) {
            _uiState.update { it.copy(errorMessage = "不能移动到自身") }
            return
        }
        viewModelScope.launch {
            repository.move(uris, dialog.browseUri).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            selection = emptySet(),
                            moveDialog = null,
                            infoMessage = "已移动 ${uris.size} 项"
                        )
                    }
                    load(refreshing = true)
                },
                onFailure = { error ->
                    _uiState.update { it.copy(errorMessage = error.message ?: "移动失败") }
                }
            )
        }
    }

    /** 读取当前浏览目录下的子文件夹（只展示文件夹作为移动目标） */
    private fun loadMoveFolders() {
        val browseUri = _uiState.value.moveDialog?.browseUri ?: return
        _uiState.update { state ->
            state.copy(moveDialog = state.moveDialog?.copy(loading = true, error = null))
        }
        viewModelScope.launch {
            repository.listAllChildren(browseUri).fold(
                onSuccess = { children ->
                    _uiState.update { state ->
                        state.copy(
                            moveDialog = state.moveDialog?.copy(
                                loading = false,
                                folders = children.filter { it.isFolder }
                            )
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { state ->
                        state.copy(
                            moveDialog = state.moveDialog?.copy(
                                loading = false,
                                error = error.message ?: "无法读取目录"
                            )
                        )
                    }
                }
            )
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    private fun load(refreshing: Boolean = false) {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                loading = !refreshing && it.files.isEmpty(),
                refreshing = refreshing,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            repository.listFiles(
                uri = state.currentUri,
                orderBy = state.sort.orderBy,
                orderDirection = state.sort.direction
            ).fold(
                onSuccess = { response ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            files = response.files.sortedWith(comparator())
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            errorMessage = error.message ?: "加载失败"
                        )
                    }
                }
            )
        }
    }

    private fun comparator(): Comparator<FileObject> {
        val sort = _uiState.value.sort
        val base = compareByDescending<FileObject> { it.isFolder }
        val nameComparator = compareBy<FileObject> { it.name.lowercase() }
        val sizeComparator = compareBy<FileObject> { it.size }
        val timeComparator = compareBy<FileObject> { it.updatedAt }
        val selected = when (sort.orderBy) {
            "size" -> sizeComparator
            "updated_at" -> timeComparator
            else -> nameComparator
        }
        val ordered = if (sort.direction == "desc") selected.reversed() else selected
        return base.then(ordered)
    }
}