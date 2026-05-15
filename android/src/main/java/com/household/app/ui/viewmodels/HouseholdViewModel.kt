package com.household.app.ui.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.household.app.data.entities.DocumentAlertEntity
import com.household.app.data.entities.FamilyMemberEntity
import com.household.app.data.entities.PantryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class HouseholdUiState(
    val familyMembers: List<FamilyMemberEntity> = emptyList(),
    val documentAlerts: List<DocumentAlertEntity> = emptyList(),
    val lowStockItems: List<PantryEntity> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

data class HouseholdProfile(
    val name: String = "My Household",
    val membersCount: Int = 1,
    val currency: String = "USD"
)

data class BackupStatus(
    val isEnabled: Boolean = true,
    val lastBackupTime: java.time.LocalDateTime? = null,
    val isPrivacyMode: Boolean = false
)

class HouseholdViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HouseholdUiState())
    val uiState: StateFlow<HouseholdUiState> = _uiState.asStateFlow()

    private val _householdProfile = MutableStateFlow(HouseholdProfile())
    val householdProfile = _householdProfile.asStateFlow()

    // LiveData for household profile
    private val _householdProfileLive = MutableLiveData(HouseholdProfile())
    val householdProfileLive: LiveData<HouseholdProfile> = _householdProfileLive

    // LiveData for backward compatibility with Fragments
    private val _backupStatusLive = MutableLiveData(BackupStatus())
    val backupStatus: LiveData<BackupStatus> = _backupStatusLive

    private val _backupStatus = MutableStateFlow(BackupStatus())
    val backupStatusFlow: StateFlow<BackupStatus> = _backupStatus.asStateFlow()

    private val _timelineLines = MutableStateFlow<List<String>>(emptyList())
    val timelineLines = _timelineLines.asStateFlow()

    // LiveData for timeline
    private val _timelineLinesLive = MutableLiveData<List<String>>()
    val timelineLinesLive: LiveData<List<String>> = _timelineLinesLive

    private val _captureMessage = MutableStateFlow<String?>(null)
    val captureMessage = _captureMessage.asStateFlow()

    // LiveData for capture message
    private val _captureMessageLive = MutableLiveData<String?>()
    val captureMessageLive: LiveData<String?> = _captureMessageLive

    private var familyMembersFlow: kotlinx.coroutines.flow.Flow<List<FamilyMemberEntity>>? = null
    private var documentAlertsFlow: kotlinx.coroutines.flow.Flow<List<DocumentAlertEntity>>? = null
    private var lowStockFlow: kotlinx.coroutines.flow.Flow<List<PantryEntity>>? = null

    init {
        val profile = HouseholdProfile(
            name = "JUGAAD Home",
            membersCount = 1,
            currency = "EUR"
        )
        _householdProfile.value = profile
        _householdProfileLive.value = profile

        val initialBackupStatus = BackupStatus(
            isEnabled = true,
            lastBackupTime = java.time.LocalDateTime.now(),
            isPrivacyMode = true
        )
        _backupStatus.value = initialBackupStatus
        _backupStatusLive.value = initialBackupStatus
        val timeline = listOf(
            "3 PM - Add your first household event",
            "6 PM - Review local-only setup",
            "Tomorrow - Connect your own reminders"
        )
        _timelineLines.value = timeline
        _timelineLinesLive.value = timeline
    }

    fun initializeDataSources(
        familyMembersSource: kotlinx.coroutines.flow.Flow<List<FamilyMemberEntity>>,
        documentAlertsSource: kotlinx.coroutines.flow.Flow<List<DocumentAlertEntity>>,
        lowStockSource: kotlinx.coroutines.flow.Flow<List<PantryEntity>>
    ) {
        viewModelScope.launch {
            try {
                combine(
                    familyMembersSource,
                    documentAlertsSource,
                    lowStockSource
                ) { members, alerts, lowStock ->
                    HouseholdUiState(
                        familyMembers = members,
                        documentAlerts = alerts,
                        lowStockItems = lowStock,
                        isLoading = false
                    )
                }.collect { state ->
                    _uiState.value = state
                    _householdProfile.value = _householdProfile.value.copy(
                        membersCount = state.familyMembers.size.coerceAtLeast(1)
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }

    fun getUrgentAlerts(): List<DocumentAlertEntity> {
        return _uiState.value.documentAlerts.filter { it.daysUntil <= 7 }
    }

    fun getUpcomingAlerts(): List<DocumentAlertEntity> {
        return _uiState.value.documentAlerts.filter { it.daysUntil in 1..30 }
    }

    fun acknowledgeAlert(alertId: Long, action: String? = null) {
        viewModelScope.launch {
            try {
                val currentAlerts = _uiState.value.documentAlerts.toMutableList()
                val index = currentAlerts.indexOfFirst { it.id == alertId }
                if (index >= 0) {
                    currentAlerts[index] = currentAlerts[index].copy(
                        isAcknowledged = true,
                        actionTaken = action
                    )
                    _uiState.value = _uiState.value.copy(
                        documentAlerts = currentAlerts
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun updatePrivacyMode(enabled: Boolean) {
        val current = _backupStatus.value
        _backupStatus.value = current.copy(isPrivacyMode = enabled)
        _backupStatusLive.value = _backupStatus.value
    }

    fun triggerBackup() {
        val current = _backupStatus.value
        _backupStatus.value = current.copy(lastBackupTime = java.time.LocalDateTime.now())
        _backupStatusLive.value = _backupStatus.value
    }

    fun getLastBackupTimeFormatted(): String {
        val lastBackup = _backupStatus.value.lastBackupTime ?: return "Never"
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
        return lastBackup.format(formatter)
    }

    fun processCapture(source: String) {
        val captured = sampleTextForSource(source)
        val now = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        val newTimeline = mutableListOf("$now - $captured").also {
            it.addAll(_timelineLines.value.take(2))
        }
        _timelineLines.value = newTimeline
        val message = "Saved locally from ${source.uppercase()}"
        _captureMessage.value = message
        _captureMessageLive.value = message
    }

    fun refreshTimeline() {
        // Timeline refresh if needed
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