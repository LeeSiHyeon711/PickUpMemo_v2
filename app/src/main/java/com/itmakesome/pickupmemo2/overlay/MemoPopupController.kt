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
import com.itmakesome.pickupmemo2.route.RouteInfo
import java.util.concurrent.atomic.AtomicLong

/**
 * FEAT-06 — 오버레이 팝업 컨트롤러
 *
 * show(context, memo): 화면 상단에 TYPE_APPLICATION_OVERLAY 카드 팝업을 표시하고
 *   AUTO_DISMISS_MS(6초) 후 자동 제거한다.
 *
 * v3(FEAT-19): show(context, memo?, hasRoute) + updateRoute(token, route)로
 *   메모와 경로 정보를 단일 팝업에 통합 표시한다.
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
    private val generation = AtomicLong(0)

    @Volatile
    private var currentToken = 0L

    private val dismissRunnable = Runnable { dismiss() }

    fun show(context: Context, memo: Memo) {
        show(context, memo, hasRoute = false)
    }

    fun show(context: Context, memo: Memo?, hasRoute: Boolean): Long {
        val token = generation.incrementAndGet()
        if (!Settings.canDrawOverlays(context)) return token

        mainHandler.post {
            dismiss()
            currentToken = token

            val wmLocal = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_memo_popup, null)

            val titleView = view.findViewById<TextView>(R.id.tvPopupTitle)
            val contentView = view.findViewById<TextView>(R.id.tvPopupContent)
            val tagView = view.findViewById<TextView>(R.id.tvPopupTag)
            val routeView = view.findViewById<TextView>(R.id.tvPopupRoute)
            val hillView = view.findViewById<TextView>(R.id.tvPopupHill)

            if (memo != null) {
                titleView.text = "${memo.storeName} ${memo.branchName}"
                contentView.text = memo.content
                contentView.visibility = View.VISIBLE
                if (memo.tag.isNullOrBlank()) {
                    tagView.visibility = View.GONE
                } else {
                    tagView.visibility = View.VISIBLE
                    tagView.text = memo.tag
                }
            } else {
                titleView.text = context.getString(R.string.popup_no_memo)
                contentView.visibility = View.GONE
                tagView.visibility = View.GONE
            }

            if (hasRoute) {
                routeView.visibility = View.VISIBLE
                routeView.text = "…"
            } else {
                routeView.visibility = View.GONE
            }

            hillView.visibility = View.GONE

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
                y = (48 * context.resources.displayMetrics.density).toInt()
            }

            try {
                wmLocal.addView(view, params)
            } catch (e: Exception) {
                return@post
            }

            wm = wmLocal
            currentView = view

            mainHandler.removeCallbacks(dismissRunnable)
            mainHandler.postDelayed(dismissRunnable, AUTO_DISMISS_MS)
        }
        return token
    }

    fun updateRoute(token: Long, route: RouteInfo?) {
        mainHandler.post {
            if (token != currentToken || currentView == null) return@post
            val routeView = currentView!!.findViewById<TextView>(R.id.tvPopupRoute)
            routeView.visibility = View.VISIBLE
            routeView.text = route?.summaryText
                ?: currentView!!.context.getString(R.string.popup_route_unavailable)
        }
    }

    fun dismiss() {
        mainHandler.removeCallbacks(dismissRunnable)
        val v = currentView ?: return
        try {
            wm?.removeView(v)
        } catch (_: Exception) {
        }
        currentView = null
        wm = null
    }
}
