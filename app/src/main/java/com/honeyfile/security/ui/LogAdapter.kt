package com.honeyfile.security.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.honeyfile.security.R
import com.honeyfile.security.data.AccessLog
import com.honeyfile.security.databinding.ItemLogBinding

class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private var logsList: List<AccessLog> = emptyList()

    fun submitList(logs: List<AccessLog>) {
        logsList = logs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(logsList[position])
    }

    override fun getItemCount(): Int = logsList.size

    inner class LogViewHolder(private val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(log: AccessLog) {
            binding.tvFile.text = log.file
            binding.tvTime.text = log.timestamp
            binding.tvUserBadge.text = log.user

            if (log.user.startsWith("Admin", ignoreCase = true)) {
                binding.tvUserBadge.setBackgroundResource(R.drawable.badge_admin_bg)
            } else {
                binding.tvUserBadge.setBackgroundResource(R.drawable.badge_intruder_bg)
            }
        }
    }
}
