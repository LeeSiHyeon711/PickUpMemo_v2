package com.itmakesome.pickupmemo2.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 데이터베이스 (pickupmemo.db).
 *
 * version 1 → 2 변경 내역 (FEAT-12):
 *   - BaeminLog 엔티티 추가 → baemin_logs 테이블 신설
 *   - MIGRATION_1_2: memos 테이블을 보존하면서 baemin_logs 테이블만 CREATE
 *   - fallbackToDestructiveMigration 사용 금지 (기존 메모 보존)
 */
@Database(entities = [Memo::class, BaeminLog::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun memoDao(): MemoDao

    abstract fun baeminLogDao(): BaeminLogDao

    companion object {

        /**
         * version 1 → 2 마이그레이션.
         * memos 테이블은 건드리지 않고 baemin_logs 테이블만 추가한다.
         * Room이 생성하는 스키마와 컬럼 정의가 일치해야 하므로 FEAT-12 명세의 SQL을 그대로 사용한다.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `baemin_logs` (" +
                    "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                    "`capturedAt` INTEGER NOT NULL, " +
                    "`packageName` TEXT NOT NULL, " +
                    "`eventType` TEXT NOT NULL, " +
                    "`text` TEXT NOT NULL)"
                )
            }
        }
    }
}
