package com.itmakesome.pickupmemo2.ui

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.itmakesome.pickupmemo2.R
import com.itmakesome.pickupmemo2.data.BaeminLogRepository
import com.itmakesome.pickupmemo2.databinding.ActivityBaeminLogBinding
import com.itmakesome.pickupmemo2.util.BaeminLogExporter
import kotlinx.coroutines.launch

/**
 * 배민 로그 조회 + 내보내기 + 전체 삭제 화면 (FEAT-13).
 *
 * - onCreate/onResume에서 refresh() 호출 → 최신순 목록 표시.
 * - [내보내기]: 0건이면 토스트, 아니면 FileProvider + ACTION_SEND 공유 시트.
 * - [전체 삭제]: 확인 다이얼로그 → BaeminLogRepository.clear() → refresh().
 */
class BaeminLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBaeminLogBinding
    private lateinit var adapter: BaeminLogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBaeminLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = getString(R.string.title_baemin_log)

        BaeminLogRepository.init(applicationContext)

        adapter = BaeminLogAdapter()
        binding.rvLogs.layoutManager = LinearLayoutManager(this)
        binding.rvLogs.adapter = adapter

        binding.btnExport.setOnClickListener { onExportClick() }
        binding.btnClearAll.setOnClickListener { onClearAllClick() }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            val logs = BaeminLogRepository.getAll()
            adapter.submit(logs)
            val count = logs.size
            binding.tvCount.text = getString(R.string.baemin_log_count, count)
            binding.tvEmpty.visibility = if (count == 0) View.VISIBLE else View.GONE
            binding.rvLogs.visibility = if (count == 0) View.GONE else View.VISIBLE
        }
    }

    private fun onExportClick() {
        lifecycleScope.launch {
            val count = BaeminLogRepository.count()
            if (count == 0) {
                Toast.makeText(
                    this@BaeminLogActivity,
                    getString(R.string.toast_export_empty),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            try {
                val uri = BaeminLogExporter.export(this@BaeminLogActivity)
                val shareIntent = BaeminLogExporter.buildShareIntent(uri)
                startActivity(
                    android.content.Intent.createChooser(
                        shareIntent,
                        getString(R.string.export_chooser_title)
                    )
                )
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(
                    this@BaeminLogActivity,
                    getString(R.string.toast_export_app_not_found),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun onClearAllClick() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_clear_title))
            .setMessage(getString(R.string.dialog_clear_message))
            .setPositiveButton(getString(R.string.dialog_clear_confirm)) { _, _ ->
                lifecycleScope.launch {
                    BaeminLogRepository.clear()
                    refresh()
                    Toast.makeText(
                        this@BaeminLogActivity,
                        getString(R.string.toast_cleared),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(getString(R.string.dialog_clear_cancel), null)
            .show()
    }
}
