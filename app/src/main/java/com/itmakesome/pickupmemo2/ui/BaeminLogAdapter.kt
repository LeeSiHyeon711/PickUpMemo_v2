package com.itmakesome.pickupmemo2.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.itmakesome.pickupmemo2.data.BaeminLog
import com.itmakesome.pickupmemo2.databinding.ItemBaeminLogBinding
import com.itmakesome.pickupmemo2.util.TimeFormat

/**
 * 배민 로그 목록 RecyclerView 어댑터 (FEAT-13).
 * item_baemin_log.xml: tvTime(시각), tvType(이벤트 타입), tvText(본문)
 */
class BaeminLogAdapter : RecyclerView.Adapter<BaeminLogAdapter.VH>() {

    private val items = ArrayList<BaeminLog>()

    fun submit(list: List<BaeminLog>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemBaeminLogBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class VH(private val binding: ItemBaeminLogBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(log: BaeminLog) {
            binding.tvTime.text = TimeFormat.formatTimestamp(log.capturedAt)
            binding.tvType.text = log.eventType
            binding.tvText.text = log.text
        }
    }
}
