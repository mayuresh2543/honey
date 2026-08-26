package com.honeyfile.security.ui.compose.dialogs

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.honeyfile.security.auth.FaceAuthManager
import com.honeyfile.security.ui.theme.*

@Composable
fun AdminManagementDialog(
    onDismiss: () -> Unit,
    onOpenEnrollScan: (adminTarget: Int, isMandatory: Boolean) -> Unit
) {
    val context = LocalContext.current
    val faceAuthManager = remember { FaceAuthManager(context) }

    // State to trigger UI recomposition when admin profiles change
    var updateTrigger by remember { mutableIntStateOf(0) }

    var showEditProfileDialogForTarget by remember { mutableStateOf<Int?>(null) }
    var showSoleAdminWarningForTarget by remember { mutableStateOf<Int?>(null) }
    var showDeleteConfirmForTarget by remember { mutableStateOf<Int?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "👥 Administrator Profiles",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.3.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Manage enrolled biometric facial profiles",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
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

                Spacer(modifier = Modifier.height(18.dp))

                // Admin 1 Card
                AdminProfileCard(
                    adminIndex = 1,
                    adminName = faceAuthManager.admin1Name,
                    adminEmail = faceAuthManager.admin1Email,
                    isEnrolled = faceAuthManager.isAdmin1Enrolled,
                    onEnroll = { onOpenEnrollScan(1, false) },
                    onEdit = { showEditProfileDialogForTarget = 1 },
                    onClear = {
                        if (faceAuthManager.getEnrolledAdminCount() <= 1) {
                            showSoleAdminWarningForTarget = 1
                        } else {
                            showDeleteConfirmForTarget = 1
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Admin 2 Card
                AdminProfileCard(
                    adminIndex = 2,
                    adminName = faceAuthManager.admin2Name,
                    adminEmail = faceAuthManager.admin2Email,
                    isEnrolled = faceAuthManager.isAdmin2Enrolled,
                    onEnroll = { onOpenEnrollScan(2, false) },
                    onEdit = { showEditProfileDialogForTarget = 2 },
                    onClear = {
                        if (faceAuthManager.getEnrolledAdminCount() <= 1) {
                            showSoleAdminWarningForTarget = 2
                        } else {
                            showDeleteConfirmForTarget = 2
                        }
                    }
                )

                Spacer(modifier = Modifier.height(22.dp))

                FilledTonalButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .expressiveBounceClickable(onClick = onDismiss)
                ) {
                    Text(
                        text = "Done",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    // Edit Profile Modal
    showEditProfileDialogForTarget?.let { target ->
        EditAdminProfileDialog(
            adminTarget = target,
            initialName = if (target == 1) faceAuthManager.admin1Name else faceAuthManager.admin2Name,
            initialEmail = if (target == 1) faceAuthManager.admin1Email ?: "" else faceAuthManager.admin2Email ?: "",
            faceAuthManager = faceAuthManager,
            onDismiss = { showEditProfileDialogForTarget = null },
            onSaved = {
                showEditProfileDialogForTarget = null
                updateTrigger++
            }
        )
    }

    // Sole Admin Warning Dialog
    showSoleAdminWarningForTarget?.let { target ->
        val adminName = if (target == 1) faceAuthManager.admin1Name else faceAuthManager.admin2Name
        AlertDialog(
            onDismissRequest = { showSoleAdminWarningForTarget = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(text = "⚠️ Sole Administrator Profile", fontWeight = FontWeight.Bold) },
            text = {
                Text("Honeyfile Security requires at least one registered administrator to operate. To replace $adminName, please enroll the other administrator first, or re-enroll Admin $target directly.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSoleAdminWarningForTarget = null
                        onOpenEnrollScan(target, true)
                    }
                ) {
                    Text("Re-enroll Admin $target", color = CyberGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSoleAdminWarningForTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    showDeleteConfirmForTarget?.let { target ->
        val adminName = if (target == 1) faceAuthManager.admin1Name else faceAuthManager.admin2Name
        AlertDialog(
            onDismissRequest = { showDeleteConfirmForTarget = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(text = "⚠️ Reset $adminName Profile?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to delete $adminName's facial biometric profile and registered email notification address? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (target == 1) faceAuthManager.clearAdmin1() else faceAuthManager.clearAdmin2()
                        Toast.makeText(context, "Admin $target profile cleared ✅", Toast.LENGTH_SHORT).show()
                        showDeleteConfirmForTarget = null
                        updateTrigger++
                    }
                ) {
                    Text("Reset Profile", color = AlertRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmForTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AdminProfileCard(
    adminIndex: Int,
    adminName: String,
    adminEmail: String?,
    isEnrolled: Boolean,
    onEnroll: () -> Unit,
    onEdit: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
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
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp)
                ) {
                    Text(
                        text = "👤 Admin $adminIndex: $adminName",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (!adminEmail.isNullOrBlank()) "📧 $adminEmail" else "📧 No email registered",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isEnrolled) CyberGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isEnrolled) CyberGreen.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Text(
                        text = if (isEnrolled) "Enrolled ✅" else "Empty ⚪",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isEnrolled) CyberGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isEnrolled) {
                    FilledTonalButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                        modifier = Modifier
                            .weight(1f)
                            .expressiveBounceClickable(onClick = onEdit)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Edit",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onClear,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, AlertRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .expressiveBounceClickable(onClick = onClear)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = AlertRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Reset",
                            fontSize = 12.sp,
                            color = AlertRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = onEnroll,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                        modifier = Modifier
                            .weight(1f)
                            .expressiveBounceClickable(onClick = onEnroll)
                    ) {
                        Icon(
                            imageVector = com.honeyfile.security.ui.theme.HoneyIcons.PersonAdd,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Enroll Face",
                            fontSize = 12.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    FilledTonalButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                        modifier = Modifier
                            .weight(1f)
                            .expressiveBounceClickable(onClick = onEdit)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Edit Details",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditAdminProfileDialog(
    adminTarget: Int,
    initialName: String,
    initialEmail: String,
    faceAuthManager: FaceAuthManager,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var nameInput by remember { mutableStateOf(if (initialName.startsWith("Admin ")) "" else initialName) }
    var emailInput by remember { mutableStateOf(initialEmail) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Text(
                    text = "👤 Edit Admin $adminTarget Profile",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.2.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Admin $adminTarget Name *") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("Alert Email Address *") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .expressiveBounceClickable(onClick = onDismiss)
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val name = nameInput.trim()
                            val email = emailInput.trim()

                            if (name.isBlank()) {
                                Toast.makeText(context, "Please enter Admin $adminTarget's name!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (faceAuthManager.isNameTaken(name, adminTarget)) {
                                Toast.makeText(context, "❌ Administrator name already registered to another account.", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            if (email.isBlank()) {
                                Toast.makeText(context, "Please enter Admin $adminTarget's email address!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                                Toast.makeText(context, "Please enter a valid email address format!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (faceAuthManager.isEmailTaken(email, adminTarget)) {
                                Toast.makeText(context, "❌ Email address already registered to another administrator account.", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            if (adminTarget == 1) {
                                faceAuthManager.admin1Name = name
                                faceAuthManager.admin1Email = if (email.isBlank()) null else email
                            } else {
                                faceAuthManager.admin2Name = name
                                faceAuthManager.admin2Email = if (email.isBlank()) null else email
                            }

                            Toast.makeText(context, "Admin $adminTarget profile updated: $name ✅", Toast.LENGTH_SHORT).show()
                            onSaved()
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .expressiveBounceClickable(onClick = {})
                    ) {
                        Text("Save", color = Color.Black, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
