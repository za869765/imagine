plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.za869765.imagine"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.za869765.imagine"
        minSdk = 26
        targetSdk = 35
        // 版次顯示整合為 1.X.X(各 1 位十進位數字,如累計 141 → "1.4.1");滿 9 進位:1.4.9→1.5.0→…1.9.9→2.0.0。
        // versionCode 仍各自單調 +1(Android 規定,與顯示版次脫鉤)。
        versionCode = 150
        versionName = "1.4.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // 純自用 — 用 repo 內 commit 的固定 keystore，所有 CI build 簽名一致。
        // 之前用 Android SDK 預設 debug.keystore，但 CI runner 每次跑新建 random
        // debug key → v1.0.24 跟 v1.0.26 簽名不符 → Android 拒裝「應用程式套件
        // 與現有套件衝突」。
        // ⚠️ v1.0.29 起 repo 已 public — keystore 跟密碼也跟著公開。理論上有人能拿這把
        // key 偽造同簽名 APK，但純自用 sideload + APK 只從 GitHub release 拿 → 風險可接受。
        // 若要 rotate keystore：所有現有裝置需「先卸載再裝新版」，否則「套件衝突」拒裝。
        create("imagine") {
            storeFile = file("imagine.keystore")
            storePassword = "imagine123"
            keyAlias = "imagine"
            keyPassword = "imagine123"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // v1.0.44: debug 也綁同一把 imagine.keystore — 不然 CI 每次跑用隨機
            // debug.keystore 簽，升 debug APK 必踩「套件衝突」→ 被迫 uninstall →
            // 歷史資料全清。debug 跟 release 因為 applicationIdSuffix 不同
            // 仍是兩個 app 並存，不互覆蓋，沒副作用。
            signingConfig = signingConfigs.getByName("imagine")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("imagine")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // 純自用 — release build 不為 lint fatal error 中斷 (例如 deprecated API、
        // tooling 暫時無法分析的 case)。lint report 仍會產出供檢視。
        checkReleaseBuilds = false
        abortOnError = false
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Coroutines + Serialization
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // HTTP
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Security
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)

    // Media
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // Background work
    implementation(libs.androidx.work.runtime.ktx)
}
