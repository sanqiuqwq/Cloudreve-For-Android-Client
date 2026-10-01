package com.cra.cloudreve.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FileListResponse(
    val parent: FileObject? = null,
    /** v4 接口返回的文件数组键名是 files，不是 objects */
    val files: List<FileObject> = emptyList(),
    val pagination: Pagination? = null
)

@Serializable
data class Pagination(
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 0,
    @SerialName("total_items") val totalItems: Long = 0L,
    /** 游标分页时下一页的令牌，为空表示没有更多 */
    @SerialName("next_token") val nextToken: String = "",
    @SerialName("is_cursor") val isCursor: Boolean = false
)

@Serializable
data class FileObject(
    val id: String = "",
    val name: String = "",
    val type: Int = 0,
    val size: Long = 0,
    val path: String = "",
    val owned: Boolean = false,
    /** base64 编码的权限位集合 */
    val capability: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    val metadata: Map<String, String>? = null
) {
    val isFolder: Boolean get() = type == TYPE_FOLDER

    /** 接口中用于定位该文件的 cloudreve URI，path 为空时退回 id */
    fun uriKey(): String = path.ifBlank { id }

    companion object {
        const val TYPE_FILE = 0
        const val TYPE_FOLDER = 1
    }
}

@Serializable
data class FileUrlRequest(
    val uris: List<String>,
    val download: Boolean = true,
    @SerialName("skip_error") val skipError: Boolean = false
)

@Serializable
data class FileUrlResponse(
    val urls: List<FileUrlItem> = emptyList(),
    /** 直链的过期时间，RFC3339 字符串 */
    val expires: String = ""
)

@Serializable
data class FileUrlItem(
    val url: String = ""
)

/** PUT /api/v4/file/source，批量获取文件外链（直链） */
@Serializable
data class DirectLinkRequest(
    val uris: List<String>
)

@Serializable
data class DirectLinkItem(
    val link: String = ""
)

/** POST /api/v4/file/create，uri 为「父目录 + 新目录名」拼接后的完整 cloudreve URI */
@Serializable
data class CreateFolderRequest(
    val uri: String,
    val type: String = "folder",
    @SerialName("err_on_conflict") val errOnConflict: Boolean = true
)

/** POST /api/v4/file/rename，重命名后的字段为 new_name */
@Serializable
data class RenameFileRequest(
    val uri: String,
    @SerialName("new_name") val newName: String
)

@Serializable
data class DeleteFilesRequest(
    val uris: List<String>
)

/**
 * POST /api/v4/file/move，把 uris 批量移动（或复制）到目标目录 dst。
 * dst 为目标目录的 cloudreve URI。
 */
@Serializable
data class MoveFilesRequest(
    val uris: List<String>,
    val dst: String,
    val copy: Boolean = false
)
