package com.itmakesome.pickupmemo2.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 타임스탬프 → 표시용 문자열 / 파일명 변환 유틸 (FEAT-13, v1 패턴 이식).
 */
object TimeFormat {
    private val logFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }
    private val fileFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    }

    /** epoch millis → "2026-06-17 09:30:00" 형식 */
    fun formatTimestamp(millis: Long): String = logFormat.get()!!.format(Date(millis))

    /** epoch millis → "baemin_log_20260617_093000.txt" 형식 */
    fun exportFileName(millis: Long): String =
        "baemin_log_${fileFormat.get()!!.format(Date(millis))}.txt"
}
