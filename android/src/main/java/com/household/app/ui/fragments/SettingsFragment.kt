package com.household.app.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.household.app.R
import com.household.app.data.BackupRestoreManager
import com.household.app.ui.viewmodels.HouseholdViewModel
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private lateinit var textBackupStatusValue: TextView
    private lateinit var textLastBackupValue: TextView
    private lateinit var switchPrivacyMode: Switch
    private lateinit var buttonBackup: Button
    private lateinit var buttonExport: Button
    private lateinit var buttonRestore: Button

    private val viewModel: HouseholdViewModel by viewModels()
    private lateinit var backupManager: BackupRestoreManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        backupManager = BackupRestoreManager(requireContext())

        textBackupStatusValue = view.findViewById(R.id.text_backup_status_value)
        textLastBackupValue = view.findViewById(R.id.text_last_backup_value)
        switchPrivacyMode = view.findViewById(R.id.switch_privacy_mode)
        buttonBackup = view.findViewById(R.id.button_backup)
        buttonExport = view.findViewById(R.id.button_export)
        buttonRestore = view.findViewById(R.id.button_restore)

        // Observe backup status
        viewModel.backupStatus.observe(viewLifecycleOwner, Observer { status ->
            textBackupStatusValue.text = if (status.isEnabled) {
                "Enabled"
            } else {
                "Disabled"
            }
            textLastBackupValue.text = viewModel.getLastBackupTimeFormatted()
            switchPrivacyMode.isChecked = status.isPrivacyMode
        })

        // Privacy mode toggle
        switchPrivacyMode.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updatePrivacyMode(isChecked)
            Toast.makeText(
                requireContext(),
                "Privacy mode ${if (isChecked) "enabled" else "disabled"}",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Backup button - creates local backup
        buttonBackup.setOnClickListener {
            lifecycleScope.launch {
                val result = backupManager.createBackup()
                result.onSuccess { filePath ->
                    viewModel.triggerBackup()
                    Toast.makeText(
                        requireContext(),
                        "Backup created successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                result.onFailure { error ->
                    Toast.makeText(
                        requireContext(),
                        "Backup failed: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // Export button - open file picker to choose backup to share
        buttonExport.setOnClickListener {
            lifecycleScope.launch {
                val backups = backupManager.listBackups()
                if (backups.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "No backups available. Create one first.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Use the latest backup
                val latestBackup = backups.maxByOrNull { it.lastModified() }
                if (latestBackup != null) {
                    val uri = Uri.fromFile(latestBackup)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "Household Backup - ${latestBackup.name}")
                    }
                    startActivity(Intent.createChooser(intent, "Share backup"))
                }
            }
        }

        // Restore button - open file picker
        buttonRestore.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "Select backup file to restore")
            }
            try {
                startActivityForResult(intent, REQUEST_RESTORE_FILE)
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Unable to open file picker: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        view.findViewById<Button>(R.id.button_open_import).setOnClickListener {
            findNavController().navigate(R.id.importCsvFragment)
        }
        view.findViewById<Button>(R.id.button_open_family).setOnClickListener {
            findNavController().navigate(R.id.familyFragment)
        }
        view.findViewById<Button>(R.id.button_open_timeline).setOnClickListener {
            findNavController().navigate(R.id.timelineFragment)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_RESTORE_FILE && data != null) {
            val uri = data.data
            if (uri != null) {
                lifecycleScope.launch {
                    try {
                        val inputStream = requireContext().contentResolver.openInputStream(uri)
                        val tempFile = java.io.File(requireContext().cacheDir, "restore_backup.json")
                        inputStream?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        val result = backupManager.restoreBackup(tempFile.absolutePath)
                        result.onSuccess {
                            Toast.makeText(
                                requireContext(),
                                "Restore completed successfully. Please restart the app.",
                                Toast.LENGTH_LONG
                            ).show()
                            tempFile.delete()
                        }
                        result.onFailure { error ->
                            Toast.makeText(
                                requireContext(),
                                "Restore failed: ${error.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                            tempFile.delete()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            requireContext(),
                            "Error reading backup file: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    companion object {
        private const val REQUEST_RESTORE_FILE = 1001
    }
}
