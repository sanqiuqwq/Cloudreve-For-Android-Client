package com.cra.cloudreve.data.remote

import com.cra.cloudreve.data.remote.dto.ApiResponse
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed class CraException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Business(val code: Int, val serverMsg: String) :
        CraException("[$code] ${serverMsg.ifBlank { ErrorMessages.of(code) }}")

    class Unauthorized(val code: Int) : CraException(ErrorMessages.NOT_LOGGED_IN)

    class NoPermission : CraException(ErrorMessages.NO_PERMISSION)

    class Network(cause: Throwable) : CraException(
        when (cause) {
            is UnknownHostException -> ErrorMessages.UNKNOWN_HOST
            is SocketTimeoutException -> ErrorMessages.TIMEOUT
            else -> ErrorMessages.NETWORK
        },
        cause
    )

    class Unexpected(message: String, cause: Throwable? = null) : CraException(message, cause)
}

object ErrorMessages {
    const val NOT_LOGGED_IN = "登录状态已失效，请重新登录"
    const val NO_PERMISSION = "没有访问权限"
    const val NOT_FOUND = "资源不存在"
    const val UNKNOWN_HOST = "无法连接服务器，请检查地址与网络"
    const val TIMEOUT = "请求超时，请稍后重试"
    const val NETWORK = "网络异常，请检查网络连接"
    const val UNKNOWN = "未知错误"

    private val map = mapOf(
        203 to "部分操作未成功",
        401 to NOT_LOGGED_IN,
        403 to NO_PERMISSION,
        404 to NOT_FOUND,
        409 to "资源冲突",
        40001 to "参数错误",
        40002 to "上传失败",
        40003 to "文件夹创建失败",
        40004 to "对象已存在",
        40005 to "签名已过期",
        40006 to "当前存储策略不允许该操作",
        40007 to "当前用户组不允许该操作",
        40008 to "需要管理员权限",
        40011 to "上传会话已过期",
        40012 to "分片序号无效",
        40013 to "内容长度无效",
        40016 to "父目录不存在",
        40017 to "用户已被封禁",
        40018 to "用户尚未激活",
        40019 to "该功能未启用",
        40020 to "账号或密码错误",
        40021 to "用户不存在",
        40022 to "两步验证码错误",
        40023 to "登录会话不存在",
        40026 to "验证码错误",
        40027 to "验证码已失效，请刷新",
        40032 to "邮箱已被使用",
        40044 to "文件不存在",
        40045 to "文件列表获取失败",
        40049 to "文件过大",
        40050 to "不支持的文件类型",
        40051 to "容量不足",
        40052 to "非法对象名称",
        40053 to "根目录受保护",
        40054 to "同名文件正在上传",
        40058 to "分享链接不存在",
        40059 to "无法保存自己的分享",
        40069 to "密码不正确",
        40071 to "签名无效",
        40078 to "文件位于回收站中",
        40080 to "密码无效",
        40081 to "批量操作未全部完成",
        40082 to "仅所有者可执行该操作",
        40083 to "需要先购买",
        50001 to "数据库操作失败",
        50004 to "IO 操作失败",
        50006 to "缓存操作失败",
        50010 to "节点离线"
    )

    fun of(code: Int): String = map[code] ?: "$UNKNOWN（code=$code）"
}

fun Throwable.toCraException(): CraException = when (this) {
    is CraException -> this
    is HttpException -> CraException.Unexpected("HTTP ${this.code()}")
    is IOException -> CraException.Network(this)
    else -> CraException.Unexpected(message ?: ErrorMessages.UNKNOWN, this)
}

fun <T> ApiResponse<T>.requireData(): T {
    if (isSuccess) {
        return data ?: throw CraException.Unexpected("响应缺少 data 字段")
    }
    throw when {
        code == ErrorCodes.NOT_LOGGED_IN -> CraException.Unauthorized(code)
        code == ErrorCodes.NO_PERMISSION -> CraException.NoPermission()
        else -> CraException.Business(code, msg.ifBlank { ErrorMessages.of(code) })
    }
}

fun <T> ApiResponse<T>.requireSuccess() {
    if (!isSuccess) {
        throw when {
            code == ErrorCodes.NOT_LOGGED_IN -> CraException.Unauthorized(code)
            code == ErrorCodes.NO_PERMISSION -> CraException.NoPermission()
            else -> CraException.Business(code, msg.ifBlank { ErrorMessages.of(code) })
        }
    }
}
