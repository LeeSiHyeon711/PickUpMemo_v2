# ─────────────────────────────────────────────────────────────────────────────
# PickUpMemo v3.1 R8/ProGuard 규칙
#
# 라이브러리(Room/OkHttp/Coroutines)는 대부분 consumer proguard 규칙을 동봉하므로
# 별도 keep이 거의 필요 없다. 여기서는 (1) 안전 keep, (2) okhttp 선택 의존성 경고 무시,
# (3) release 빌드에서 진단 로그(Log.d/Log.v) 제거만 명시한다.
# ─────────────────────────────────────────────────────────────────────────────

# --- Kotlin 메타데이터/코루틴 ---------------------------------------------------
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**

# --- Room: 엔티티/DAO는 리플렉션·생성코드와 결선되므로 멤버 보존 ----------------
# (room-runtime이 consumer 규칙을 포함하지만, 엔티티 필드명 보존을 명시적으로 둔다)
-keep class com.itmakesome.pickupmemo2.data.** { *; }

# --- OkHttp / Okio: 선택적(런타임에 없을 수 있는) 의존성 경고 무시 --------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- org.json은 안드로이드 런타임 제공(별도 keep 불필요) -------------------------

# ─────────────────────────────────────────────────────────────────────────────
# release 진단 로그 제거: Log.d / Log.v 호출을 R8가 부작용 없는 코드로 보고 삭제.
# (proguard-android-optimize.txt 사용 시에만 적용됨. Log.w/Log.e/Log.i는 유지)
# ─────────────────────────────────────────────────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
