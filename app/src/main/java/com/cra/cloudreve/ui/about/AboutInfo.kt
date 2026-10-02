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

    /** 本项目采用的许可证 */
    const val LICENSE_NAME = "GNU AGPL-3.0"
    const val LICENSE_URL = "$REPO_URL/blob/main/LICENSE"

    /** 应用内展示的第三方开源组件，版本与 gradle/libs.versions.toml 保持一致 */
    val OPEN_SOURCE_LIBS: List<OssComponent> = listOf(
        OssComponent("Kotlin / kotlinx-coroutines", "2.1.0 / 1.9.0", "Apache-2.0", "https://github.com/JetBrains/kotlin"),
        OssComponent("kotlinx.serialization", "1.7.3", "Apache-2.0", "https://github.com/Kotlin/kotlinx.serialization"),
        OssComponent("AndroidX Core KTX", "1.15.0", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/core"),
        OssComponent("AndroidX Lifecycle", "2.8.7", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/lifecycle"),
        OssComponent("AndroidX Activity Compose", "1.9.3", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/activity"),
        OssComponent("Jetpack Compose (BOM)", "2024.12.01", "Apache-2.0", "https://developer.android.com/jetpack/compose"),
        OssComponent("Compose Material 3", "BOM 2024.12.01", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/compose-material3"),
        OssComponent("Compose Material Icons Extended", "BOM 2024.12.01", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/compose-material"),
        OssComponent("AndroidX Navigation Compose", "2.8.5", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/navigation"),
        OssComponent("AndroidX DataStore", "1.1.1", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/datastore"),
        OssComponent("AndroidX Hilt Navigation Compose", "1.2.0", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/hilt"),
        OssComponent("Dagger Hilt", "2.54", "Apache-2.0", "https://github.com/google/dagger"),
        OssComponent("Retrofit", "2.11.0", "Apache-2.0", "https://github.com/square/retrofit"),
        OssComponent("retrofit2-kotlinx-serialization-converter", "1.0.0", "Apache-2.0", "https://github.com/JakeWharton/retrofit2-kotlinx-serialization-converter"),
        OssComponent("OkHttp", "4.12.0", "Apache-2.0", "https://github.com/square/okhttp"),
        OssComponent("Coil", "2.7.0", "Apache-2.0", "https://github.com/coil-kt/coil")
    )
}

/** 一个第三方开源组件的说明 */
data class OssComponent(
    val name: String,
    val version: String,
    val license: String,
    val url: String
)

/** 用浏览器打开链接；链接为空时返回 false */
fun openUrl(context: Context, url: String): Boolean {
    if (url.isBlank()) return false
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(intent) }.isSuccess
}