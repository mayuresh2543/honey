package com.honeyfile.security.ui.compose.dialogs

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

    // Prepare Pie Slices
    val pieSlices = remember(allLogs) {
        val authorizedCount = allLogs.count { !it.user.contains("Intruder", ignoreCase = true) && it.action != "BREACH" && it.action != "DEPLOYED" }
        val deletedCount = allLogs.count { it.action.equals("DELETED", ignoreCase = true) }
        val editedCount = allLogs.count { it.action.equals("EDITED", ignoreCase = true) || it.action.equals("MODIFIED", ignoreCase = true) }
        val createdCount = allLogs.count { it.action.equals("CREATED", ignoreCase = true) || it.action.equals("NEW", ignoreCase = true) || it.action.equals("COPIED", ignoreCase = true) }
        val breachCount = allLogs.count { it.user.contains("Intruder", ignoreCase = true) || it.action.equals("BREACH", ignoreCase = true) }

        if (allLogs.isEmpty()) {
            listOf(ComposePieSlice("Protected System (0 Threat Events)", 1f, CyberGreen))
        } else {
            val list = mutableListOf<ComposePieSlice>()
            if (authorizedCount > 0) list.add(ComposePieSlice("Admin Passes ($authorizedCount)", authorizedCount.toFloat(), CyberGreen))
            if (breachCount > 0) list.add(ComposePieSlice("Intruder Breaches ($breachCount)", breachCount.toFloat(), AlertRed))
            if (deletedCount > 0) list.add(ComposePieSlice("Deletions ($deletedCount)", deletedCount.toFloat(), PurpleAccent))
            if (editedCount > 0) list.add(ComposePieSlice("Modifications ($editedCount)", editedCount.toFloat(), WarningYellow))
            if (createdCount > 0) list.add(ComposePieSlice("New Files ($createdCount)", createdCount.toFloat(), CyanAccent))
            if (list.isEmpty()) list.add(ComposePieSlice("Audited Events (${allLogs.size})", allLogs.size.toFloat(), CyanAccent))
            list
        }
    }

    val timeSlots = listOf("00-04h", "04-08h", "08-12h", "12-16h", "16-20h", "20-24h")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Top Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "📊 Threat Intelligence",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "24-hour endpoint risk distribution & heatmap",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Severity Banner
                val (badgeText, badgeColor, explanation) = when (summary.severityLevel) {
                    SeverityLevel.LOW -> Triple(
                        "LOW RISK 🟢",
                        CyberGreen,
                        "System threat level is LOW (${summary.threatScore}/100). No significant unauthorized breach patterns detected."
                    )
                    SeverityLevel.ELEVATED -> Triple(
                        "ELEVATED THREAT 🟡",
                        WarningYellow,
                        "System threat level is ELEVATED (${summary.threatScore}/100). Multiple unauthorized file access attempts or alterations recorded."
                    )
                    SeverityLevel.CRITICAL -> Triple(
                        "CRITICAL BREACH 🔴",
                        AlertRed,
                        "CRITICAL SECURITY ALERT (${summary.threatScore}/100)! High frequency of intruder intrusions or file deletions detected."
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(badgeColor.copy(alpha = 0.12f))
                        .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                        Text(
                            text = "Score: ${summary.threatScore} / 100",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = explanation,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Donut Chart Graphic
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    DonutChart(slices = pieSlices)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${summary.threatScore}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                        Text(
                            text = "Risk Index",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Legends
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pieSlices.forEach { slice ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(slice.color)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = slice.label,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Peak Attack Window Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "🔥 Peak Attack Window: ${summary.peakAttackTimeWindow}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Highest volume of security alerts recorded during active surveillance.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 6-Slot Heatmap Time Chips
                Text(
                    text = "Hourly Attack Window Heatmap",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    timeSlots.forEachIndexed { index, slotLabel ->
                        val isSelected = selectedSlotIndex == index
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedSlotIndex = index },
                            label = { Text(slotLabel, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
                                selectedLabelColor = CyanAccent
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Slot Breaches Header
                Text(
                    text = "Logs in ${timeSlots[selectedSlotIndex]} (${slotLogs.size} events)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (slotLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No recorded events during this time slot ✅",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
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

@Composable
private fun DonutChart(slices: List<ComposePieSlice>) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 800),
        label = "donut_anim"
    )

    Canvas(modifier = Modifier.size(160.dp)) {
        var startAngle = -90f
        val strokeWidth = 22.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2f
        val topLeft = Offset(
            (size.width - radius * 2) / 2f,
            (size.height - radius * 2) / 2f
        )
        val arcSize = Size(radius * 2, radius * 2)

        slices.forEach { slice ->
            val sweepAngle = if (total > 0f) (slice.value / total) * 360f * animatedProgress else 360f
            drawArc(
                color = slice.color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            startAngle += sweepAngle
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = log.file,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = log.timestamp,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(badgeColor.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = badgeText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor
            )
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
