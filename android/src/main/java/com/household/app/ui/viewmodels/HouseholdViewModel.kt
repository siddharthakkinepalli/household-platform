package com.household.app.ui.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class HouseholdProfile(
    val name: String = "My Household",
    val membersCount: Int = 1,
    val currency: String = "USD"
)

data class BackupStatus(
    val isEnabled: Boolean = true,
    val lastBackupTime: LocalDateTime? = null,
    val isPrivacyMode: Boolean = false
)

class HouseholdViewModel : ViewModel() {
    private val _householdProfile = MutableLiveData<HouseholdProfile>()
    val householdProfile: LiveData<HouseholdProfile> = _householdProfile

    private val _backupStatus = MutableLiveData<BackupStatus>()
    val backupStatus: LiveData<BackupStatus> = _backupStatus

    private val _timelineLines = MutableLiveData<List<String>>()
    val timelineLines: LiveData<List<String>> = _timelineLines

    private val _captureMessage = MutableLiveData<String?>()
    val captureMessage: LiveData<String?> = _captureMessage

    private val timelineStore = mutableListOf<String>()

    init {
        _householdProfile.value = HouseholdProfile(
            name = "JUGAAD Home",
            membersCount = 1,
            currency = "EUR"
        )
        _backupStatus.value = BackupStatus(
            isEnabled = true,
            lastBackupTime = LocalDateTime.now(),
            isPrivacyMode = true
        )
        timelineStore.addAll(
            listOf(
            "3 PM - Add your first household event",
            "6 PM - Review local-only setup",
            "Tomorrow - Connect your own reminders"
            )
        )
        _timelineLines.value = timelineStore.take(3)
    }

    fun updatePrivacyMode(enabled: Boolean) {
        val current = _backupStatus.value ?: BackupStatus()
        _backupStatus.value = current.copy(isPrivacyMode = enabled)
    }

    fun triggerBackup() {
        val current = _backupStatus.value ?: BackupStatus()
        _backupStatus.value = current.copy(lastBackupTime = LocalDateTime.now())
    }

    fun getLastBackupTimeFormatted(): String {
        val lastBackup = _backupStatus.value?.lastBackupTime ?: return "Never"
        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
        return lastBackup.format(formatter)
    }

    fun processCapture(source: String) {
        val captured = sampleTextForSource(source)
        val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        timelineStore.add(0, "$now - $captured")
        _timelineLines.value = timelineStore.take(3)
        _captureMessage.value = "Saved locally from ${source.uppercase()}"
    }

    fun refreshTimeline() {
        _timelineLines.value = timelineStore.take(3)
    }

    private fun sampleTextForSource(source: String): String {
        return when (source.lowercase()) {
            "scan" -> "Scanned item saved as a private placeholder"
            "voice" -> "Voice note saved as a private placeholder"
            "email" -> "Email summary saved as a private placeholder"
            else -> "Household item saved as a private placeholder"
        }
    }
}
