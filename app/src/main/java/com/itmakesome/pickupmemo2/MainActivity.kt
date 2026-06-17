package com.itmakesome.pickupmemo2

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.itmakesome.pickupmemo2.databinding.ActivityMainBinding
import com.itmakesome.pickupmemo2.ui.MemoListActivity

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnMemoManage.setOnClickListener {
            startActivity(Intent(this, MemoListActivity::class.java))
        }

        // FEAT-08에서 권한 판정/안내 구현
    }
}
