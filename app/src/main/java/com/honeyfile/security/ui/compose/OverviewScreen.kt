package com.honeyfile.security.ui.compose

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

        // Severity Badge & Glow Color
        val (badgeText, badgeColor) = when (threatSummary.severityLevel) {
            SeverityLevel.LOW -> Pair("LOW RISK 🟢", CyberGreen)
            SeverityLevel.ELEVATED -> Pair("ELEVATED 🟡", WarningYellow)
            SeverityLevel.CRITICAL -> Pair("CRITICAL 🔴", AlertRed)
        }

        val infiniteGlow = rememberInfiniteTransition(label = "risk_pulse")
        val borderAlpha by infiniteGlow.animateFloat(
            initialValue = if (threatSummary.severityLevel == SeverityLevel.LOW) 0.25f else 0.4f,
            targetValue = if (threatSummary.severityLevel == SeverityLevel.LOW) 0.55f else 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "border_alpha"
        )

        // THREAT INTELLIGENCE SUMMARY CARD (M3 Expressive Container)
        Surface(
            shape = ContainerSurfaceShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, badgeColor.copy(alpha = borderAlpha)),
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                when (threatSummary.severityLevel) {
                                    SeverityLevel.LOW -> CyberGreen.copy(alpha = 0.08f)
                                    SeverityLevel.ELEVATED -> WarningYellow.copy(alpha = 0.10f)
                                    SeverityLevel.CRITICAL -> AlertRed.copy(alpha = 0.12f)
                                },
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
                    Text(
                        text = "🛡️ Endpoint Risk Index",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.2.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        shape = FullPillShape,
                        color = badgeColor.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Score Display & Peak Window
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${threatSummary.threatScore}",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = " / 100",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "Peak: ${threatSummary.peakAttackTimeWindow}",
                            style = TelemetryCodeBold,
                            color = CyanAccent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { (threatSummary.threatScore / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(50)),
                    color = when (threatSummary.severityLevel) {
                        SeverityLevel.LOW -> CyberGreen
                        SeverityLevel.ELEVATED -> WarningYellow
                        SeverityLevel.CRITICAL -> AlertRed
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )

                Spacer(modifier = Modifier.height(18.dp))

                // View Details Button with Expressive Spring Press
                FilledTonalButton(
                    onClick = onOpenThreatDetails,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = CyanNeon
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .expressiveBounceClickable(onClick = onOpenThreatDetails)
                ) {
                    Icon(
                        imageVector = HoneyIcons.Analytics,
                        contentDescription = null,
                        tint = CyanNeon,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "View Analytics & Heatmap 📊",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // QUICK ACTIONS SECTION
        Text(
            text = "⚡ Security Management",
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.3.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionTile(
                modifier = Modifier.weight(1f),
                title = "Trigger Access",
                subtitle = "Simulate Access",
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
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
    Surface(
        shape = StatCardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 3.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(accentColor.copy(alpha = 0.08f), Color.Transparent)
                    )
                )
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = icon, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = count,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = accentColor
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
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
    Surface(
        shape = ActionTileShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 3.dp,
        modifier = modifier.expressiveBounceClickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.15f))
                    .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
