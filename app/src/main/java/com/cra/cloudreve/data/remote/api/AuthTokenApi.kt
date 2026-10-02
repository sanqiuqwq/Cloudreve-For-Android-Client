package com.cra.cloudreve.data.remote.api

import com.cra.cloudreve.data.remote.dto.ApiResponse
import com.cra.cloudreve.data.remote.dto.RefreshTokenRequest
import com.cra.cloudreve.data.remote.dto.TokenPair
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 仅用于刷新令牌。刻意独立于 [CloudreveApi]，配套一个单独的 OkHttpClient，
 * 这样刷新请求不会与业务请求争抢同一个调度器，避免并发 401 时刷新被饿死。
 */
interface AuthTokenApi {

    @POST("api/v4/session/token/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): ApiResponse<TokenPair>
}