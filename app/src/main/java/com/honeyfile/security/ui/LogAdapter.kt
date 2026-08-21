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
        val themeManager = com.honeyfile.security.auth.ThemeManager(holder.itemView.context)
        themeManager.applyInstant(holder.itemView, toDark = themeManager.isDarkMode)
        holder.bind(logsList[position])
    }

    override fun getItemCount(): Int = logsList.size

    inner class LogViewHolder(private val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(log: AccessLog) {
            binding.tvFile.text = log.file
            binding.tvTime.text = log.timestamp
            binding.tvUserBadge.text = log.user

            val isIntruder = log.user.contains("Intruder", ignoreCase = true) ||
                             log.action.equals("BREACH", ignoreCase = true)

            val isDeployed = log.action.equals("DEPLOYED", ignoreCase = true) ||
                             log.user.contains("DEPLOYED", ignoreCase = true)

            when {
                isDeployed -> {
                    binding.tvUserBadge.setBackgroundResource(R.drawable.badge_rounded_cyan)
                    binding.tvUserBadge.setTextColor(ContextCompat.getColor(binding.root.context, R.color.primary_accent))
                }
                isIntruder -> {
                    binding.tvUserBadge.setBackgroundResource(R.drawable.badge_intruder_bg)
                    binding.tvUserBadge.setTextColor(ContextCompat.getColor(binding.root.context, R.color.white))
                }
                else -> {
                    // All enrolled admins (individual names like Mayuresh, Anirudh, Admin, etc.) get green badge
                    binding.tvUserBadge.setBackgroundResource(R.drawable.badge_admin_bg)
                    binding.tvUserBadge.setTextColor(ContextCompat.getColor(binding.root.context, R.color.white))
                }
            }
        }
    }
}
