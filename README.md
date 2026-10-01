# Cloudreve Android 客户端

基于 Cloudreve v4 RESTful API（路由前缀 `/api/v4/`）构建的 Android 客户端，采用 Kotlin + Jetpack Compose + Material Design 3。

## API 约定

- 所有业务路由以 `/api/v4/` 开头
- 响应统一为 JSON，HTTP 状态码固定 200，业务结果通过 `code` / `msg` 判断
- `code == 0` 表示成功；401 未登录、403 无权限、404 资源不存在等为业务错误码
- 认证方式：Bearer Token，登录后持久化到 DataStore，请求头携带 `Authorization: Bearer <token>`
- 批量操作的响应体为 `data` 数组，每项单独携带 `code` / `msg`

## 已实现功能

- 登录：服务器地址 + 邮箱 + 密码，支持图形验证码 / reCAPTCHA / Turnstile / 腾讯验证码，登录前先校验站点连通性
- 文件浏览：目录进入、面包屑导航、下拉刷新、排序（名称/大小/修改时间）
- 文件操作：新建文件夹、重命名、删除、移动、批量移动 / 删除 / 下载
- 下载：应用内下载引擎，支持并发上限（默认 4，可调 1-20）、暂停 / 恢复 / 取消、前台服务通知显示进度
- 上传：多选上传，支持预签名直传与中转分片两种策略
- 文件夹下载：递归下载整个目录，保持层级结构，统一落盘到 `下载/cloudreve/`
- 分享：单文件 / 文件夹分享，支持密码保护、有效期与下载次数限制
- 设置：账号信息、服务器地址、深色模式、动态取色（Android 12+）、下载并发上限、关于、退出登录
- 关于：展示应用版本号、开源地址与开发者联系方式
- 检查更新：应用启动时自动检查一次，发现新版本弹窗提示，也可在「关于」页手动检查

## 关键端点

| 功能 | 方法与路径 |
| --- | --- |
| 站点信息 | `GET /api/v4/site/config` |
| 站点探活 | `GET /api/v4/site/ping` |
| 登录 | `POST /api/v4/user/session` |
| 当前用户 | `GET /api/v4/user/profile` |
| 退出登录 | `DELETE /api/v4/user/session` |
| 文件列表 | `GET /api/v4/file` |
| 文件详情 | `GET /api/v4/file/{id}` |
| 创建目录 | `PUT /api/v4/file/create` |
| 重命名 | `PATCH /api/v4/file/rename` |
| 移动 / 复制 | `POST /api/v4/file/move` |
| 删除 | `DELETE /api/v4/file` |
| 获取直链 | `POST /api/v4/file/url` |
| 创建分享 | `PUT /api/v4/share` |
| 创建上传会话 | `PUT /api/v4/file/upload` |
| 中转分片上传 | `POST /api/v4/file/upload/{sessionId}/{index}` |

> 请求/响应字段以官方接口文档（https://cloudrevev4.apifox.cn/）为准。若服务端版本存在字段或路径差异，只需调整 `data/remote/dto` 与 `data/remote/api/CloudreveApi.kt`，上层仓库与 UI 无需改动。

## 项目结构

```
app/src/main/java/com/cra/cloudreve/
├── CraApp.kt                 # Application，Hilt 入口
├── MainActivity.kt           # 根 Composable 与 RootViewModel
├── data/
│   ├── local/SessionStore.kt         # DataStore 会话与偏好
│   ├── remote/
│   │   ├── api/CloudreveApi.kt       # Retrofit 接口
│   │   ├── dto/                      # 序列化模型
│   │   ├── ApiException.kt           # 错误码映射与统一异常
│   │   ├── AuthInterceptor.kt        # Bearer Token 注入
│   │   └── BaseUrlInterceptor.kt     # 运行时切换服务器地址
│   ├── update/UpdateChecker.kt       # 检查更新（直连 GitHub raw JSON）
│   └── repository/CloudreveRepository.kt
├── di/NetworkModule.kt
└── ui/
    ├── theme/                # Material 3 主题与配色
    ├── navigation/           # 路由与 NavHost
    ├── login/                # 登录页
    ├── files/                # 文件浏览
    ├── transfer/             # 传输任务
    ├── about/                # 关于与更新弹窗
    └── settings/             # 设置
```

## 检查更新

应用启动时会读取仓库中的更新清单，若发现比当前 `versionCode`（缺省时比较 `versionName`）更高的版本则弹窗提示。

- 更新清单地址：`https://raw.githubusercontent.com/sanqiuqwq/Cloudreve-For-Android-Client/main/docs/check_update.json`
- 清单文件：[docs/check_update.json](docs/check_update.json)

发布新版本时，把 `app/build.gradle.kts` 的 `versionCode` / `versionName` 同步到清单中：

```json
{
  "versionCode": 2,
  "versionName": "1.1.0",
  "changelog": "1. 新增 xxx\n2. 修复 xxx",
  "downloadUrl": "https://github.com/sanqiuqwq/Cloudreve-For-Android-Client/releases/latest",
  "forceUpdate": false
}
```

| 字段 | 说明 |
| --- | --- |
| `versionCode` | 版本号（整数），优先用它判断新旧 |
| `versionName` | 版本名，`versionCode` 缺省时用于比较 |
| `changelog` | 更新说明，展示在弹窗中 |
| `downloadUrl` | 点击「前往更新」时打开的地址 |
| `forceUpdate` | 为 `true` 时弹窗不可关闭 |

> 注意：必须使用 `raw.githubusercontent.com` 原始地址，`github.com/.../blob/...` 返回的是 HTML 页面而非 JSON。

## 关于 / 开发者

- 开源地址：https://github.com/sanqiuqwq/Cloudreve-For-Android-Client
- 开发者：nnn
  - GitHub：https://github.com/sanqiuqwq
  - Telegram：[@qwqtop](https://t.me/qwqtop)
  - Telegram 群组：[@NekoCraftChat](https://t.me/NekoCraftChat)

## 技术栈

- Kotlin 2.1 + Coroutines / Flow
- Jetpack Compose + Material 3（支持动态取色）
- Navigation Compose
- Retrofit + OkHttp + kotlinx.serialization
- Hilt 依赖注入
- DataStore 持久化会话与偏好设置

## 构建

```bash
./gradlew assembleDebug
```

需要 Android SDK（compileSdk 35、build-tools 35.0.0）与 JDK 17。SDK 路径在 `local.properties` 中通过 `sdk.dir` 指定。

产物：`app/build/outputs/apk/debug/app-debug.apk`