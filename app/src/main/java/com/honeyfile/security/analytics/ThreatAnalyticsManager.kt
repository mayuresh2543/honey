package com.honeyfile.security.analytics

import com.honeyfile.security.data.AccessLog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ThreatAnalyticsManager {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun analyzeThreats(logs: List<AccessLog>): ThreatSummary {
        val intruderLogs = logs.filter { log ->
            val user = log.user.lowercase()
            val action = log.action.uppercase()
            action != "DEPLOYED" && (user == "intruder" || user.startsWith("intruder") || action == "BREACH")
        }

        val now = System.currentTimeMillis()
        val twentyFourHoursAgo = now - (24 * 60 * 60 * 1000L)

        val intruderLogs24h = intruderLogs.filter { log ->
            val timestampMs = parseTimestampToMs(log.timestamp)
            timestampMs >= twentyFourHoursAgo
        }

        val count24h = intruderLogs24h.size
        val countAllTime = intruderLogs.size

        val (severityLevel, threatScore) = calculateSeverity(count24h, countAllTime)

        val peakWindow = calculatePeakWindow(intruderLogs)

        val heatmapSlots = generateHeatmapSlots(intruderLogs)

        return ThreatSummary(
            severityLevel = severityLevel,
            threatScore = threatScore,
            peakAttackTimeWindow = peakWindow,
            totalIntruderAttempts24h = count24h,
            totalIntruderAttemptsAllTime = countAllTime,
            heatmapSlots = heatmapSlots
        )
    }

    private fun calculateSeverity(count24h: Int, countAllTime: Int): Pair<SeverityLevel, Int> {
        return when {
            count24h >= 3 -> Pair(SeverityLevel.CRITICAL, 95)
            count24h in 1..2 -> Pair(SeverityLevel.ELEVATED, 55)
            countAllTime > 0 -> Pair(SeverityLevel.LOW, 20)
            else -> Pair(SeverityLevel.LOW, 5)
        }
    }

    private fun calculatePeakWindow(intruderLogs: List<AccessLog>): String {
        if (intruderLogs.isEmpty()) return "None Detected"

        val hourCounts = IntArray(24) { 0 }
        val calendar = Calendar.getInstance()

        for (log in intruderLogs) {
            val ms = parseTimestampToMs(log.timestamp)
            if (ms > 0) {
                calendar.timeInMillis = ms
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                if (hour in 0..23) {
                    hourCounts[hour]++
                }
            }
        }

        var maxHour = -1
        var maxCount = 0
        for (i in 0..23) {
            if (hourCounts[i] > maxCount) {
                maxCount = hourCounts[i]
                maxHour = i
            }
        }

        if (maxHour == -1 || maxCount == 0) return "None Detected"

        val endHour = (maxHour + 2) % 24
        return String.format(Locale.getDefault(), "%02d:00 - %02d:00", maxHour, endHour)
    }

    private fun generateHeatmapSlots(intruderLogs: List<AccessLog>): List<HeatmapSlot> {
        val slotCounts = IntArray(6) { 0 }
        val calendar = Calendar.getInstance()

        for (log in intruderLogs) {
            val ms = parseTimestampToMs(log.timestamp)
            if (ms > 0) {
                calendar.timeInMillis = ms
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val slotIndex = (hour / 4).coerceIn(0, 5)
                slotCounts[slotIndex]++
            }
        }

        val slotLabels = listOf("00-04h", "04-08h", "08-12h", "12-16h", "16-20h", "20-24h")
        return slotLabels.mapIndexed { index, label ->
            val count = slotCounts[index]
            val colorHex = when {
                count >= 3 -> "#DC2626" // Solid Vivid Red
                count in 1..2 -> "#D97706" // Solid Vivid Amber
                else -> "#16A34A" // Solid Vivid Green
            }
            HeatmapSlot(timeLabel = label, count = count, intensityColorHex = colorHex)
        }
    }

    private fun parseTimestampToMs(timestampStr: String): Long {
        return try {
            val date = dateFormat.parse(timestampStr)
            date?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
