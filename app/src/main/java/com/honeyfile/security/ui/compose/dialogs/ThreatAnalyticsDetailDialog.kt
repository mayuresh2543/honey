package com.honeyfile.security.ui.compose.dialogs

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.honeyfile.security.analytics.HeatmapSlot
import com.honeyfile.security.analytics.SeverityLevel
import com.honeyfile.security.analytics.ThreatAnalyticsManager
import com.honeyfile.security.data.AccessLog
import com.honeyfile.security.data.AppDatabase
import com.honeyfile.security.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ComposePieSlice(
    val label: String,
    val value: Float,
    val color: Color
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThreatAnalyticsDetailDialog(
    initialSlotIndex: Int = 0,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val threatAnalyticsManager = remember { ThreatAnalyticsManager() }

    var allLogs by remember { mutableStateOf<List<AccessLog>>(emptyList()) }
    var selectedSlotIndex by remember { mutableIntStateOf(initialSlotIndex) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            allLogs = db.logDao().getAllLogsList()
        }
    }

    val summary = remember(allLogs) {
        threatAnalyticsManager.analyzeThreats(allLogs)
    }

    val slotLogs = remember(allLogs, selectedSlotIndex) {
        val startHour = selectedSlotIndex * 4
        val endHour = startHour + 4
        allLogs.filter { log ->
            val hour = parseHourFromTimestamp(log.timestamp)
            hour in startHour until endHour
        }
    }

    // Prepare Pie Slices for actual security event breakdown
    val pieSlices = remember(allLogs) {
        val authorizedCount = allLogs.count { !it.user.contains("Intruder", ignoreCase = true) && it.action != "BREACH" && it.action != "DEPLOYED" }
        val deletedCount = allLogs.count { it.action.equals("DELETED", ignoreCase = true) }
        val editedCount = allLogs.count { it.action.equals("EDITED", ignoreCase = true) || it.action.equals("MODIFIED", ignoreCase = true) }
        val createdCount = allLogs.count { it.action.equals("CREATED", ignoreCase = true) || it.action.equals("NEW", ignoreCase = true) || it.action.equals("COPIED", ignoreCase = true) }
        val breachCount = allLogs.count { it.user.contains("Intruder", ignoreCase = true) || it.action.equals("BREACH", ignoreCase = true) }

        val list = mutableListOf<ComposePieSlice>()
        if (authorizedCount > 0) list.add(ComposePieSlice("Admin Passes ($authorizedCount)", authorizedCount.toFloat(), CyberGreen))
        if (breachCount > 0) list.add(ComposePieSlice("Intruder Breaches ($breachCount)", breachCount.toFloat(), AlertRed))
        if (deletedCount > 0) list.add(ComposePieSlice("Deletions ($deletedCount)", deletedCount.toFloat(), PurpleAccent))
        if (editedCount > 0) list.add(ComposePieSlice("Modifications ($editedCount)", editedCount.toFloat(), WarningYellow))
        if (createdCount > 0) list.add(ComposePieSlice("New Files ($createdCount)", createdCount.toFloat(), CyanAccent))
        list
    }

    val timeSlots = listOf("00-04h", "04-08h", "08-12h", "12-16h", "16-20h", "20-24h")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = DialogSurfaceShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .fillMaxHeight(0.92f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // PINNED TOP HEADER: Stays fixed at the top so it never scrolls away or gets cut off
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "📊 Threat Intelligence",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.3.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "24-hour endpoint risk distribution & heatmap",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.expressiveBounceClickable(onClick = onDismiss)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // SCROLLABLE CONTENT BODY
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Severity Status Banner
                    val (badgeText, badgeColor, explanation) = when (summary.severityLevel) {
                        SeverityLevel.LOW -> Triple(
                            "LOW RISK 🟢",
                            CyberGreen,
                            "System threat level is LOW (${summary.threatScore}/100). Endpoint perimeter is secure with zero critical breach patterns."
                        )
                        SeverityLevel.ELEVATED -> Triple(
                            "ELEVATED THREAT 🟡",
                            WarningYellow,
                            "System threat level is ELEVATED (${summary.threatScore}/100). Multiple unauthorized file access attempts or alterations recorded."
                        )
                        SeverityLevel.CRITICAL -> Triple(
                            "CRITICAL BREACH 🔴",
                            AlertRed,
                            "CRITICAL SECURITY ALERT (${summary.threatScore}/100)! High frequency of intruder breaches or honeypot tampering detected."
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = badgeColor.copy(alpha = 0.10f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = FullPillShape,
                                    color = badgeColor.copy(alpha = 0.2f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = badgeText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = badgeColor,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Text(
                                    text = "Score: ${summary.threatScore} / 100",
                                    style = TelemetryCodeBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = explanation,
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // 2. RADIAL RISK GAUGE: Accurately renders threatScore / 100% arc
                    RadialRiskGauge(
                        threatScore = summary.threatScore,
                        severityLevel = summary.severityLevel,
                        badgeColor = badgeColor
                    )

                    // 3. SECURITY EVENT DISTRIBUTION (Pie / Donut Chart)
                    if (pieSlices.isEmpty()) {
                        // Clean state when there are 0 events
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(CyberGreen.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🛡️", fontSize = 18.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Clean Audit Ledger (0 Threat Events)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "No honeypot triggers, breach attempts, or file alterations detected.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        // Multi-segment event distribution donut
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Security Event Distribution",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            EventDonutChart(slices = pieSlices, totalEvents = allLogs.size)

                            Spacer(modifier = Modifier.height(12.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pieSlices.forEach { slice ->
                                    Surface(
                                        shape = FullPillShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(slice.color)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = slice.label,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Peak Attack Window Card
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "🔥 Peak Attack Window: ${summary.peakAttackTimeWindow}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color = CyanNeon
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Highest concentration of security alerts recorded during active surveillance.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 5. HOURLY ATTACK WINDOW HEATMAP (2 Rows of 3 Columns - Zero character wrapping)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Hourly Attack Window Heatmap",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.2.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        val heatmapSlots = remember(summary.heatmapSlots) {
                            if (summary.heatmapSlots.size == 6) summary.heatmapSlots
                            else timeSlots.map { HeatmapSlot(it, 0, "#16A34A") }
                        }

                        // 2 Rows x 3 Columns Layout
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Row 1: Slots 0, 1, 2 (00-04h, 04-08h, 08-12h)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (index in 0..2) {
                                    HeatmapSlotCard(
                                        modifier = Modifier.weight(1f),
                                        slot = heatmapSlots[index],
                                        isSelected = selectedSlotIndex == index,
                                        onClick = { selectedSlotIndex = index }
                                    )
                                }
                            }

                            // Row 2: Slots 3, 4, 5 (12-16h, 16-20h, 20-24h)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (index in 3..5) {
                                    HeatmapSlotCard(
                                        modifier = Modifier.weight(1f),
                                        slot = heatmapSlots[index],
                                        isSelected = selectedSlotIndex == index,
                                        onClick = { selectedSlotIndex = index }
                                    )
                                }
                            }
                        }
                    }

                    // 6. Slot Breaches & Events List
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Logs in ${timeSlots[selectedSlotIndex]} (${slotLogs.size} events)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (slotLogs.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 18.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No recorded events during this time slot ✅",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                slotLogs.forEach { log ->
                                    SlotLogItem(log = log)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Radial Risk Gauge (0-100)
 * Displays an animated progress arc accurately proportional to the threat score (e.g. 5/100 = 5% arc).
 */
@Composable
private fun RadialRiskGauge(
    threatScore: Int,
    severityLevel: SeverityLevel,
    badgeColor: Color
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (threatScore / 100f).coerceIn(0.04f, 1f),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "gauge_progress_anim"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

        Canvas(modifier = Modifier.size(160.dp)) {
            val strokeWidth = 18.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2f
            val topLeft = Offset(
                (size.width - radius * 2) / 2f,
                (size.height - radius * 2) / 2f
            )
            val arcSize = Size(radius * 2, radius * 2)

            // 1. Full 360-degree background track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 2. Active score progress arc
            val sweep = animatedProgress * 360f

            // Soft glowing underlay
            drawArc(
                color = badgeColor.copy(alpha = 0.25f),
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth + 6.dp.toPx(), cap = StrokeCap.Round)
            )

            // Primary active arc
            drawArc(
                color = badgeColor,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Center Risk Score Metrics
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$threatScore",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = badgeColor
                )
                Text(
                    text = " / 100",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                )
            }
            Text(
                text = "Risk Index",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Event Donut Chart
 * Multi-segment pie chart with clean separation gaps (no cap overlap) for event breakdown.
 */
@Composable
private fun EventDonutChart(slices: List<ComposePieSlice>, totalEvents: Int) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "donut_anim"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(145.dp)) {
            var startAngle = -90f
            val strokeWidth = 18.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2f
            val topLeft = Offset(
                (size.width - radius * 2) / 2f,
                (size.height - radius * 2) / 2f
            )
            val arcSize = Size(radius * 2, radius * 2)

            slices.forEach { slice ->
                val rawSweep = if (total > 0f) (slice.value / total) * 360f * animatedProgress else 360f
                val gap = if (slices.size > 1 && rawSweep > 4f) 2f else 0f
                val sweepAngle = (rawSweep - gap).coerceAtLeast(1f)

                drawArc(
                    color = slice.color,
                    startAngle = startAngle + (gap / 2f),
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
                startAngle += rawSweep
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$totalEvents",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Total Events",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Interactive Heatmap Slot Card
 * Clean 2-row layout with single-line horizontal text and live alert status indicator dot.
 */
@Composable
private fun HeatmapSlotCard(
    modifier: Modifier = Modifier,
    slot: HeatmapSlot,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val indicatorColor = remember(slot.intensityColorHex) {
        try {
            Color(android.graphics.Color.parseColor(slot.intensityColorHex))
        } catch (e: Exception) {
            CyberGreen
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) CyanAccent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (isSelected) CyanAccent else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier.expressiveBounceClickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = slot.timeLabel,
                style = TelemetryCodeBold,
                fontSize = 11.sp,
                color = if (isSelected) CyanAccent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(indicatorColor)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (slot.count == 0) "Normal" else "${slot.count} alerts",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (slot.count == 0) MaterialTheme.colorScheme.onSurfaceVariant else AlertRed,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun SlotLogItem(log: AccessLog) {
    val isIntruder = log.user.contains("Intruder", ignoreCase = true) || log.action.equals("BREACH", ignoreCase = true)
    val isDeployed = log.action.equals("DEPLOYED", ignoreCase = true)

    val (badgeText, badgeColor) = when {
        isDeployed -> Pair("DEPLOYED 🍯", CyanAccent)
        isIntruder -> Pair("INTRUDER 🚨", AlertRed)
        else -> Pair("${log.user} 🟢", CyberGreen)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.file,
                    style = TelemetryCodeBold.copy(color = MaterialTheme.colorScheme.onSurface),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = log.timestamp,
                    style = TelemetryCodeSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
            Surface(
                shape = FullPillShape,
                color = badgeColor.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
            ) {
                Text(
                    text = badgeText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

private fun parseHourFromTimestamp(timestamp: String): Int {
    return try {
        val parts = timestamp.split(" ")
        if (parts.size >= 2) {
            val timeParts = parts[1].split(":")
            timeParts[0].toInt()
        } else 0
    } catch (e: Exception) {
        0
    }
}
