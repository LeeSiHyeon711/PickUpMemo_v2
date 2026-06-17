package com.itmakesome.pickupmemo2.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memos")
data class Memo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val storeName: String,
    val branchName: String,
    val content: String,
    val tag: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
