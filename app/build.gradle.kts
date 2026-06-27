import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// local.properties에서 카카오 REST API 키를 읽는다. 파일이 없거나 항목이 없으면 빈 문자열 fallback.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val kakaoRestKey: String = localProps.getProperty("KAKAO_REST_API_KEY") ?: ""

android {
    namespace = "com.itmakesome.pickupmemo2"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.itmakesome.pickupmemo2"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "KAKAO_REST_API_KEY", "\"$kakaoRestKey\"")
    }

    buildTypes {
        release {
            // 내부 배터리/안정성 테스트용: release를 debug 키로 서명해 바로 설치 가능하게 한다.
            // (정식 배포 시 별도 서명키로 교체할 것 — 안정화 테스트 한정)
            signingConfig = signingConfigs.getByName("debug")
            // v3.1 안정화: R8 minify 활성화 + 코드 최적화로 접근성 이벤트 처리 비용 절감.
            isMinifyEnabled = true
            // shrinkResources는 리소스 제거 리스크가 있어 안정화 패치에서는 보류(필요 시 후속 이슈).
            isShrinkResources = false
            proguardFiles(
                // optimize 버전이라야 proguard-rules.pro의 assumenosideeffects(로그 제거)가 적용됨.
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
