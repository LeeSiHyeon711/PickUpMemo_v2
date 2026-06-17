package com.itmakesome.pickupmemo2.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.itmakesome.pickupmemo2.data.Memo
import com.itmakesome.pickupmemo2.databinding.ItemMemoBinding

class MemoAdapter(
    private val onClick: (Memo) -> Unit,
    private val onDelete: (Memo) -> Unit
) : ListAdapter<Memo, MemoAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Memo>() {
            override fun areItemsTheSame(oldItem: Memo, newItem: Memo): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Memo, newItem: Memo): Boolean =
                oldItem == newItem
        }
    }

    inner class VH(private val binding: ItemMemoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(memo: Memo) {
            binding.textTitle.text = "${memo.storeName} ${memo.branchName}"
            binding.textContent.text = memo.content

            if (memo.tag.isNullOrEmpty()) {
                binding.textTag.visibility = View.GONE
            } else {
                binding.textTag.visibility = View.VISIBLE
                binding.textTag.text = memo.tag
            }

            binding.root.setOnClickListener { onClick(memo) }
            binding.btnDelete.setOnClickListener { onDelete(memo) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMemoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }
}
