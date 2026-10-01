package com.cra.cloudreve.data.remote

import com.cra.cloudreve.data.local.SessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val sessionStore: SessionStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(HEADER_NO_AUTH) != null) {
            return chain.proceed(request.newBuilder().removeHeader(HEADER_NO_AUTH).build())
        }
        val token = runBlocking { sessionStore.tokenOnce() }
        val builder = request.newBuilder()
            .header("Accept", "application/json")
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        return chain.proceed(builder.build())
    }

    companion object {
        const val HEADER_NO_AUTH = "X-Cra-No-Auth"
    }
}