package com.honeyfile.security.ui.compose

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honeyfile.security.analytics.ThreatSummary
import com.honeyfile.security.data.AccessLog
import com.honeyfile.security.ui.compose.dialogs.*
import com.honeyfile.security.ui.theme.CyanAccent
import com.honeyfile.security.ui.theme.CyberGreen
import com.honeyfile.security.ui.theme.HoneyIcons
import java.io.File

enum class HoneyNavTab(val label: String, val icon: ImageVector) {
    OVERVIEW("Overview", HoneyIcons.Security),
    SCANNER("Scanner", Icons.Default.Search),
    LOGS("Logs", HoneyIcons.Article),
    VAULT("Vault", HoneyIcons.PhotoLibrary)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoneyfileApp(
    isDarkMode: Boolean,
    onThemeToggled: (Boolean) -> Unit,
    adminCount: Int,
    intruderCount: Int,
    threatSummary: ThreatSummary,
    folderUri: Uri?,
    folderDisplayName: String,
    isAutoScanEnabled: Boolean,
    onAutoScanToggled: (Boolean) -> Unit,
    onSelectFolderClicked: () -> Unit,
    totalFilesScanned: Int,
    honeypotsFound: Int,
    latestChangeSummary: String,
    directoryLogs: List<AccessLog>,
    allAccessLogs: List<AccessLog>,
    capturedPhotos: List<File>,
    onRefreshGallery: () -> Unit,
    versionName: String = "v1.0.3",
    mandatoryEnrollmentRequested: Boolean = false,
    onMandatoryEnrollmentHandled: () -> Unit = {},
    onAdminEnrolled: () -> Unit = {}
) {
    var currentTab by remember { mutableStateOf(HoneyNavTab.OVERVIEW) }

    // Dialog & Modal States
    var showAboutDialog by remember { mutableStateOf(false) }
    var showThreatDetailsDialog by remember { mutableStateOf(false) }
    var showAdminManagementDialog by remember { mutableStateOf(false) }
    var enrollDialogTarget by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    var showDecoyStudioSheet by remember { mutableStateOf(false) }
    var selectedPhotoForDetail by remember { mutableStateOf<File?>(null) }

    // Handle mandatory enrollment on first startup
    LaunchedEffect(mandatoryEnrollmentRequested) {
        if (mandatoryEnrollmentRequested) {
            enrollDialogTarget = Pair(1, true)
            onMandatoryEnrollmentHandled()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🛡️ Honeyfile",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = versionName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                },
                actions = {
                    // Dark / Light Theme Toggle in Expressive Pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isDarkMode) "🌙" else "☀️",
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = isDarkMode,
                                onCheckedChange = onThemeToggled,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = CyberGreen,
                                    checkedTrackColor = CyberGreen.copy(alpha = 0.25f),
                                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.scale(0.85f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            val navTabs = remember { HoneyNavTab.entries }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(30.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        modifier = Modifier.height(68.dp)
                    ) {
                        navTabs.forEach { tab ->
                            val isSelected = currentTab == tab
                            val iconScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.18f else 1.0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "nav_icon_scale"
                            )
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    currentTab = tab
                                    if (tab == HoneyNavTab.VAULT) {
                                        onRefreshGallery()
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label,
                                        tint = if (isSelected) CyberGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.scale(iconScale)
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isSelected) CyberGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = CyberGreen.copy(alpha = 0.18f),
                                    selectedIconColor = CyberGreen,
                                    selectedTextColor = CyberGreen,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val enterAnim = slideInHorizontally(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) { width -> if (forward) width / 4 else -width / 4 } + fadeIn(
                        animationSpec = tween(220)
                    )
                    val exitAnim = slideOutHorizontally(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) { width -> if (forward) -width / 4 else width / 4 } + fadeOut(
                        animationSpec = tween(180)
                    )
                    enterAnim togetherWith exitAnim
                },
                label = "HoneyTabTransition"
            ) { tab ->
                when (tab) {
                    HoneyNavTab.OVERVIEW -> {
                        OverviewScreen(
                            adminCount = adminCount,
                            intruderCount = intruderCount,
                            threatSummary = threatSummary,
                            onOpenThreatDetails = { showThreatDetailsDialog = true },
                            onOpenAdminManagement = { showAdminManagementDialog = true },
                            onOpenDecoyStudio = { showDecoyStudioSheet = true },
                            onOpenAbout = { showAboutDialog = true }
                        )
                    }
                    HoneyNavTab.SCANNER -> {
                        ScannerScreen(
                            folderUri = folderUri,
                            folderDisplayName = folderDisplayName,
                            isAutoScanEnabled = isAutoScanEnabled,
                            onAutoScanToggled = onAutoScanToggled,
                            onSelectFolderClicked = onSelectFolderClicked,
                            totalFilesScanned = totalFilesScanned,
                            honeypotsFound = honeypotsFound,
                            latestChangeSummary = latestChangeSummary,
                            directoryLogs = directoryLogs
                        )
                    }
                    HoneyNavTab.LOGS -> {
                        LogsScreen(logs = allAccessLogs)
                    }
                    HoneyNavTab.VAULT -> {
                        VaultScreen(
                            capturedPhotos = capturedPhotos,
                            onRefresh = onRefreshGallery,
                            onPhotoClicked = { selectedPhotoForDetail = it }
                        )
                    }
                }
            }
        }
    }

    // MODALS & DIALOGS
    if (showAboutDialog) {
        AboutCreditsDialog(
            versionName = versionName,
            onDismiss = { showAboutDialog = false }
        )
    }

    if (showThreatDetailsDialog) {
        ThreatAnalyticsDetailDialog(
            onDismiss = { showThreatDetailsDialog = false }
        )
    }

    if (showAdminManagementDialog) {
        AdminManagementDialog(
            onDismiss = { showAdminManagementDialog = false },
            onOpenEnrollScan = { target, mandatory ->
                enrollDialogTarget = Pair(target, mandatory)
            }
        )
    }

    enrollDialogTarget?.let { (target, isMandatory) ->
        AdminEnrollScanDialog(
            adminTarget = target,
            isMandatory = isMandatory,
            onDismiss = { enrollDialogTarget = null },
            onEnrollmentCompleted = {
                enrollDialogTarget = null
                onAdminEnrolled()
            }
        )
    }

    if (showDecoyStudioSheet) {
        DecoyStudioSheet(
            folderUri = folderUri,
            versionName = versionName,
            onDismiss = { showDecoyStudioSheet = false }
        )
    }

    selectedPhotoForDetail?.let { photoFile ->
        PhotoDetailDialog(
            photoFile = photoFile,
            onDismiss = { selectedPhotoForDetail = null },
            onPhotoDeleted = {
                selectedPhotoForDetail = null
                onRefreshGallery()
            }
        )
    }
}
