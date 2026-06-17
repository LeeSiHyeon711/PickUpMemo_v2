package com.itmakesome.pickupmemo2.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import com.itmakesome.pickupmemo2.service.PickupAccessibilityService

/**
 * 접근성 서비스 / 오버레이("다른 앱 위에 표시") 권한 활성 여부 판정 유틸 (FEAT-08).
 *
 * 두 권한 모두 시스템 바인드/OS 설정 권한이므로 코드로 직접 켜고 끌 수 없다.
 * 앱은 OS 설정값을 읽어 활성 여부만 판정하고 설정 화면으로 이동하는 Intent를 제공한다.
 *
 * 접근성 판정: Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES(":" 구분 목록)에서
 * [PickupAccessibilityService] ComponentName 포함 여부를 확인한다.
 * (v1 PermissionChecker.isAccessibilityEnabled + matchesComponent 로직 재사용, 서비스명만 교체)
 */
object PermissionChecker {

    /**
     * [PickupAccessibilityService]가 OS 설정에서 활성화돼 있으면 true.
     *
     * ENABLED_ACCESSIBILITY_SERVICES 는 "pkgA/.SvcA:pkgB/.SvcB" 형태의 ':' 구분 목록이다.
     * 우리 서비스 ComponentName을 flatten 형태(짧은/긴 표기 모두)로 비교해 포함 여부를 판정한다.
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, PickupAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (component in splitter) {
            if (matchesComponent(component, expected)) return true
        }
        return false
    }

    /**
     * "다른 앱 위에 표시"(SYSTEM_ALERT_WINDOW) 권한이 부여돼 있으면 true.
     */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * 시스템 "접근성" 설정 화면으로 이동하는 Intent.
     */
    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * "다른 앱 위에 표시" 권한 설정 화면(패키지 한정)으로 이동하는 Intent.
     */
    fun overlaySettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * 설정 목록의 한 항목 문자열이 [expected] 컴포넌트와 같은지 비교한다.
     * flatten 짧은/긴 표기를 모두 허용하고 unflattenFromString으로도 대조해
     * 표기 차이에 견고하게 한다.
     */
    private fun matchesComponent(entry: String, expected: ComponentName): Boolean {
        val trimmed = entry.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.equals(expected.flattenToString(), ignoreCase = true)) return true
        if (trimmed.equals(expected.flattenToShortString(), ignoreCase = true)) return true
        val parsed = ComponentName.unflattenFromString(trimmed) ?: return false
        return parsed == expected
    }
}
