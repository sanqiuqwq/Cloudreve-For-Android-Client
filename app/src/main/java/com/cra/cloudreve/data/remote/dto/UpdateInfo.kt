package com.cra.cloudreve.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * 版本更新信息，来自仓库中的 `docs/check_update.json`。
 *
 * 示例：
 * ```json
 * {
 *   "versionCode": 2,
 *   "versionName": "1.1.0",
 *   "changelog": "1. 新增检查更新\n2. 修复若干问题",
 *   "downloadUrl": "https://github.com/sanqiuqwq/Cloudreve-For-Android-Client/releases/latest",
 *   "forceUpdate": false
 * }
 * ```
 *
 * 判断新旧优先比较 [versionCode]；缺省（<=0）时退回比较 [versionName]。
 */
@Serializable
data class UpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val changelog: String = "",
    val downloadUrl: String = "",
    val forceUpdate: Boolean = false
) {

    fun isNewerThan(currentVersionCode: Int, currentVersionName: String): Boolean {
        if (versionCode > 0 && currentVersionCode > 0) {
            return versionCode > currentVersionCode
        }
        return compareVersions(versionName, currentVersionName) > 0
    }

    companion object {
        /** 按点分段做数值比较，如 1.10.0 > 1.9.2；非法段按 0 处理 */
        fun compareVersions(a: String, b: String): Int {
            val left = a.split('.').map { it.trim().toIntOrNull() ?: 0 }
            val right = b.split('.').map { it.trim().toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(left.size, right.size)) {
                val diff = (left.getOrNull(i) ?: 0) - (right.getOrNull(i) ?: 0)
                if (diff != 0) return diff
            }
            return 0
        }
    }
}