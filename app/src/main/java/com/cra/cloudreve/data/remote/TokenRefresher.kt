package com.cra.cloudreve.data.remote

import com.cra.cloudreve.data.local.SessionStore
import com.cra.cloudreve.data.remote.api.AuthTokenApi
import com.cra.cloudreve.data.remote.dto.RefreshTokenRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AccessToken 过期时用 RefreshToken 换取新的一对令牌。
 *
 * 用 [Mutex] 保证同一时刻只有一次刷新；若并发调用期间已有请求刷新成功，
 * 直接复用新令牌（比对 [staleToken]）而不重复请求服务端。
 */
@Singleton
class TokenRefresher @Inject constructor(
    private val sessionStore: SessionStore,
    private val api: AuthTokenApi
) {

    private val mutex = Mutex()

    /** 供 OkHttp 拦截器（同步上下文）调用 */
    fun refreshBlocking(staleToken: String): String? = runBlocking { refresh(staleToken) }

    /**
     * @param staleToken 触发刷新的那对请求使用的旧 access token
     * @return 新的 access token；刷新失败返回 null（保留原会话）
     */
    suspend fun refresh(staleToken: String): String? = mutex.withLock {
        // 其他请求可能已经刷新成功，令牌已变化则直接复用
        val current = sessionStore.tokenOnce()
        if (current.isNotBlank() && current != staleToken) return@withLock current

        val refreshToken = sessionStore.refreshTokenOnce()
        if (refreshToken.isBlank()) return@withLock null

        val pair = try {
            api.refreshToken(RefreshTokenRequest(refreshToken)).requireData()
        } catch (e: Throwable) {
            // 网络抖动或服务端拒绝：保留会话，交由调用方回退到原始响应
            return@withLock null
        }
        if (pair.accessToken.isBlank()) return@withLock null

        sessionStore.updateTokens(pair.accessToken, pair.refreshToken)
        pair.accessToken
    }
}