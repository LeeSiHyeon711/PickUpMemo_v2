package com.itmakesome.pickupmemo2.matcher

import java.util.concurrent.ConcurrentHashMap

/**
 * (#26) canShow()는 마킹 없이 "지금 보여줘도 되는가"만 확인한다.
 * 실제로 오버레이 표시(MemoPopupController.show → addView)가 성공한 뒤에만
 * markShown()을 호출해야 한다 — 표시가 안 된 카드가 30초 동안 재시도 불가능해지는
 * 문제(같은 카드의 후속 이벤트가 억제됨)를 막기 위함.
 */
object DedupGuard {
    const val WINDOW_MS = 30_000L
    private val lastShownByKey = ConcurrentHashMap<String, Long>()

    /** 표시 여부만 확인(마킹하지 않음). 메모매칭 시 key="memo id 문자열", 미매칭 시 key=AddressPair.key(). */
    fun canShow(key: String, now: Long = System.currentTimeMillis()): Boolean {
        val last = lastShownByKey[key]
        return last == null || now - last >= WINDOW_MS
    }

    /** 실제 표시 성공 후 호출해 dedup 창을 시작한다. */
    fun markShown(key: String, now: Long = System.currentTimeMillis()) {
        lastShownByKey[key] = now
    }

    fun reset() {
        lastShownByKey.clear()
    }
}
