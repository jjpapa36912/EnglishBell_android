plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.blessing.englishbell"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.blessing.englishbell"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // 필요시 네트워크 디버깅 옵션 등 추가 가능
        }
    }

    // ✅ Java 17 / Kotlin 17 (권장)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true   // ✅ 추가

    }

    // (composeCompilerExtensionVersion은 compose 플러그인 쓰면 보통 불필요)
    // composeOptions { kotlinCompilerExtensionVersion = "..." }
}

dependencies {
    // --- AndroidX 기본 ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // --- Compose BOM 및 UI ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // ✅ 추가: Material Icons (스피커/뒤로가기/삭제 아이콘 등 사용)
    implementation("androidx.compose.material:material-icons-extended")

    // ✅ 추가: Kotlin Coroutines (Flow/withContext 등 사용)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // (선택) 런타임 Compose 확장 — 필요 시 활성화
    // implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.compose.material3:material3:1.2.1") // 최신 안정 버전

    // --- Test ---
    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
