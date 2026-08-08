package com.honeyfile.security.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.honeyfile.security.R
import com.honeyfile.security.auth.FaceAuthManager
import com.honeyfile.security.databinding.DialogAdminManagementBinding
import com.honeyfile.security.databinding.DialogEditEmailBinding

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
                .setTitle("⚠️ Reset ${faceAuthManager.admin1Name} Profile?")
                .setMessage("Are you sure you want to delete ${faceAuthManager.admin1Name}'s facial biometric profile and registered email notification address? This action cannot be undone.")
                .setPositiveButton("Reset Profile") { _, _ ->
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
                .setTitle("⚠️ Reset ${faceAuthManager.admin2Name} Profile?")
                .setMessage("Are you sure you want to delete ${faceAuthManager.admin2Name}'s facial biometric profile and registered email notification address? This action cannot be undone.")
                .setPositiveButton("Reset Profile") { _, _ ->
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

    private fun showEditEmailDialog(adminTarget: Int) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_email, null)
        val bindingDialog = DialogEditEmailBinding.bind(dialogView)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val themeManager = com.honeyfile.security.auth.ThemeManager(requireContext())
        themeManager.applyInstant(bindingDialog.root, dialog.window, themeManager.isDarkMode)

        val targetTitle = if (adminTarget == 1) faceAuthManager.admin1Name else faceAuthManager.admin2Name
        bindingDialog.tvEditEmailTitle.text = "👤 Edit $targetTitle Profile"

        val currentName = if (adminTarget == 1) faceAuthManager.admin1Name else faceAuthManager.admin2Name
        val currentEmail = if (adminTarget == 1) faceAuthManager.admin1Email else faceAuthManager.admin2Email

        bindingDialog.etAdminNameInput.setText(currentName)
        bindingDialog.etAdminEmailInput.setText(currentEmail ?: "")

        bindingDialog.btnCancelEditEmail.setOnClickListener {
            dialog.dismiss()
        }

        bindingDialog.btnSaveAdminEmail.setOnClickListener {
            val inputName = bindingDialog.etAdminNameInput.text?.toString()?.trim()
            val inputEmail = bindingDialog.etAdminEmailInput.text?.toString()?.trim()

            if (inputName.isNullOrBlank()) {
                Toast.makeText(context, "Please enter Admin $adminTarget's name!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Duplicate Name Validation Check
            if (faceAuthManager.isNameTaken(inputName, adminTarget)) {
                val otherAdmin = if (adminTarget == 1) faceAuthManager.admin2Name else faceAuthManager.admin1Name
                Toast.makeText(context, "❌ Name '$inputName' is already in use by $otherAdmin!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Duplicate Email Validation Check
            if (!inputEmail.isNullOrBlank()) {
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(inputEmail).matches()) {
                    Toast.makeText(context, "Please enter a valid email address format!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (faceAuthManager.isEmailTaken(inputEmail, adminTarget)) {
                    val otherAdmin = if (adminTarget == 1) faceAuthManager.admin2Name else faceAuthManager.admin1Name
                    Toast.makeText(context, "❌ Email '$inputEmail' is already registered to $otherAdmin!", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
            }

            if (adminTarget == 1) {
                faceAuthManager.admin1Name = inputName
                faceAuthManager.admin1Email = if (inputEmail.isNullOrBlank()) null else inputEmail
            } else {
                faceAuthManager.admin2Name = inputName
                faceAuthManager.admin2Email = if (inputEmail.isNullOrBlank()) null else inputEmail
            }

            Toast.makeText(context, "Admin $adminTarget profile updated: $inputName ✅", Toast.LENGTH_SHORT).show()
            updateAdminStatusUI()
            dialog.dismiss()
        }

        dialog.show()
    }

    fun updateAdminStatusUI() {
        val ctx = context ?: return
        val greenColor = ContextCompat.getColor(ctx, R.color.success_green)
        val secondaryColor = ContextCompat.getColor(ctx, R.color.dark_text_secondary)

        if (faceAuthManager.isAdmin1Enrolled) {
            binding.tvAdmin1Title.text = "👤 Admin 1: ${faceAuthManager.admin1Name}"
            binding.tvAdmin1Status.text = "Enrolled ✅"
            binding.tvAdmin1Status.setTextColor(greenColor)
            binding.tvAdmin1Status.setBackgroundResource(R.drawable.badge_rounded_green)
            val email1 = faceAuthManager.admin1Email
            binding.tvAdmin1Email.text = if (!email1.isNullOrBlank()) "📧 $email1" else "📧 No email registered"
            binding.tvAdmin1Email.visibility = View.VISIBLE

            binding.btnEnrollAdmin1.visibility = View.GONE
            binding.btnEditEmailAdmin1.visibility = View.VISIBLE
            binding.btnClearAdmin1.visibility = View.VISIBLE
        } else {
            binding.tvAdmin1Title.text = if (faceAuthManager.admin1Name != "Admin 1") "👤 Admin 1: ${faceAuthManager.admin1Name}" else "👤 Admin 1 Profile"
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

            binding.btnEnrollAdmin1.visibility = View.VISIBLE
            binding.btnEditEmailAdmin1.visibility = View.VISIBLE
            binding.btnClearAdmin1.visibility = View.GONE
        }

        if (faceAuthManager.isAdmin2Enrolled) {
            binding.tvAdmin2Title.text = "👤 Admin 2: ${faceAuthManager.admin2Name}"
            binding.tvAdmin2Status.text = "Enrolled ✅"
            binding.tvAdmin2Status.setTextColor(greenColor)
            binding.tvAdmin2Status.setBackgroundResource(R.drawable.badge_rounded_green)
            val email2 = faceAuthManager.admin2Email
            binding.tvAdmin2Email.text = if (!email2.isNullOrBlank()) "📧 $email2" else "📧 No email registered"
            binding.tvAdmin2Email.visibility = View.VISIBLE

            binding.btnEnrollAdmin2.visibility = View.GONE
            binding.btnEditEmailAdmin2.visibility = View.VISIBLE
            binding.btnClearAdmin2.visibility = View.VISIBLE
        } else {
            binding.tvAdmin2Title.text = if (faceAuthManager.admin2Name != "Admin 2") "👤 Admin 2: ${faceAuthManager.admin2Name}" else "👤 Admin 2 Profile"
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

            binding.btnEnrollAdmin2.visibility = View.VISIBLE
            binding.btnEditEmailAdmin2.visibility = View.VISIBLE
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
