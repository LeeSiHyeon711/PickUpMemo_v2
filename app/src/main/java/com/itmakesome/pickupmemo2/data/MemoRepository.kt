package com.itmakesome.pickupmemo2.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

object MemoRepository {

    private lateinit var db: AppDatabase

    @Volatile private var inited = false

    /**
     * DB를 초기화한다(멱등). 캐시 로딩은 하지 않는다.
     * suspend 함수를 내부에서 호출하지 않는다.
     * 각 진입점은 이 함수 호출 후 코루틴에서 refreshCache()를 별도 호출해야 한다.
     *
     * 예)
     *   MemoRepository.init(applicationContext)
     *   lifecycleScope.launch { MemoRepository.refreshCache() }  // Activity
     */
    fun init(context: Context) {
        if (inited) return
        synchronized(this) {
            if (inited) return
            db = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "pickupmemo.db"
            ).build()
            inited = true
        }
    }

    // ── 목록 조회 ──────────────────────────────────────────────────────────

    fun observeAll(): Flow<List<Memo>> = db.memoDao().observeAll()

    // ── 캐시 (매칭 로직 전용 동기 접근) ────────────────────────────────────

    @Volatile private var cache: List<Memo> = emptyList()

    /**
     * DB에서 전체 메모를 읽어 캐시를 갱신한다.
     * 각 진입점의 코루틴에서 초기 1회 호출 필요.
     * CRUD 함수는 내부에서 자동 호출한다.
     */
    suspend fun refreshCache() {
        cache = withContext(Dispatchers.IO) { db.memoDao().getAll() }
    }

    /**
     * 접근성 콜백(메인 스레드) 등에서 블로킹 없이 즉시 읽는다.
     * 초기화 전이면 빈 리스트를 반환한다(NPE 없음).
     */
    fun getCachedSnapshot(): List<Memo> = cache

    // ── CRUD ───────────────────────────────────────────────────────────────

    suspend fun add(
        storeName: String,
        branchName: String,
        content: String,
        tag: String?
    ): Long {
        val id = withContext(Dispatchers.IO) {
            db.memoDao().insert(
                Memo(
                    storeName = storeName,
                    branchName = branchName,
                    content = content,
                    tag = tag
                )
            )
        }
        refreshCache()
        return id
    }

    suspend fun update(memo: Memo) {
        withContext(Dispatchers.IO) { db.memoDao().update(memo) }
        refreshCache()
    }

    suspend fun delete(memo: Memo) {
        withContext(Dispatchers.IO) { db.memoDao().delete(memo) }
        refreshCache()
    }

    suspend fun getById(id: Long): Memo? =
        withContext(Dispatchers.IO) { db.memoDao().getById(id) }
}
