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
        // ?活憿舐內?游???1.X.X(??1 雿??脖??詨?,憒敞閮?141 ??"1.4.1");皛?9 ?脖?:1.4.9??.5.0??.9.9??.0.0??        // versionCode 隞??芸隤?+1(Android 閬?,?＊蝷箇?甈∟????        versionCode = 263
        versionName = "2.7.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // 蝝??????repo ??commit ?摰?keystore嚗???CI build 蝪賢?銝?氬?        // 銋???Android SDK ?身 debug.keystore嚗? CI runner 瘥活頝撱?random
        // debug key ??v1.0.24 頝?v1.0.26 蝪賢?銝泵 ??Android ?????函?撘?隞?        // ???隞嗉?蝒?        // ?? v1.0.29 韏?repo 撌?public ??keystore 頝?蝣潔?頝??祇???隢??犖?賣??
        // key ?賡?蝪賢? APK嚗?蝝??sideload + APK ?芸? GitHub release ????憸券?舀??        // ?亥? rotate keystore嚗????蝵桅????貉????啁????血???隞嗉?蝒?鋆?        create("imagine") {
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
            // v1.0.44: debug 銋?????imagine.keystore ??銝 CI 瘥活頝?冽?
            // debug.keystore 蝪踝???debug APK 敹萱??隞嗉?蝒? 鋡怨翰 uninstall ??            // 甇瑕鞈??冽??ebug 頝?release ? applicationIdSuffix 銝?
            // 隞?拙?app 銝血?嚗?鈭???瘝雿??            signingConfig = signingConfigs.getByName("imagine")
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
        // 蝝????release build 銝 lint fatal error 銝剜 (靘? deprecated API??        // tooling ?急??⊥?????case)?int report 隞??Ｗ靘炎閬?        checkReleaseBuilds = false
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
    implementation(libs.coil.video) // v2.3.0 ??蝮桀?嚗蔣???急
    implementation(libs.coil.svg)   // v1.8.1: OpenRouter ??璅∪?(Recraft)??SVG 銋?汗(ServiceLoader ?芸?閮餃?)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // Background work
    implementation(libs.androidx.work.runtime.ktx)
}
