package com.itmakesome.pickupmemo2.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.itmakesome.pickupmemo2.data.MemoRepository
import com.itmakesome.pickupmemo2.databinding.ActivityMemoListBinding
import kotlinx.coroutines.launch

class MemoListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMemoListBinding
    private lateinit var adapter: MemoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMemoListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        MemoRepository.init(applicationContext)

        adapter = MemoAdapter(
            onClick = { memo ->
                val intent = Intent(this, MemoEditActivity::class.java)
                    .putExtra(MemoEditActivity.EXTRA_MEMO_ID, memo.id)
                startActivity(intent)
            },
            onDelete = { memo ->
                lifecycleScope.launch { MemoRepository.delete(memo) }
            }
        )

        binding.recyclerMemos.layoutManager = LinearLayoutManager(this)
        binding.recyclerMemos.adapter = adapter

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, MemoEditActivity::class.java))
        }

        lifecycleScope.launch {
            MemoRepository.observeAll().collect { memos ->
                adapter.submitList(memos)
                binding.textEmpty.visibility =
                    if (memos.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}
