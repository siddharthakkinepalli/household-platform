package com.household.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.household.app.data.AppDatabase
import com.household.app.data.repository.PantryRepositoryImpl
import com.household.app.domain.models.PantryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StagedItemUi(
    val item: PantryItem,
    val editedName: String,
    val isSelected: Boolean = true
)

data class PantryUiState(
    val items: List<StagedItemUi> = emptyList(),
    val isSaving: Boolean = false,
    val savedCount: Int = 0,
    val isLoading: Boolean = true
)

sealed class PantryIntent {
    data class ToggleItem(val id: Long) : PantryIntent()
    data class EditName(val id: Long, val name: String) : PantryIntent()
    object SelectAll : PantryIntent()
    object DeselectAll : PantryIntent()
    object BulkConfirm : PantryIntent()
}

class PantryViewModel(
    application: Application,
    private val vaultId: Long
) : AndroidViewModel(application) {

    private val repository by lazy {
        PantryRepositoryImpl(AppDatabase.getInstance(getApplication()).pantryDao())
    }

    private val _uiState = MutableStateFlow(PantryUiState())
    val uiState: StateFlow<PantryUiState> = _uiState.asStateFlow()

    init {
        loadStagedItems()
    }

    fun onIntent(intent: PantryIntent) {
        when (intent) {
            is PantryIntent.ToggleItem  -> toggleItem(intent.id)
            is PantryIntent.EditName    -> editName(intent.id, intent.name)
            PantryIntent.SelectAll      -> setAllSelected(true)
            PantryIntent.DeselectAll    -> setAllSelected(false)
            PantryIntent.BulkConfirm    -> confirmSelected()
        }
    }

    private fun loadStagedItems() {
        viewModelScope.launch {
            val items = repository.getStagedItems(vaultId).first()
            _uiState.update { state ->
                state.copy(
                    items = items.map { StagedItemUi(item = it, editedName = it.name) },
                    isLoading = false
                )
            }
        }
    }

    private fun toggleItem(id: Long) {
        _uiState.update { state ->
            state.copy(items = state.items.map { ui ->
                if (ui.item.id == id) ui.copy(isSelected = !ui.isSelected) else ui
            })
        }
    }

    private fun editName(id: Long, name: String) {
        _uiState.update { state ->
            state.copy(items = state.items.map { ui ->
                if (ui.item.id == id) ui.copy(editedName = name) else ui
            })
        }
    }

    private fun setAllSelected(selected: Boolean) {
        _uiState.update { state ->
            state.copy(items = state.items.map { it.copy(isSelected = selected) })
        }
    }

    private fun confirmSelected() {
        val selected = _uiState.value.items.filter { it.isSelected }
        if (selected.isEmpty()) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val toSave = selected.map { ui -> ui.item.copy(name = ui.editedName.trim()) }
            repository.confirmItems(toSave)
            repository.deleteStagedForVault(vaultId)
            _uiState.update { it.copy(isSaving = false, savedCount = selected.size) }
        }
    }

    companion object {
        fun factory(vaultId: Long) = viewModelFactory {
            initializer {
                PantryViewModel(this[APPLICATION_KEY]!!, vaultId)
            }
        }
    }
}
