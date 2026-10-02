package com.cra.cloudreve.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /api/v4/site/config/basic 的响应 */
@Serializable
data class SiteConfig(
    @SerialName("instance_id") val instanceId: String = "",
    val title: String = "Cloudreve",
    val themes: String = "",
    val logo: String = "",
    /** normal / recaptcha / turnstile / hcaptcha / tcaptcha / cap，空串表示未开启验证码 */
    @SerialName("captcha_type") val captchaType: String = "",
    /** reCAPTCHA 站点密钥 */
    @SerialName("captcha_ReCaptchaKey") val reCaptchaKey: String = ""
)

/** GET /api/v4/site/config/login 的响应 */
@Serializable
data class LoginConfig(
    @SerialName("login_captcha") val loginCaptcha: Boolean = false,
    @SerialName("reg_captcha") val regCaptcha: Boolean = false,
    @SerialName("forget_captcha") val forgetCaptcha: Boolean = false,
    @SerialName("register_enabled") val registerEnabled: Boolean = false,
    val authn: Boolean = false
)

/** GET /api/v4/site/captcha 的响应，image 为 data:image/png;base64 开头的图片 */
@Serializable
data class Captcha(
    val image: String = "",
    val ticket: String = ""
)

/** POST /api/v4/session/token 的请求体，验证码开启时需同时携带 captcha 与 ticket */
@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    val captcha: String? = null,
    val ticket: String? = null
)

@Serializable
data class LoginResponse(
    val user: User? = null,
    val token: TokenPair? = null
)

/** POST /api/v4/session/token/refresh 的请求体 */
@Serializable
data class RefreshTokenRequest(
    @SerialName("refresh_token") val refreshToken: String
)

/** 服务端返回的 token 是一个对象而非字符串，两个过期时间是 RFC3339 字符串 */
@Serializable
data class TokenPair(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("access_expires") val accessExpires: String = "",
    @SerialName("refresh_expires") val refreshExpires: String = ""
)

@Serializable
data class User(
    val id: String = "",
    val email: String = "",
    val nickname: String = "",
    /** active / inactive / manual_banned / sys_banned */
    val status: String = "",
    val avatar: String = "",
    @SerialName("created_at") val createdAt: String = "",
    val group: UserGroup? = null,
    val anonymous: Boolean = false
)

@Serializable
data class UserGroup(
    val id: String = "",
    val name: String = "",
    /** base64 编码的权限位集合 */
    val permission: String = "",
    @SerialName("direct_link_batch_size") val directLinkBatchSize: Int = 0,
    @SerialName("trash_retention") val trashRetention: Int = 0
)