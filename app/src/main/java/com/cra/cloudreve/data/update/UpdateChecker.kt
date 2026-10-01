package com.cra.cloudreve.data.update

import com.cra.cloudreve.data.remote.AuthInterceptor
import com.cra.cloudreve.data.remote.CraException
import com.cra.cloudreve.data.remote.dto.UpdateInfo
import com.cra.cloudreve.data.remote.toCraException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 检查更新：从 GitHub 仓库读取 `docs/check_update.json`。
 *
 * 该文件不受当前登录服务器影响，因此走绝对地址直连，
 * 并通过 [AuthInterceptor.HEADER_NO_AUTH] 跳过鉴权注入。
 */
@Singleton
class UpdateChecker @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {

    suspend fun check(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(CHECK_UPDATE_URL)
                .header(AuthInterceptor.HEADER_NO_AUTH, "1")
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw CraException.Unexpected("检查更新失败：HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) throw CraException.Unexpected("更新信息为空")
                json.decodeFromString(UpdateInfo.serializer(), body)
            }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it.toCraException()) }
        )
    }

    companion object {
        /** GitHub 上的原始 JSON；blob 链接返回 HTML，必须走 raw */
        const val CHECK_UPDATE_URL =
            "https://raw.githubusercontent.com/sanqiuqwq/Cloudreve-For-Android-Client/main/docs/check_update.json"
    }
}