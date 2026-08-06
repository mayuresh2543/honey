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
            faceAuthManager.clearAdmin1()
            Toast.makeText(context, "Admin 1 profile cleared", Toast.LENGTH_SHORT).show()
            updateAdminStatusUI()
        }

        binding.btnEnrollAdmin2.setOnClickListener {
            val scanDialog = AdminEnrollScanDialogFragment.newInstance(2)
            scanDialog.onEnrollmentCompleted = {
                updateAdminStatusUI()
            }
            scanDialog.show(parentFragmentManager, AdminEnrollScanDialogFragment.TAG)
        }

        binding.btnClearAdmin2.setOnClickListener {
            faceAuthManager.clearAdmin2()
            Toast.makeText(context, "Admin 2 profile cleared", Toast.LENGTH_SHORT).show()
            updateAdminStatusUI()
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
        } else {
            binding.tvAdmin1Status.text = "Empty ⚪"
            binding.tvAdmin1Status.setTextColor(secondaryColor)
            binding.tvAdmin1Email.visibility = View.GONE
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
        } else {
            binding.tvAdmin2Status.text = "Empty ⚪"
            binding.tvAdmin2Status.setTextColor(secondaryColor)
            binding.tvAdmin2Email.visibility = View.GONE
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
