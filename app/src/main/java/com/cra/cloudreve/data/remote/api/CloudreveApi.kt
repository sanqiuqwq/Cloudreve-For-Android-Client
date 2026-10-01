package com.cra.cloudreve.data.remote.api

import com.cra.cloudreve.data.remote.dto.ApiResponse
import com.cra.cloudreve.data.remote.dto.BatchResponse
import com.cra.cloudreve.data.remote.dto.Captcha
import com.cra.cloudreve.data.remote.dto.CreateFolderRequest
import com.cra.cloudreve.data.remote.dto.CreateShareRequest
import com.cra.cloudreve.data.remote.dto.CreateUploadSessionRequest
import com.cra.cloudreve.data.remote.dto.DeleteFilesRequest
import com.cra.cloudreve.data.remote.dto.DeleteUploadSessionRequest
import com.cra.cloudreve.data.remote.dto.DirectLinkItem
import com.cra.cloudreve.data.remote.dto.DirectLinkRequest
import com.cra.cloudreve.data.remote.dto.FileListResponse
import com.cra.cloudreve.data.remote.dto.FileObject
import com.cra.cloudreve.data.remote.dto.FileUrlRequest
import com.cra.cloudreve.data.remote.dto.FileUrlResponse
import com.cra.cloudreve.data.remote.dto.LoginConfig
import com.cra.cloudreve.data.remote.dto.LoginRequest
import com.cra.cloudreve.data.remote.dto.LoginResponse
import com.cra.cloudreve.data.remote.dto.MoveFilesRequest
import com.cra.cloudreve.data.remote.dto.RenameFileRequest
import com.cra.cloudreve.data.remote.dto.SiteConfig
import com.cra.cloudreve.data.remote.dto.UploadSession
import com.cra.cloudreve.data.remote.dto.User
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CloudreveApi {

    @GET("api/v4/site/ping")
    suspend fun ping(): ApiResponse<String>

    @GET("api/v4/site/config/basic")
    suspend fun getSiteConfig(): ApiResponse<SiteConfig>

    @GET("api/v4/site/config/login")
    suspend fun getLoginConfig(): ApiResponse<LoginConfig>

    @GET("api/v4/site/captcha")
    suspend fun getCaptcha(): ApiResponse<Captcha>

    @POST("api/v4/session/token")
    suspend fun login(@Body request: LoginRequest): ApiResponse<LoginResponse>

    @HTTP(method = "DELETE", path = "api/v4/session/token", hasBody = true)
    suspend fun logout(): ApiResponse<Unit>

    @GET("api/v4/user/profile")
    suspend fun getProfile(): ApiResponse<User>

    @GET("api/v4/file")
    suspend fun listFiles(
        @Query("uri") uri: String = ROOT_URI,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 200,
        @Query("order_by") orderBy: String = "name",
        @Query("order_direction") orderDirection: String = "asc",
        @Query("next_page_token") nextPageToken: String = ""
    ): ApiResponse<FileListResponse>

    @GET("api/v4/file/info")
    suspend fun getFile(@Query("uri") uri: String): ApiResponse<FileObject>

    @POST("api/v4/file/create")
    suspend fun createFolder(@Body request: CreateFolderRequest): ApiResponse<FileObject>

    @POST("api/v4/file/rename")
    suspend fun renameFile(@Body request: RenameFileRequest): ApiResponse<FileObject>

    /** 批量移动/复制文件或文件夹到目标目录，响应仅含 code */
    @POST("api/v4/file/move")
    suspend fun moveFiles(@Body request: MoveFilesRequest): ApiResponse<Unit>

    @HTTP(method = "DELETE", path = "api/v4/file", hasBody = true)
    suspend fun deleteFiles(@Body request: DeleteFilesRequest): BatchResponse<Unit>

    @POST("api/v4/file/url")
    suspend fun getFileUrl(@Body request: FileUrlRequest): ApiResponse<FileUrlResponse>

    /** 获取文件外链（直链），data 为 [{ "link": "..." }] */
    @HTTP(method = "PUT", path = "api/v4/file/source", hasBody = true)
    suspend fun getDirectLinks(@Body request: DirectLinkRequest): ApiResponse<List<DirectLinkItem>>

    /** 创建分享链接，data 为完整的分享地址字符串 */
    @HTTP(method = "PUT", path = "api/v4/share", hasBody = true)
    suspend fun createShare(@Body request: CreateShareRequest): ApiResponse<String>

    /** 创建上传会话；upload_urls 非空表示可直传（预签名），否则走中转分片上传 */
    @HTTP(method = "PUT", path = "api/v4/file/upload", hasBody = true)
    suspend fun createUploadSession(@Body request: CreateUploadSessionRequest): ApiResponse<UploadSession>

    /** 中转分片上传：raw body 必须为 application/octet-stream，且 Content-Length 等于该分片长度 */
    @POST("api/v4/file/upload/{sessionId}/{index}")
    suspend fun uploadChunk(
        @Path("sessionId") sessionId: String,
        @Path("index") index: Int,
        @Body body: RequestBody
    ): ApiResponse<Unit>

    /** 取消上传会话并清理服务端占位文件 */
    @HTTP(method = "DELETE", path = "api/v4/file/upload", hasBody = true)
    suspend fun deleteUploadSession(@Body request: DeleteUploadSessionRequest): ApiResponse<Unit>

    companion object {
        /** 当前用户根目录的 cloudreve URI 方案 */
        const val ROOT_URI = "cloudreve://my"
    }
}