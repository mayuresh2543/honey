package com.honeyfile.security.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
    var onEnrollmentCompleted: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
        faceAuthManager = FaceAuthManager(requireContext())
        cameraExecutor = Executors.newSingleThreadExecutor()
        adminTarget = arguments?.getInt(ARG_ADMIN_TARGET, 1) ?: 1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAdminEnrollScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvEnrollTitle.text = "📸 Admin $adminTarget Facial Enrollment"
        binding.tvEnrollSubtitle.text = "Align Admin $adminTarget's face in the camera preview below"

        startCameraPreview()

        binding.btnCaptureEnroll.setOnClickListener {
            captureAndEnrollFace()
        }

        binding.btnCancelEnroll.setOnClickListener {
            dismiss()
        }
    }

    private fun startCameraPreview() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.enrollPreviewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

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
            Toast.makeText(context, "Camera not ready", Toast.LENGTH_SHORT).show()
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
                            val result = if (adminTarget == 1) {
                                faceAuthManager.enrollAdmin1FromBitmap(bitmap)
                            } else {
                                faceAuthManager.enrollAdmin2FromBitmap(bitmap)
                            }

                            withContext(Dispatchers.Main) {
                                binding.btnCaptureEnroll.isEnabled = true
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()

                                if (result.isSuccess) {
                                    onEnrollmentCompleted?.invoke(true)
                                    dismiss()
                                }
                            }
                        }
                    } else {
                        binding.btnCaptureEnroll.isEnabled = true
                        Toast.makeText(context, "Failed to capture image frame", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Camera capture failed", exception)
                    binding.btnCaptureEnroll.isEnabled = true
                    Toast.makeText(context, "Capture error: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val planeProxy = imageProxy.planes.firstOrNull() ?: return null
        val buffer: ByteBuffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }

    companion object {
        const val TAG = "AdminEnrollScanDialogFragment"
        private const val ARG_ADMIN_TARGET = "arg_admin_target"

        fun newInstance(adminTarget: Int): AdminEnrollScanDialogFragment {
            return AdminEnrollScanDialogFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_ADMIN_TARGET, adminTarget)
                }
            }
        }
    }
}
