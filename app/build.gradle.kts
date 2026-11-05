import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

// 显示版本由 version.properties 手工维护，内部编号由 Git 提交数派生。
// 构建本身从不修改任何计数：同一提交在任何机器上得到同一个 versionCode。
// 正式发布只由 GitHub Actions 触发（.github/workflows/release.yml）。
val versionProperties = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}

fun versionSegment(key: String): Int {
    val raw = versionProperties.getProperty(key, "").trim()
    val value = raw.toIntOrNull()
    require(value != null && value >= 0) { "$key must be a non-negative integer, was '$raw'" }
    require(raw == value.toString()) { "$key must not have leading zeros, was '$raw'" }
    return value
}

val versionMajor = versionSegment("VERSION_MAJOR")
val versionMinor = versionSegment("VERSION_MINOR")
val versionPatch = versionSegment("VERSION_PATCH")
val versionSuffix = versionSegment("VERSION_SUFFIX")
val versionCodeBase = versionSegment("VERSION_CODE_BASE")
require(versionSuffix == 0 || versionSuffix == 1) { "VERSION_SUFFIX must be 0 (release) or 1 (prerelease)" }

val baseVersionName = "$versionMajor.$versionMinor.$versionPatch"
// 预发布序号 -beta.N 需要已有 tag 才能计算，由 CI 通过 -PjmxVersionName 传入；
// 本地预发布构建固定为 -beta.local，避免与线上已发布版本重名。
val appVersionName = providers.gradleProperty("jmxVersionName")
    .getOrElse(if (versionSuffix == 1) "$baseVersionName-beta.local" else baseVersionName)
    .trim()

// 提交数是仓库属性：本地与 CI 一致，无需回写文件，也不存在并发分配。
// 注意 --depth 1 的浅克隆只会得到 1，CI 必须 fetch-depth: 0。
val gitCommitCount = runCatching {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
    }.standardOutput.asText.get().trim().toInt()
}.getOrNull()

// 旧方案是 major*10000 + minor*100 + patch（2.8.2 = 20802），
// 因此编号必须叠加 VERSION_CODE_BASE 才能覆盖安装，见 version.properties。
val appVersionCode = providers.gradleProperty("jmxVersionCode").orNull?.trim()?.toIntOrNull()
    ?: (versionCodeBase + (gitCommitCount ?: 0))
require(appVersionCode in 1..2_100_000_000) { "versionCode must be between 1 and 2100000000, was $appVersionCode" }
require(Regex("""(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-(alpha|beta|rc)\.([1-9][0-9]*|local))?""").matches(appVersionName)) {
    "versionName must be X.Y.Z or X.Y.Z-alpha.N / beta.N / rc.N, was $appVersionName"
}
val releaseKeystoreFile = providers.gradleProperty("RELEASE_STORE_FILE")
    .orElse(providers.environmentVariable("JMX_RELEASE_KEYSTORE_FILE"))
    .orElse("signing/jmx-release.keystore")
    .get()
    .let(::file)
val releaseStorePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD")
    .orElse(providers.environmentVariable("JMX_RELEASE_STORE_PASSWORD"))
    .orNull
val releaseKeyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS")
    .orElse(providers.environmentVariable("JMX_RELEASE_KEY_ALIAS"))
    .orNull
val releaseKeyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD")
    .orElse(providers.environmentVariable("JMX_RELEASE_KEY_PASSWORD"))
    .orNull
val releaseSigningReady = releaseKeystoreFile.isFile &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

@Suppress("UnstableApiUsage")
android {
    namespace = "dev.jmx.client"
    buildToolsVersion = "37.0.0"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "dev.jmx.client"
        minSdk = 33
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("jmxRelease") {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("jmxRelease")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 设置页里的"漫画更新提示"自测入口（立即检查 / 模拟一次 / 清除全部）只在调试构建出现；
            // release 为编译期常量 false，整块被折叠掉，正式包不含这几个按钮。功能本身照常发布。
            buildConfigField("boolean", "COMIC_UPDATE_HINTS", "false")
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            if (releaseSigningReady) {
                signingConfig = signingConfigs.findByName("jmxRelease")
            }
            buildConfigField("boolean", "COMIC_UPDATE_HINTS", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild") {
        doFirst {
            check(releaseSigningReady) {
                "Release signing is not configured. Set JMX_RELEASE_KEYSTORE_FILE, " +
                    "JMX_RELEASE_STORE_PASSWORD, JMX_RELEASE_KEY_ALIAS and JMX_RELEASE_KEY_PASSWORD."
            }
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.backdrop)
    implementation(libs.shapes)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.navigationevent.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.miuix.blur.android)
    implementation(libs.miuix.icons.android)
    implementation(libs.miuix.nav.android)
    implementation(libs.miuix.preference.android)
    implementation(libs.miuix.ui.android)
    implementation(libs.okhttp)
    implementation(libs.opencc4j)

    testImplementation(libs.junit)
}
