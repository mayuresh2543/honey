package com.honeyfile.security.ui

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.honeyfile.security.auth.FaceAuthManager
import com.honeyfile.security.databinding.DialogAdminEnrollScanBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AdminEnrollScanDialogFragment : DialogFragment() {

    private var _binding: DialogAdminEnrollScanBinding? = null
    private val binding get() = _binding!!

    private lateinit var faceAuthManager: FaceAuthManager
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private var adminTarget: Int = 1 // 1 for Admin 1, 2 for Admin 2
    private var isMandatory: Boolean = false
    private var tempCapturedBitmap: Bitmap? = null
    private var isEnrollmentSavedSuccessfully: Boolean = false
    var onEnrollmentCompleted: ((Boolean) -> Unit)? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCameraPreview()
        } else {
            Toast.makeText(context, "Camera permission is required to scan administrator face profile.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
        faceAuthManager = FaceAuthManager(requireContext())
        cameraExecutor = Executors.newSingleThreadExecutor()
        adminTarget = arguments?.getInt(ARG_ADMIN_TARGET, 1) ?: 1
        isMandatory = arguments?.getBoolean(ARG_IS_MANDATORY, false) ?: false
        isCancelable = !isMandatory
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAdminEnrollScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.setCanceledOnTouchOutside(!isMandatory)

        val themeManager = com.honeyfile.security.auth.ThemeManager(requireContext())
        themeManager.applyInstant(binding.root, dialog?.window, themeManager.isDarkMode)

        if (isMandatory) {
            binding.tvEnrollTitle.text = "🛡️ Admin 1 Setup (Mandatory)"
            binding.tvEnrollSubtitle.text = "Honeyfile Security requires at least 1 Administrator. Align your face in the preview below."
            binding.btnCancelEnroll.text = "Exit App"
            binding.btnCancelEnroll.setOnClickListener {
                showExitConfirmationDialog()
            }
        } else {
            binding.tvEnrollTitle.text = "📸 Admin $adminTarget Facial Enrollment"
            binding.tvEnrollSubtitle.text = "Align Admin $adminTarget's face in the camera preview below"
            binding.btnCancelEnroll.text = "Cancel"
            binding.btnCancelEnroll.setOnClickListener {
                dismiss()
            }
        }

        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCameraPreview()
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }

        binding.btnCaptureEnroll.setOnClickListener {
            captureAndEnrollFace()
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (binding.layoutScanStep.visibility == View.VISIBLE && imageCapture == null) {
                startCameraPreview()
            }
        }
    }

    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Exit Honeyfile Security?")
            .setMessage("Honeyfile deception and intrusion monitoring cannot operate without an enrolled Administrator biometric profile. Exit app now?")
            .setPositiveButton("Exit") { _, _ ->
                activity?.finishAffinity()
            }
            .setNegativeButton("Continue Setup", null)
            .show()
    }

    private fun startCameraPreview() {
        if (_binding == null || !isAdded) return
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            try {
                if (_binding == null || !isAdded) return@addListener

                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.enrollPreviewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                Log.d(TAG, "Enrollment camera preview initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error binding enrollment camera preview", e)
                Toast.makeText(context, "Failed to start camera preview: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun captureAndEnrollFace() {
        val capture = imageCapture ?: run {
            Toast.makeText(context, "Camera initializing... Please wait a moment.", Toast.LENGTH_SHORT).show()
            startCameraPreview()
            return
        }

        binding.btnCaptureEnroll.isEnabled = false
        Toast.makeText(context, "Processing face detection...", Toast.LENGTH_SHORT).show()

        capture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val bitmap = imageProxyToBitmap(imageProxy)
                    imageProxy.close()

                    if (bitmap != null) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            // Validate that scanned face does NOT match existing enrolled admin
                            val authCheck = faceAuthManager.authenticateFace(bitmap)
                            withContext(Dispatchers.Main) {
                                binding.btnCaptureEnroll.isEnabled = true
                                if (authCheck.isAuthenticated && authCheck.adminName != null) {
                                    Toast.makeText(
                                        context,
                                        "❌ Facial profile already enrolled to an administrator account. Each administrator slot must belong to a distinct person.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@withContext
                                }

                                tempCapturedBitmap = bitmap
                                Toast.makeText(context, "Face scan captured! Complete profile details below. ✅", Toast.LENGTH_SHORT).show()
                                showEmailRegistrationStep()
                            }
                        }
                    } else {
                        binding.btnCaptureEnroll.isEnabled = true
                        Toast.makeText(context, "Failed to capture image frame", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Camera capture failed [code=${exception.imageCaptureError}]", exception)
                    binding.btnCaptureEnroll.isEnabled = true
                    Toast.makeText(context, "Capture error: ${exception.message}", Toast.LENGTH_SHORT).show()
                    // Rebind camera if needed
                    startCameraPreview()
                }
            }
        )
    }

    private fun showEmailRegistrationStep() {
        binding.layoutScanStep.visibility = View.GONE
        binding.layoutEmailStep.visibility = View.VISIBLE

        val defaultName = if (adminTarget == 1) faceAuthManager.admin1Name else faceAuthManager.admin2Name
        binding.tvEnrollTitle.text = "👤 Admin $adminTarget Profile Details"
        binding.tvEnrollSubtitle.text = "Enter administrator name and email address for intruder alerts"

        binding.etAdminName.setText(if (defaultName.startsWith("Admin ")) "" else defaultName)
        val currentEmail = if (adminTarget == 1) faceAuthManager.admin1Email else faceAuthManager.admin2Email
        binding.etAdminEmail.setText(currentEmail ?: "")

        binding.btnSaveEmail.setOnClickListener {
            val inputName = binding.etAdminName.text?.toString()?.trim()
            val inputEmail = binding.etAdminEmail.text?.toString()?.trim()

            if (inputName.isNullOrEmpty()) {
                Toast.makeText(context, "Please enter Admin $adminTarget's name!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Duplicate Name Check (Non-Enumerable Message)
            if (faceAuthManager.isNameTaken(inputName, adminTarget)) {
                Toast.makeText(context, "❌ Administrator name already registered to another account. Please choose a distinct name.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Duplicate Email Check (Non-Enumerable Message)
            if (!inputEmail.isNullOrEmpty()) {
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(inputEmail).matches()) {
                    Toast.makeText(context, "Please enter a valid email address format!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (faceAuthManager.isEmailTaken(inputEmail, adminTarget)) {
                    Toast.makeText(context, "❌ Email address already registered to another administrator account.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
            }

            val faceBitmap = tempCapturedBitmap ?: run {
                Toast.makeText(context, "Face scan missing. Please re-scan face.", Toast.LENGTH_SHORT).show()
                resetToScanStep()
                return@setOnClickListener
            }

            binding.btnSaveEmail.isEnabled = false
            lifecycleScope.launch(Dispatchers.IO) {
                val enrollResult = if (adminTarget == 1) {
                    faceAuthManager.enrollAdmin1FromBitmap(faceBitmap)
                } else {
                    faceAuthManager.enrollAdmin2FromBitmap(faceBitmap)
                }

                withContext(Dispatchers.Main) {
                    binding.btnSaveEmail.isEnabled = true
                    if (enrollResult.isSuccess) {
                        if (adminTarget == 1) {
                            faceAuthManager.admin1Name = inputName
                            faceAuthManager.admin1Email = if (inputEmail.isNullOrEmpty()) null else inputEmail
                        } else {
                            faceAuthManager.admin2Name = inputName
                            faceAuthManager.admin2Email = if (inputEmail.isNullOrEmpty()) null else inputEmail
                        }

                        isEnrollmentSavedSuccessfully = true
                        Toast.makeText(context, "Admin $adminTarget profile enrolled: $inputName ✅", Toast.LENGTH_LONG).show()
                        onEnrollmentCompleted?.invoke(true)
                        dismiss()
                    } else {
                        Toast.makeText(context, enrollResult.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        binding.btnSkipEmail.text = "🔄 Retake Scan"
        binding.btnSkipEmail.setOnClickListener {
            resetToScanStep()
        }
    }

    private fun resetToScanStep() {
        tempCapturedBitmap = null
        binding.layoutEmailStep.visibility = View.GONE
        binding.layoutScanStep.visibility = View.VISIBLE
        binding.btnCaptureEnroll.isEnabled = true

        if (isMandatory) {
            binding.tvEnrollTitle.text = "🛡️ Admin 1 Setup (Mandatory)"
            binding.tvEnrollSubtitle.text = "Honeyfile Security requires at least 1 Administrator. Align your face in the preview below."
        } else {
            binding.tvEnrollTitle.text = "📸 Admin $adminTarget Facial Enrollment"
            binding.tvEnrollSubtitle.text = "Align Admin $adminTarget's face in the camera preview below"
        }
        startCameraPreview()
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val planeProxy = imageProxy.planes.firstOrNull() ?: return null
        val buffer: ByteBuffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        if (!isEnrollmentSavedSuccessfully) {
            if (adminTarget == 1) faceAuthManager.clearAdmin1() else faceAuthManager.clearAdmin2()
            onEnrollmentCompleted?.invoke(false)
        }
        (activity as? MainActivity)?.rebindBackgroundCamera()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }

    companion object {
        const val TAG = "AdminEnrollScanDialogFragment"
        private const val ARG_ADMIN_TARGET = "arg_admin_target"
        private const val ARG_IS_MANDATORY = "arg_is_mandatory"

        fun newInstance(adminTarget: Int, isMandatory: Boolean = false): AdminEnrollScanDialogFragment {
            return AdminEnrollScanDialogFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_ADMIN_TARGET, adminTarget)
                    putBoolean(ARG_IS_MANDATORY, isMandatory)
                }
            }
        }
    }
}
