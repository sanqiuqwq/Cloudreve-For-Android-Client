package com.cra.cloudreve.ui.about

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** 项目与开发者相关信息，供「关于」界面与更新弹窗复用 */
object AboutInfo {
    const val APP_NAME = "Cloudreve for Android"
    const val DEVELOPER = "nnn"

    const val REPO_URL = "https://github.com/sanqiuqwq/Cloudreve-For-Android-Client"
    const val GITHUB_URL = "https://github.com/sanqiuqwq"
    const val TELEGRAM_URL = "https://t.me/qwqtop"
    const val TELEGRAM_GROUP_URL = "https://t.me/NekoCraftChat"

    const val GITHUB_LABEL = "https://github.com/sanqiuqwq"
    const val TELEGRAM_LABEL = "@qwqtop"
    const val TELEGRAM_GROUP_LABEL = "@NekoCraftChat"
}

/** 用浏览器打开链接；链接为空时返回 false */
fun openUrl(context: Context, url: String): Boolean {
    if (url.isBlank()) return false
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(intent) }.isSuccess
}