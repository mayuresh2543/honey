package com.honeyfile.security.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.honeyfile.security.R
import com.honeyfile.security.analytics.SeverityLevel
import com.honeyfile.security.analytics.ThreatAnalyticsManager
import com.honeyfile.security.data.AccessLog
import com.honeyfile.security.data.AppDatabase
import com.honeyfile.security.databinding.DialogThreatAnalyticsDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ThreatAnalyticsDetailDialogFragment : DialogFragment() {

    private var _binding: DialogThreatAnalyticsDetailBinding? = null
    private val binding get() = _binding!!

    private val threatAnalyticsManager = ThreatAnalyticsManager()
    private val slotAdapter = DirectoryLogAdapter()
    private var allLogs: List<AccessLog> = emptyList()
    private var selectedSlotIndex = 0

    companion object {
        const val TAG = "ThreatAnalyticsDetailDialogFragment"
        private const val ARG_INITIAL_SLOT = "arg_initial_slot"

        fun newInstance(initialSlotIndex: Int = 0): ThreatAnalyticsDetailDialogFragment {
            val fragment = ThreatAnalyticsDetailDialogFragment()
            val args = Bundle()
            args.putInt(ARG_INITIAL_SLOT, initialSlotIndex)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_HoneyfileSecurity)
        selectedSlotIndex = arguments?.getInt(ARG_INITIAL_SLOT, 0) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogThreatAnalyticsDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val themeManager = com.honeyfile.security.auth.ThemeManager(requireContext())
        themeManager.applyInstant(binding.root, dialog?.window, themeManager.isDarkMode)

        binding.btnClose.setOnClickListener { dismiss() }

        binding.rvSlotBreaches.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSlotBreaches.adapter = slotAdapter

        setupChipListeners()
        loadAnalyticsData()
    }

    private fun setupChipListeners() {
        binding.chipGroupTimeSlots.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: R.id.chipSlot0
            selectedSlotIndex = when (checkedId) {
                R.id.chipSlot0 -> 0
                R.id.chipSlot1 -> 1
                R.id.chipSlot2 -> 2
                R.id.chipSlot3 -> 3
                R.id.chipSlot4 -> 4
                R.id.chipSlot5 -> 5
                else -> 0
            }
            filterSlotLogs()
        }

        // Set initial chip check
        val targetChipId = when (selectedSlotIndex) {
            0 -> R.id.chipSlot0
            1 -> R.id.chipSlot1
            2 -> R.id.chipSlot2
            3 -> R.id.chipSlot3
            4 -> R.id.chipSlot4
            5 -> R.id.chipSlot5
            else -> R.id.chipSlot0
        }
        binding.chipGroupTimeSlots.check(targetChipId)
    }

    private fun loadAnalyticsData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            allLogs = db.logDao().getAllLogsList()
            val summary = threatAnalyticsManager.analyzeThreats(allLogs)

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext

                // Render Severity Badge
                when (summary.severityLevel) {
                    SeverityLevel.LOW -> {
                        binding.tvModalSeverityBadge.text = "LOW 🟢"
                        binding.tvModalSeverityBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.success_green))
                        binding.tvModalSeverityBadge.setBackgroundResource(R.drawable.badge_rounded_green)
                        binding.tvModalSeverityExplanation.text = "System threat level is LOW (${summary.threatScore}/100). No significant unauthorized breach patterns detected."
                    }
                    SeverityLevel.ELEVATED -> {
                        binding.tvModalSeverityBadge.text = "ELEVATED 🟡"
                        binding.tvModalSeverityBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.warning_yellow))
                        binding.tvModalSeverityBadge.setBackgroundResource(R.drawable.badge_rounded_yellow)
                        binding.tvModalSeverityExplanation.text = "System threat level is ELEVATED (${summary.threatScore}/100). Multiple unauthorized file access attempts or alterations recorded."
                    }
                    SeverityLevel.CRITICAL -> {
                        binding.tvModalSeverityBadge.text = "CRITICAL 🔴"
                        binding.tvModalSeverityBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.alert_red))
                        binding.tvModalSeverityBadge.setBackgroundResource(R.drawable.badge_rounded_red)
                        binding.tvModalSeverityExplanation.text = "CRITICAL SECURITY BREACH ALERT (${summary.threatScore}/100)! High frequency of intruder intrusions or file deletions detected."
                    }
                }

                binding.tvModalScoreText.text = "Risk Score: ${summary.threatScore} / 100"
                binding.tvModalPeakWindow.text = "Peak Window: ${summary.peakAttackTimeWindow}"
                binding.tvModalPeakExplanation.text = "This 4-hour window registered the highest volume of intruder breaches during active surveillance."

                // Configure Pie Chart Data & Legends
                val authorizedCount = allLogs.count { !it.user.contains("Intruder", ignoreCase = true) }
                val deletedCount = allLogs.count { it.action.equals("DELETED", ignoreCase = true) }
                val editedCount = allLogs.count { it.action.equals("EDITED", ignoreCase = true) || it.action.equals("MODIFIED", ignoreCase = true) }
                val createdCount = allLogs.count { it.action.equals("CREATED", ignoreCase = true) || it.action.equals("NEW", ignoreCase = true) || it.action.equals("COPIED", ignoreCase = true) }
                val breachCount = allLogs.count { it.user.contains("Intruder", ignoreCase = true) && it.action.equals("BREACH", ignoreCase = true) }

                val pieSlices = if (allLogs.isEmpty()) {
                    listOf(
                        PieSlice("Protected System (0 Threat Events)", 1f, ContextCompat.getColor(requireContext(), R.color.success_green))
                    )
                } else {
                    val list = mutableListOf<PieSlice>()
                    if (authorizedCount > 0) {
                        list.add(PieSlice("Authorized Admin Access ($authorizedCount)", authorizedCount.toFloat(), ContextCompat.getColor(requireContext(), R.color.success_green)))
                    }
                    if (deletedCount > 0) {
                        list.add(PieSlice("File Deletions ($deletedCount)", deletedCount.toFloat(), android.graphics.Color.parseColor("#9C27B0")))
                    }
                    if (editedCount > 0) {
                        list.add(PieSlice("File Modifications ($editedCount)", editedCount.toFloat(), ContextCompat.getColor(requireContext(), R.color.warning_yellow)))
                    }
                    if (createdCount > 0) {
                        list.add(PieSlice("New Files Created ($createdCount)", createdCount.toFloat(), ContextCompat.getColor(requireContext(), R.color.primary_accent)))
                    }
                    if (breachCount > 0) {
                        list.add(PieSlice("Intruder Access Breaches ($breachCount)", breachCount.toFloat(), ContextCompat.getColor(requireContext(), R.color.alert_red)))
                    }
                    if (list.isEmpty()) {
                        list.add(PieSlice("Audited Security Events (${allLogs.size})", allLogs.size.toFloat(), ContextCompat.getColor(requireContext(), R.color.primary_accent)))
                    }
                    list
                }

                binding.pieChartView.setData(
                    slicesList = pieSlices,
                    title = "${summary.threatScore}",
                    subTitle = "Risk Index"
                )

                // Render dynamic legend items
                binding.legendContainer.removeAllViews()
                for (slice in pieSlices) {
                    val legendItem = android.widget.LinearLayout(requireContext()).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(0, 8, 0, 8)
                    }

                    val colorDot = View(requireContext()).apply {
                        layoutParams = android.widget.LinearLayout.LayoutParams(24, 24).apply {
                            setMargins(0, 0, 16, 0)
                        }
                        background = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setColor(slice.color)
                        }
                    }

                    val labelTv = android.widget.TextView(requireContext()).apply {
                        text = slice.label
                        textSize = 12f
                        setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                    }

                    legendItem.addView(colorDot)
                    legendItem.addView(labelTv)
                    binding.legendContainer.addView(legendItem)
                }

                filterSlotLogs()
            }
        }
    }

    private fun filterSlotLogs() {
        val startHour = selectedSlotIndex * 4
        val endHour = startHour + 4

        val slotLogs = allLogs.filter { log ->
            val hour = parseLogHour(log.timestamp)
            hour in startHour until endHour
        }

        slotAdapter.updateLogs(slotLogs)
    }

    private fun parseLogHour(timestamp: String): Int {
        return try {
            val parts = timestamp.split(" ")
            if (parts.size >= 2) {
                val timeParts = parts[1].split(":")
                timeParts[0].toInt()
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
