package com.honeyfile.security.ui

import android.view.LayoutInflater
import android.view.View
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
    private val expandedPositions = mutableSetOf<Int>()

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
            "NEW", "CREATED" -> allLogsList.filter { it.action.equals("CREATED", true) || it.action.equals("COPIED", true) || it.user.contains("CREATED", true) }
            "EDITED" -> allLogsList.filter { it.action.equals("EDITED", true) || it.action.equals("MODIFIED", true) || it.user.contains("EDITED", true) }
            "COPIED" -> allLogsList.filter { it.action.equals("COPIED", true) || it.user.contains("COPIED", true) }
            "DELETED" -> allLogsList.filter { it.action.equals("DELETED", true) || it.user.contains("DELETED", true) }
            "BREACHES" -> allLogsList.filter { it.action.equals("BREACH", true) || it.user.contains("Intruder", true) }
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
        val log = getItem(position)
        val isExpanded = expandedPositions.contains(position)

        holder.bind(log, isExpanded) {
            if (isExpanded) {
                expandedPositions.remove(position)
            } else {
                expandedPositions.add(position)
            }
            notifyItemChanged(position)
        }
    }

    class DirectoryLogViewHolder(private val binding: ItemDirectoryLogBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(log: AccessLog, isExpanded: Boolean, onItemClicked: () -> Unit) {
            binding.tvDirectoryFileName.text = log.file
            binding.tvDirectoryLogTime.text = log.timestamp

            val actionUpper = log.action.uppercase()
            val userStr = log.user
            val isIntruder = userStr.contains("Intruder", ignoreCase = true) || actionUpper == "BREACH"
            val ctx = binding.root.context

            val (icon, eventText, textColor, bgDrawable, defaultDetails) = when {
                actionUpper == "DELETED" || userStr.contains("DELETED", ignoreCase = true) -> {
                    Tuple(
                        "🗑️",
                        "DELETED",
                        ContextCompat.getColor(ctx, R.color.alert_red),
                        R.drawable.badge_rounded_red,
                        if (isIntruder) "UNAUTHORIZED INTRUSION: File '${log.file}' DELETED by an Intruder at ${log.timestamp}!" else "File '${log.file}' was DELETED from directory at ${log.timestamp}."
                    )
                }
                actionUpper == "EDITED" || actionUpper == "MODIFIED" || userStr.contains("EDITED", ignoreCase = true) || userStr.contains("MODIFIED", ignoreCase = true) -> {
                    Tuple(
                        "✏️",
                        "EDITED",
                        ContextCompat.getColor(ctx, R.color.warning_yellow),
                        R.drawable.badge_rounded_yellow,
                        if (isIntruder) "UNAUTHORIZED INTRUSION: File '${log.file}' EDITED by an Intruder at ${log.timestamp}!" else "File '${log.file}' modified at ${log.timestamp}."
                    )
                }
                actionUpper == "CREATED" || userStr.contains("CREATED", ignoreCase = true) -> {
                    Tuple(
                        "➕",
                        "NEW FILE",
                        ContextCompat.getColor(ctx, R.color.primary_accent),
                        R.drawable.badge_rounded_green,
                        if (isIntruder) "UNAUTHORIZED INTRUSION: New file '${log.file}' CREATED by an Intruder at ${log.timestamp}!" else "New file '${log.file}' created in directory at ${log.timestamp}."
                    )
                }
                actionUpper == "COPIED" || userStr.contains("COPIED", ignoreCase = true) -> {
                    Tuple(
                        "📋",
                        "COPIED",
                        ContextCompat.getColor(ctx, R.color.primary_accent),
                        R.drawable.badge_rounded_green,
                        if (isIntruder) "UNAUTHORIZED INTRUSION: File '${log.file}' COPIED by an Intruder at ${log.timestamp}!" else "File '${log.file}' copied into directory at ${log.timestamp}."
                    )
                }
                actionUpper == "RENAMED" || userStr.contains("RENAMED", ignoreCase = true) -> {
                    Tuple(
                        "🔄",
                        "RENAMED",
                        ContextCompat.getColor(ctx, R.color.primary_accent),
                        R.drawable.badge_rounded_green,
                        "File '${log.file}' renamed or moved at ${log.timestamp}."
                    )
                }
                isIntruder -> {
                    Tuple(
                        "🚨",
                        "BREACH",
                        ContextCompat.getColor(ctx, R.color.alert_red),
                        R.drawable.badge_rounded_red,
                        "UNAUTHORIZED ACCESS BREACH on honeyfile '${log.file}' at ${log.timestamp}! Camera captured intruder photo & email alert sent."
                    )
                }
                else -> {
                    Tuple(
                        "👤",
                        userStr,
                        ContextCompat.getColor(ctx, R.color.success_green),
                        R.drawable.badge_rounded_green,
                        "Verified access by $userStr on file '${log.file}' at ${log.timestamp}."
                    )
                }
            }

            binding.tvCategoryIcon.text = icon
            binding.tvDirectoryEventType.text = eventText
            binding.tvDirectoryEventType.setTextColor(textColor)
            binding.tvDirectoryEventType.setBackgroundResource(bgDrawable)

            binding.tvChangeDetails.text = if (log.details.isNotBlank()) log.details else defaultDetails

            // Expand / Collapse state
            if (isExpanded) {
                binding.llDetailsContainer.visibility = View.VISIBLE
                binding.tvExpandArrow.text = "▲"
            } else {
                binding.llDetailsContainer.visibility = View.GONE
                binding.tvExpandArrow.text = "▼"
            }

            binding.llHeader.setOnClickListener {
                onItemClicked()
            }
        }
    }

    private data class Tuple(
        val icon: String,
        val text: String,
        val textColor: Int,
        val bgDrawable: Int,
        val details: String
    )

    private class AccessLogDiffCallback : DiffUtil.ItemCallback<AccessLog>() {
        override fun areItemsTheSame(oldItem: AccessLog, newItem: AccessLog): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AccessLog, newItem: AccessLog): Boolean {
            return oldItem == newItem
        }
    }
}
