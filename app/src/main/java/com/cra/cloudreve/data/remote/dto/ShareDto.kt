package com.cra.cloudreve.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ShareListResponse(
    val shares: List<Share> = emptyList(),
    val pagination: Pagination? = null
)

@Serializable
data class Share(
    val id: String = "",
    val name: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("expires") val expires: String? = null,
    val password: String = "",
    val views: Long = 0,
    @SerialName("downloaded") val downloads: Long = 0,
    @SerialName("file") val file: FileObject? = null,
    @SerialName("source") val source: List<FileObject>? = null
)

/**
 * PUT /api/v4/share，一次只接受一个 uri（多选时逐个调用）；
 * 响应 data 为完整的分享地址字符串。
 */
@Serializable
data class CreateShareRequest(
    val uri: String,
    @SerialName("is_private") val isPrivate: Boolean = false,
    val password: String = "",
    val downloads: Int = 0,
    /** 有效期，单位秒；0 表示永久 */
    val expire: Int = 0,
    @SerialName("share_view") val shareView: Boolean = false,
    @SerialName("show_readme") val showReadme: Boolean = false
)

@Serializable
data class ShareDownloadResponse(
    val url: String = "",
    val expires: String = ""
)
