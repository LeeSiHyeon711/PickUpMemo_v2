package com.itmakesome.pickupmemo2.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoDao {
    @Query("SELECT * FROM memos ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Memo>>

    @Query("SELECT * FROM memos ORDER BY updatedAt DESC")
    suspend fun getAll(): List<Memo>

    @Query("SELECT * FROM memos WHERE id = :id")
    suspend fun getById(id: Long): Memo?

    @Insert
    suspend fun insert(memo: Memo): Long

    @Update
    suspend fun update(memo: Memo)

    @Delete
    suspend fun delete(memo: Memo)
}
