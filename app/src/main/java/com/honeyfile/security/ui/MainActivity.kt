package com.honeyfile.security.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.honeyfile.security.alert.EmailAlertManager
import com.honeyfile.security.auth.FaceAuthManager
import com.honeyfile.security.auth.ThemeManager
import com.honeyfile.security.camera.IntruderCaptureManager
import com.honeyfile.security.data.AccessLog
import com.honeyfile.security.data.AppDatabase
import com.honeyfile.security.databinding.ActivityMainBinding
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

    private val logAdapter = LogAdapter()
    private lateinit var galleryAdapter: CapturedImageAdapter

    private var currentFrameBitmap: Bitmap? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var selectedFolderUri: android.net.Uri? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            initializeBackgroundCamera()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        themeManager = ThemeManager(this)
        themeManager.applyTheme()

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getDatabase(this)
        faceAuthManager = FaceAuthManager(this)
        intruderCaptureManager = IntruderCaptureManager(this)
        emailAlertManager = EmailAlertManager()
        folderScannerManager = com.honeyfile.security.scanner.FolderScannerManager(this)

        setupUI()
        setupFolderScanner()
        checkAndRequestPermissions()
        observeDatabase()
        observeFolderScanner()
        refreshGallery()
    }

    private fun setupUI() {
        // Theme switch listener
        binding.switchTheme.isChecked = themeManager.isDarkMode
        binding.switchTheme.setOnCheckedChangeListener { _, isChecked ->
            themeManager.isDarkMode = isChecked
        }

        binding.rvLogs.layoutManager = LinearLayoutManager(this)
        binding.rvLogs.adapter = logAdapter

        galleryAdapter = CapturedImageAdapter { file ->
            Toast.makeText(this, "Captured photo: ${file.name}", Toast.LENGTH_SHORT).show()
        }
        binding.rvGallery.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvGallery.adapter = galleryAdapter

        binding.btnTrigger.setOnClickListener {
            onTriggerAccessClicked()
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
            val uri = selectedFolderUri
            if (isChecked) {
                if (uri != null) {
                    folderScannerManager.startContinuousScanning(uri)
                } else {
                    binding.switchAutoScan.isChecked = false
                    Toast.makeText(this, "Please select a folder first!", Toast.LENGTH_SHORT).show()
                    folderPickerLauncher.launch(null)
                }
            } else {
                folderScannerManager.stopScanning()
            }
        }

        // Auto-start scanning if folder was previously saved
        selectedFolderUri?.let { uri ->
            binding.switchAutoScan.isChecked = true
            folderScannerManager.startContinuousScanning(uri)
        }
    }

    private fun observeFolderScanner() {
        lifecycleScope.launch {
            folderScannerManager.scanResult.collect { result ->
                if (result.folderUri.isNotEmpty()) {
                    binding.tvSelectedFolder.text = "Monitored: ${result.folderName}"
                    binding.tvScanStats.text = "Scanned: ${result.totalFilesScanned} files | Honeyfiles: ${result.honeyFilesFound} (Last: ${result.lastScanTime})"
                } else {
                    binding.tvSelectedFolder.text = getString(com.honeyfile.security.R.string.no_folder_selected)
                    binding.tvScanStats.text = "Scanned Files: 0 | Honeyfiles Detected: 0"
                }
            }
        }
    }

    private var imageCapture: ImageCapture? = null

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        } else {
            initializeBackgroundCamera()
        }
    }

    private fun initializeBackgroundCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = when {
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    else -> {
                        Log.e(TAG, "No camera available on device/emulator")
                        return@addListener
                    }
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture)
                Log.d(TAG, "Background CameraX silent capture initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Background camera initialization failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onTriggerAccessClicked() {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "Capturing photo & verifying security...", Toast.LENGTH_SHORT).show()

            // Silently capture photo in background
            val frame = intruderCaptureManager.takeSilentPhoto(imageCapture, cameraExecutor)

            val authResult = frame?.let { faceAuthManager.authenticateFace(it) }
            val isAuthenticated = authResult?.isAuthenticated ?: false
            val adminName = authResult?.adminName ?: "Admin"
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val detectedHoneyName = folderScannerManager.scanResult.value.honeyFileNames.firstOrNull()
            val filename = detectedHoneyName ?: "admin_passwords.txt"

            if (isAuthenticated) {
                Log.d(TAG, "$adminName verified ✅")
                database.logDao().insertLog(AccessLog(file = filename, user = adminName, timestamp = timestamp))
                Toast.makeText(this@MainActivity, "$adminName Verified ✅ Opening Confidential File", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@MainActivity, RealFileViewerActivity::class.java))
            } else {
                Log.d(TAG, "Intruder detected 🚨")
                database.logDao().insertLog(AccessLog(file = filename, user = "Intruder", timestamp = timestamp))

                // Save captured intruder image
                val photoFile = intruderCaptureManager.captureIntruderImage(frame)

                // Dispatch Email alert
                emailAlertManager.sendAlert(
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

    private fun observeDatabase() {
        database.logDao().getAllLogs().observe(this) { logs ->
            logAdapter.submitList(logs)
            binding.tvTotalLogs.text = logs.size.toString()
        }

        database.logDao().getAdminCount().observe(this) { count ->
            binding.tvAdminCount.text = (count ?: 0).toString()
        }

        database.logDao().getIntruderCount().observe(this) { count ->
            binding.tvIntruderCount.text = (count ?: 0).toString()
        }
    }

    private fun refreshGallery() {
        lifecycleScope.launch(Dispatchers.IO) {
            val files = intruderCaptureManager.getCapturedImages()
            withContext(Dispatchers.Main) {
                galleryAdapter.submitList(files)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
