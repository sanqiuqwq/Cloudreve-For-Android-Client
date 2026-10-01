package com.cra.cloudreve.data.repository

import com.cra.cloudreve.data.local.SessionStore
import com.cra.cloudreve.data.remote.CraException
import com.cra.cloudreve.data.remote.api.CloudreveApi
import com.cra.cloudreve.data.remote.dto.Captcha
import com.cra.cloudreve.data.remote.dto.CreateFolderRequest
import com.cra.cloudreve.data.remote.dto.CreateShareRequest
import com.cra.cloudreve.data.remote.dto.CreateUploadSessionRequest
import com.cra.cloudreve.data.remote.dto.DeleteFilesRequest
import com.cra.cloudreve.data.remote.dto.DeleteUploadSessionRequest
import com.cra.cloudreve.data.remote.dto.DirectLinkRequest
import com.cra.cloudreve.data.remote.dto.FileListResponse
import com.cra.cloudreve.data.remote.dto.FileObject
import com.cra.cloudreve.data.remote.dto.FileUrlRequest
import com.cra.cloudreve.data.remote.dto.LoginConfig
import com.cra.cloudreve.data.remote.dto.LoginRequest
import com.cra.cloudreve.data.remote.dto.MoveFilesRequest
import com.cra.cloudreve.data.remote.dto.RenameFileRequest
import com.cra.cloudreve.data.remote.dto.SiteConfig
import com.cra.cloudreve.data.remote.dto.UploadSession
import com.cra.cloudreve.data.remote.dto.User
import com.cra.cloudreve.data.remote.requireData
import com.cra.cloudreve.data.remote.requireSuccess
import com.cra.cloudreve.data.remote.toCraException
import kotlinx.coroutines.flow.Flow
import okhttp3.RequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudreveRepository @Inject constructor(
    private val api: CloudreveApi,
    private val sessionStore: SessionStore
) {

    val sessionFlow: Flow<com.cra.cloudreve.data.local.Session> = sessionStore.sessionFlow
    val preferencesFlow: Flow<com.cra.cloudreve.data.local.AppPreferences> = sessionStore.preferencesFlow

    suspend fun saveServer(url: String) = sessionStore.saveServer(url)
    suspend fun setDarkMode(enabled: Boolean?) = sessionStore.setDarkMode(enabled)
    suspend fun setDynamicColor(enabled: Boolean) = sessionStore.setDynamicColor(enabled)
    suspend fun setDownloadConcurrency(value: Int) = sessionStore.setDownloadConcurrency(value)

    suspend fun getSiteConfig(serverUrl: String? = null): Result<SiteConfig> = runCatching {
        if (!serverUrl.isNullOrBlank()) sessionStore.saveServer(serverUrl)
        api.getSiteConfig().requireData()
    }.mapFailure()

    /** 读取服务器登录策略，用于判断是否需要验证码 */
    suspend fun getLoginConfig(): Result<LoginConfig> = runCatching {
        api.getLoginConfig().requireData()
    }.mapFailure()

    /** 获取图形验证码，image 为 base64 data URI，ticket 需在登录时一并提交 */
    suspend fun getCaptcha(): Result<Captcha> = runCatching {
        api.getCaptcha().requireData()
    }.mapFailure()

    suspend fun login(
        serverUrl: String,
        email: String,
        password: String,
        captcha: String? = null,
        ticket: String? = null
    ): Result<User> = runCatching {
        sessionStore.saveServer(serverUrl)
        val response = api.login(
            LoginRequest(email = email, password = password, captcha = captcha, ticket = ticket)
        ).requireData()
        val token = response.token?.accessToken.orEmpty()
        if (token.isBlank()) throw CraException.Unexpected("登录响应缺少 access_token")
        val user = response.user
        sessionStore.saveLogin(
            token = token,
            userId = user?.id.orEmpty(),
            email = user?.email ?: email,
            nickname = user?.nickname.orEmpty(),
            avatar = user?.avatar.orEmpty()
        )
        user ?: User(email = email)
    }.mapFailure()

    suspend fun refreshProfile(): Result<User> = runCatching {
        val user = api.getProfile().requireData()
        sessionStore.updateProfile(
            userId = user.id,
            email = user.email,
            nickname = user.nickname,
            avatar = user.avatar
        )
        user
    }.mapFailure()

    suspend fun logout(): Result<Unit> = runCatching {
        runCatching { api.logout().requireSuccess() }
        sessionStore.clearToken()
    }.mapFailure()

    suspend fun listFiles(
        uri: String,
        page: Int = 1,
        pageSize: Int = 200,
        orderBy: String = "name",
        orderDirection: String = "asc"
    ): Result<FileListResponse> = runCatching {
        api.listFiles(uri, page, pageSize, orderBy, orderDirection).requireData()
    }.mapFailure()

    suspend fun createFolder(parentUri: String, name: String): Result<FileObject> = runCatching {
        api.createFolder(CreateFolderRequest(uri = joinUri(parentUri, name))).requireData()
    }.mapFailure()

    suspend fun rename(uri: String, newName: String): Result<FileObject> = runCatching {
        api.renameFile(RenameFileRequest(uri = uri, newName = newName)).requireData()
    }.mapFailure()

    suspend fun delete(uris: List<String>): Result<Unit> = runCatching {
        api.deleteFiles(DeleteFilesRequest(uris))
        Unit
    }.mapFailure()

    /** 批量移动文件/文件夹到目标目录（copy=false 表示移动） */
    suspend fun move(uris: List<String>, dstUri: String, copy: Boolean = false): Result<Unit> = runCatching {
        api.moveFiles(MoveFilesRequest(uris = uris, dst = dstUri, copy = copy))
        Unit
    }.mapFailure()

    /**
     * 拉取某个目录下的全部条目，自动翻页。
     * 用于文件夹递归下载等需要遍历整棵子树的场景（普通列表页仍走单页 [listFiles]）。
     */
    suspend fun listAllChildren(uri: String): Result<List<FileObject>> = runCatching {
        val all = mutableListOf<FileObject>()
        var page = 1
        var token = ""
        while (true) {
            val response = api.listFiles(
                uri = uri,
                page = page,
                pageSize = PAGE_SIZE,
                orderBy = "name",
                orderDirection = "asc",
                nextPageToken = token
            ).requireData()
            all += response.files
            val pagination = response.pagination ?: break
            val fetched = response.files.size
            if (pagination.isCursor) {
                // 游标分页：只能靠 next_token 前进
                val next = pagination.nextToken
                if (next.isBlank() || fetched == 0) break
                token = next
            } else {
                val pageSize = pagination.pageSize.takeIf { it > 0 } ?: PAGE_SIZE
                if (fetched < pageSize) break
                if (pagination.totalItems > 0L && all.size >= pagination.totalItems) break
                page++
                token = ""
            }
        }
        all
    }.mapFailure()

    suspend fun getDownloadUrl(uri: String): Result<String> = runCatching {
        val url = api.getFileUrl(FileUrlRequest(uris = listOf(uri)))
            .requireData()
            .urls
            .firstOrNull()
            ?.url
        if (url.isNullOrBlank()) throw CraException.Unexpected("未获取到下载地址")
        url
    }.mapFailure()

    /**
     * 创建分享链接；uri 为单个文件/文件夹的 cloudreve URI，返回可对外分发的分享地址。
     *
     * 服务端只在 `is_private=true` 时才会写入密码（否则 password 被静默忽略），
     * 因此开启密码保护时必须同时置位 is_private。密码仅接受 1-32 位字母数字。
     *
     * @param expireSeconds 有效期（秒），0 表示永久有效
     * @param downloadLimit 可下载次数，0 表示不限次数；达到次数后分享自动过期
     */
    suspend fun createShare(
        uri: String,
        password: String = "",
        expireSeconds: Int = 0,
        downloadLimit: Int = 0
    ): Result<String> = runCatching {
        val request = CreateShareRequest(
            uri = uri,
            isPrivate = password.isNotEmpty(),
            password = password,
            expire = expireSeconds,
            downloads = downloadLimit
        )
        val link = api.createShare(request).requireData()
        if (link.isBlank()) throw CraException.Unexpected("未获取到分享链接")
        // 服务端返回的是「已解锁」链接，把密码拼在了路径末尾（/s/{id}/{password}），
        // 直接分发等于免密打开；这里剥掉密码段，访问者必须手动输入密码才算真正受保护
        if (password.isNotEmpty() && link.endsWith("/$password")) {
            link.dropLast(password.length + 1)
        } else {
            link
        }
    }.mapFailure()

    /** 获取文件外链（直链） */
    suspend fun getDirectLink(uri: String): Result<String> = runCatching {
        val link = api.getDirectLinks(DirectLinkRequest(uris = listOf(uri)))
            .requireData()
            .firstOrNull()
            ?.link
        if (link.isNullOrBlank()) throw CraException.Unexpected("未获取到直链")
        link
    }.mapFailure()

    /** 拼接 cloudreve URI，例如 cloudreve://my/a + b => cloudreve://my/a/b */
    private fun joinUri(parent: String, name: String): String =
        parent.trimEnd('/') + "/" + name.trimStart('/')

    /** 站点根地址（不含结尾斜杠），用于拼接回调地址等 */
    suspend fun serverUrl(): String = sessionStore.serverUrlOnce()

    /** 创建上传会话；upload_urls 非空表示可预签名直传，否则走中转分片上传 */
    suspend fun createUploadSession(
        uri: String,
        size: Long,
        lastModified: Long,
        mimeType: String
    ): Result<UploadSession> = runCatching {
        api.createUploadSession(
            CreateUploadSessionRequest(
                uri = uri,
                size = size,
                lastModified = lastModified,
                mimeType = mimeType
            )
        ).requireData()
    }.mapFailure()

    /** 中转分片上传单个分片 */
    suspend fun uploadChunk(
        sessionId: String,
        index: Int,
        body: RequestBody
    ): Result<Unit> = runCatching {
        api.uploadChunk(sessionId, index, body)
        Unit
    }.mapFailure()

    /** 取消上传会话，清理服务端占位文件 */
    suspend fun deleteUploadSession(sessionId: String, uri: String): Result<Unit> = runCatching {
        api.deleteUploadSession(DeleteUploadSessionRequest(id = sessionId, uri = uri))
        Unit
    }.mapFailure()

    private fun <T> Result<T>.mapFailure(): Result<T> = fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it.toCraException()) }
    )

    private companion object {
        const val PAGE_SIZE = 200
    }
}
