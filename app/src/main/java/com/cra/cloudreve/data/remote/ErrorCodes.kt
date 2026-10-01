package com.cra.cloudreve.data.remote

object ErrorCodes {
    const val NOT_FULLY_SUCCESSFUL = 203
    const val NOT_LOGGED_IN = 401
    const val NO_PERMISSION = 403
    const val NOT_FOUND = 404
    const val CONFLICT = 409

    const val PARAM_ERROR = 40001
    const val INVALID_CREDENTIALS = 40020
    const val USER_NOT_FOUND = 40021
    const val TWO_FACTOR_ERROR = 40022
    const val CAPTCHA_ERROR = 40026
    const val CAPTCHA_REFRESH_NEEDED = 40027
    const val FILE_NOT_FOUND = 40044
    const val INSUFFICIENT_CAPACITY = 40051
    const val ILLEGAL_OBJECT_NAME = 40052
    const val SAME_NAME_UPLOADING = 40054
    const val SHARE_LINK_NOT_FOUND = 40058
    const val INVALID_SIGNATURE = 40071
    const val INVALID_PASSWORD = 40080

    fun isAuthError(code: Int): Boolean = code == NOT_LOGGED_IN || code == 40023
}
