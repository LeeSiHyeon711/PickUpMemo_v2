package com.itmakesome.pickupmemo2.ui

import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.itmakesome.pickupmemo2.R
import com.itmakesome.pickupmemo2.data.Memo
import com.itmakesome.pickupmemo2.data.MemoRepository
import com.itmakesome.pickupmemo2.databinding.ActivityTestBinding
import com.itmakesome.pickupmemo2.matcher.MemoMatcher
import com.itmakesome.pickupmemo2.matcher.StoreExtractor
import com.itmakesome.pickupmemo2.overlay.MemoPopupController
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

        // DB 초기화(멱등) + 캐시 갱신 → Spinner 채움
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

        binding.btnShowSelected.setOnClickListener { showSelectedMemo() }
        binding.btnMatchTest.setOnClickListener { runMatchTest() }
    }

    /**
     * 모드 A: Spinner에서 선택한 메모로 팝업을 직접 띄운다.
     */
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

    /**
     * 모드 B: 입력란 텍스트를 추출→매칭→팝업 파이프라인에 태운다.
     * FEAT-05 StoreExtractor / MemoMatcher, FEAT-06 MemoPopupController 기존 object 호출만.
     */
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
}
