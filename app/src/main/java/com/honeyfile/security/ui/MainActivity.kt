package com.honeyfile.security.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.honeyfile.security.alert.EmailAlertManager
import com.honeyfile.security.alert.TelemetryManager
import com.honeyfile.security.analytics.ThreatAnalyticsManager
import com.honeyfile.security.analytics.ThreatSummary
import com.honeyfile.security.auth.FaceAuthManager
import com.honeyfile.security.auth.ThemeManager
import com.honeyfile.security.camera.IntruderCaptureManager
import com.honeyfile.security.cloud.FirebaseCloudVaultManager
import com.honeyfile.security.data.AccessLog
import com.honeyfile.security.data.AppDatabase
import com.honeyfile.security.scanner.FileChangeEvent
import com.honeyfile.security.scanner.FolderScannerManager
import com.honeyfile.security.ui.compose.HoneyfileApp
import com.honeyfile.security.ui.theme.HoneyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase
    private lateinit var faceAuthManager: FaceAuthManager
    private lateinit var intruderCaptureManager: IntruderCaptureManager
    private lateinit var emailAlertManager: EmailAlertManager
    private lateinit var themeManager: ThemeManager
    private lateinit var folderScannerManager: FolderScannerManager
    private lateinit var telemetryManager: TelemetryManager
    private val threatAnalyticsManager = ThreatAnalyticsManager()

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null

    // Compose Reactive State Holders
    private val isDarkModeState = mutableStateOf(false)
    private val selectedFolderUriState = mutableStateOf<Uri?>(null)
    private val folderDisplayNameState = mutableStateOf("")
    private val isAutoScanEnabledState = mutableStateOf(false)
    private val capturedPhotosState = mutableStateListOf<File>()
    private val mandatoryEnrollmentState = mutableStateOf(false)

    private val lastSecurityAlertTimeMs = AtomicLong(0L)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            if (faceAuthManager.hasAtLeastOneAdmin()) {
                initializeBackgroundCamera()
            } else {
                checkMandatoryAdminEnrollment()
            }
        } else {
            Toast.makeText(this, "Camera permission is required for security surveillance", Toast.LENGTH_LONG).show()
        }
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w(TAG, "Persistable permission warning: ${e.message}")
            }
            selectedFolderUriState.value = uri
            val docFile = DocumentFile.fromTreeUri(this, uri)
            folderDisplayNameState.value = docFile?.name ?: uri.lastPathSegment ?: "Monitored Folder"

            getSharedPreferences("honey_prefs", MODE_PRIVATE)
                .edit()
                .putString("monitored_folder_uri", uri.toString())
                .apply()

            Toast.makeText(this, "Selected folder for monitoring!", Toast.LENGTH_SHORT).show()

            if (isAutoScanEnabledState.value) {
                folderScannerManager.startContinuousScanning(uri)
                if (faceAuthManager.hasAtLeastOneAdmin()) {
                    com.honeyfile.security.service.HoneyMonitoringService.startService(this, uri)
                }
            } else {
                isAutoScanEnabledState.value = true
                folderScannerManager.startContinuousScanning(uri)
                if (faceAuthManager.hasAtLeastOneAdmin()) {
                    com.honeyfile.security.service.HoneyMonitoringService.startService(this, uri)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        themeManager = ThemeManager(this)
        isDarkModeState.value = themeManager.isDarkMode

        database = AppDatabase.getDatabase(this)
        faceAuthManager = FaceAuthManager(this)
        intruderCaptureManager = IntruderCaptureManager(this)
        emailAlertManager = EmailAlertManager()
        folderScannerManager = FolderScannerManager(this)
        telemetryManager = TelemetryManager(this)

        setupFolderScanner()
        checkAndRequestPermissions()
        observeFolderScanner()
        refreshGallery()

        setContent {
            val isDark by isDarkModeState
            val folderUri by selectedFolderUriState
            val folderName by folderDisplayNameState
            val isAutoScan by isAutoScanEnabledState
            val isMandatoryEnroll by mandatoryEnrollmentState

            val allLogs by database.logDao().getAllLogs().observeAsState(initial = emptyList())
            val adminCount by database.logDao().getAdminCount().observeAsState(initial = 0)
            val intruderCount by database.logDao().getIntruderCount().observeAsState(initial = 0)

            val scanResult by folderScannerManager.scanResult.collectAsState()
            val threatSummary = remember(allLogs) {
                threatAnalyticsManager.analyzeThreats(allLogs)
            }

            HoneyTheme(darkTheme = isDark) {
                HoneyfileApp(
                    isDarkMode = isDark,
                    onThemeToggled = { newDark ->
                        isDarkModeState.value = newDark
                        themeManager.isDarkMode = newDark
                    },
                    adminCount = adminCount,
                    intruderCount = intruderCount,
                    threatSummary = threatSummary,
                    folderUri = folderUri,
                    folderDisplayName = folderName,
                    isAutoScanEnabled = isAutoScan,
                    onAutoScanToggled = { enabled ->
                        isAutoScanEnabledState.value = enabled
                        val uri = selectedFolderUriState.value
                        if (enabled && uri != null) {
                            folderScannerManager.startContinuousScanning(uri)
                            if (faceAuthManager.hasAtLeastOneAdmin()) {
                                com.honeyfile.security.service.HoneyMonitoringService.startService(this@MainActivity, uri)
                            }
                        } else {
                            folderScannerManager.stopScanning()
                            com.honeyfile.security.service.HoneyMonitoringService.stopService(this@MainActivity)
                        }
                    },
                    onSelectFolderClicked = { folderPickerLauncher.launch(null) },
                    totalFilesScanned = scanResult.totalFilesScanned,
                    honeypotsFound = scanResult.honeyFilesFound,
                    latestChangeSummary = scanResult.latestChangeSummary,
                    directoryLogs = allLogs,
                    allAccessLogs = allLogs,
                    capturedPhotos = capturedPhotosState,
                    onRefreshGallery = { refreshGallery() },
                    onTriggerAccess = { onTriggerAccessClicked() },
                    versionName = "v1.0.3-beta",
                    mandatoryEnrollmentRequested = isMandatoryEnroll,
                    onMandatoryEnrollmentHandled = {
                        mandatoryEnrollmentState.value = false
                    },
                    onAdminEnrolled = {
                        rebindBackgroundCamera()
                        val uri = selectedFolderUriState.value
                        if (uri != null && isAutoScanEnabledState.value) {
                            folderScannerManager.startContinuousScanning(uri)
                            com.honeyfile.security.service.HoneyMonitoringService.startService(this@MainActivity, uri)
                        }
                    }
                )
            }
        }
    }

    private fun setupFolderScanner() {
        val savedUriString = getSharedPreferences("honey_prefs", MODE_PRIVATE)
            .getString("monitored_folder_uri", null)

        if (savedUriString != null) {
            try {
                val uri = Uri.parse(savedUriString)
                selectedFolderUriState.value = uri
                val doc = DocumentFile.fromTreeUri(this, uri)
                folderDisplayNameState.value = doc?.name ?: uri.lastPathSegment ?: "Monitored Folder"
                isAutoScanEnabledState.value = true
                folderScannerManager.startContinuousScanning(uri)
                if (faceAuthManager.hasAtLeastOneAdmin()) {
                    com.honeyfile.security.service.HoneyMonitoringService.startService(this, uri)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring monitored folder uri", e)
            }
        }
    }

    private fun checkMandatoryAdminEnrollment() {
        if (!faceAuthManager.hasAtLeastOneAdmin()) {
            mandatoryEnrollmentState.value = true
        }
    }

    fun rebindBackgroundCamera() {
        if (faceAuthManager.hasAtLeastOneAdmin()) {
            initializeBackgroundCamera()
            val uri = selectedFolderUriState.value
            if (uri != null && isAutoScanEnabledState.value) {
                com.honeyfile.security.service.HoneyMonitoringService.startService(this, uri)
            }
        }
    }

    private fun observeFolderScanner() {
        lifecycleScope.launch {
            folderScannerManager.fileChangeEvents.collect { event ->
                processBackgroundSecurityVerification(event)
            }
        }
    }

    private suspend fun processBackgroundSecurityVerification(event: FileChangeEvent) {
        if (!faceAuthManager.hasAtLeastOneAdmin()) {
            Log.d(TAG, "Skipping event verification: No admin enrolled.")
            return
        }

        // Suppress DEPLOYED honeypot events
        if (event.eventType == "DEPLOYED" ||
            com.honeyfile.security.decoy.DecoyGeneratorEngine.isDecoyFileName(event.fileName) && (event.eventType == "CREATED" || event.eventType == "DEPLOYED")) {
            Log.d(TAG, "Suppressed background verification for decoy template deployment: ${event.fileName}")
            return
        }

        val now = System.currentTimeMillis()
        val last = lastSecurityAlertTimeMs.get()
        if (now - last < 6000L || !lastSecurityAlertTimeMs.compareAndSet(last, now)) {
            Log.d(TAG, "Security verification debounced for: ${event.fileName}")
            return
        }

        if (imageCapture == null) {
            initializeBackgroundCamera()
            kotlinx.coroutines.delay(600)
        }

        var captureInstance = getOrAwaitImageCapture()
        var frame = intruderCaptureManager.takeSilentPhoto(captureInstance, cameraExecutor)
        if (frame == null) {
            Log.w(TAG, "Initial camera frame capture returned null. Re-binding background CameraX pipeline...")
            withContext(Dispatchers.Main) {
                initializeBackgroundCamera()
            }
            kotlinx.coroutines.delay(600)
            captureInstance = getOrAwaitImageCapture()
            frame = intruderCaptureManager.takeSilentPhoto(captureInstance, cameraExecutor)
        }

        val authResult = frame?.let { faceAuthManager.authenticateFace(it) }
        val isAuthenticated = authResult?.isAuthenticated ?: false
        val adminName = authResult?.adminName ?: "Admin"
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val actionTag = when (event.eventType.uppercase()) {
            "DELETED" -> "DELETED"
            "MODIFIED", "EDITED" -> "EDITED"
            "CREATED", "NEW", "COPIED" -> "CREATED"
            "RENAMED" -> "RENAMED"
            "ACCESSED", "OPENED" -> "ACCESSED"
            else -> event.eventType
        }

        val actionVerb = when (actionTag) {
            "ACCESSED" -> "opened/accessed"
            "DELETED" -> "deleted"
            "EDITED" -> "edited"
            "CREATED" -> "created"
            "RENAMED" -> "renamed"
            else -> actionTag.lowercase()
        }

        if (isAuthenticated) {
            Log.d(TAG, "File access/change verified by $adminName ✅")
            database.logDao().insertLog(
                AccessLog(
                    file = event.fileName,
                    user = adminName,
                    action = actionTag,
                    details = "Authorized access: File '${event.fileName}' $actionVerb by $adminName at $timestamp.",
                    timestamp = timestamp
                )
            )
        } else {
            Log.w(TAG, "Unauthorized file action ($actionTag) by Intruder 🚨")
            val photoFile = intruderCaptureManager.captureIntruderImage(frame)
            val telemetry = telemetryManager.getDeviceTelemetry()

            database.logDao().insertLog(
                AccessLog(
                    file = event.fileName,
                    user = "Intruder",
                    action = actionTag,
                    details = "UNAUTHORIZED INTRUSION: File '${event.fileName}' $actionVerb by Intruder at $timestamp.\n${telemetry.formattedSummary}",
                    timestamp = timestamp
                )
            )

            val alertSubject = if (actionTag == "ACCESSED") "🚨 Intruder opened monitored file: ${event.fileName}" else "🚨 Intruder modified monitored file: ${event.fileName}"
            val alertBody = "Unauthorized file access detected at ${event.timestamp}.\n\nAction: $actionTag ($actionVerb)\nFile: ${event.fileName}\nDetails:\n${event.changeDetails}"

            emailAlertManager.sendAlert(
                context = this@MainActivity,
                subject = alertSubject,
                body = alertBody,
                imageFile = photoFile,
                telemetry = telemetry
            )

            FirebaseCloudVaultManager(this@MainActivity).syncBreachIncidentToCloud(
                fileName = event.fileName,
                actionType = actionTag,
                timestamp = timestamp,
                details = "UNAUTHORIZED INTRUSION: File '${event.fileName}' $actionVerb by Intruder at $timestamp.",
                imageFile = photoFile,
                telemetry = telemetry
            )
        }

        refreshGallery()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        } else {
            if (faceAuthManager.hasAtLeastOneAdmin()) {
                initializeBackgroundCamera()
            }
        }
    }

    private fun initializeBackgroundCamera() {
        if (!faceAuthManager.hasAtLeastOneAdmin()) {
            Log.d(TAG, "Skipping background camera init: No enrolled admin yet.")
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(cameraExecutor) { proxy -> proxy.close() } }

                this.imageCapture = capture

                val cameraSelector = when {
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    else -> {
                        Log.e(TAG, "No camera available on device")
                        return@addListener
                    }
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, capture, analysis)
                Log.d(TAG, "Background CameraX silent capture initialized with ImageCapture + ImageAnalysis")
            } catch (e: Exception) {
                Log.e(TAG, "Background camera initialization failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onResume() {
        super.onResume()
        isInForeground = true

        val isCameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!isCameraGranted) return

        if (faceAuthManager.hasAtLeastOneAdmin()) {
            initializeBackgroundCamera()
            val uri = selectedFolderUriState.value
            if (uri != null && isAutoScanEnabledState.value) {
                com.honeyfile.security.service.HoneyMonitoringService.startService(this, uri)
            }
        } else {
            checkMandatoryAdminEnrollment()
        }
        refreshGallery()
    }

    override fun onStop() {
        super.onStop()
        isInForeground = false
    }

    private suspend fun getOrAwaitImageCapture(): ImageCapture? {
        if (imageCapture != null) return imageCapture
        for (i in 0..50) {
            kotlinx.coroutines.delay(100)
            if (imageCapture != null) return imageCapture
        }
        return imageCapture
    }

    private fun onTriggerAccessClicked() {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "Capturing photo & verifying security...", Toast.LENGTH_SHORT).show()

            val captureInstance = getOrAwaitImageCapture()
            val frame = intruderCaptureManager.takeSilentPhoto(captureInstance, cameraExecutor)

            val authResult = frame?.let { faceAuthManager.authenticateFace(it) }
            val isAuthenticated = authResult?.isAuthenticated ?: false
            val adminName = authResult?.adminName ?: "Admin"
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val detectedHoneyName = folderScannerManager.scanResult.value.honeyFileNames.firstOrNull()
            val filename = detectedHoneyName ?: "admin_passwords.txt"

            if (isAuthenticated) {
                Log.d(TAG, "$adminName verified ✅")
                database.logDao().insertLog(
                    AccessLog(
                        file = filename,
                        user = adminName,
                        action = "ACCESS",
                        details = "$adminName verified via facial biometric auth. Confidential file opened.",
                        timestamp = timestamp
                    )
                )
                Toast.makeText(this@MainActivity, "$adminName Verified ✅ Opening Confidential File", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@MainActivity, RealFileViewerActivity::class.java))
            } else {
                Log.d(TAG, "Intruder detected 🚨")
                database.logDao().insertLog(
                    AccessLog(
                        file = filename,
                        user = "Intruder",
                        action = "BREACH",
                        details = "UNAUTHORIZED INTRUDER BREACH on honeyfile '$filename'! Facial auth failed. Silent photo captured and email alert sent.",
                        timestamp = timestamp
                    )
                )

                val photoFile = intruderCaptureManager.captureIntruderImage(frame)

                emailAlertManager.sendAlert(
                    context = this@MainActivity,
                    subject = "Intruder tried opening honeyfile!",
                    body = "Unauthorized access attempt detected at $timestamp on file: $filename.",
                    imageFile = photoFile
                )

                refreshGallery()
                Toast.makeText(this@MainActivity, "Intruder Detected 🚨 Diverting to Decoy File", Toast.LENGTH_LONG).show()
                startActivity(Intent(this@MainActivity, DecoyViewerActivity::class.java))
            }
        }
    }

    private fun refreshGallery() {
        lifecycleScope.launch(Dispatchers.IO) {
            val folder = File(filesDir, "captured")
            val files = folder.listFiles()
                ?.filter { it.extension.equals("jpg", ignoreCase = true) || it.extension.equals("jpeg", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()

            withContext(Dispatchers.Main) {
                capturedPhotosState.clear()
                capturedPhotosState.addAll(files)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "MainActivity"

        @Volatile
        var isInForeground = false
    }
}
