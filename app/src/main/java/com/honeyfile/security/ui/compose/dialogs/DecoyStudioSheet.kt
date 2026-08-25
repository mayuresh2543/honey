package com.honeyfile.security.ui.compose.dialogs

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honeyfile.security.data.AccessLog
import com.honeyfile.security.data.AppDatabase
import com.honeyfile.security.decoy.DecoyGeneratorEngine
import com.honeyfile.security.ui.theme.CyanAccent
import com.honeyfile.security.ui.theme.CyberGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecoyStudioSheet(
    folderUri: Uri?,
    versionName: String = "v2.0.1-beta",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val engine = remember { DecoyGeneratorEngine(context) }
    val allTemplates = remember { engine.templates }

    var activeCategory by remember { mutableStateOf("All") }
    val checkedTemplates = remember { mutableStateListOf<String>().apply { addAll(allTemplates.map { it.fileName }) } }

    var isDeploying by remember { mutableStateOf(false) }
    var deployProgress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("") }

    val categories = listOf("All", "PDFs", "Office Docs", "Dev & Database")
    val visibleTemplates = remember(activeCategory) {
        if (activeCategory == "All") allTemplates
        else allTemplates.filter { it.category.label == activeCategory }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🍯 Decoy Studio",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = versionName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    Text(
                        text = "Deploy multi-format honeypot traps into monitored folder",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Category Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { category ->
                    val isSelected = activeCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { activeCategory = category },
                        label = { Text(category, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberGreen.copy(alpha = 0.2f),
                            selectedLabelColor = CyberGreen
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = CyberGreen
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Selection Controls (Select All / Deselect All)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        visibleTemplates.forEach {
                            if (!checkedTemplates.contains(it.fileName)) checkedTemplates.add(it.fileName)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Select All", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        visibleTemplates.forEach { checkedTemplates.remove(it.fileName) }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Deselect All", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Template Checklist
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visibleTemplates) { template ->
                    val isChecked = checkedTemplates.contains(template.fileName)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                1.dp,
                                if (isChecked) CyberGreen.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                if (isChecked) checkedTemplates.remove(template.fileName)
                                else checkedTemplates.add(template.fileName)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                if (checked) checkedTemplates.add(template.fileName)
                                else checkedTemplates.remove(template.fileName)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = CyberGreen,
                                checkmarkColor = Color.Black
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${template.emoji}  ${template.displayName}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = template.fileName,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Indicator & Status
            if (isDeploying) {
                LinearProgressIndicator(
                    progress = { deployProgress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = CyberGreen,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    color = CyanAccent,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Deploy Action Button
            Button(
                onClick = {
                    if (folderUri == null) {
                        Toast.makeText(context, "No monitored directory selected!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val selected = allTemplates.filter { checkedTemplates.contains(it.fileName) }
                    if (selected.isEmpty()) {
                        Toast.makeText(context, "Select at least one decoy template!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isDeploying = true
                    deployProgress = 0f
                    statusText = "Deploying honeypot decoys..."

                    com.honeyfile.security.scanner.FolderScannerManager.isDeploymentInProgress = true
                    com.honeyfile.security.integrity.HoneyFileObserver.isDeploymentInProgress = true

                    coroutineScope.launch(Dispatchers.IO) {
                        var deployed = 0
                        var skipped = 0
                        val db = AppDatabase.getDatabase(context)
                        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                        try {
                            selected.forEachIndexed { index, template ->
                                withContext(Dispatchers.Main) {
                                    statusText = "Generating ${template.displayName}…"
                                    deployProgress = (index + 1).toFloat() / selected.size
                                }
                                try {
                                    val created = engine.deploy(template, folderUri)
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
                                    // Log and continue
                                }
                            }
                        } finally {
                            delay(1500L)
                            com.honeyfile.security.scanner.FolderScannerManager.isDeploymentInProgress = false
                            com.honeyfile.security.integrity.HoneyFileObserver.isDeploymentInProgress = false
                        }

                        withContext(Dispatchers.Main) {
                            isDeploying = false
                            val msg = when {
                                deployed > 0 && skipped > 0 -> "✅ Deployed $deployed new decoys, $skipped already existed"
                                deployed > 0 -> "✅ Deployed $deployed decoy honeyfiles into folder 🍯"
                                else -> "All selected decoys already exist in folder"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            if (skipped == 0 && deployed > 0) {
                                onDismiss()
                            }
                        }
                    }
                },
                enabled = !isDeploying && folderUri != null,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = com.honeyfile.security.ui.theme.HoneyIcons.ElectricBolt,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Deploy Selected Decoys 🍯",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}
