package com.cra.cloudreve.di

import com.cra.cloudreve.data.local.SessionStore
import com.cra.cloudreve.data.remote.AuthInterceptor
import com.cra.cloudreve.data.remote.BaseUrlInterceptor
import com.cra.cloudreve.data.remote.TokenRefresher
import com.cra.cloudreve.data.remote.api.AuthTokenApi
import com.cra.cloudreve.data.remote.api.CloudreveApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        sessionStore: SessionStore,
        tokenRefresher: TokenRefresher,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(BaseUrlInterceptor(sessionStore))
        .addInterceptor(AuthInterceptor(sessionStore, tokenRefresher))
        .addInterceptor(loggingInterceptor)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    /**
     * 刷新令牌专用客户端：独立实例（独立 Dispatcher），且不挂 AuthInterceptor，
     * 避免并发请求触发刷新时互相占用连接/线程导致刷新请求无法发出。
     */
    @Provides
    @Singleton
    fun provideAuthTokenApi(
        sessionStore: SessionStore,
        loggingInterceptor: HttpLoggingInterceptor,
        json: Json
    ): AuthTokenApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(BaseUrlInterceptor(sessionStore))
            .addInterceptor(loggingInterceptor)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AuthTokenApi::class.java)
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit = Retrofit.Builder()
        .baseUrl(PLACEHOLDER_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideCloudreveApi(retrofit: Retrofit): CloudreveApi =
        retrofit.create(CloudreveApi::class.java)

    private const val PLACEHOLDER_BASE_URL = "https://localhost/"
}
