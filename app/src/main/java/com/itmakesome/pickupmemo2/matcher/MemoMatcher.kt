package com.itmakesome.pickupmemo2.matcher

import com.itmakesome.pickupmemo2.data.Memo

object MemoMatcher {
    fun match(detectedStoreText: String, memos: List<Memo>): Memo? {
        val detected = detectedStoreText.trim()
        return memos.firstOrNull { memo ->
            val store = memo.storeName.trim()
            val branch = memo.branchName.trim()
            store.isNotEmpty() &&
            branch.isNotEmpty() &&
            detected.contains(store) &&
            detected.contains(branch)
        }
    }
}
