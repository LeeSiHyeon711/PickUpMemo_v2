package com.itmakesome.pickupmemo2.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.itmakesome.pickupmemo2.R

class MemoEditActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_MEMO_ID = "memoId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memo_edit)
        // FEAT-04에서 본문 구현
    }
}
