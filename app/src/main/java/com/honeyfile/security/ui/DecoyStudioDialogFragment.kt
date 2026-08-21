package com.honeyfile.security.ui

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.honeyfile.security.R
import com.honeyfile.security.data.AccessLog
import com.honeyfile.security.data.AppDatabase
import com.honeyfile.security.decoy.DecoyGeneratorEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DecoyStudioDialogFragment : BottomSheetDialogFragment() {

    private lateinit var engine: DecoyGeneratorEngine
    private var folderUri: Uri? = null
    private val checkedTemplates = mutableSetOf<String>()
    private var activeCategory: String = "All"

    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var btnDeploy: MaterialButton
    private lateinit var checkboxContainer: LinearLayout

    private val allTemplates get() = engine.templates

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = DecoyGeneratorEngine(requireContext())
        folderUri = arguments?.getString(ARG_FOLDER_URI)?.let { Uri.parse(it) }
        // Default all checked
        checkedTemplates.addAll(allTemplates.map { it.fileName })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_decoy_studio, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Apply dark/light theme matching the rest of the app
        val themeManager = com.honeyfile.security.auth.ThemeManager(requireContext())
        themeManager.applyInstant(view, dialog?.window, themeManager.isDarkMode)

        progressBar       = view.findViewById(R.id.pbDecoyDeploy)
        tvStatus          = view.findViewById(R.id.tvDecoyStatus)
        btnDeploy         = view.findViewById(R.id.btnDeploySelected)
        checkboxContainer = view.findViewById(R.id.llDecoyCheckboxes)

        // Category filter chips
        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupCategory)
        listOf("All", "PDFs", "Office Docs", "Dev & Database").forEach { label ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                isChecked = label == "All"
            }
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    activeCategory = label
                    rebuildCheckboxes()
                }
            }
            chipGroup.addView(chip)
        }

        // Select all / deselect all
        view.findViewById<MaterialButton>(R.id.btnSelectAll).setOnClickListener {
            val visible = visibleTemplates()
            checkedTemplates.addAll(visible.map { it.fileName })
            rebuildCheckboxes()
        }
        view.findViewById<MaterialButton>(R.id.btnDeselectAll).setOnClickListener {
            val visible = visibleTemplates()
            checkedTemplates.removeAll(visible.map { it.fileName }.toSet())
            rebuildCheckboxes()
        }

        btnDeploy.setOnClickListener { startDeploy() }

        rebuildCheckboxes()
    }

    private fun visibleTemplates(): List<DecoyGeneratorEngine.DecoyTemplate> {
        return if (activeCategory == "All") allTemplates
        else allTemplates.filter { it.category.label == activeCategory }
    }

    private fun rebuildCheckboxes() {
        checkboxContainer.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        val themeManager = com.honeyfile.security.auth.ThemeManager(requireContext())
        visibleTemplates().forEach { template ->
            val row = inflater.inflate(R.layout.item_decoy_template, checkboxContainer, false)

            val cb = row.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbDecoyItem)
            val tvName = row.findViewById<TextView>(R.id.tvDecoyName)
            val tvFile = row.findViewById<TextView>(R.id.tvDecoyFileName)

            cb.isChecked = checkedTemplates.contains(template.fileName)
            tvName.text = "${template.emoji}  ${template.displayName}"
            tvFile.text = template.fileName

            // Tap anywhere on the row to toggle
            row.setOnClickListener {
                cb.isChecked = !cb.isChecked
            }
            cb.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) checkedTemplates.add(template.fileName)
                else checkedTemplates.remove(template.fileName)
            }

            checkboxContainer.addView(row)
            // Re-apply theme colors to this freshly inflated row
            themeManager.applyInstant(row, null, themeManager.isDarkMode)
        }
    }

    private fun startDeploy() {
        val uri = folderUri
        if (uri == null) {
            Toast.makeText(requireContext(), "No monitored directory selected!", Toast.LENGTH_SHORT).show()
            return
        }
        if (checkedTemplates.isEmpty()) {
            Toast.makeText(requireContext(), "Select at least one decoy template!", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedTemplates = allTemplates.filter { checkedTemplates.contains(it.fileName) }

        btnDeploy.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.max = selectedTemplates.size
        progressBar.progress = 0
        tvStatus.visibility = View.VISIBLE
        tvStatus.text = "Generating decoy files…"

        CoroutineScope(Dispatchers.IO).launch {
            var deployed = 0
            var skipped = 0

            val db = AppDatabase.getDatabase(requireContext())
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            selectedTemplates.forEachIndexed { index, template ->
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Generating ${template.displayName}…"
                    progressBar.progress = index
                }

                try {
                    val created = engine.deploy(template, uri)
                    if (created) {
                        deployed++
                        db.logDao().insertLog(
                            AccessLog(
                                file = template.fileName,
                                user = "Admin",
                                action = "DEPLOYED",
                                details = "Decoy honeyfile deployed: ${template.displayName} (${template.fileName})",
                                timestamp = timestamp
                            )
                        )
                    } else {
                        skipped++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to deploy ${template.fileName}", e)
                }
            }

            withContext(Dispatchers.Main) {
                progressBar.progress = selectedTemplates.size
                val msg = when {
                    deployed > 0 && skipped > 0 -> "✅ Deployed $deployed new decoys, $skipped already existed"
                    deployed > 0                 -> "✅ Deployed $deployed decoy honeyfiles into monitored folder 🍯"
                    else                         -> "All selected decoys already exist in folder"
                }
                tvStatus.text = msg
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                btnDeploy.isEnabled = true

                // Auto-dismiss after 1.5s on full success
                if (skipped == 0 && deployed > 0) {
                    view?.postDelayed({ dismissAllowingStateLoss() }, 1500)
                }
            }
        }
    }

    companion object {
        const val TAG = "DecoyStudioDialog"
        private const val ARG_FOLDER_URI = "arg_folder_uri"

        fun newInstance(folderUri: Uri?): DecoyStudioDialogFragment {
            return DecoyStudioDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FOLDER_URI, folderUri?.toString())
                }
            }
        }
    }
}
