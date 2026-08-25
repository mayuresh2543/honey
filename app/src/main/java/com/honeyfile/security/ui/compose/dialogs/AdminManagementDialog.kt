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
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.honeyfile.security.auth.FaceAuthManager
import com.honeyfile.security.ui.theme.AlertRed
import com.honeyfile.security.ui.theme.CyanAccent
import com.honeyfile.security.ui.theme.CyberGreen

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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
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
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Manage enrolled biometric facial profiles",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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

                Spacer(modifier = Modifier.height(12.dp))

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

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Done",
                        fontWeight = FontWeight.Bold,
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "👤 Admin $adminIndex: $adminName",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (!adminEmail.isNullOrBlank()) "📧 $adminEmail" else "📧 No email registered",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isEnrolled) CyberGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        if (isEnrolled) CyberGreen else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isEnrolled) "Enrolled ✅" else "Empty ⚪",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isEnrolled) CyberGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isEnrolled) {
                Button(
                    onClick = onEdit,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.weight(1f)
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
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed.copy(alpha = 0.12f)),
                    modifier = Modifier.weight(1f)
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
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Enroll Face",
                        fontSize = 12.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onEdit,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.weight(1f)
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
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "👤 Edit Admin $adminTarget Profile",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Admin $adminTarget Name *") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("Alert Email Address (Optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            if (email.isNotBlank()) {
                                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                                    Toast.makeText(context, "Please enter a valid email address format!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (faceAuthManager.isEmailTaken(email, adminTarget)) {
                                    Toast.makeText(context, "❌ Email address already registered to another administrator account.", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
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
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
