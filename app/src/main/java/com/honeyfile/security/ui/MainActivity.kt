package com.honeyfile.security.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import java.io.File
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.drawToBitmap
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import com.honeyfile.security.R
import com.honeyfile.security.analytics.SeverityLevel
import com.honeyfile.security.analytics.ThreatAnalyticsManager
import com.honeyfile.security.analytics.ThreatSummary
import com.honeyfile.security.alert.EmailAlertManager
import com.honeyfile.security.alert.TelemetryManager
import com.honeyfile.security.auth.FaceAuthManager
import com.honeyfile.security.auth.ThemeManager
import com.honeyfile.security.camera.IntruderCaptureManager
import com.honeyfile.security.cloud.FirebaseCloudVaultManager
import com.honeyfile.security.data.AccessLog
import com.honeyfile.security.data.AppDatabase
import com.honeyfile.security.databinding.ActivityMainBinding
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var database: AppDatabase
    private lateinit var faceAuthManager: FaceAuthManager
    private lateinit var intruderCaptureManager: IntruderCaptureManager
    private lateinit var emailAlertManager: EmailAlertManager
    private lateinit var themeManager: ThemeManager
    private lateinit var folderScannerManager: com.honeyfile.security.scanner.FolderScannerManager
    private lateinit var telemetryManager: TelemetryManager

    private val logAdapter = LogAdapter()
    private val directoryLogAdapter = DirectoryLogAdapter()
    private lateinit var galleryAdapter: CapturedImageAdapter

    private var currentFrameBitmap: Bitmap? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var selectedFolderUri: android.net.Uri? = null

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
            Toast.makeText(this, "Camera permission is required for security checks", Toast.LENGTH_LONG).show()
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
            selectedFolderUri = uri
            getSharedPreferences("honey_prefs", MODE_PRIVATE)
                .edit()
                .putString("monitored_folder_uri", uri.toString())
                .apply()

            Toast.makeText(this, "Selected folder for monitoring!", Toast.LENGTH_SHORT).show()

            if (binding.switchAutoScan.isChecked) {
                folderScannerManager.startContinuousScanning(uri)
            } else {
                binding.switchAutoScan.isChecked = true
            }
        }
    }

    private var pendingExportFile: File? = null
    private val exportImageLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("image/jpeg")
    ) { destUri: Uri? ->
        if (destUri != null && pendingExportFile != null && pendingExportFile!!.exists()) {
            exportPhotoToUri(pendingExportFile!!, destUri)
        }
    }

    private fun exportPhotoToUri(sourceFile: File, destUri: Uri) {
        try {
            contentResolver.openOutputStream(destUri)?.use { outputStream ->
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Toast.makeText(this, "Photo exported to internal storage! 📁", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        themeManager = ThemeManager(this)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply theme colors in-place instantly (zero window recreation, zero blank screen)
        themeManager.applyInstant(binding.root, window, themeManager.isDarkMode)

        database = AppDatabase.getDatabase(this)
        faceAuthManager = FaceAuthManager(this)
        intruderCaptureManager = IntruderCaptureManager(this)
        emailAlertManager = EmailAlertManager()
        folderScannerManager = com.honeyfile.security.scanner.FolderScannerManager(this)
        telemetryManager = TelemetryManager(this)

        setupUI(savedInstanceState)
        setupFolderScanner()
        checkAndRequestPermissions()
        observeDatabase()
        observeFolderScanner()
        refreshGallery()
    }

    private fun selectTab(tabId: Int) {
        binding.tabOverview.visibility = if (tabId == R.id.nav_dashboard) View.VISIBLE else View.GONE
        binding.tabScanner.visibility = if (tabId == R.id.nav_scanner) View.VISIBLE else View.GONE
        binding.tabVault.visibility = if (tabId == R.id.nav_vault) View.VISIBLE else View.GONE
        binding.tabLogs.visibility = if (tabId == R.id.nav_logs) View.VISIBLE else View.GONE
        if (tabId == R.id.nav_vault) {
            refreshGallery()
        }
    }

    private fun setupUI(savedInstanceState: Bundle?) {
        // Theme switch listener — fast 150ms in-place color animation with ZERO window recreation or blank screen flash
        binding.switchTheme.isChecked = themeManager.isDarkMode
        binding.switchTheme.setOnCheckedChangeListener { _, isChecked ->
            if (themeManager.isDarkMode != isChecked) {
                themeManager.isDarkMode = isChecked
                themeManager.animateTransition(binding.root, window, isChecked, 150L)
                logAdapter.notifyDataSetChanged()
                directoryLogAdapter.notifyDataSetChanged()
                galleryAdapter.notifyDataSetChanged()
            }
        }

        binding.rvLogs.layoutManager = LinearLayoutManager(this)
        binding.rvLogs.adapter = logAdapter

        binding.rvDirectoryLogs.layoutManager = LinearLayoutManager(this)
        binding.rvDirectoryLogs.adapter = directoryLogAdapter

        binding.chipGroupDirFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: R.id.chipFilterAll
            val category = when (checkedId) {
                R.id.chipFilterNew -> "NEW"
                R.id.chipFilterEdited -> "EDITED"
                R.id.chipFilterCopied -> "COPIED"
                R.id.chipFilterDeleted -> "DELETED"
                R.id.chipFilterOpened -> "OPENED"
                R.id.chipFilterBreaches -> "BREACHES"
                else -> "ALL"
            }
            directoryLogAdapter.setFilterCategory(category)
        }

        // Open Intruder Photo Evidence Detail Dialog on item click & Long-press for Delete / Export
        galleryAdapter = CapturedImageAdapter(
            onImageClick = { file ->
                val dialog = PhotoDetailDialogFragment.newInstance(file)
                dialog.onPhotoDeletedListener = { refreshGallery() }
                dialog.show(supportFragmentManager, PhotoDetailDialogFragment.TAG)
            },
            onImageLongClick = { file ->
                showVaultItemOptionsDialog(file)
            }
        )
        binding.rvGallery.layoutManager = GridLayoutManager(this, 3)
        binding.rvGallery.adapter = galleryAdapter
        // setHasFixedSize: RecyclerView won't call requestLayout() when items change,
        // since the grid size doesn't depend on the number of items.
        binding.rvGallery.setHasFixedSize(true)

        binding.btnManageAdmins.setOnClickListener {
            val dialog = AdminManagementDialogFragment.newInstance()
            dialog.show(supportFragmentManager, AdminManagementDialogFragment.TAG)
        }

        binding.cardThreatAnalytics.setOnClickListener {
            showThreatDetailDialog(0)
        }
        binding.tvSlot0.setOnClickListener { showThreatDetailDialog(0) }
        binding.tvSlot1.setOnClickListener { showThreatDetailDialog(1) }
        binding.tvSlot2.setOnClickListener { showThreatDetailDialog(2) }
        binding.tvSlot3.setOnClickListener { showThreatDetailDialog(3) }
        binding.tvSlot4.setOnClickListener { showThreatDetailDialog(4) }
        binding.tvSlot5.setOnClickListener { showThreatDetailDialog(5) }

        binding.btnDeployDecoys.setOnClickListener {
            if (!checkMandatoryAdminEnrollment()) return@setOnClickListener
            val dialog = DecoyStudioDialogFragment.newInstance(selectedFolderUri)
            dialog.show(supportFragmentManager, DecoyStudioDialogFragment.TAG)
        }

        binding.btnExportCsv.setOnClickListener {
            exportAuditLogsToCsv()
        }

        binding.cardCredits.setOnClickListener {
            showAboutCreditsDialog()
        }

        // Bottom Navigation Tab Listener
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            selectTab(item.itemId)
            if (item.itemId == R.id.nav_vault) {
                refreshGallery()
            }
            true
        }

        updateAdminUIStatus()
    }

    fun checkMandatoryAdminEnrollment(): Boolean {
        if (!faceAuthManager.hasAtLeastOneAdmin()) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                checkAndRequestPermissions()
                return false
            }

            val existing = supportFragmentManager.findFragmentByTag(AdminEnrollScanDialogFragment.TAG)
            if (existing == null || !existing.isAdded) {
                val scanDialog = AdminEnrollScanDialogFragment.newInstance(1, isMandatory = true)
                scanDialog.onEnrollmentCompleted = { success ->
                    updateAdminUIStatus()
                    if (success) {
                        Toast.makeText(this, "Administrator profile enrolled! Honeyfile Security armed 🛡️", Toast.LENGTH_LONG).show()
                        rebindBackgroundCamera()
                    } else {
                        checkMandatoryAdminEnrollment()
                    }
                }
                scanDialog.show(supportFragmentManager, AdminEnrollScanDialogFragment.TAG)
            }
            return false
        }
        updateAdminUIStatus()
        return true
    }

    fun updateAdminUIStatus() {
        val count = faceAuthManager.getEnrolledAdminCount()
        if (count > 0) {
            val names = mutableListOf<String>()
            if (faceAuthManager.isAdmin1Enrolled) names.add(faceAuthManager.admin1Name)
            if (faceAuthManager.isAdmin2Enrolled) names.add(faceAuthManager.admin2Name)
            binding.btnManageAdmins.text = "👥 Admin Profiles: ${names.joinToString(", ")} ($count/2)"
        } else {
            binding.btnManageAdmins.text = "⚠️ 0 Admins Enrolled (Tap to Setup)"
        }
    }

    private fun setupFolderScanner() {
        val savedUriStr = getSharedPreferences("honey_prefs", MODE_PRIVATE)
            .getString("monitored_folder_uri", null)

        if (savedUriStr != null) {
            selectedFolderUri = android.net.Uri.parse(savedUriStr)
        }

        binding.btnSelectFolder.setOnClickListener {
            folderPickerLauncher.launch(null)
        }

        binding.switchAutoScan.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !checkMandatoryAdminEnrollment()) {
                binding.switchAutoScan.isChecked = false
                return@setOnCheckedChangeListener
            }
            val uri = selectedFolderUri
            if (isChecked) {
                if (uri != null) {
                    folderScannerManager.startContinuousScanning(uri)
                    com.honeyfile.security.service.HoneyMonitoringService.startService(this, uri)
                } else {
                    binding.switchAutoScan.isChecked = false
                    Toast.makeText(this, "Please select a folder first!", Toast.LENGTH_SHORT).show()
                    folderPickerLauncher.launch(null)
                }
            } else {
                folderScannerManager.stopScanning()
                com.honeyfile.security.service.HoneyMonitoringService.stopService(this)
            }
        }

        // Auto-start scanning if folder was previously saved and admin is enrolled
        if (faceAuthManager.hasAtLeastOneAdmin()) {
            selectedFolderUri?.let { uri ->
                binding.switchAutoScan.isChecked = true
                folderScannerManager.startContinuousScanning(uri)
                com.honeyfile.security.service.HoneyMonitoringService.startService(this, uri)
            }
        }
    }

    private fun observeFolderScanner() {
        lifecycleScope.launch {
            folderScannerManager.scanResult.collect { result ->
                if (result.folderUri.isNotEmpty()) {
                    binding.tvSelectedFolder.text = "Monitored: ${result.folderName}"
                    binding.tvScanStats.text = "Scanned: ${result.totalFilesScanned} files | Honeyfiles: ${result.honeyFilesFound} (Last: ${result.lastScanTime})"
                    binding.tvLatestFileChange.text = "📝 ${result.latestChangeSummary}"
                } else {
                    binding.tvSelectedFolder.text = getString(com.honeyfile.security.R.string.no_folder_selected)
                    binding.tvScanStats.text = "Scanned Files: 0 | Honeyfiles Detected: 0"
                    binding.tvLatestFileChange.text = getString(com.honeyfile.security.R.string.no_changes_yet)
                }
            }
        }

        lifecycleScope.launch {
            folderScannerManager.fileChangeEvents.collect { event ->
                Log.w(TAG, "File change event: ${event.fileName} (${event.eventType}), foreground=$isInForeground")
                // Only handle capture here when the app is in the foreground.
                // When the app is in the background, HoneyMonitoringService launches
                // OverlayCaptureActivity which handles the capture independently.
                // Handling it here too causes a second (fallback) photo to be saved.
                if (isInForeground) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        processBackgroundSecurityVerification(event)
                    }
                } else {
                    Log.d(TAG, "App in background — skipping MainActivity capture, service handles it")
                }
            }
        }
    }

    // AtomicLong for thread-safe debounce in processBackgroundSecurityVerification.
    // 6s matches the service's BREACH_DEBOUNCE_MS so one event doesn't fire in both places.
    private val lastSecurityAlertTimeMs = java.util.concurrent.atomic.AtomicLong(0L)

    fun rebindBackgroundCamera() {
        if (!faceAuthManager.hasAtLeastOneAdmin()) return
        lifecycleScope.launch(Dispatchers.Main) {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                initializeBackgroundCamera()
            }
        }
    }

    private suspend fun processBackgroundSecurityVerification(event: com.honeyfile.security.scanner.FileChangeEvent) {
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

        val photoFile = intruderCaptureManager.captureIntruderImage(frame)

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

            // Real-time sub-second off-device backup to Firebase Cloud Vault
            FirebaseCloudVaultManager(this@MainActivity).syncBreachIncidentToCloud(
                fileName = event.fileName,
                actionType = actionTag,
                timestamp = timestamp,
                details = "UNAUTHORIZED INTRUSION: File '${event.fileName}' $actionVerb by Intruder at $timestamp.",
                imageFile = photoFile,
                telemetry = telemetry
            )
        }

        withContext(Dispatchers.Main) {
            refreshGallery()
        }
    }

    private var imageCapture: ImageCapture? = null

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

        // Request "Display over other apps" permission — required so that OverlayCaptureActivity
        // can appear on top of other apps when a breach is detected while app is in background.
        // This is a special permission that must be granted via Settings.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val prefs = getSharedPreferences("honey_prefs", MODE_PRIVATE)
            val alreadyAsked = prefs.getBoolean("overlay_permission_asked", false)
            if (!alreadyAsked) {
                prefs.edit().putBoolean("overlay_permission_asked", true).apply()
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("⚠️ Background Camera Permission Required")
                    .setMessage(
                        "Honeyfile needs \"Display over other apps\" permission to silently photograph " +
                        "intruders in the background when your monitored folder is tampered with.\n\n" +
                        "This is the ONLY way Android allows camera access when the app is not open. " +
                        "No visible UI will ever appear to the intruder."
                    )
                    .setPositiveButton("Grant Permission") { _, _ ->
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    }
                    .setNegativeButton("Later", null)
                    .show()
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

                // ImageAnalysis provides the repeating-request surface that primes the
                // capture pipeline. Without it (or Preview), ImageCapture.takePicture()
                // silently fails because no repeating capture session is established.
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
                        Toast.makeText(this, "No camera available on device", Toast.LENGTH_SHORT).show()
                        return@addListener
                    }
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, capture, analysis)
                Log.d(TAG, "Background CameraX silent capture initialized with ImageCapture + ImageAnalysis")
            } catch (e: Exception) {
                Log.e(TAG, "Background camera initialization failed", e)
                Toast.makeText(this, "Camera initialization error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onResume() {
        super.onResume()
        isInForeground = true
        updateAdminUIStatus()

        val isCameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!isCameraGranted) {
            // Permission request launched in onCreate is in-flight.
            // requestPermissionLauncher will trigger enrollment or camera init when granted.
            return
        }

        val hasAdmin = faceAuthManager.hasAtLeastOneAdmin()
        if (hasAdmin) {
            initializeBackgroundCamera()
        } else {
            checkMandatoryAdminEnrollment()
        }
        refreshGallery()
    }

    override fun onStop() {
        super.onStop()
        // Signal to HoneyMonitoringService that the app is no longer visible.
        // The service will now launch OverlayCaptureActivity on breach instead of
        // relying on the main activity's already-bound camera.
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

                // Dispatch Email alert
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

    private val threatAnalyticsManager = ThreatAnalyticsManager()

    private fun observeDatabase() {
        database.logDao().getAllLogs().observe(this) { logs ->
            logAdapter.submitList(logs)
            directoryLogAdapter.updateLogs(logs)
            binding.tvTotalLogs.text = logs.size.toString()

            val summary = threatAnalyticsManager.analyzeThreats(logs)
            updateThreatAnalyticsUI(summary)

            // Real-time Vault sync: whenever a new breach log is inserted (from background
            // service or foreground verification), immediately refresh the vault grid.
            refreshGallery()
        }

        database.logDao().getAdminCount().observe(this) { count ->
            binding.tvAdminCount.text = (count ?: 0).toString()
        }

        database.logDao().getIntruderCount().observe(this) { count ->
            binding.tvIntruderCount.text = (count ?: 0).toString()
        }
    }

    private fun updateThreatAnalyticsUI(summary: ThreatSummary) {
        binding.tvThreatScore.text = " ${summary.threatScore} / 100"
        binding.pbThreatScore.progress = summary.threatScore
        binding.tvPeakAttackWindow.text = "Peak: ${summary.peakAttackTimeWindow}"

        when (summary.severityLevel) {
            SeverityLevel.LOW -> {
                binding.tvSeverityBadge.text = "LOW RISK 🟢"
                binding.tvSeverityBadge.setTextColor(ContextCompat.getColor(this, R.color.success_green))
                binding.tvSeverityBadge.setBackgroundResource(R.drawable.badge_rounded_green)
                binding.pbThreatScore.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.success_green))
            }
            SeverityLevel.ELEVATED -> {
                binding.tvSeverityBadge.text = "ELEVATED THREAT 🟡"
                binding.tvSeverityBadge.setTextColor(ContextCompat.getColor(this, R.color.warning_yellow))
                binding.tvSeverityBadge.setBackgroundResource(R.drawable.badge_rounded_yellow)
                binding.pbThreatScore.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.warning_yellow))
            }
            SeverityLevel.CRITICAL -> {
                binding.tvSeverityBadge.text = "CRITICAL SEVERITY 🔴"
                binding.tvSeverityBadge.setTextColor(ContextCompat.getColor(this, R.color.alert_red))
                binding.tvSeverityBadge.setBackgroundResource(R.drawable.badge_rounded_red)
                binding.pbThreatScore.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.alert_red))
            }
        }

        val slots = summary.heatmapSlots
        if (slots.size >= 6) {
            val slotViews = listOf(
                binding.tvSlot0, binding.tvSlot1, binding.tvSlot2,
                binding.tvSlot3, binding.tvSlot4, binding.tvSlot5
            )
            for (i in 0..5) {
                val slot = slots[i]
                val tv = slotViews[i]
                tv.text = "${slot.timeLabel}\n${slot.count}"
                val solidColor = Color.parseColor(slot.intensityColorHex)
                tv.background = createSolidRoundedDrawable(solidColor)
                tv.setTextColor(Color.WHITE)
                tv.setTag(R.id.theme_text_tag, "theme_text_skip")
            }
        }
    }

    private fun createSolidRoundedDrawable(color: Int): GradientDrawable {
        val radius = 20f * resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
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
                galleryAdapter.submitList(files.toList())
            }
        }
    }

    private fun showVaultItemOptionsDialog(file: File) {
        val options = arrayOf("📸 View Detail", "📁 Export to Internal Storage", "🗑️ Delete from Vault")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Vault Snapshot: ${file.name}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val dialog = PhotoDetailDialogFragment.newInstance(file)
                        dialog.onPhotoDeletedListener = { refreshGallery() }
                        dialog.show(supportFragmentManager, PhotoDetailDialogFragment.TAG)
                    }
                    1 -> {
                        pendingExportFile = file
                        exportImageLauncher.launch(file.name)
                    }
                    2 -> {
                        confirmAndDeleteVaultPhoto(file)
                    }
                }
            }
            .show()
    }

    private fun confirmAndDeleteVaultPhoto(file: File) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Photo")
            .setMessage("Are you sure you want to delete '${file.name}' from the Vault?")
            .setPositiveButton("Delete") { _, _ ->
                if (file.exists() && file.delete()) {
                    Toast.makeText(this, "Photo deleted from Vault", Toast.LENGTH_SHORT).show()
                    refreshGallery()
                } else {
                    Toast.makeText(this, "Failed to delete photo", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun exportAuditLogsToCsv() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val logs = database.logDao().getAllLogsList()
                if (logs.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "No audit logs available to export!", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val csvHeader = "Log ID,Target File,User Identity,Timestamp\n"
                val csvBody = logs.joinToString("\n") { log ->
                    "${log.id},\"${log.file}\",\"${log.user}\",\"${log.timestamp}\""
                }

                val csvFile = java.io.File(cacheDir, "honeyfile_security_audit_logs.csv")
                csvFile.writeText(csvHeader + csvBody)

                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this@MainActivity,
                    "$packageName.fileprovider",
                    csvFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "📊 Honeyfile Security Audit Logs Export")
                    putExtra(Intent.EXTRA_TEXT, "Exported Security Access Logs from Honeyfile Security System.")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                withContext(Dispatchers.Main) {
                    startActivity(Intent.createChooser(shareIntent, "Export Security Audit Logs CSV via"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting CSV logs", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to export CSV: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showThreatDetailDialog(slotIndex: Int) {
        ThreatAnalyticsDetailDialogFragment.newInstance(slotIndex)
            .show(supportFragmentManager, ThreatAnalyticsDetailDialogFragment.TAG)
    }

    private fun showAboutCreditsDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🛡️ Honeyfile Security v1.0.0-beta")
            .setMessage(
                "Deception & Endpoint Intrusion Detection Platform\n\n" +
                "👨‍💻 Project Developers:\n" +
                "• Mayuresh Nanal\n" +
                "• Anirudh Kewat\n\n" +
                "Honeyfile Security deploys realistic honeypot canary files to proactively detect unauthorized file access, capture silent biometric snapshots, and instantly alert administrators."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "MainActivity"

        /**
         * True when MainActivity is in the foreground (between onResume and onStop).
         * Read by HoneyMonitoringService to decide whether to launch OverlayCaptureActivity:
         * - App in foreground → MainActivity's own camera handles breach capture
         * - App in background → OverlayCaptureActivity must open to get camera access
         */
        @Volatile
        var isInForeground = false
    }
}

