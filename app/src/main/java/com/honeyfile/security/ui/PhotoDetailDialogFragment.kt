package com.honeyfile.security.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import com.honeyfile.security.databinding.DialogPhotoDetailBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotoDetailDialogFragment : DialogFragment() {

    private var _binding: DialogPhotoDetailBinding? = null
    private val binding get() = _binding!!

    private var photoFile: File? = null

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
