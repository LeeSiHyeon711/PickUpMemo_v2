package com.itmakesome.pickupmemo2.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.itmakesome.pickupmemo2.BuildConfig
import com.itmakesome.pickupmemo2.data.BaeminLogRepository
import com.itmakesome.pickupmemo2.data.MemoRepository
import com.itmakesome.pickupmemo2.matcher.AddressExtractor
import com.itmakesome.pickupmemo2.matcher.DedupGuard
import com.itmakesome.pickupmemo2.matcher.MemoMatcher
import com.itmakesome.pickupmemo2.matcher.StoreExtractor
import com.itmakesome.pickupmemo2.overlay.MemoPopupController
import com.itmakesome.pickupmemo2.route.RouteService
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
 * StoreExtractor → MemoMatcher → AddressExtractor → DedupGuard → MemoPopupController 순으로 결선한다.
 *
 * - lifecycleScope 없음(AccessibilityService는 Activity/Fragment가 아님).
 *   코루틴은 전용 serviceScope(SupervisorJob + IO)를 사용하고 onDestroy에서 cancel.
 * - RouteService.resolve는 serviceScope에서 비동기 실행한다.
 */
class PickupAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onServiceConnected() {
        super.onServiceConnected()
        MemoRepository.init(applicationContext)
        BaeminLogRepository.init(applicationContext)
        RouteService.init(BuildConfig.KAKAO_REST_API_KEY)
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
        maybeLogBaemin(pkg, event.eventType, fullText)

        val snapshot = MemoRepository.getCachedSnapshot()
        val candidate = StoreExtractor.extract(fullText)
        val matched = candidate?.let { MemoMatcher.match(it, snapshot) }
        val addr = AddressExtractor.extract(segments.toList(), fullText)

        if (matched == null && addr == null) return

        val hasRoute = addr != null

        if (matched != null) {
            if (!DedupGuard.shouldShow(matched.id)) return
        } else {
            if (!DedupGuard.shouldShow(addr!!.key())) return
        }

        when {
            matched != null -> {
                val token = MemoPopupController.show(this, matched, hasRoute)
                if (hasRoute) {
                    serviceScope.launch {
                        val r = RouteService.resolve(addr!!.pickup, addr.dest)
                        MemoPopupController.updateRoute(token, r)
                    }
                }
            }
            addr != null && BuildConfig.DEBUG -> {
                val token = MemoPopupController.show(this, null, true)
                serviceScope.launch {
                    val r = RouteService.resolve(addr.pickup, addr.dest)
                    MemoPopupController.updateRoute(token, r)
                }
            }
            addr != null -> {
                serviceScope.launch {
                    val r = RouteService.resolve(addr.pickup, addr.dest)
                    if (r != null) {
                        val token = MemoPopupController.show(this@PickupAccessibilityService, null, true)
                        MemoPopupController.updateRoute(token, r)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onInterrupt() {
        // 수집형 서비스라 별도 처리 불필요.
    }

    private fun collectNode(node: AccessibilityNodeInfo, out: LinkedHashSet<String>) {
        if (out.size >= MAX_SEGMENTS) return

        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val rawId = node.viewIdResourceName?.trim().orEmpty()
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

    private var lastLoggedText: String? = null
    private var lastLoggedAt: Long = 0L

    private fun maybeLogBaemin(pkg: String, eventType: Int, fullText: String) {
        if (fullText.isBlank()) return
        val now = System.currentTimeMillis()
        if (fullText == lastLoggedText && now - lastLoggedAt < LOG_DEDUP_MS) return
        lastLoggedText = fullText
        lastLoggedAt = now
        val typeName = AccessibilityEvent.eventTypeToString(eventType)
        serviceScope.launch { BaeminLogRepository.save(pkg, typeName, fullText) }
    }

    private companion object {
        const val MAX_SEGMENTS = 200
        const val LOG_DEDUP_MS = 3000L
    }
}
