package com.itmakesome.pickupmemo2.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 배민 로그 저장소 싱글톤 (FEAT-12).
 *
 * - MemoRepository.requireDatabase()가 반환하는 DB 인스턴스를 공유해 단일 Connection 유지.
 * - save(): insert 후 trimTo(MAX_LOGS)로 1000건 상한을 유지하는 링버퍼 동작 보장.
 * - init()은 멱등(MemoRepository.init이 내부적으로 중복 방지).
 * - 모든 DB 접근은 Dispatchers.IO에서 실행한다.
 */
object BaeminLogRepository {

    /** 링버퍼 최대 건수. */
    const val MAX_LOGS = 1000

    private fun dao(): BaeminLogDao = MemoRepository.requireDatabase().baeminLogDao()

    /**
     * DB를 초기화한다(멱등). onServiceConnected에서 호출한다.
     * 내부적으로 MemoRepository.init()을 위임하므로 별도 DB 빌드 없이 동일 인스턴스를 공유한다.
     */
    fun init(context: Context) {
        MemoRepository.init(context)
    }

    /**
     * 배민 이벤트 텍스트를 저장하고, MAX_LOGS 초과 시 오래된 레코드를 자동 삭제한다.
     */
    suspend fun save(packageName: String, eventType: String, text: String) =
        withContext(Dispatchers.IO) {
            dao().insert(
                BaeminLog(
                    capturedAt = System.currentTimeMillis(),
                    packageName = packageName,
                    eventType = eventType,
                    text = text
                )
            )
            dao().trimTo(MAX_LOGS)
        }

    suspend fun getAll(): List<BaeminLog> = withContext(Dispatchers.IO) { dao().getAll() }

    suspend fun count(): Int = withContext(Dispatchers.IO) { dao().count() }

    suspend fun clear() = withContext(Dispatchers.IO) { dao().clear() }
}
