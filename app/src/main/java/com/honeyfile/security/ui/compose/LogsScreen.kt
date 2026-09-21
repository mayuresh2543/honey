package com.honeyfile.security.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honeyfile.security.data.AccessLog
import com.honeyfile.security.ui.theme.*

@Composable
fun LogsScreen(
    logs: List<AccessLog>
) {
    var searchQuery by remember { mutableStateOf("") }
    val expandedLogIds = remember { mutableStateListOf<Long>() }

    val filteredLogs = remember(logs, searchQuery) {
        if (searchQuery.isBlank()) logs
        else {
            val query = searchQuery.trim().lowercase()
            logs.filter {
                it.file.lowercase().contains(query) ||
                it.action.lowercase().contains(query) ||
                it.user.lowercase().contains(query) ||
                it.details.lowercase().contains(query)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = "📜 Access Audit Trail",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.3.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Immutable ledger of endpoint and honeypot interactions",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "${logs.size} Logs",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CyanAccent,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Expressive Search & Filter Bar (M3 Pill)
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Search logs by file, user, or action...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = FullPillShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanAccent,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

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
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No logs match \"$searchQuery\" 🔍" else "No audit log records found yet 🛡️",
                            fontSize = 13.sp,
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
                contentType = { "access_log" }
            ) { log ->
                val isExpanded = expandedLogIds.contains(log.id)
                AccessLogCard(
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
private fun AccessLogCard(
    log: AccessLog,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val isIntruder = log.user.contains("Intruder", ignoreCase = true) || log.action.equals("BREACH", ignoreCase = true)
    val isDeployed = log.action.equals("DEPLOYED", ignoreCase = true) || log.user.contains("DEPLOYED", ignoreCase = true)

    val (badgeBgColor, badgeTextColor, badgeBorderColor) = when {
        isDeployed -> Triple(CyanAccent.copy(alpha = 0.15f), CyanAccent, CyanAccent.copy(alpha = 0.4f))
        isIntruder -> Triple(AlertRed.copy(alpha = 0.2f), AlertRed, AlertRed.copy(alpha = 0.5f))
        else -> Triple(CyberGreen.copy(alpha = 0.18f), CyberGreen, CyberGreen.copy(alpha = 0.4f))
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 2.dp,
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
                // User / Actor Badge (Expressive Full Pill)
                Surface(
                    shape = FullPillShape,
                    color = badgeBgColor,
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeBorderColor),
                    modifier = Modifier.widthIn(min = 80.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 5.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = log.user,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = badgeTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // File & Timestamp (Telemetry typography)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = log.file,
                        style = TelemetryCodeBold.copy(color = MaterialTheme.colorScheme.onSurface),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${log.action.uppercase()} • ${log.timestamp}",
                        style = TelemetryCodeSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }

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
                        text = if (log.details.isNotBlank()) log.details else "Action: ${log.action} on ${log.file} at ${log.timestamp}",
                        style = TelemetryCodeStyle.copy(
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }
    }
}
