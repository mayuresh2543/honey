package com.honeyfile.security.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import com.honeyfile.security.databinding.DialogPhotoDetailBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotoDetailDialogFragment : DialogFragment() {

    private var _binding: DialogPhotoDetailBinding? = null
    private val binding get() = _binding!!

    private var photoFile: File? = null
    var onPhotoDeletedListener: (() -> Unit)? = null

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("image/jpeg")
    ) { destUri: Uri? ->
        if (destUri != null && photoFile != null && photoFile!!.exists()) {
            exportPhotoToUri(photoFile!!, destUri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPhotoDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val themeManager = com.honeyfile.security.auth.ThemeManager(requireContext())
        themeManager.applyInstant(binding.root, dialog?.window, themeManager.isDarkMode)

        val filePath = arguments?.getString(ARG_FILE_PATH)
        if (filePath != null) {
            photoFile = File(filePath)
        }

        val file = photoFile
        if (file != null && file.exists()) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            binding.ivDetailPhoto.setImageBitmap(bitmap)

            val lastModDate = Date(file.lastModified())
            val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(lastModDate)

            binding.tvDetailFileName.text = "Snapshot: ${file.name}"
            binding.tvDetailTimestamp.text = "Captured at: $formattedTime"
        } else {
            binding.tvDetailFileName.text = "Snapshot unavailable"
        }

        binding.btnCloseDetail.setOnClickListener {
            dismiss()
        }

        binding.btnShareEvidence.setOnClickListener {
            shareEvidence()
        }

        binding.btnExportPhoto.setOnClickListener {
            file?.let { exportPhoto(it) }
        }

        binding.btnDeletePhoto.setOnClickListener {
            file?.let { confirmAndDeletePhoto(it) }
        }
    }

    private fun exportPhoto(file: File) {
        if (!file.exists()) {
            Toast.makeText(context, "Photo file not found", Toast.LENGTH_SHORT).show()
            return
        }
        createDocumentLauncher.launch(file.name)
    }

    private fun exportPhotoToUri(sourceFile: File, destUri: Uri) {
        try {
            requireContext().contentResolver.openOutputStream(destUri)?.use { outputStream ->
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Toast.makeText(context, "Photo exported successfully! 📁", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmAndDeletePhoto(file: File) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Photo from Vault")
            .setMessage("Are you sure you want to permanently delete snapshot '${file.name}' from the Vault?")
            .setPositiveButton("Delete") { _, _ ->
                if (file.exists() && file.delete()) {
                    Toast.makeText(context, "Photo deleted from Vault", Toast.LENGTH_SHORT).show()
                    onPhotoDeletedListener?.invoke()
                    dismiss()
                } else {
                    Toast.makeText(context, "Failed to delete file", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareEvidence() {
        val file = photoFile
        if (file == null || !file.exists()) {
            Toast.makeText(context, "Photo file not found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val ctx = requireContext()
            val uri: Uri = FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "🚨 Honeyfile Intrusion Evidence - ${file.name}")
                putExtra(Intent.EXTRA_TEXT, "Security Intrusion Evidence Snapshot captured on ${file.name}.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Share Intruder Evidence via"))
        } catch (e: Exception) {
            Toast.makeText(context, "Error sharing evidence: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PhotoDetailDialogFragment"
        private const val ARG_FILE_PATH = "arg_file_path"

        fun newInstance(photoFile: File): PhotoDetailDialogFragment {
            return PhotoDetailDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FILE_PATH, photoFile.absolutePath)
                }
            }
        }
    }
}
