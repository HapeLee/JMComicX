# AGENTS.md

本文件面向 AI 编码助手，提供快速理解和修改 JMComicX 代码库所需的上下文。

## 项目概述

JMComicX 是使用 Kotlin、Jetpack Compose 与 Compose Miuix 构建的 Android 第三方漫画客户端。工程将易变的协议/网络/会话/图片处理能力隔离在 `core` 模块，`app` 只负责 Android 和 UI。

- **语言**: Kotlin 2.4
- **Android**: minSdk 33 / targetSdk 37
- **构建**: AGP 9.5、Gradle 9.7、JDK 21
- **许可证**: GPL-3.0

## 模块结构

```
JMComicX/
├── app/          Android APP、Compose 界面、导航
│   └── src/main/kotlin/io/kazusa/jmcomic/
├── core/         协议、网络、加解密、线路、图片、会话（纯 Kotlin，不依赖 Android UI）
│   └── src/main/kotlin/io/kazusa/jmcomic/core/
├── gradle/libs.versions.toml   依赖版本目录
└── version.properties          统一版本号（VERSION_CODE / VERSION_NAME）
```

依赖方向固定：`app -> core`。`core` 不得引用 `app` 或任何 Android UI 依赖。

### app 模块

- **包名 / namespace**: `io.kazusa.jmcomic`
- **applicationId**: `io.kazusa.jmcomic`（debug 包含 `.debug` 后缀：`io.kazusa.jmcomic.debug`，可与 release 并行安装）
- 负责 Compose 页面、MIUIX 组件、导航、转场
- 首页、搜索、详情、账号、签到、设置、阅读器、离线下载
- Android Keystore 凭据加密、SharedPreferences、Coil 缓存
- 将 `core` 的 `JmxResult<T>` 转换为 UI 状态

### core 模块

纯 Kotlin JVM 模块，按职责分包：

| 包 | 职责 |
|---|---|
| `api` | API 路由、请求/响应模型、数据映射 |
| `cache` | KV 存储、JSON 响应缓存、协议状态持久化 |
| `chapter` | 章节模板解析、图片地址规划、分段还原 |
| `crypto` | AES-ECB 响应解密、哈希 |
| `download` | 下载任务、批量执行、重试、图源故障切换 |
| `image` | 图片管道、乱序还原、图源路由、PNG 编码 |
| `network` | HTTP 客户端、API 端点、域名服务、健康评分、重试策略 |
| `protocol` | API Token、路由、版本、协议常量 |
| `result` | `JmxResult<T>`、错误分类、用户消息 |
| `runtime` | `JmxCore` 门面、初始化、诊断、探针、冒烟测试 |
| `security` | 字节加密、加密存储封装 |
| `session` | Cookie 持久化、AVS 同步、会话管理 |

## 构建

### Debug APK

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
```

### Release APK（需签名环境变量）

```powershell
$env:JMX_RELEASE_KEYSTORE_FILE="path/to/release.keystore"
$env:JMX_RELEASE_STORE_PASSWORD="<store password>"
$env:JMX_RELEASE_KEY_ALIAS="<key alias>"
$env:JMX_RELEASE_KEY_PASSWORD="<key password>"
.\gradlew.bat :app:lintRelease :app:assembleRelease --no-daemon
```

签名变量缺失时 `preReleaseBuild` 会主动失败，不会静默生成未签名包。

## 测试

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:lintDebug --no-daemon
```

三层测试：

1. **纯逻辑单元测试**: 协议、Token、解密、映射、分页、版本、阅读计算
2. **MockWebServer 测试**: 请求路径、参数、Cookie、重试、登录、错误响应
3. **真实环境测试**: 动态域名、登录、收藏、历史、签到、章节、图片还原（通过环境变量获取凭据）

普通测试不会读取真实账号。`core` 测试使用 JUnit 4 + OkHttp MockWebServer。

## 版本管理

版本号定义在 `version.properties`，与同作者的 Matter 一致：

```properties
VERSION_MAJOR=2
VERSION_MINOR=8
VERSION_PATCH=3
VERSION_SUFFIX=0
VERSION_CODE_BASE=1000000
```

- `versionName` = `MAJOR.MINOR.PATCH`；`VERSION_SUFFIX=1` 时由 CI 追加 `-beta.N`（N 以已有 tag 递增，本地固定 `-beta.local`）。
- `versionCode` = `VERSION_CODE_BASE + git rev-list --count HEAD`。旧方案是 `major*10000+minor*100+patch`（2.8.2 = 20802），偏移不可再改，否则覆盖安装断链。
- 构建脚本从不回写任何文件；CI 用 `-PjmxVersionName` / `-PjmxVersionCode` 注入。浅克隆 `--depth 1` 只会算出 1，需要 `fetch-depth: 0`。
- 发布前只改 `VERSION_MAJOR/MINOR/PATCH/SUFFIX`。

## Build Types

| Build Type | 特殊配置 |
|---|---|
| `debug` | `COMIC_UPDATE_HINTS = true`（设置页显示更新提示自测入口） |
| `release` | `COMIC_UPDATE_HINTS = false`、启用 R8 混淆与资源压缩 |

debug 配置了 `applicationIdSuffix = ".debug"`（包名为 `io.kazusa.jmcomic.debug`），可与正式包并存安装。

## CI / GitHub Actions

工作流（`.github/workflows/`）：

- `verify.yml` — push `main` / PR：`core` 与 `app` 单测 + Debug 构建
- `auto-release.yml` — 测试 + Lint + R8 + 签名 + Release APK；`publish=false` 时只出 artifact
- `release.yml` — 正式发布唯一入口（`workflow_dispatch`，仅 `HapeLee` 在 `main` 可用），打 `v<versionName>` tag 并建 GitHub Release，随后推送 Telegram
- `android-debug.yml` — 手动触发的调试包

签名证书由 Secrets 提供：`JMX_RELEASE_KEYSTORE_BASE64` / `JMX_RELEASE_STORE_PASSWORD` / `JMX_RELEASE_KEY_ALIAS` / `JMX_RELEASE_KEY_PASSWORD`。缺任一项 `assembleRelease` 直接失败（`preReleaseBuild` 校验）。Telegram 分发用 `TELEGRAM_BOT_TOKEN` / `TELEGRAM_CHAT_ID`（逗号分隔可多个会话），未配置则跳过。

## 编码约定

- 语言：Kotlin，JVM target 21
- UI：Jetpack Compose + Compose Miuix 组件
- 导航：MIUIX Nav
- 网络：OkHttp 5（`core` 和 `app` 通过 Coil OkHttp fetcher 共用连接池）
- 序列化：Kotlin Serialization
- 中文简繁转换：OpenCC4J
- 异步：Kotlin Coroutines
- 依赖管理：Gradle Version Catalog (`gradle/libs.versions.toml`)
- `core` 模块不得引入 Android 依赖
- 保持现有注释和文档字符串

## 关键入口

| 文件 | 说明 |
|---|---|
| `app/.../JmxApplication.kt` | Application 入口 |
| `app/.../MainActivity.kt` | Activity 入口 |
| `app/.../JmxNavigation.kt` | 导航图 |
| `app/.../JmxApp.kt` | 根 Composable |
| `app/.../AppJmxCore.kt` | 应用层 JmxCore 封装 |
| `core/.../runtime/JmxCore.kt` | Core 门面 API |
| `core/.../runtime/JmxCoreInitializer.kt` | Core 初始化 |
