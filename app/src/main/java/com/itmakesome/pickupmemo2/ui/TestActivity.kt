package com.itmakesome.pickupmemo2.ui

import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.itmakesome.pickupmemo2.BuildConfig
import com.itmakesome.pickupmemo2.R
import com.itmakesome.pickupmemo2.data.Memo
import com.itmakesome.pickupmemo2.data.MemoRepository
import com.itmakesome.pickupmemo2.databinding.ActivityTestBinding
import com.itmakesome.pickupmemo2.matcher.MemoMatcher
import com.itmakesome.pickupmemo2.matcher.StoreExtractor
import com.itmakesome.pickupmemo2.overlay.MemoPopupController
import com.itmakesome.pickupmemo2.route.RouteFailure
import com.itmakesome.pickupmemo2.route.RouteResolveOutcome
import com.itmakesome.pickupmemo2.route.RouteService
import kotlinx.coroutines.launch

/**
 * FEAT-10 — 팝업 검증 테스트 화면
 *
 * 모드 A: 저장된 메모를 Spinner에서 선택해 MemoPopupController.show 직접 호출.
 *         팝업 렌더링과 오버레이 권한을 빠르게 확인.
 *
 * 모드 B: 화면에서 본 텍스트를 붙여넣어 StoreExtractor → MemoMatcher →
 *         MemoPopupController 파이프라인(FEAT-05/06)을 그대로 태워 검증.
 *
 * 모드 C: 픽업/전달 수동 입력 → 메모 매칭(픽업지 기준) + RouteService → 단일 팝업.
 *
 * 운영 매칭(PickupAccessibilityService) 코드를 일절 수정하지 않는다.
 */
class TestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTestBinding
    private var memos: List<Memo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.title_test)

        val key = BuildConfig.KAKAO_REST_API_KEY
        Log.d(
            TAG,
            "BuildConfig.KAKAO_REST_API_KEY isBlank=${key.isBlank()}, length=${key.length}"
        )

        MemoRepository.init(applicationContext)
        lifecycleScope.launch {
            MemoRepository.refreshCache()
            memos = MemoRepository.getCachedSnapshot()
            val labels = memos.map { "${it.storeName} ${it.branchName}" }
            binding.spinnerMemos.adapter = ArrayAdapter(
                this@TestActivity,
                android.R.layout.simple_spinner_dropdown_item,
                labels
            )
        }

        RouteService.init(key)

        binding.btnShowSelected.setOnClickListener { showSelectedMemo() }
        binding.btnMatchTest.setOnClickListener { runMatchTest() }
        binding.btnRouteTest.setOnClickListener { runRouteTest() }
    }

    private fun showSelectedMemo() {
        if (memos.isEmpty()) {
            Toast.makeText(this, getString(R.string.test_no_memo), Toast.LENGTH_SHORT).show()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, getString(R.string.test_overlay_required), Toast.LENGTH_SHORT).show()
            return
        }
        val selectedIndex = binding.spinnerMemos.selectedItemPosition
        val memo = memos.getOrNull(selectedIndex) ?: return
        MemoPopupController.show(this, memo)
    }

    private fun runMatchTest() {
        val input = binding.etTestText.text?.toString() ?: ""
        if (input.isBlank()) {
            Toast.makeText(this, getString(R.string.test_input_empty), Toast.LENGTH_SHORT).show()
            return
        }

        val candidate = StoreExtractor.extract(input)
        if (candidate == null) {
            Toast.makeText(this, getString(R.string.test_extract_failed), Toast.LENGTH_LONG).show()
            return
        }

        val snapshot = MemoRepository.getCachedSnapshot()
        val matched = MemoMatcher.match(candidate, snapshot)
        if (matched == null) {
            Toast.makeText(
                this,
                getString(R.string.test_no_match, candidate),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, getString(R.string.test_overlay_required), Toast.LENGTH_SHORT).show()
            return
        }

        MemoPopupController.show(this, matched)
        Toast.makeText(
            this,
            getString(R.string.test_matched, "${matched.storeName} ${matched.branchName}"),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun runRouteTest() {
        val pickup = binding.etPickup.text?.toString()?.trim().orEmpty()
        val dest = binding.etDest.text?.toString()?.trim().orEmpty()
        if (pickup.isBlank() || dest.isBlank()) {
            Toast.makeText(this, getString(R.string.test_route_input_empty), Toast.LENGTH_SHORT).show()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, getString(R.string.test_overlay_required), Toast.LENGTH_SHORT).show()
            return
        }

        val matchedMemo = matchMemoByPickup(pickup)
        Log.d(
            TAG,
            "mode C: pickup=$pickup, matchedMemo=${
                matchedMemo?.let { "${it.storeName} ${it.branchName}" } ?: "none"
            }"
        )

        val token = MemoPopupController.show(this, matchedMemo, hasRoute = true)
        lifecycleScope.launch {
            val outcome = RouteService.resolve(pickup, dest)
            MemoPopupController.updateRoute(token, outcome.route)
            Toast.makeText(
                this@TestActivity,
                formatRouteTestToast(outcome, matchedMemo),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * 픽업지 텍스트로 저장 메모를 찾는다.
     * MemoMatcher(상호+지점 포함) 우선, 없으면 "상호명 지점명" 라벨 직접 매칭.
     */
    private fun matchMemoByPickup(pickup: String): Memo? {
        val snapshot = MemoRepository.getCachedSnapshot()
        MemoMatcher.match(pickup, snapshot)?.let { return it }
        return snapshot.firstOrNull { memo ->
            val store = memo.storeName.trim()
            val branch = memo.branchName.trim()
            if (store.isEmpty() || branch.isEmpty()) return@firstOrNull false
            val label = "$store $branch"
            pickup == label || pickup.contains(label) ||
                (pickup.contains(store) && pickup.contains(branch))
        }
    }

    private fun formatRouteTestToast(outcome: RouteResolveOutcome, memo: Memo?): String {
        if (outcome.route != null) {
            val memoPart = memo?.let { " · 메모: ${it.storeName} ${it.branchName}" }.orEmpty()
            return outcome.route.summaryText + memoPart
        }
        val reason = when (outcome.failure) {
            RouteFailure.API_KEY_BLANK -> getString(R.string.route_fail_api_key)
            RouteFailure.PROVIDER_NOT_INIT -> getString(R.string.route_fail_provider)
            RouteFailure.PICKUP_GEOCODE_FAILED -> getString(R.string.route_fail_pickup_geocode)
            RouteFailure.DEST_GEOCODE_FAILED -> getString(R.string.route_fail_dest_geocode)
            RouteFailure.DIRECTIONS_FAILED -> getString(R.string.route_fail_directions)
            RouteFailure.TIMEOUT -> getString(R.string.route_fail_timeout)
            null -> getString(R.string.popup_route_unavailable)
        }
        return reason
    }

    private companion object {
        const val TAG = "TestActivity"
    }
}
