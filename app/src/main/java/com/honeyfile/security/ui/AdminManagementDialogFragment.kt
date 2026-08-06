package com.honeyfile.security.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.honeyfile.security.R
import com.honeyfile.security.auth.FaceAuthManager
import com.honeyfile.security.databinding.DialogAdminManagementBinding

class AdminManagementDialogFragment : DialogFragment() {

    private var _binding: DialogAdminManagementBinding? = null
    private val binding get() = _binding!!

    private lateinit var faceAuthManager: FaceAuthManager

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

        binding.btnCloseAdminMgmt.setOnClickListener {
            dismiss()
        }
    }

    fun updateAdminStatusUI() {
        val ctx = context ?: return
        val greenColor = ContextCompat.getColor(ctx, R.color.success_green)
        val secondaryColor = ContextCompat.getColor(ctx, R.color.dark_text_secondary)

        if (faceAuthManager.isAdmin1Enrolled) {
            binding.tvAdmin1Status.text = "Enrolled ✅"
            binding.tvAdmin1Status.setTextColor(greenColor)
            val email1 = faceAuthManager.admin1Email
            if (!email1.isNullOrBlank()) {
                binding.tvAdmin1Email.text = "📧 $email1"
                binding.tvAdmin1Email.visibility = View.VISIBLE
            } else {
                binding.tvAdmin1Email.visibility = View.GONE
            }

            binding.btnEnrollAdmin1.isEnabled = false
            binding.btnEnrollAdmin1.alpha = 0.5f
            binding.btnClearAdmin1.isEnabled = true
            binding.btnClearAdmin1.alpha = 1.0f
        } else {
            binding.tvAdmin1Status.text = "Empty ⚪"
            binding.tvAdmin1Status.setTextColor(secondaryColor)
            binding.tvAdmin1Email.visibility = View.GONE

            binding.btnEnrollAdmin1.isEnabled = true
            binding.btnEnrollAdmin1.alpha = 1.0f
            binding.btnClearAdmin1.isEnabled = false
            binding.btnClearAdmin1.alpha = 0.5f
        }

        if (faceAuthManager.isAdmin2Enrolled) {
            binding.tvAdmin2Status.text = "Enrolled ✅"
            binding.tvAdmin2Status.setTextColor(greenColor)
            val email2 = faceAuthManager.admin2Email
            if (!email2.isNullOrBlank()) {
                binding.tvAdmin2Email.text = "📧 $email2"
                binding.tvAdmin2Email.visibility = View.VISIBLE
            } else {
                binding.tvAdmin2Email.visibility = View.GONE
            }

            binding.btnEnrollAdmin2.isEnabled = false
            binding.btnEnrollAdmin2.alpha = 0.5f
            binding.btnClearAdmin2.isEnabled = true
            binding.btnClearAdmin2.alpha = 1.0f
        } else {
            binding.tvAdmin2Status.text = "Empty ⚪"
            binding.tvAdmin2Status.setTextColor(secondaryColor)
            binding.tvAdmin2Email.visibility = View.GONE

            binding.btnEnrollAdmin2.isEnabled = true
            binding.btnEnrollAdmin2.alpha = 1.0f
            binding.btnClearAdmin2.isEnabled = false
            binding.btnClearAdmin2.alpha = 0.5f
        }
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
