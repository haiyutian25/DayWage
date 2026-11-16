import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Release signing comes from `keystore.properties` (local builds) or environment variables (CI).
 * Neither is committed; without them the release build is simply left unsigned.
 */
val signing = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signingValue(key: String, env: String): String? = signing.getProperty(key) ?: System.getenv(env)

android {
    namespace = "com.lhzkml.DayWage"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lhzkml.DayWage"
        minSdk = 26
        targetSdk = 36
        versionCode = 25
        versionName = "1.10.0"
    }

    signingConfigs {
        val storePath = signingValue("storeFile", "SIGNING_STORE_FILE")
        if (storePath != null) {
            create("release") {
                storeFile = rootProject.file(storePath)
                storePassword = signingValue("storePassword", "SIGNING_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "SIGNING_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // 调试包和正式包并存：
        //   包名   com.lhzkml.DayWage.dev
        //   名字   工时账本 Dev
        //   图标   src/debug/res 里的带 DEBUG 角标版本
        //   签名   Android 默认 debug keystore（和正式版刻意区分开）
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // No git commit stamp in the APK, so builds of the same code always match byte for byte.
            vcsInfo.include = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Leave out the encrypted dependency list Google Play reads.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.haze)
    implementation(libs.haze.blur)
    // Liquid glass (the picture behind bends at the edges) for the tab bar, on Android 13 and newer. Apache-2.0.
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)
}
