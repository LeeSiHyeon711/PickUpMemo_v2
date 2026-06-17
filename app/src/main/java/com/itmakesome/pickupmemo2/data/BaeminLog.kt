package com.itmakesome.pickupmemo2.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 배민커넥트 접근성 이벤트 로그 엔티티 (FEAT-12).
 *
 * baemin_logs 테이블에 저장된다.
 * 최대 1000건 링버퍼로 관리되며 초과분은 BaeminLogRepository.save()에서 자동 삭제된다.
 */
@Entity(tableName = "baemin_logs")
data class BaeminLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val capturedAt: Long,       // epoch millis
    val packageName: String,    // 항상 com.woowahan.bros
    val eventType: String,      // AccessibilityEvent.eventTypeToString(...)
    val text: String            // 조립된 화면 텍스트(fullText)
)
