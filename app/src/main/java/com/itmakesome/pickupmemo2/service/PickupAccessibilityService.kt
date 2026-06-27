package com.itmakesome.pickupmemo2.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
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

    /** TYPE_WINDOW_CONTENT_CHANGED throttle 기준 시각(ms). */
    @Volatile
    private var lastContentHandledAt = 0L

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

        // ① 대상 패키지(배민커넥트)가 아니면 가장 먼저 즉시 return — 다른 앱 이벤트는 일절 처리하지 않는다(P0/P1).
        val pkg = event.packageName?.toString()
        if (pkg.isNullOrBlank()) return
        if (pkg != Packages.TARGET_PACKAGE) return

        val eventType = event.eventType

        // ② content-changed는 배민 지도/타이머 화면에서 초당 수 회 폭주 → 강한 throttle(700ms).
        //    window-state-changed(화면 전환)는 드물고 중요하므로 throttle 없이 통과.
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val now = System.currentTimeMillis()
            if (now - lastContentHandledAt < CONTENT_THROTTLE_MS) return
            lastContentHandledAt = now
        }

        // 보수적 방식(v3.1): traversal은 콜백 스레드에서 수행하되 depth/node/time budget으로 상한을 건다.
        //   (AccessibilityNodeInfo를 백그라운드 스레드에서 직접 만지는 방식은 OEM 호환성 리스크가 있어 회피)
        val t0 = System.nanoTime()
        val segments = LinkedHashSet<String>()
        val budget = TraversalBudget()
        val root = event.source ?: rootInActiveWindow ?: return
        collectNode(root, segments, depth = 0, budget = budget)

        if (BuildConfig.DEBUG) {
            val ms = (System.nanoTime() - t0) / 1_000_000
            if (ms >= SLOW_EVENT_WARN_MS) {
                Log.w(
                    TAG,
                    "slow event type=${AccessibilityEvent.eventTypeToString(eventType)} " +
                        "nodes=${budget.nodes} segments=${segments.size} ${ms}ms" +
                        if (budget.exceeded()) " (budget capped)" else ""
                )
            }
        }

        if (segments.isEmpty()) return

        val fullText = segments.joinToString(" / ")
        maybeLogBaemin(pkg, eventType, fullText)

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
                        val outcome = RouteService.resolve(addr!!.pickup, addr.dest)
                        MemoPopupController.updateRoute(token, outcome.route)
                    }
                }
            }
            addr != null && BuildConfig.DEBUG -> {
                val token = MemoPopupController.show(this, null, true)
                serviceScope.launch {
                    val outcome = RouteService.resolve(addr.pickup, addr.dest)
                    MemoPopupController.updateRoute(token, outcome.route)
                }
            }
            addr != null -> {
                serviceScope.launch {
                    val outcome = RouteService.resolve(addr.pickup, addr.dest)
                    if (outcome.route != null) {
                        val token = MemoPopupController.show(this@PickupAccessibilityService, null, true)
                        MemoPopupController.updateRoute(token, outcome.route)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        // 서비스 종료 시 남아 있을 수 있는 오버레이 팝업을 확실히 제거(P3).
        MemoPopupController.dismiss()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onInterrupt() {
        // 접근성 피드백 중단 시에도 오버레이가 남지 않도록 정리(P3).
        MemoPopupController.dismiss()
    }

    /**
     * traversal 1회의 예산(node 수 / 경과 시간). depth는 재귀 인자로 별도 관리한다.
     * childCount가 큰 화면에서 무제한 순회로 콜백 스레드/시스템을 막는 것을 방지한다(P2).
     */
    private class TraversalBudget(private val startNs: Long = System.nanoTime()) {
        var nodes = 0
        fun exceeded(): Boolean =
            nodes >= MAX_NODES || (System.nanoTime() - startNs) > MAX_TRAVERSAL_NS
    }

    private fun collectNode(
        node: AccessibilityNodeInfo,
        out: LinkedHashSet<String>,
        depth: Int,
        budget: TraversalBudget
    ) {
        // depth / segment / node·time budget 중 하나라도 초과하면 즉시 중단.
        if (depth > MAX_DEPTH || out.size >= MAX_SEGMENTS || budget.exceeded()) return
        budget.nodes++

        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val rawId = node.viewIdResourceName?.trim().orEmpty()
        val id = if (rawId.isNotEmpty()) rawId.substringAfterLast('/') else ""

        // text/contentDescription/viewId가 모두 없는 노드는 세그먼트로 만들지 않는다(빈 컨테이너 최소 처리).
        if (text.isNotEmpty() || desc.isNotEmpty() || id.isNotEmpty()) {
            val parts = ArrayList<String>(3)
            if (text.isNotEmpty()) parts.add(text)
            if (desc.isNotEmpty()) parts.add("desc=$desc")
            if (id.isNotEmpty()) parts.add("id=$id")
            out.add(parts.joinToString(" | "))
            if (out.size >= MAX_SEGMENTS) return
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            if (budget.exceeded()) return
            val child = node.getChild(i) ?: continue
            collectNode(child, out, depth + 1, budget)
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
        const val TAG = "PickupA11yService"

        const val MAX_SEGMENTS = 200
        const val LOG_DEDUP_MS = 3000L

        /** content-changed throttle 간격(ms). 배민 지도 폭주 차단(P1). */
        const val CONTENT_THROTTLE_MS = 700L

        /** traversal 상한(P2). */
        const val MAX_DEPTH = 25
        const val MAX_NODES = 1500
        const val MAX_TRAVERSAL_NS = 40_000_000L // 40ms

        /** debug에서 이 시간(ms) 이상 걸린 이벤트만 경고 로그. */
        const val SLOW_EVENT_WARN_MS = 100L
    }
}
