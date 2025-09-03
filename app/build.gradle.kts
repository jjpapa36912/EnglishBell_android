// app/build.gradle.kts

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
        versionCode = 5
        versionName = "1.4"
// 기본값(혹시 모를 누락 방지용) - 디버그에서 override됨
        manifestPlaceholders += mapOf(
            "ADMOB_APP_ID" to "ca-app-pub-2190585582842197~5998280487" // 테스트 App ID
        )
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    /**
     * ✅ 릴리즈 서명 설정
     * 환경변수 사용 (권장): SIGNING_STORE_FILE, SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD
     * 예)
     *  macOS/Linux:
     *    export SIGNING_STORE_FILE=/absolute/path/to/release.jks
     *    export SIGNING_STORE_PASSWORD=xxxx
     *    export SIGNING_KEY_ALIAS=your_alias
     *    export SIGNING_KEY_PASSWORD=xxxx
     *
     *  Windows (PowerShell):
     *    setx SIGNING_STORE_FILE "C:\\path\\to\\release.jks"
     *    setx SIGNING_STORE_PASSWORD "xxxx"
     *    setx SIGNING_KEY_ALIAS "your_alias"
     *    setx SIGNING_KEY_PASSWORD "xxxx"
     */
    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("SIGNING_STORE_FILE") ?: ""
            if (storeFilePath.isNotBlank()) {
                storeFile = file(storeFilePath)
            }
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")

            enableV1Signing = false   // AAB 업로드 시 v1은 불필요
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            // ✅ 릴리즈에 서명 연결
            signingConfig = signingConfigs.getByName("release")

            // 필요시 난독화/최적화 활성화
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            manifestPlaceholders["ADMOB_APP_ID"] =
                "ca-app-pub-2190585582842197~5998280487"

            // 크래시 분석에 심볼 업로드하려면 아래 사용 (선택)
            // ndk {
            //     debugSymbolLevel = "FULL"
            // }
        }
        debug {
            // 디버그 빌드 구분용 (선택)
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["ADMOB_APP_ID"] =
                "ca-app-pub-3940256099942544~3347511713"
        }
    }

    // ✅ Java 17 / Kotlin 17
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // (선택) JDK toolchain 지정
    // kotlin {
    //     jvmToolchain(17)
    // }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // (선택) 리소스 충돌 회피
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
        }
    }

    // compose 플러그인을 쓰는 경우 보통 별도 compilerExtensionVersion 지정 불필요
    // composeOptions { kotlinCompilerExtensionVersion = "..." }
}

dependencies {
    // --- AndroidX 기본 ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation("com.google.android.gms:play-services-ads:23.3.0")

    // --- Compose BOM 및 UI ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // ✅ Material Icons (스피커/뒤로가기/삭제 아이콘 등)
    implementation("androidx.compose.material:material-icons-extended")

    // ✅ Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ⛔️ 중복 제거: 이미 BOM을 통해 material3를 가져오므로 아래 개별 버전 의존성은 제거했습니다.
    // implementation("androidx.compose.material3:material3:1.2.1")

    // --- Test ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
