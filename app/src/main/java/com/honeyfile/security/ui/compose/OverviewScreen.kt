package com.honeyfile.security.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honeyfile.security.analytics.SeverityLevel
import com.honeyfile.security.analytics.ThreatSummary
import com.honeyfile.security.ui.theme.*

@Composable
fun OverviewScreen(
    adminCount: Int,
    intruderCount: Int,
    threatSummary: ThreatSummary,
    onOpenThreatDetails: () -> Unit,
    onTriggerAccess: () -> Unit,
    onOpenAdminManagement: () -> Unit,
    onOpenDecoyStudio: () -> Unit,
    onOpenAbout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // OVERVIEW STATS ROW (Admin Passes & Intruder Breaches)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Admin Passes Card
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Admin Passes",
                count = "$adminCount",
                icon = "🛡️",
                accentColor = CyberGreen,
                subtitle = "Authorized Access"
            )

            // Intruder Breaches Card
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Intruder Breaches",
                count = "$intruderCount",
                icon = "🚨",
                accentColor = AlertRed,
                subtitle = "Traps Triggered"
            )
        }

        // THREAT INTELLIGENCE SUMMARY CARD
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
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
                        Text(
                            text = "🛡️ Endpoint Risk Index",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Severity Badge
                    val (badgeText, badgeColor) = when (threatSummary.severityLevel) {
                        SeverityLevel.LOW -> Pair("LOW RISK 🟢", CyberGreen)
                        SeverityLevel.ELEVATED -> Pair("ELEVATED 🟡", WarningYellow)
                        SeverityLevel.CRITICAL -> Pair("CRITICAL 🔴", AlertRed)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Score Display & Progress Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "Score: ${threatSummary.threatScore} / 100",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Peak: ${threatSummary.peakAttackTimeWindow}",
                        fontSize = 12.sp,
                        color = CyanAccent,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { (threatSummary.threatScore / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = when (threatSummary.severityLevel) {
                        SeverityLevel.LOW -> CyberGreen
                        SeverityLevel.ELEVATED -> WarningYellow
                        SeverityLevel.CRITICAL -> AlertRed
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // View Details Button
                OutlinedButton(
                    onClick = onOpenThreatDetails,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = HoneyIcons.Analytics,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "View Analytics & Heatmap 📊",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // QUICK ACTIONS SECTION
        Text(
            text = "⚡ Security Management",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActionTile(
                modifier = Modifier.weight(1f),
                title = "Trigger Access",
                subtitle = "Simulate File Access",
                icon = HoneyIcons.FlashOn,
                accentColor = CyberGreen,
                onClick = onTriggerAccess
            )

            ActionTile(
                modifier = Modifier.weight(1f),
                title = "Manage Admins",
                subtitle = "Biometric Profiles",
                icon = HoneyIcons.People,
                accentColor = CyanAccent,
                onClick = onOpenAdminManagement
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActionTile(
                modifier = Modifier.weight(1f),
                title = "Deploy Decoy",
                subtitle = "Multi-Format Traps",
                icon = HoneyIcons.FolderSpecial,
                accentColor = WarningYellow,
                onClick = onOpenDecoyStudio
            )

            ActionTile(
                modifier = Modifier.weight(1f),
                title = "About & Credits",
                subtitle = "Honeyfile v1.0.2",
                icon = Icons.Default.Info,
                accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onOpenAbout
            )
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    count: String,
    icon: String,
    accentColor: Color,
    subtitle: String
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = icon, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = count,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActionTile(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
