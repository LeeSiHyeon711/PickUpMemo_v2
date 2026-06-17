package com.itmakesome.pickupmemo2

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.itmakesome.pickupmemo2.databinding.ActivityMainBinding
import com.itmakesome.pickupmemo2.ui.MemoListActivity
import com.itmakesome.pickupmemo2.ui.TestActivity
import com.itmakesome.pickupmemo2.util.PermissionChecker

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnMemoManage.setOnClickListener {
            startActivity(Intent(this, MemoListActivity::class.java))
        }

        binding.btnPopupTest.setOnClickListener {
            startActivity(Intent(this, TestActivity::class.java))
        }

        binding.btnAccessibilitySettings.setOnClickListener {
            try {
                startActivity(PermissionChecker.accessibilitySettingsIntent())
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, getString(R.string.toast_setting_not_found), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnOverlaySettings.setOnClickListener {
            try {
                startActivity(PermissionChecker.overlaySettingsIntent(this))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, getString(R.string.toast_setting_not_found), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val a11y = PermissionChecker.isAccessibilityEnabled(this)
        val overlay = PermissionChecker.canDrawOverlays(this)
        binding.tvAccessibilityStatus.text = getString(
            R.string.status_accessibility,
            if (a11y) getString(R.string.status_on) else getString(R.string.status_off)
        )
        binding.tvOverlayStatus.text = getString(
            R.string.status_overlay,
            if (overlay) getString(R.string.status_granted) else getString(R.string.status_denied)
        )
    }
}
