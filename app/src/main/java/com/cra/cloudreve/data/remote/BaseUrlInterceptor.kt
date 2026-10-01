package com.cra.cloudreve.data.remote

import com.cra.cloudreve.data.local.SessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

class BaseUrlInterceptor(
    private val sessionStore: SessionStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val current = request.url

        if (isAbsoluteServerUrl(current.host)) {
            return chain.proceed(request)
        }

        val serverUrl = runBlocking { sessionStore.serverUrlOnce() }
        val base = serverUrl.toHttpUrlOrNull()
            ?: return chain.proceed(request)

        val newUrl = current.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()

        return chain.proceed(request.newBuilder().url(newUrl).build())
    }

    private fun isAbsoluteServerUrl(host: String): Boolean =
        host != PLACEHOLDER_HOST

    companion object {
        const val PLACEHOLDER_HOST = "localhost"
    }
}
