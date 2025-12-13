plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.autoglm.helper"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.autoglm.helper"
        minSdk = 24  // Android 7.0
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("KEYSTORE_PATH") ?: System.getenv("KEYSTORE_FILE")
            val storePwd = System.getenv("KEYSTORE_PASSWORD") ?: System.getenv("STORE_PASSWORD")
            val alias = System.getenv("KEY_ALIAS") ?: System.getenv("KEYSTORE_ALIAS")
            val keyPwd = System.getenv("KEY_PASSWORD") ?: System.getenv("KEYSTORE_KEY_PASSWORD")

            if (!storeFilePath.isNullOrBlank() && !storePwd.isNullOrBlank() && !alias.isNullOrBlank() && !keyPwd.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = storePwd
                keyAlias = alias
                keyPassword = keyPwd
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    // NanoHTTPD - 轻量级HTTP 服务器
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    
    // JSON 处理 (Android 自带，但显式声明)
    // implementation("org.json:json:20230227")
}
