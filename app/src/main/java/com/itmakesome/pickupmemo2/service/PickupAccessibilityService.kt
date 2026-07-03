package com.itmakesome.pickupmemo2.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
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
        // (#28 진단계측) 서비스 생존 확인 + 기기 정보 1회 출력.
        //   OEM 배터리 정책이 접근성 서비스를 죽이는지, 의뢰자 기기가 어떤 기종/SDK인지
        //   Logcat만으로 판별하기 위한 근거 로그.
        Log.d(
            TAG,
            "service connected: manufacturer=${Build.MANUFACTURER} model=${Build.MODEL} sdk=${Build.VERSION.SDK_INT}"
        )
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
        val isContentChanged = eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

        // (#28) throttle보다 event.source 수집을 먼저 한다 — throttle에 걸린 이벤트라도
        //   카드 본문 신호가 있으면 버리지 않고 통과시켜야 하기 때문(방어 보강 4).
        //   보수적 방식(v3.1): traversal은 콜백 스레드에서 수행하되 depth/node/time budget으로 상한을 건다.
        //   (AccessibilityNodeInfo를 백그라운드 스레드에서 직접 만지는 방식은 OEM 호환성 리스크가 있어 회피)
        val t0 = System.nanoTime()
        val sourceNode = event.source
        val segments = LinkedHashSet<String>()
        val budget = TraversalBudget()
        val collectedFrom: String
        if (sourceNode != null) {
            collectedFrom = "event.source"
            collectNode(sourceNode, segments, depth = 0, budget = budget)
        } else {
            val root = rootInActiveWindow
            if (root == null) {
                Log.d(TAG, "popup skipped: reason=no-source-no-root type=${AccessibilityEvent.eventTypeToString(eventType)}")
                return
            }
            collectedFrom = "rootInActiveWindow(source-null-fallback)"
            collectNode(root, segments, depth = 0, budget = budget)
        }

        var fullText = segments.joinToString(" / ")
        val hasCardId = fullText.contains(CARD_SIGNAL_ID)
        val hasPickupLabel = fullText.contains(KEY_PICKUP)
        val hasDestLabel = fullText.contains(KEY_DEST)
        val hasCardBody = hasCardId || (hasPickupLabel && hasDestLabel)
        val hasWeakCardSignal = WEAK_CARD_SIGNAL_REGEX.containsMatchIn(fullText)

        Log.d(
            TAG,
            "source collected: from=$collectedFrom segments=${segments.size} " +
                "hasCardId=$hasCardId hasPickup=$hasPickupLabel hasDest=$hasDestLabel"
        )

        // (저사양 진단, #28) 카드 관련 신호가 있는 이벤트는 DEBUG 여부와 무관하게 소요시간/노드수/budget
        //   초과 여부를 남긴다 — 저사양 기기에서 카드 노드에 닿기 전에 budget이 잘리는지 확인하기 위함.
        run {
            val ms = (System.nanoTime() - t0) / 1_000_000
            val cappedNote = if (budget.exceeded()) " (budget capped)" else ""
            if (hasCardBody || hasWeakCardSignal) {
                Log.d(
                    TAG,
                    "traversal timing: from=$collectedFrom nodes=${budget.nodes} segments=${segments.size} " +
                        "${ms}ms$cappedNote hasCardBody=$hasCardBody"
                )
            } else if (BuildConfig.DEBUG && ms >= SLOW_EVENT_WARN_MS) {
                Log.w(
                    TAG,
                    "slow event type=${AccessibilityEvent.eventTypeToString(eventType)} " +
                        "nodes=${budget.nodes} segments=${segments.size} ${ms}ms$cappedNote"
                )
            }
            Unit
        }

        // ② content-changed는 배민 지도/타이머 화면에서 초당 수 회 폭주 → 강한 throttle(700ms).
        //    window-state-changed(화면 전환)는 드물고 중요하므로 throttle 없이 통과.
        //    (#28 방어 보강) 카드 본문 신호(id=신규배차_카드 또는 픽업지+전달지)가 있는 이벤트는
        //    throttle에 걸려도 버리지 않는다.
        if (isContentChanged) {
            val now = System.currentTimeMillis()
            val throttled = now - lastContentHandledAt < CONTENT_THROTTLE_MS
            when {
                throttled && !hasCardBody -> {
                    Log.d(TAG, "popup skipped: reason=throttle-blocked")
                    return
                }
                throttled && hasCardBody -> {
                    Log.d(TAG, "throttle bypassed: reason=card-signal-detected")
                }
                else -> {
                    Log.d(TAG, "throttle passed: normal interval")
                }
            }
            lastContentHandledAt = now
        }

        // ③ 조건부 root 재수집(#28 방어 보강 2): event.source에 카드 관련 신호(수락버튼/배차수락/
        //    카운트다운 등)만 있고 카드 본문(id=신규배차_카드 + 픽업지 + 전달지)이 없으면,
        //    그때만 rootInActiveWindow를 새 budget으로 재수집해 카드 본문 복구를 시도한다.
        //    순수 카운트다운 이벤트(카드 신호 자체가 없음)는 재수집도 하지 않고 조기 return한다.
        if (!hasCardBody) {
            if (!hasWeakCardSignal) {
                Log.d(TAG, "popup skipped: reason=no-card-signal")
                return
            }

            Log.d(TAG, "recollect: triggered reason=weak-card-signal-no-body")
            val root = rootInActiveWindow
            if (root == null) {
                Log.d(TAG, "recollect: skipped reason=root-null")
                Log.d(TAG, "popup skipped: reason=recollect-failed")
                return
            }

            val recollectBudget = TraversalBudget()
            val recollectSegments = LinkedHashSet<String>()
            collectNode(root, recollectSegments, depth = 0, budget = recollectBudget)
            val recollectText = recollectSegments.joinToString(" / ")
            val recHasCardId = recollectText.contains(CARD_SIGNAL_ID)
            val recHasPickupLabel = recollectText.contains(KEY_PICKUP)
            val recHasDestLabel = recollectText.contains(KEY_DEST)

            Log.d(
                TAG,
                "recollect: beforeSegments=${segments.size} afterSegments=${recollectSegments.size} " +
                    "beforeCardId=$hasCardId afterCardId=$recHasCardId " +
                    "afterHasPickup=$recHasPickupLabel afterHasDest=$recHasDestLabel"
            )

            if (recHasCardId && recHasPickupLabel && recHasDestLabel) {
                segments.clear()
                segments.addAll(recollectSegments)
                fullText = recollectText
                Log.d(TAG, "recollect: result=success (card body recovered)")
            } else {
                Log.d(TAG, "popup skipped: reason=recollect-failed")
                return
            }
        }

        if (segments.isEmpty()) {
            Log.d(TAG, "popup skipped: reason=segments-empty")
            return
        }

        maybeLogBaemin(pkg, eventType, fullText)
        Log.d(TAG, "detect: card text collected segments=${segments.size}")

        val snapshot = MemoRepository.getCachedSnapshot()
        val candidate = StoreExtractor.extract(fullText)
        Log.d(TAG, "StoreExtractor result: success=${candidate != null} value=$candidate")
        val matched = candidate?.let { MemoMatcher.match(it, snapshot) }
        val addr = AddressExtractor.extract(segments.toList(), fullText)
        Log.d(TAG, "AddressExtractor result: success=${addr != null} pickup=${addr?.pickup} dest=${addr?.dest}")

        if (matched == null && addr == null) {
            Log.d(TAG, "popup skipped: reason=no-match-no-address")
            return
        }
        Log.d(
            TAG,
            "parse success: storeText=$candidate " +
                "matched=${matched?.let { "${it.storeName}/${it.branchName}" }} " +
                "pickup=${addr?.pickup} dest=${addr?.dest}"
        )

        // (#26) 감지 성공 시에는 메모 유무·빌드타입(DEBUG/release)·경로조회 결과와 무관하게
        //   즉시 오버레이를 띄운다. FEAT-21 케이스 C/D의 DEBUG 게이트·release의 route 성공
        //   종속(구 케이스 D)을 제거해 release에서 팝업이 아예 안 뜨는 문제를 없앤다.
        //   경로 정보는 표시 이후 RouteService 조회가 끝나면 updateRoute로 채운다.
        val hasRoute = addr != null
        val dedupKey = matched?.id?.toString() ?: addr!!.key()

        // (#26) dedup은 "지금 마킹"이 아니라 "표시 성공 여부 확인 후 마킹"으로 바꾼다.
        //   여기서는 canShow로 창(WINDOW_MS) 안에 이미 보여줬는지만 확인한다.
        if (!DedupGuard.canShow(dedupKey)) {
            Log.d(TAG, "popup skipped: reason=dedup key=$dedupKey")
            return
        }
        Log.d(TAG, "dedup passed: key=$dedupKey")

        Log.d(TAG, "overlay show requested: matched=${matched != null} hasRoute=$hasRoute key=$dedupKey")
        val token = MemoPopupController.show(this, matched, hasRoute) { success ->
            Log.d(TAG, "show onResult received: success=$success key=$dedupKey")
            if (success) {
                DedupGuard.markShown(dedupKey)
            }
            // 실패(권한 OFF·addView 실패)는 MemoPopupController가 사유를 로그로 남긴다.
            // 여기서 마킹하지 않으므로 동일 카드의 후속 이벤트가 재시도될 수 있다.
        }

        if (addr != null) {
            serviceScope.launch {
                val outcome = RouteService.resolve(addr.pickup, addr.dest)
                MemoPopupController.updateRoute(token, outcome.route)
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

        /** (#28) 카드 본문 신호 판별용 상수. StoreExtractor/AddressExtractor의 키와 동일한 문자열을 쓰되,
         *  id=신규배차_카드는 리소스 id로 등장할 때만 매칭되도록 "id=" 접두를 포함해 오탐(다른 텍스트에
         *  우연히 "신규배차_카드"가 포함되는 경우)을 줄인다. */
        const val CARD_SIGNAL_ID = "id=신규배차_카드"
        const val KEY_PICKUP = "픽업지"
        const val KEY_DEST = "전달지"

        /** (#28) 카드 본문은 아직 없지만 카드 관련 화면임을 시사하는 약한 신호(수락버튼/카운트다운 등).
         *  이 신호만 있을 때는 rootInActiveWindow 재수집을 시도하되, 그 자체로 팝업을 단독 트리거하지 않는다. */
        val WEAK_CARD_SIGNAL_REGEX = Regex("신규배차_수락버튼|배차수락|신규배달\\s*\\d+건|\\d+초")

        /** traversal 상한(P2). */
        const val MAX_DEPTH = 25
        const val MAX_NODES = 1500
        const val MAX_TRAVERSAL_NS = 40_000_000L // 40ms

        /** debug에서 이 시간(ms) 이상 걸린 이벤트만 경고 로그. */
        const val SLOW_EVENT_WARN_MS = 100L
    }
}
