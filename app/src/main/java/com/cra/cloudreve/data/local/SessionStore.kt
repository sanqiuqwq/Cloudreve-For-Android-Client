package com.cra.cloudreve.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cra_session")

@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val TOKEN = stringPreferencesKey("token")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_NICKNAME = stringPreferencesKey("user_nickname")
        val USER_AVATAR = stringPreferencesKey("user_avatar")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DOWNLOAD_CONCURRENCY = intPreferencesKey("download_concurrency")
    }

    val sessionFlow: Flow<Session> = context.dataStore.data.map { prefs ->
        Session(
            serverUrl = prefs[Keys.SERVER_URL] ?: "",
            token = prefs[Keys.TOKEN] ?: "",
            userId = prefs[Keys.USER_ID] ?: "",
            email = prefs[Keys.USER_EMAIL] ?: "",
            nickname = prefs[Keys.USER_NICKNAME] ?: "",
            avatar = prefs[Keys.USER_AVATAR] ?: ""
        )
    }

    val preferencesFlow: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        AppPreferences(
            darkMode = prefs[Keys.DARK_MODE],
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            downloadConcurrency = (prefs[Keys.DOWNLOAD_CONCURRENCY]
                ?: AppPreferences.DEFAULT_DOWNLOAD_CONCURRENCY)
                .coerceIn(
                    AppPreferences.MIN_DOWNLOAD_CONCURRENCY,
                    AppPreferences.MAX_DOWNLOAD_CONCURRENCY
                )
        )
    }

    suspend fun tokenOnce(): String =
        context.dataStore.data.first()[Keys.TOKEN] ?: ""

    suspend fun serverUrlOnce(): String =
        context.dataStore.data.first()[Keys.SERVER_URL] ?: ""

    suspend fun saveServer(url: String) {
        context.dataStore.edit { it[Keys.SERVER_URL] = normalize(url) }
    }

    suspend fun saveLogin(token: String, userId: String, email: String, nickname: String, avatar: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TOKEN] = token
            prefs[Keys.USER_ID] = userId
            prefs[Keys.USER_EMAIL] = email
            prefs[Keys.USER_NICKNAME] = nickname
            prefs[Keys.USER_AVATAR] = avatar
        }
    }

    suspend fun updateProfile(userId: String, email: String, nickname: String, avatar: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_ID] = userId
            prefs[Keys.USER_EMAIL] = email
            prefs[Keys.USER_NICKNAME] = nickname
            prefs[Keys.USER_AVATAR] = avatar
        }
    }

    suspend fun setDarkMode(enabled: Boolean?) {
        context.dataStore.edit { prefs ->
            if (enabled == null) prefs.remove(Keys.DARK_MODE) else prefs[Keys.DARK_MODE] = enabled
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    /** 同时下载的文件个数上限，越界值会被夹到合法范围内 */
    suspend fun setDownloadConcurrency(value: Int) {
        val clamped = value.coerceIn(
            AppPreferences.MIN_DOWNLOAD_CONCURRENCY,
            AppPreferences.MAX_DOWNLOAD_CONCURRENCY
        )
        context.dataStore.edit { it[Keys.DOWNLOAD_CONCURRENCY] = clamped }
    }

    suspend fun clearToken() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.TOKEN)
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.USER_EMAIL)
            prefs.remove(Keys.USER_NICKNAME)
            prefs.remove(Keys.USER_AVATAR)
        }
    }

    private fun normalize(url: String): String {
        val trimmed = url.trim().trimEnd('/')
        return when {
            trimmed.isEmpty() -> ""
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "https://$trimmed"
        }
    }
}

data class Session(
    val serverUrl: String = "",
    val token: String = "",
    val userId: String = "",
    val email: String = "",
    val nickname: String = "",
    val avatar: String = ""
) {
    val isLoggedIn: Boolean get() = token.isNotBlank()
    val hasServer: Boolean get() = serverUrl.isNotBlank()
}

data class AppPreferences(
    val darkMode: Boolean? = null,
    val dynamicColor: Boolean = true,
    /** 同时下载的文件个数上限 */
    val downloadConcurrency: Int = DEFAULT_DOWNLOAD_CONCURRENCY
) {
    companion object {
        const val MIN_DOWNLOAD_CONCURRENCY = 1
        const val MAX_DOWNLOAD_CONCURRENCY = 20
        const val DEFAULT_DOWNLOAD_CONCURRENCY = 4
    }
}
