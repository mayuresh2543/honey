package com.honeyfile.security.ui.compose

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honeyfile.security.data.AccessLog
import com.honeyfile.security.ui.theme.*

@Composable
fun ScannerScreen(
    folderUri: Uri?,
    folderDisplayName: String,
    isAutoScanEnabled: Boolean,
    onAutoScanToggled: (Boolean) -> Unit,
    onSelectFolderClicked: () -> Unit,
    totalFilesScanned: Int,
    honeypotsFound: Int,
    latestChangeSummary: String,
    directoryLogs: List<AccessLog>
) {
    var selectedFilterCategory by remember { mutableStateOf("ALL") }
    val expandedLogIds = remember { mutableStateListOf<Long>() }

    val filterChips = listOf(
        "ALL" to "ALL",
        "NEW" to "NEW ➕",
        "EDITED" to "EDITED ✏️",
        "COPIED" to "COPIED 📋",
        "DELETED" to "DELETED 🗑️",
        "OPENED" to "OPENED 👁️",
        "DEPLOYED" to "DEPLOYED 🍯",
        "BREACHES" to "BREACHES 🚨"
    )

    val filteredLogs = remember(directoryLogs, selectedFilterCategory) {
        if (selectedFilterCategory == "ALL") {
            directoryLogs
        } else {
            directoryLogs.filter { log ->
                val action = log.action.uppercase()
                val user = log.user.lowercase()
                val isIntruder = user.contains("intruder") || action == "BREACH"
                when (selectedFilterCategory) {
                    "NEW" -> (action == "CREATED" || action == "NEW" || user.contains("created")) && action != "DEPLOYED"
                    "EDITED" -> (action == "EDITED" || action == "MODIFIED" || user.contains("edited") || user.contains("modified")) && action != "DEPLOYED"
                    "COPIED" -> (action == "COPIED" || user.contains("copied")) && action != "DEPLOYED"
                    "DELETED" -> (action == "DELETED" || user.contains("deleted")) && action != "DEPLOYED"
                    "OPENED" -> (action == "OPENED" || action == "ACCESSED" || user.contains("opened") || user.contains("accessed")) && action != "DEPLOYED"
                    "DEPLOYED" -> action == "DEPLOYED" || user.contains("deployed")
                    "BREACHES" -> isIntruder && action != "DEPLOYED"
                    else -> true
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // MONITORED FOLDER CARD (M3 Expressive 28dp Container)
        item {
            val isMonitoring = folderUri != null && isAutoScanEnabled
            val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_scale"
            )

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    if (isMonitoring) CyberGreen.copy(alpha = 0.08f) else Color.Transparent,
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CyanAccent.copy(alpha = 0.15f))
                                    .border(1.dp, CyanAccent.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = HoneyIcons.Folder,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Directory Sentinel",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.2.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Status Badge with Animated Radar Pulse
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isMonitoring) CyberGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isMonitoring) CyberGreen.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                if (isMonitoring) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .scale(pulseScale)
                                            .clip(CircleShape)
                                            .background(CyberGreen)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = if (isMonitoring) "MONITORING 🟢" else "IDLE ⚪",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isMonitoring) CyberGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (folderUri != null) "📂 $folderDisplayName" else "No directory selected for surveillance",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (folderUri != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = onSelectFolderClicked,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = CyanNeon
                            ),
                            modifier = Modifier.expressiveBounceClickable(onClick = onSelectFolderClicked)
                        ) {
                            Icon(
                                imageVector = HoneyIcons.FolderOpen,
                                contentDescription = null,
                                tint = CyanNeon,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (folderUri == null) "Select Folder" else "Change Folder",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Auto-Scan",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = isAutoScanEnabled,
                                onCheckedChange = onAutoScanToggled,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = CyberGreen,
                                    checkedTrackColor = CyberGreen.copy(alpha = 0.25f),
                                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }

        // STATS STRIP (Files Scanned, Honeypots Armed)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallStatCard(
                    modifier = Modifier.weight(1f),
                    label = "Files Scanned",
                    value = "$totalFilesScanned",
                    accentColor = CyanAccent
                )
                SmallStatCard(
                    modifier = Modifier.weight(1f),
                    label = "Honeypots Armed",
                    value = "$honeypotsFound",
                    accentColor = CyberGreen
                )
            }
        }

        // LATEST SUMMARY BANNER (Expressive Pill Container)
        if (latestChangeSummary.isNotBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚡ $latestChangeSummary",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }

        // FILTER CHIPS ROW (Expressive Pill Capsules)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filterChips.forEach { (catKey, catLabel) ->
                    val isSelected = selectedFilterCategory == catKey
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilterCategory = catKey },
                        label = { Text(catLabel, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (catKey) {
                                "DEPLOYED" -> CyanAccent.copy(alpha = 0.2f)
                                "BREACHES", "DELETED" -> AlertRed.copy(alpha = 0.2f)
                                "EDITED" -> WarningYellow.copy(alpha = 0.2f)
                                else -> CyberGreen.copy(alpha = 0.2f)
                            },
                            selectedLabelColor = when (catKey) {
                                "DEPLOYED" -> CyanAccent
                                "BREACHES", "DELETED" -> AlertRed
                                "EDITED" -> WarningYellow
                                else -> CyberGreen
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(50)
                    )
                }
            }
        }

        // ACTIVITY LOGS HEADER
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kernel Event Stream (${filteredLogs.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.2.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CyberGreen.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Inotify Active 🟢",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CyberGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // LOG ITEMS
        if (filteredLogs.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No filesystem activity recorded under this filter 🛡️",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(
                items = filteredLogs,
                key = { it.id },
                contentType = { "directory_log" }
            ) { log ->
                val isExpanded = expandedLogIds.contains(log.id)
                DirectoryLogCard(
                    log = log,
                    isExpanded = isExpanded,
                    onToggleExpand = {
                        if (isExpanded) expandedLogIds.remove(log.id)
                        else expandedLogIds.add(log.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun SmallStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = accentColor
            )
        }
    }
}

@Composable
private fun DirectoryLogCard(
    log: AccessLog,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val actionUpper = log.action.uppercase()
    val userStr = log.user
    val isIntruder = userStr.contains("Intruder", ignoreCase = true) || actionUpper == "BREACH"

    val (icon, eventLabel, badgeColor) = when {
        actionUpper == "DELETED" || userStr.contains("DELETED", ignoreCase = true) -> Triple("🗑️", "DELETED", AlertRed)
        actionUpper == "EDITED" || actionUpper == "MODIFIED" || userStr.contains("EDITED", ignoreCase = true) || userStr.contains("MODIFIED", ignoreCase = true) -> Triple("✏️", "EDITED", WarningYellow)
        actionUpper == "DEPLOYED" -> Triple("🍯", "DEPLOYED", CyanAccent)
        actionUpper == "CREATED" || userStr.contains("CREATED", ignoreCase = true) -> Triple("➕", "NEW FILE", CyberGreen)
        actionUpper == "COPIED" || userStr.contains("COPIED", ignoreCase = true) -> Triple("📋", "COPIED", CyberGreen)
        actionUpper == "RENAMED" || userStr.contains("RENAMED", ignoreCase = true) -> Triple("🔄", "RENAMED", CyberGreen)
        actionUpper == "ACCESSED" || actionUpper == "OPENED" || userStr.contains("ACCESSED", ignoreCase = true) || userStr.contains("OPENED", ignoreCase = true) -> {
            Triple("👁️", "OPENED", if (isIntruder) AlertRed else CyberGreen)
        }
        isIntruder -> Triple("🚨", "BREACH", AlertRed)
        else -> Triple("👤", userStr, CyberGreen)
    }

    val defaultDetails = when {
        actionUpper == "DELETED" -> if (isIntruder) "UNAUTHORIZED INTRUSION: File '${log.file}' DELETED by an Intruder at ${log.timestamp}!" else "File '${log.file}' was DELETED at ${log.timestamp}."
        actionUpper == "EDITED" -> if (isIntruder) "UNAUTHORIZED INTRUSION: File '${log.file}' EDITED by an Intruder at ${log.timestamp}!" else "File '${log.file}' modified at ${log.timestamp}."
        actionUpper == "DEPLOYED" -> "Decoy honeyfile '${log.file}' deployed to directory at ${log.timestamp}."
        actionUpper == "OPENED" || actionUpper == "ACCESSED" -> if (isIntruder) "UNAUTHORIZED ACCESS: Honeyfile '${log.file}' OPENED by an Intruder at ${log.timestamp}!" else "Honeyfile '${log.file}' was opened/accessed at ${log.timestamp}."
        isIntruder -> "UNAUTHORIZED ACCESS BREACH on honeyfile '${log.file}' at ${log.timestamp}! Camera captured intruder photo & email alert sent."
        else -> "Verified access by $userStr on file '${log.file}' at ${log.timestamp}."
    }

    val displayDetails = if (log.details.isNotBlank()) log.details else defaultDetails

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .expressiveBounceClickable(onClick = onToggleExpand)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = icon, fontSize = 20.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = log.file,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = log.timestamp,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = badgeColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = eventLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = badgeColor,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Icon(
                    imageVector = if (isExpanded) HoneyIcons.KeyboardArrowUp else HoneyIcons.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(12.dp)
                ) {
                    Text(
                        text = displayDetails,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
