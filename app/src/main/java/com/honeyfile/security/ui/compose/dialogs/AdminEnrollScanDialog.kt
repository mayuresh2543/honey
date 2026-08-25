package com.honeyfile.security.ui.compose.dialogs

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.honeyfile.security.auth.FaceAuthManager
import com.honeyfile.security.ui.theme.AlertRed
import com.honeyfile.security.ui.theme.CyanAccent
import com.honeyfile.security.ui.theme.CyberGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.concurrent.Executors

@Composable
fun AdminEnrollScanDialog(
    adminTarget: Int = 1,
    isMandatory: Boolean = false,
    onDismiss: () -> Unit,
    onEnrollmentCompleted: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val faceAuthManager = remember { FaceAuthManager(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var isScanStep by remember { mutableStateOf(true) }
    var tempCapturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }

    var adminNameInput by remember {
        val def = if (adminTarget == 1) faceAuthManager.admin1Name else faceAuthManager.admin2Name
        mutableStateOf(if (def.startsWith("Admin ")) "" else def)
    }
    var adminEmailInput by remember {
        val def = if (adminTarget == 1) faceAuthManager.admin1Email else faceAuthManager.admin2Email
        mutableStateOf(def ?: "")
    }

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Camera permission is required to scan administrator face profile.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Dialog(
        onDismissRequest = {
            if (isMandatory) {
                showExitConfirm = true
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isMandatory,
            dismissOnClickOutside = !isMandatory,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title & Subtitle
                Text(
                    text = if (isMandatory) "🛡️ Admin 1 Setup (Mandatory)"
                           else if (isScanStep) "📸 Admin $adminTarget Facial Enrollment"
                           else "👤 Admin $adminTarget Profile Details",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isScanStep) "Align your face inside the neon oval below"
                           else "Enter administrator details for security alerts",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isScanStep) {
                    // CAMERA SCAN STEP
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasCameraPermission) {
                            AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx).apply {
                                        scaleType = PreviewView.ScaleType.FILL_CENTER
                                    }
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                    cameraProviderFuture.addListener({
                                        try {
                                            val cameraProvider = cameraProviderFuture.get()
                                            val preview = Preview.Builder().build().also {
                                                it.setSurfaceProvider(previewView.surfaceProvider)
                                            }
                                            val capture = ImageCapture.Builder()
                                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                                .build()
                                            imageCapture = capture

                                            val cameraSelector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                                                CameraSelector.DEFAULT_FRONT_CAMERA
                                            } else {
                                                CameraSelector.DEFAULT_BACK_CAMERA
                                            }

                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, capture)
                                        } catch (e: Exception) {
                                            Log.e("AdminEnroll", "CameraX bind failed", e)
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))
                                    previewView
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Oval overlay
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val canvasWidth = size.width
                                val canvasHeight = size.height
                                val ovalWidth = canvasWidth * 0.65f
                                val ovalHeight = canvasHeight * 0.78f
                                val left = (canvasWidth - ovalWidth) / 2f
                                val top = (canvasHeight - ovalHeight) / 2f

                                drawOval(
                                    color = CyberGreen,
                                    topLeft = Offset(left, top),
                                    size = Size(ovalWidth, ovalHeight),
                                    style = Stroke(width = 3.dp.toPx())
                                )
                            }
                        } else {
                            Text(
                                text = "Camera permission required",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }

                        if (isProcessing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = CyberGreen)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Verifying biometric profile...",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Scan Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isMandatory) showExitConfirm = true else onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isMandatory) "Exit App" else "Cancel",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                val capture = imageCapture ?: run {
                                    Toast.makeText(context, "Camera initializing...", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isProcessing = true
                                capture.takePicture(
                                    cameraExecutor,
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                            val bitmap = imageProxyToBitmap(imageProxy)
                                            imageProxy.close()
                                            if (bitmap != null) {
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    val authCheck = faceAuthManager.authenticateFace(bitmap)
                                                    withContext(Dispatchers.Main) {
                                                        isProcessing = false
                                                        if (authCheck.isAuthenticated && authCheck.adminName != null) {
                                                            Toast.makeText(
                                                                context,
                                                                "❌ Facial profile already enrolled to an administrator account. Each administrator slot must belong to a distinct person.",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                            return@withContext
                                                        }
                                                        tempCapturedBitmap = bitmap
                                                        isScanStep = false
                                                    }
                                                }
                                            } else {
                                                coroutineScope.launch(Dispatchers.Main) {
                                                    isProcessing = false
                                                    Toast.makeText(context, "Failed to capture image frame", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            coroutineScope.launch(Dispatchers.Main) {
                                                isProcessing = false
                                                Toast.makeText(context, "Capture error: ${exception.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            },
                            enabled = !isProcessing,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                            modifier = Modifier.weight(1.4f)
                        ) {
                            Icon(
                                imageVector = com.honeyfile.security.ui.theme.HoneyIcons.CameraAlt,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Capture Scan",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Temporary Testing Bypass Button
                    OutlinedButton(
                        onClick = {
                            faceAuthManager.enrollTestAdminBypass(adminTarget)
                            Toast.makeText(context, "⚡ Testing Bypass Active: Enrolled default 'Test Admin'", Toast.LENGTH_LONG).show()
                            onEnrollmentCompleted(true)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚡ Skip / Bypass Registration (Testing)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent
                        )
                    }
                } else {
                    // PROFILE DETAILS STEP (Name & Email)
                    OutlinedTextField(
                        value = adminNameInput,
                        onValueChange = { adminNameInput = it },
                        label = { Text("Admin $adminTarget Name *") },
                        placeholder = { Text("e.g. Mayuresh") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = adminEmailInput,
                        onValueChange = { adminEmailInput = it },
                        label = { Text("Alert Email Address (Optional)") },
                        placeholder = { Text("admin@example.com") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                tempCapturedBitmap = null
                                isScanStep = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Retake",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                val name = adminNameInput.trim()
                                val email = adminEmailInput.trim()

                                if (name.isEmpty()) {
                                    Toast.makeText(context, "Please enter Admin $adminTarget's name!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (faceAuthManager.isNameTaken(name, adminTarget)) {
                                    Toast.makeText(context, "❌ Administrator name already registered to another account.", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                if (email.isNotEmpty()) {
                                    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                                        Toast.makeText(context, "Please enter a valid email address format!", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (faceAuthManager.isEmailTaken(email, adminTarget)) {
                                        Toast.makeText(context, "❌ Email address already registered to another administrator account.", Toast.LENGTH_LONG).show()
                                        return@Button
                                    }
                                }

                                val bitmap = tempCapturedBitmap ?: return@Button
                                isProcessing = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    val enrollResult = if (adminTarget == 1) {
                                        faceAuthManager.enrollAdmin1FromBitmap(bitmap)
                                    } else {
                                        faceAuthManager.enrollAdmin2FromBitmap(bitmap)
                                    }
                                    withContext(Dispatchers.Main) {
                                        isProcessing = false
                                        if (enrollResult.isSuccess) {
                                            if (adminTarget == 1) {
                                                faceAuthManager.admin1Name = name
                                                faceAuthManager.admin1Email = if (email.isEmpty()) null else email
                                            } else {
                                                faceAuthManager.admin2Name = name
                                                faceAuthManager.admin2Email = if (email.isEmpty()) null else email
                                            }
                                            Toast.makeText(context, "Admin $adminTarget profile enrolled: $name ✅", Toast.LENGTH_LONG).show()
                                            onEnrollmentCompleted(true)
                                            onDismiss()
                                        } else {
                                            Toast.makeText(context, enrollResult.message, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            enabled = !isProcessing,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Save Profile",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(text = "Exit Honeyfile Security?", fontWeight = FontWeight.Bold) },
            text = { Text("Honeyfile deception and intrusion monitoring cannot operate without an enrolled Administrator biometric profile. Exit app now?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        (context as? Activity)?.finishAffinity()
                    }
                ) {
                    Text("Exit", color = AlertRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text("Continue Setup")
                }
            }
        )
    }
}

private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    val planeProxy = imageProxy.planes.firstOrNull() ?: return null
    val buffer: ByteBuffer = planeProxy.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}
