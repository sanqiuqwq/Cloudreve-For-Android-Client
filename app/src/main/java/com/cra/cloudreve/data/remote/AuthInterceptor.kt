package com.cra.cloudreve.data.remote

import com.cra.cloudreve.data.local.SessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

class AuthInterceptor(
    private val sessionStore: SessionStore,
    private val tokenRefresher: TokenRefresher
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(HEADER_NO_AUTH) != null) {
            return chain.proceed(request.newBuilder().removeHeader(HEADER_NO_AUTH).build())
        }

        val token = runBlocking { sessionStore.tokenOnce() }
        val response = chain.proceed(withToken(request, token))
        // 未登录时没有可用的 refresh token，直接返回原响应
        if (token.isBlank() || !isAuthFailure(response)) return response

        // AccessToken 过期：用 RefreshToken 换新令牌后重放一次请求
        val refreshed = tokenRefresher.refreshBlocking(token) ?: return response
        response.close()
        return chain.proceed(withToken(request, refreshed))
    }

    private fun withToken(request: Request, token: String): Request =
        request.newBuilder()
            .header("Accept", "application/json")
            .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }
            .build()

    /**
     * 登录态失效的判定：服务端可能用 HTTP 401，也可能用 HTTP 200 + body `code=401`。
     * 后者只能窥探响应体前若干字节来判断（peekBody 不会消费原始响应流）。
     */
    private fun isAuthFailure(response: Response): Boolean {
        if (response.code == UNAUTHORIZED_HTTP) return true
        if (response.code != 200) return false
        if (response.body?.contentType()?.subtype != "json") return false
        val body = runCatching { response.peekBody(PEEK_LIMIT).string() }.getOrNull() ?: return false
        return AUTH_CODE_PATTERN.containsMatchIn(body)
    }

    companion object {
        const val HEADER_NO_AUTH = "X-Cra-No-Auth"

        private const val UNAUTHORIZED_HTTP = 401
        private const val PEEK_LIMIT = 2048L

        /** 匹配 `"code":401`，负向断言避免误命中 4010、401xx 等业务码 */
        private val AUTH_CODE_PATTERN = Regex("\"code\"\\s*:\\s*401(?!\\d)")
    }
}