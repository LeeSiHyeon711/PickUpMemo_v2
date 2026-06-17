package com.itmakesome.pickupmemo2.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * baemin_logs 테이블 DAO (FEAT-12).
 *
 * trimTo: 최신 cap 건만 남기고 나머지(오래된 것)를 삭제한다.
 * 링버퍼 상한 관리는 BaeminLogRepository.save()가 insert 직후 이 함수를 호출해 보장한다.
 */
@Dao
interface BaeminLogDao {

    @Insert
    suspend fun insert(log: BaeminLog): Long

    /**
     * 최신 [cap]건만 남기고 오래된 레코드를 삭제한다.
     * capturedAt DESC, id DESC 기준으로 최신순 정렬 후 초과분을 제거한다.
     */
    @Query(
        "DELETE FROM baemin_logs WHERE id NOT IN " +
        "(SELECT id FROM baemin_logs ORDER BY capturedAt DESC, id DESC LIMIT :cap)"
    )
    suspend fun trimTo(cap: Int)

    @Query("SELECT * FROM baemin_logs ORDER BY capturedAt DESC, id DESC")
    suspend fun getAll(): List<BaeminLog>

    @Query("SELECT COUNT(*) FROM baemin_logs")
    suspend fun count(): Int

    @Query("DELETE FROM baemin_logs")
    suspend fun clear()
}
