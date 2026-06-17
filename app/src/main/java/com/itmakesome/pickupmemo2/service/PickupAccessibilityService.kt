package com.itmakesome.pickupmemo2.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.itmakesome.pickupmemo2.data.MemoRepository
import com.itmakesome.pickupmemo2.matcher.DedupGuard
import com.itmakesome.pickupmemo2.matcher.MemoMatcher
import com.itmakesome.pickupmemo2.matcher.StoreExtractor
import com.itmakesome.pickupmemo2.overlay.MemoPopupController
import com.itmakesome.pickupmemo2.util.Packages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 접근성 서비스 본문 (FEAT-07).
 *
 * 배민커넥트(TARGET_PACKAGE) 화면 변경 이벤트를 수신해 노드 트리 텍스트를 조립하고,
 * StoreExtractor → MemoMatcher → DedupGuard → MemoPopupController 순으로 결선한다.
 *
 * - lifecycleScope 없음(AccessibilityService는 Activity/Fragment가 아님).
 *   코루틴은 전용 serviceScope(SupervisorJob + IO)를 사용하고 onDestroy에서 cancel.
 * - 매칭·팝업 호출은 접근성 콜백(메인 스레드)에서 동기 실행한다.
 *   getCachedSnapshot()은 메모리 읽기라 지연 없음.
 */
class PickupAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onServiceConnected() {
        super.onServiceConnected()
        MemoRepository.init(applicationContext)
        serviceScope.launch {
            MemoRepository.refreshCache()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val pkg = event.packageName?.toString()
        if (pkg.isNullOrBlank()) return
        if (pkg == applicationContext.packageName) return
        if (pkg in Packages.EXCLUDED_PACKAGES) return
        if (pkg != Packages.TARGET_PACKAGE) return

        val segments = LinkedHashSet<String>()
        val root = event.source ?: rootInActiveWindow ?: return
        collectNode(root, segments)
        if (segments.isEmpty()) return

        val fullText = segments.joinToString(" / ")
        val candidate = StoreExtractor.extract(fullText) ?: return
        val matched = MemoMatcher.match(candidate, MemoRepository.getCachedSnapshot()) ?: return
        if (!DedupGuard.shouldShow(matched.id)) return
        MemoPopupController.show(this, matched)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onInterrupt() {
        // 수집형 서비스라 별도 처리 불필요.
    }

    /**
     * 노드 트리를 깊이 우선으로 순회하며 text / contentDescription / viewIdResourceName
     * 세그먼트를 [out]에 모은다. [MAX_SEGMENTS] 도달 시 순회 중단.
     *
     * 세그먼트 형식: `텍스트 | desc=설명 | id=리소스아이디`
     * (v1 ScreenAccessibilityService.collectNode 86~110행 패턴 재사용)
     */
    private fun collectNode(node: AccessibilityNodeInfo, out: LinkedHashSet<String>) {
        if (out.size >= MAX_SEGMENTS) return

        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val rawId = node.viewIdResourceName?.trim().orEmpty()
        // "패키지:id/tv_store_name" → "tv_store_name" 으로 축약.
        val id = if (rawId.isNotEmpty()) rawId.substringAfterLast('/') else ""

        if (text.isNotEmpty() || desc.isNotEmpty()) {
            val parts = ArrayList<String>(3)
            if (text.isNotEmpty()) parts.add(text)
            if (desc.isNotEmpty()) parts.add("desc=$desc")
            if (id.isNotEmpty()) parts.add("id=$id")
            out.add(parts.joinToString(" | "))
            if (out.size >= MAX_SEGMENTS) return
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            collectNode(child, out)
            if (out.size >= MAX_SEGMENTS) return
        }
    }

    private companion object {
        /** 한 이벤트에서 수집하는 최대 세그먼트 수 (무한 순회·거대 텍스트 방지). */
        const val MAX_SEGMENTS = 200
    }
}
