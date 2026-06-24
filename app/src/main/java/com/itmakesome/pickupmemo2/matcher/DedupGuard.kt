package com.itmakesome.pickupmemo2.matcher

import java.util.concurrent.ConcurrentHashMap

object DedupGuard {
    const val WINDOW_MS = 30_000L
    private val lastShownAt = ConcurrentHashMap<Long, Long>()
    private val lastShownByKey = ConcurrentHashMap<String, Long>()

    fun shouldShow(memoId: Long, now: Long = System.currentTimeMillis()): Boolean {
        val last = lastShownAt[memoId]
        if (last != null && now - last < WINDOW_MS) return false
        lastShownAt[memoId] = now
        return true
    }

    fun shouldShow(key: String, now: Long = System.currentTimeMillis()): Boolean {
        val last = lastShownByKey[key]
        if (last != null && now - last < WINDOW_MS) return false
        lastShownByKey[key] = now
        return true
    }

    fun reset() {
        lastShownAt.clear()
        lastShownByKey.clear()
    }
}
