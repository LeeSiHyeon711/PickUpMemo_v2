package com.itmakesome.pickupmemo2.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.itmakesome.pickupmemo2.R
import com.itmakesome.pickupmemo2.data.Memo

/**
 * FEAT-06 — 오버레이 팝업 컨트롤러
 *
 * show(context, memo): 화면 상단에 TYPE_APPLICATION_OVERLAY 카드 팝업을 표시하고
 *   AUTO_DISMISS_MS(6초) 후 자동 제거한다.
 *
 * 제약:
 *   - canDrawOverlays 미허가 시 조용히 skip (권한 요청은 FEAT-08 책임).
 *   - FLAG_NOT_TOUCHABLE — 팝업이 배차 카드 거절/수락 버튼 조작을 방해하지 않음.
 *   - 태그 null/blank → tvPopupTag GONE ("태그 없음" 등 대체 문구 금지).
 */
object MemoPopupController {

    const val AUTO_DISMISS_MS = 6000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentView: View? = null
    private var wm: WindowManager? = null

    private val dismissRunnable = Runnable { dismiss() }

    /**
     * 오버레이 팝업을 표시한다.
     *
     * @param context 접근성 서비스 컨텍스트 (FEAT-07이 전달) 또는 앱 컨텍스트
     * @param memo    표시할 메모 데이터
     */
    fun show(context: Context, memo: Memo) {
        // 오버레이 권한 미허가 시 조용히 skip
        if (!Settings.canDrawOverlays(context)) return

        // 메인 스레드 보장
        mainHandler.post {
            // 기존 팝업 및 타이머 제거
            dismiss()

            val wmLocal = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_memo_popup, null)

            // 제목: 상호명 + 지점명
            view.findViewById<TextView>(R.id.tvPopupTitle).text =
                "${memo.storeName} ${memo.branchName}"

            // 메모 내용
            view.findViewById<TextView>(R.id.tvPopupContent).text = memo.content

            // 태그: null 또는 blank면 GONE (대체 문구 금지)
            val tagView = view.findViewById<TextView>(R.id.tvPopupTag)
            if (memo.tag.isNullOrBlank()) {
                tagView.visibility = View.GONE
            } else {
                tagView.visibility = View.VISIBLE
                tagView.text = memo.tag
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // FLAG_NOT_FOCUSABLE: IME 방해 없음
                // FLAG_NOT_TOUCHABLE: 터치가 팝업을 통과해 배차 카드로 전달됨
                // FLAG_LAYOUT_IN_SCREEN: 화면 가장자리까지 배치
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
                // 상태바 아래 여백 48dp
                y = (48 * context.resources.displayMetrics.density).toInt()
            }

            try {
                wmLocal.addView(view, params)
            } catch (e: Exception) {
                // addView 실패(detached window 등) 시 조용히 무시
                return@post
            }

            wm = wmLocal
            currentView = view

            // 6초 후 자동 닫힘
            mainHandler.removeCallbacks(dismissRunnable)
            mainHandler.postDelayed(dismissRunnable, AUTO_DISMISS_MS)
        }
    }

    /**
     * 현재 표시 중인 팝업을 즉시 제거한다.
     * 이미 제거된 상태이거나 addView 전이면 안전하게 무시된다.
     */
    fun dismiss() {
        mainHandler.removeCallbacks(dismissRunnable)
        val v = currentView ?: return
        try {
            wm?.removeView(v)
        } catch (_: Exception) {
            // removeView 중복 호출·view 미부착 예외 무시
        }
        currentView = null
        wm = null
    }
}
