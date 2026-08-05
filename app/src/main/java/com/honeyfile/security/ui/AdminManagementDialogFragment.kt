package com.honeyfile.security.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.honeyfile.security.R
import com.honeyfile.security.auth.FaceAuthManager
import com.honeyfile.security.databinding.DialogAdminManagementBinding

class AdminManagementDialogFragment : DialogFragment() {

    private var _binding: DialogAdminManagementBinding? = null
    private val binding get() = _binding!!

    private lateinit var faceAuthManager: FaceAuthManager

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

        updateAdminStatusUI()

        binding.btnEnrollAdmin1.setOnClickListener {
            faceAuthManager.enrollAdmin1()
            Toast.makeText(context, "Admin 1 face profile enrolled! ✅", Toast.LENGTH_SHORT).show()
            updateAdminStatusUI()
        }

        binding.btnClearAdmin1.setOnClickListener {
            faceAuthManager.clearAdmin1()
            Toast.makeText(context, "Admin 1 profile cleared", Toast.LENGTH_SHORT).show()
            updateAdminStatusUI()
        }

        binding.btnEnrollAdmin2.setOnClickListener {
            faceAuthManager.enrollAdmin2()
            Toast.makeText(context, "Admin 2 face profile enrolled! ✅", Toast.LENGTH_SHORT).show()
            updateAdminStatusUI()
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

    private fun updateAdminStatusUI() {
        if (faceAuthManager.isAdmin1Enrolled) {
            binding.tvAdmin1Status.text = "Enrolled ✅"
            binding.tvAdmin1Status.setTextColor(requireContext().getColor(R.color.success_green))
        } else {
            binding.tvAdmin1Status.text = "Empty ⚪"
            binding.tvAdmin1Status.setTextColor(requireContext().getColor(R.color.light_text_secondary))
        }

        if (faceAuthManager.isAdmin2Enrolled) {
            binding.tvAdmin2Status.text = "Enrolled ✅"
            binding.tvAdmin2Status.setTextColor(requireContext().getColor(R.color.success_green))
        } else {
            binding.tvAdmin2Status.text = "Empty ⚪"
            binding.tvAdmin2Status.setTextColor(requireContext().getColor(R.color.light_text_secondary))
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
