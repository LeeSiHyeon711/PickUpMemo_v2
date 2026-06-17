package com.itmakesome.pickupmemo2

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.itmakesome.pickupmemo2.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // FEAT-08에서 권한 판정/안내 구현
    }
}
