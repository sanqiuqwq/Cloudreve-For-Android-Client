package com.cra.cloudreve.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PUT /api/v4/file/upload —— 创建上传会话。
 * uri 为「父目录 + 文件名」拼接后的完整 cloudreve URI，last_modified 为毫秒时间戳。
 */
@Serializable
data class CreateUploadSessionRequest(
    val uri: String,
    val size: Long,
    @SerialName("last_modified") val lastModified: Long,
    @SerialName("mime_type") val mimeType: String = "application/octet-stream",
    @SerialName("policy_id") val policyId: Int? = null
)

@Serializable
data class UploadSession(
    @SerialName("session_id") val sessionId: String = "",
    @SerialName("upload_id") val uploadId: String = "",
    /** 分片大小，0 表示不分片（单次上传） */
    @SerialName("chunk_size") val chunkSize: Long = 0,
    /** 过期时间，unix 秒 */
    val expires: Long = 0,
    /** 预签名直传地址（如 OneDrive）。为空则走中转分片上传 */
    @SerialName("upload_urls") val uploadUrls: List<String> = emptyList(),
    @SerialName("storage_policy") val storagePolicy: StoragePolicy? = null,
    val uri: String = "",
    @SerialName("callback_secret") val callbackSecret: String = "",
    @SerialName("mime_type") val mimeType: String = ""
)

@Serializable
data class StoragePolicy(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    @SerialName("max_size") val maxSize: Long = 0
)

/** DELETE /api/v4/file/upload —— 取消上传会话并清理占位文件 */
@Serializable
data class DeleteUploadSessionRequest(
    val id: String,
    val uri: String
)