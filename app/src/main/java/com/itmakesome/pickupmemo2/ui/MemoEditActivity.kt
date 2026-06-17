package com.itmakesome.pickupmemo2.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.itmakesome.pickupmemo2.R
import com.itmakesome.pickupmemo2.data.Memo
import com.itmakesome.pickupmemo2.data.MemoRepository
import com.itmakesome.pickupmemo2.databinding.ActivityMemoEditBinding
import kotlinx.coroutines.launch

class MemoEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MEMO_ID = "memoId"
    }

    private lateinit var binding: ActivityMemoEditBinding

    /** 수정 모드일 때 원본 Memo. null이면 추가 모드. */
    private var editing: Memo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMemoEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        MemoRepository.init(applicationContext)

        val memoId = intent.getLongExtra(EXTRA_MEMO_ID, -1L)

        if (memoId != -1L) {
            // 수정 모드 진입 — DB에서 기존 값 로드
            binding.btnDelete.visibility = View.VISIBLE
            lifecycleScope.launch {
                val memo = MemoRepository.getById(memoId)
                if (memo == null) {
                    Toast.makeText(
                        this@MemoEditActivity,
                        getString(R.string.toast_memo_not_found),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                    return@launch
                }
                editing = memo
                binding.etStore.setText(memo.storeName)
                binding.etBranch.setText(memo.branchName)
                binding.etContent.setText(memo.content)
                binding.etTag.setText(memo.tag ?: "")
            }
        }

        binding.btnSave.setOnClickListener { onSave() }
        binding.btnDelete.setOnClickListener { onDelete() }
    }

    private fun onSave() {
        val store = binding.etStore.text.toString().trim()
        val branch = binding.etBranch.text.toString().trim()
        val content = binding.etContent.text.toString().trim()
        val tagRaw = binding.etTag.text.toString().trim()
        val tag = tagRaw.ifBlank { null }   // 빈 문자열 저장 금지 — null 변환

        var hasError = false

        if (store.isBlank()) {
            binding.tilStore.error = getString(R.string.error_required)
            hasError = true
        } else {
            binding.tilStore.error = null
        }

        if (branch.isBlank()) {
            binding.tilBranch.error = getString(R.string.error_required)
            hasError = true
        } else {
            binding.tilBranch.error = null
        }

        if (content.isBlank()) {
            binding.tilContent.error = getString(R.string.error_required)
            hasError = true
        } else {
            binding.tilContent.error = null
        }

        if (hasError) {
            Toast.makeText(this, getString(R.string.toast_required_fields), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val current = editing
            if (current == null) {
                MemoRepository.add(store, branch, content, tag)
            } else {
                MemoRepository.update(
                    current.copy(
                        storeName = store,
                        branchName = branch,
                        content = content,
                        tag = tag,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            finish()
        }
    }

    private fun onDelete() {
        val memo = editing ?: return
        lifecycleScope.launch {
            MemoRepository.delete(memo)
            finish()
        }
    }
}
