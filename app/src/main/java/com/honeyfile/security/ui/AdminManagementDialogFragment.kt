package com.honeyfile.security.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.honeyfile.security.R
import com.honeyfile.security.alert.EmailAlertManager
import com.honeyfile.security.auth.FaceAuthManager
import com.honeyfile.security.camera.IntruderCaptureManager
import com.honeyfile.security.databinding.DialogAdminManagementBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminManagementDialogFragment : DialogFragment() {

    private var _binding: DialogAdminManagementBinding? = null
    private val binding get() = _binding!!

    private lateinit var faceAuthManager: FaceAuthManager
    private val emailAlertManager = EmailAlertManager()

    var onEnrollAdmin1Clicked: (() -> Unit)? = null
    var onEnrollAdmin2Clicked: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
        faceAuthManager = FaceAuthManager(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAdminManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val themeManager = com.honeyfile.security.auth.ThemeManager(requireContext())
        themeManager.applyInstant(binding.root, dialog?.window, themeManager.isDarkMode)

        updateAdminStatusUI()

        binding.btnEnrollAdmin1.setOnClickListener {
            val scanDialog = AdminEnrollScanDialogFragment.newInstance(1)
            scanDialog.onEnrollmentCompleted = {
                updateAdminStatusUI()
            }
            scanDialog.show(parentFragmentManager, AdminEnrollScanDialogFragment.TAG)
        }

        binding.btnEditEmailAdmin1.setOnClickListener {
            showEditEmailDialog(1)
        }

        binding.btnClearAdmin1.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("⚠️ Reset Admin 1 Profile?")
                .setMessage("Are you sure you want to delete Admin 1's facial biometric profile and registered email notification address? This action cannot be undone.")
                .setPositiveButton("Reset Admin 1") { _, _ ->
                    faceAuthManager.clearAdmin1()
                    Toast.makeText(context, "Admin 1 profile cleared ✅", Toast.LENGTH_SHORT).show()
                    updateAdminStatusUI()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnEnrollAdmin2.setOnClickListener {
            val scanDialog = AdminEnrollScanDialogFragment.newInstance(2)
            scanDialog.onEnrollmentCompleted = {
                updateAdminStatusUI()
            }
            scanDialog.show(parentFragmentManager, AdminEnrollScanDialogFragment.TAG)
        }

        binding.btnEditEmailAdmin2.setOnClickListener {
            showEditEmailDialog(2)
        }

        binding.btnClearAdmin2.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("⚠️ Reset Admin 2 Profile?")
                .setMessage("Are you sure you want to delete Admin 2's facial biometric profile and registered email notification address? This action cannot be undone.")
                .setPositiveButton("Reset Admin 2") { _, _ ->
                    faceAuthManager.clearAdmin2()
                    Toast.makeText(context, "Admin 2 profile cleared ✅", Toast.LENGTH_SHORT).show()
                    updateAdminStatusUI()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnTestEmailAlert.setOnClickListener {
            sendTestEmailAlert()
        }

        binding.btnCloseAdminMgmt.setOnClickListener {
            dismiss()
        }
    }

    private fun showEditEmailDialog(adminTarget: Int) {
        val currentEmail = if (adminTarget == 1) faceAuthManager.admin1Email else faceAuthManager.admin2Email
        val input = EditText(requireContext()).apply {
            hint = "admin@example.com"
            setText(currentEmail ?: "")
            setSingleLine()
            setPadding(40, 30, 40, 30)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("📧 Register Admin $adminTarget Email")
            .setMessage("Enter the email address where Admin $adminTarget will receive intruder photo breach alerts:")
            .setView(input)
            .setPositiveButton("Save Email") { _, _ ->
                val newEmail = input.text.toString().trim()
                if (newEmail.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                    Toast.makeText(context, "Invalid email address format!", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (adminTarget == 1) {
                    faceAuthManager.admin1Email = if (newEmail.isBlank()) null else newEmail
                } else {
                    faceAuthManager.admin2Email = if (newEmail.isBlank()) null else newEmail
                }
                Toast.makeText(context, "Admin $adminTarget email updated ✅", Toast.LENGTH_SHORT).show()
                updateAdminStatusUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendTestEmailAlert() {
        val recipients = faceAuthManager.getNotificationRecipients()
        if (recipients.isEmpty()) {
            Toast.makeText(context, "Please register an admin email address first!", Toast.LENGTH_LONG).show()
            return
        }

        binding.btnTestEmailAlert.isEnabled = false
        Toast.makeText(context, "Sending test alert email to ${recipients.joinToString(", ")}...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            val captureManager = IntruderCaptureManager(requireContext())
            val sampleImage = captureManager.getCapturedImages().firstOrNull()

            val result = emailAlertManager.sendAlertDetailed(
                context = requireContext(),
                subject = "TEST Intruder Photo Email Alert",
                body = "This is a test notification from Honeyfile Security Engine to verify intruder email delivery and photo attachment integration.",
                imageFile = sampleImage
            )

            withContext(Dispatchers.Main) {
                binding.btnTestEmailAlert.isEnabled = true
                if (result.isSuccess) {
                    Toast.makeText(context, "✅ ${result.message}", Toast.LENGTH_LONG).show()
                } else {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("❌ Email Delivery Failed")
                        .setMessage("${result.message}\n\nPlease check your device internet connection or verify the registered admin email address.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    fun updateAdminStatusUI() {
        val ctx = context ?: return
        val greenColor = ContextCompat.getColor(ctx, R.color.success_green)
        val secondaryColor = ContextCompat.getColor(ctx, R.color.dark_text_secondary)

        if (faceAuthManager.isAdmin1Enrolled) {
            binding.tvAdmin1Status.text = "Enrolled ✅"
            binding.tvAdmin1Status.setTextColor(greenColor)
            binding.tvAdmin1Status.setBackgroundResource(R.drawable.badge_rounded_green)
            val email1 = faceAuthManager.admin1Email
            if (!email1.isNullOrBlank()) {
                binding.tvAdmin1Email.text = "📧 $email1"
                binding.tvAdmin1Email.visibility = View.VISIBLE
            } else {
                binding.tvAdmin1Email.text = "📧 No email registered (Tap ✏️ Email)"
                binding.tvAdmin1Email.visibility = View.VISIBLE
            }

            binding.btnEnrollAdmin1.isEnabled = false
            binding.btnEnrollAdmin1.alpha = 0.5f
            binding.btnClearAdmin1.visibility = View.VISIBLE
            binding.btnClearAdmin1.isEnabled = true
            binding.btnClearAdmin1.alpha = 1.0f
        } else {
            binding.tvAdmin1Status.text = "Empty ⚪"
            binding.tvAdmin1Status.setTextColor(secondaryColor)
            binding.tvAdmin1Status.setBackgroundResource(R.drawable.badge_rounded_green)
            val email1 = faceAuthManager.admin1Email
            if (!email1.isNullOrBlank()) {
                binding.tvAdmin1Email.text = "📧 $email1"
                binding.tvAdmin1Email.visibility = View.VISIBLE
            } else {
                binding.tvAdmin1Email.visibility = View.GONE
            }

            binding.btnEnrollAdmin1.isEnabled = true
            binding.btnEnrollAdmin1.alpha = 1.0f
            binding.btnClearAdmin1.visibility = View.GONE
        }

        if (faceAuthManager.isAdmin2Enrolled) {
            binding.tvAdmin2Status.text = "Enrolled ✅"
            binding.tvAdmin2Status.setTextColor(greenColor)
            binding.tvAdmin2Status.setBackgroundResource(R.drawable.badge_rounded_green)
            val email2 = faceAuthManager.admin2Email
            if (!email2.isNullOrBlank()) {
                binding.tvAdmin2Email.text = "📧 $email2"
                binding.tvAdmin2Email.visibility = View.VISIBLE
            } else {
                binding.tvAdmin2Email.text = "📧 No email registered (Tap ✏️ Email)"
                binding.tvAdmin2Email.visibility = View.VISIBLE
            }

            binding.btnEnrollAdmin2.isEnabled = false
            binding.btnEnrollAdmin2.alpha = 0.5f
            binding.btnClearAdmin2.visibility = View.VISIBLE
            binding.btnClearAdmin2.isEnabled = true
            binding.btnClearAdmin2.alpha = 1.0f
        } else {
            binding.tvAdmin2Status.text = "Empty ⚪"
            binding.tvAdmin2Status.setTextColor(secondaryColor)
            binding.tvAdmin2Status.setBackgroundResource(R.drawable.badge_rounded_green)
            val email2 = faceAuthManager.admin2Email
            if (!email2.isNullOrBlank()) {
                binding.tvAdmin2Email.text = "📧 $email2"
                binding.tvAdmin2Email.visibility = View.VISIBLE
            } else {
                binding.tvAdmin2Email.visibility = View.GONE
            }

            binding.btnEnrollAdmin2.isEnabled = true
            binding.btnEnrollAdmin2.alpha = 1.0f
            binding.btnClearAdmin2.visibility = View.GONE
        }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        (activity as? MainActivity)?.rebindBackgroundCamera()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AdminManagementDialogFragment"

        fun newInstance(): AdminManagementDialogFragment {
            return AdminManagementDialogFragment()
        }
    }
}
