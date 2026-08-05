package com.honeyfile.security.analytics

enum class SeverityLevel {
    LOW,
    ELEVATED,
    CRITICAL
}

data class HeatmapSlot(
    val timeLabel: String,
    val count: Int,
    val intensityColorHex: String
)

data class ThreatSummary(
    val severityLevel: SeverityLevel,
    val threatScore: Int,
    val peakAttackTimeWindow: String,
    val totalIntruderAttempts24h: Int,
    val totalIntruderAttemptsAllTime: Int,
    val heatmapSlots: List<HeatmapSlot>
)
