package com.honeyfile.security.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.honeyfile.security.R
import com.honeyfile.security.data.AccessLog
import com.honeyfile.security.databinding.ItemDirectoryLogBinding

class DirectoryLogAdapter : ListAdapter<AccessLog, DirectoryLogAdapter.DirectoryLogViewHolder>(AccessLogDiffCallback()) {

    private var allLogsList: List<AccessLog> = emptyList()
    private var currentFilterCategory: String = "ALL"

    fun updateLogs(newLogs: List<AccessLog>) {
        allLogsList = newLogs
        applyCurrentFilter()
    }

    fun setFilterCategory(category: String) {
        currentFilterCategory = category
        applyCurrentFilter()
    }

    private fun applyCurrentFilter() {
        val filtered = when (currentFilterCategory.uppercase()) {
            "EDITED" -> allLogsList.filter { it.user.contains("EDITED", ignoreCase = true) || it.user.contains("MODIFIED", ignoreCase = true) }
            "COPIED" -> allLogsList.filter { it.user.contains("COPIED", ignoreCase = true) || it.user.contains("CREATED", ignoreCase = true) }
            "DELETED" -> allLogsList.filter { it.user.contains("DELETED", ignoreCase = true) }
            "BREACHES" -> allLogsList.filter { it.user.contains("Intruder", ignoreCase = true) }
            else -> allLogsList
        }
        submitList(filtered)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DirectoryLogViewHolder {
        val binding = ItemDirectoryLogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DirectoryLogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DirectoryLogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DirectoryLogViewHolder(private val binding: ItemDirectoryLogBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(log: AccessLog) {
            binding.tvDirectoryFileName.text = log.file
            binding.tvDirectoryLogTime.text = log.timestamp

            val userStr = log.user
            val ctx = binding.root.context

            when {
                userStr.contains("DELETED", ignoreCase = true) -> {
                    binding.tvCategoryIcon.text = "🗑️"
                    binding.tvDirectoryEventType.text = "DELETED"
                    binding.tvDirectoryEventType.setTextColor(ContextCompat.getColor(ctx, R.color.alert_red))
                    binding.tvDirectoryEventType.setBackgroundResource(R.drawable.badge_rounded_red)
                }
                userStr.contains("EDITED", ignoreCase = true) || userStr.contains("MODIFIED", ignoreCase = true) -> {
                    binding.tvCategoryIcon.text = "✏️"
                    binding.tvDirectoryEventType.text = "EDITED"
                    binding.tvDirectoryEventType.setTextColor(ContextCompat.getColor(ctx, R.color.warning_yellow))
                    binding.tvDirectoryEventType.setBackgroundResource(R.drawable.badge_rounded_yellow)
                }
                userStr.contains("COPIED", ignoreCase = true) || userStr.contains("CREATED", ignoreCase = true) -> {
                    binding.tvCategoryIcon.text = "📋"
                    binding.tvDirectoryEventType.text = "COPIED/NEW"
                    binding.tvDirectoryEventType.setTextColor(ContextCompat.getColor(ctx, R.color.primary_accent))
                    binding.tvDirectoryEventType.setBackgroundResource(R.drawable.badge_rounded_green)
                }
                userStr.contains("RENAMED", ignoreCase = true) -> {
                    binding.tvCategoryIcon.text = "🔄"
                    binding.tvDirectoryEventType.text = "RENAMED"
                    binding.tvDirectoryEventType.setTextColor(ContextCompat.getColor(ctx, R.color.primary_accent))
                    binding.tvDirectoryEventType.setBackgroundResource(R.drawable.badge_rounded_green)
                }
                userStr.contains("Intruder", ignoreCase = true) -> {
                    binding.tvCategoryIcon.text = "🚨"
                    binding.tvDirectoryEventType.text = "BREACH"
                    binding.tvDirectoryEventType.setTextColor(ContextCompat.getColor(ctx, R.color.alert_red))
                    binding.tvDirectoryEventType.setBackgroundResource(R.drawable.badge_rounded_red)
                }
                else -> {
                    binding.tvCategoryIcon.text = "👤"
                    binding.tvDirectoryEventType.text = userStr
                    binding.tvDirectoryEventType.setTextColor(ContextCompat.getColor(ctx, R.color.success_green))
                    binding.tvDirectoryEventType.setBackgroundResource(R.drawable.badge_rounded_green)
                }
            }
        }
    }

    private class AccessLogDiffCallback : DiffUtil.ItemCallback<AccessLog>() {
        override fun areItemsTheSame(oldItem: AccessLog, newItem: AccessLog): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AccessLog, newItem: AccessLog): Boolean {
            return oldItem == newItem
        }
    }
}
