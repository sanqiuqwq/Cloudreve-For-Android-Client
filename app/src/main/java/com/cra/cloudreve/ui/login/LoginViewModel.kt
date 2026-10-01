package com.cra.cloudreve.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cra.cloudreve.data.remote.dto.Captcha
import com.cra.cloudreve.data.repository.CloudreveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: CloudreveRepository
) : ViewModel() {

    /** 需要 WebView 承载的第三方验证码类型 */
    private val webCaptchaTypes = setOf("recaptcha", "turnstile")

    data class UiState(
        val serverUrl: String = "",
        val email: String = "",
        val password: String = "",
        // 内置图形验证码
        val captchaCode: String = "",
        val captchaImage: String? = null,
        val captchaTicket: String? = null,
        val captchaRequired: Boolean = false,
        // 第三方验证码（reCAPTCHA / Turnstile / hCaptcha）
        val webCaptchaType: String? = null,
        val webCaptchaSiteKey: String = "",
        val webCaptchaToken: String? = null,
        val webCaptchaVisible: Boolean = false,
        /** 用于 WebView 的页面来源，需与站点同域才能通过 reCAPTCHA 的域名校验 */
        val serverOrigin: String = "",
        val loading: Boolean = false,
        val testing: Boolean = false,
        val errorMessage: String? = null,
        val infoMessage: String? = null
    ) {
        val canSubmit: Boolean
            get() = serverUrl.isNotBlank() && email.isNotBlank() && password.isNotBlank() &&
                !loading && (!captchaRequired || captchaCode.isNotBlank())

        val canTest: Boolean
            get() = serverUrl.isNotBlank() && !testing && !loading
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 是否已经探测过该服务器的验证码策略 */
    private var captchaProbed = false

    init {
        viewModelScope.launch {
            repository.sessionFlow.collect { session ->
                if (session.serverUrl.isNotBlank() && _uiState.value.serverUrl.isBlank()) {
                    _uiState.update { it.copy(serverUrl = session.serverUrl) }
                }
            }
        }
    }

    fun onServerUrlChange(value: String) {
        captchaProbed = false
        _uiState.update {
            it.copy(
                serverUrl = value,
                captchaRequired = false,
                captchaImage = null,
                captchaTicket = null,
                captchaCode = "",
                webCaptchaType = null,
                webCaptchaToken = null,
                serverOrigin = ""
            )
        }
    }

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value) }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value) }

    fun onCaptchaChange(value: String) = _uiState.update { it.copy(captchaCode = value) }

    fun consumeMessage() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    fun testConnection() {
        val state = _uiState.value
        if (!state.canTest) return
        _uiState.update { it.copy(testing = true, errorMessage = null, infoMessage = null) }
        viewModelScope.launch {
            repository.getSiteConfig(state.serverUrl).fold(
                onSuccess = { config ->
                    val name = config.title.ifBlank { "服务器" }
                    _uiState.update { it.copy(testing = false, infoMessage = "已连接到 $name") }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(testing = false, errorMessage = error.message ?: "连接失败") }
                }
            )
        }
    }

    /** 图形验证码刷新 */
    fun refreshCaptcha() {
        viewModelScope.launch {
            val captcha = repository.getCaptcha().getOrNull() ?: return@launch
            _uiState.update {
                it.copy(
                    captchaImage = captcha.image,
                    captchaTicket = captcha.ticket,
                    captchaCode = "",
                    errorMessage = null
                )
            }
        }
    }

    /** WebView 验证码完成，拿到 token 后直接提交登录 */
    fun onWebCaptchaSuccess(token: String, onSuccess: () -> Unit) {
        _uiState.update { it.copy(webCaptchaToken = token, webCaptchaVisible = false, errorMessage = null) }
        viewModelScope.launch { submit(onSuccess) }
    }

    fun onWebCaptchaError(message: String) {
        captchaProbed = false
        _uiState.update {
            it.copy(webCaptchaVisible = false, webCaptchaToken = null, loading = false, errorMessage = message)
        }
    }

    fun onWebCaptchaDismiss() {
        _uiState.update { it.copy(webCaptchaVisible = false, loading = false) }
    }

    fun login(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.update { it.copy(loading = true, errorMessage = null, infoMessage = null) }

        viewModelScope.launch {
            // 1. 探测服务器与验证码策略（每次改过地址后重新探测）
            if (!captchaProbed) {
                val site = repository.getSiteConfig(state.serverUrl)
                if (site.isFailure) {
                    _uiState.update {
                        it.copy(loading = false, errorMessage = site.exceptionOrNull()?.message ?: "无法连接服务器")
                    }
                    return@launch
                }
                // captcha_type 与 reCAPTCHA 密钥在 basic 配置里，登录开关在 login 配置里
                val basic = site.getOrNull()
                val loginConfig = repository.getLoginConfig().getOrNull()
                captchaProbed = true
                val needCaptcha = loginConfig?.loginCaptcha == true
                val type = basic?.captchaType.orEmpty().lowercase()

                if (needCaptcha && type in webCaptchaTypes) {
                    // 交给 WebView 完成人机验证
                    _uiState.update {
                        it.copy(
                            loading = false,
                            webCaptchaType = type,
                            webCaptchaSiteKey = basic?.reCaptchaKey.orEmpty(),
                            webCaptchaVisible = true,
                            serverOrigin = originOf(state.serverUrl),
                            infoMessage = null
                        )
                    }
                    return@launch
                }

                if (needCaptcha) {
                    // 内置图形验证码
                    val captcha = repository.getCaptcha().getOrNull()
                    _uiState.update {
                        it.copy(
                            loading = false,
                            captchaRequired = true,
                            captchaImage = captcha?.image,
                            captchaTicket = captcha?.ticket,
                            infoMessage = "该服务器开启了图形验证码，请输入图片中的字符"
                        )
                    }
                    return@launch
                }
            }

            submit(onSuccess)
        }
    }

    private suspend fun submit(onSuccess: () -> Unit) {
        val state = _uiState.value

        if (state.captchaRequired && state.captchaCode.isBlank()) {
            _uiState.update { it.copy(loading = false, errorMessage = "请输入验证码") }
            return
        }
        if (state.webCaptchaType != null && state.webCaptchaToken.isNullOrBlank()) {
            _uiState.update {
                it.copy(loading = false, webCaptchaVisible = true, errorMessage = "请先完成人机验证")
            }
            return
        }

        _uiState.update { it.copy(loading = true) }

        // reCAPTCHA 的 token 走 captcha 字段；Turnstile 与图形验证码走 ticket 字段
        val webToken = state.webCaptchaToken
        val captchaValue = when {
            state.webCaptchaType == "recaptcha" -> webToken
            state.captchaRequired -> state.captchaCode
            else -> null
        }
        val ticketValue = when {
            state.webCaptchaType != null -> webToken
            state.captchaRequired -> state.captchaTicket
            else -> null
        }

        repository.login(
            serverUrl = state.serverUrl,
            email = state.email,
            password = state.password,
            captcha = captchaValue,
            ticket = ticketValue
        ).fold(
            onSuccess = {
                _uiState.update { it.copy(loading = false) }
                onSuccess()
            },
            onFailure = { error ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorMessage = error.message ?: "登录失败",
                        captchaCode = "",
                        webCaptchaToken = null
                    )
                }
                when {
                    // 第三方验证码一次性有效，失败后需重新验证
                    _uiState.value.webCaptchaType != null ->
                        _uiState.update { it.copy(webCaptchaVisible = true) }
                    // 图形验证码失败后换一张
                    _uiState.value.captchaRequired -> refreshCaptchaSilently()
                }
            }
        )
    }

    private suspend fun refreshCaptchaSilently() {
        val captcha: Captcha = repository.getCaptcha().getOrNull() ?: return
        _uiState.update { it.copy(captchaImage = captcha.image, captchaTicket = captcha.ticket) }
    }

    /** 取 scheme://host[:port]，作为 WebView 的 baseURL */
    private fun originOf(rawUrl: String): String {
        val trimmed = rawUrl.trim().trimEnd('/')
        val normalized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val match = Regex("^(https?://[^/]+)").find(normalized)
        return match?.value ?: normalized
    }
}