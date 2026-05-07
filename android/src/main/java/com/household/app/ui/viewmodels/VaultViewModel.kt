package com.household.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.text.Text
import com.household.app.data.AppDatabase
import com.household.app.data.refiner.toVisionTextPayload
import com.household.app.data.refiner.WeightedReceiptRefiner
import com.household.app.data.repository.ExpenseRepositoryImpl
import com.household.app.domain.models.Expense
import com.household.app.domain.models.RefinedScan
import com.household.app.domain.services.VisionTextPayload
import com.household.app.domain.usecases.GetPaperclipCandidatesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

sealed interface VaultUiState {
    data object Idle : VaultUiState
    data class ConfirmScan(
        val refinedScan: RefinedScan,
        val candidates: List<Expense>
    ) : VaultUiState
}

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = getApplication<Application>()
    private val useCase by lazy {
        val dao = AppDatabase.getInstance(appContext).walletTransactionDao()
        GetPaperclipCandidatesUseCase(ExpenseRepositoryImpl(dao))
    }
    private val receiptRefiner = WeightedReceiptRefiner()

    private val _uiState = MutableStateFlow<VaultUiState>(VaultUiState.Idle)
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    fun processScanResult(visionText: Text) {
        processScanResult(visionText.toVisionTextPayload())
    }

    fun processScanResult(visionText: VisionTextPayload) {
        viewModelScope.launch {
            val refinedScan = withContext(Dispatchers.Default) {
                receiptRefiner.refine(visionText)
            }

            val amount = refinedScan.amount.value ?: 0.0
            val date = refinedScan.date.value ?: LocalDate.now()

            val matches = withContext(Dispatchers.IO) {
                useCase.execute(amount = amount, date = date)
            }

            _uiState.value = VaultUiState.ConfirmScan(refinedScan, matches)
        }
    }

    fun dismissConfirmation() {
        _uiState.value = VaultUiState.Idle
    }
}
