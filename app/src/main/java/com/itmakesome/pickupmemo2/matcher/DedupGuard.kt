package com.itmakesome.pickupmemo2.matcher

import java.util.concurrent.ConcurrentHashMap

object DedupGuard {
    const val WINDOW_MS = 30_000L
    private val lastShownAt = ConcurrentHashMap<Long, Long>()

    fun shouldShow(memoId: Long, now: Long = System.currentTimeMillis()): Boolean {
        val last = lastShownAt[memoId]
        if (last != null && now - last < WINDOW_MS) return false
        lastShownAt[memoId] = now
        return true
    }

    fun reset() = lastShownAt.clear()  // 테스트/디버그용
}
