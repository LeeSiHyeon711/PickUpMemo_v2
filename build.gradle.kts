// 루트 빌드 스크립트 — 플러그인 버전만 선언(apply는 모듈에서)
// AGP 8.5.2 + Kotlin 1.9.24 + KSP 1.9.24-1.0.20 (Room 컴파일러용)
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
