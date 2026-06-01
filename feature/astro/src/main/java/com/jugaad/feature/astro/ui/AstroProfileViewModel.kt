package com.jugaad.feature.astro.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jugaad.feature.astro.domain.usecase.CreateProfileUseCase
import com.jugaad.feature.astro.domain.usecase.ProfileInput
import com.jugaad.feature.astro.domain.repository.AstroRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class AstroProfileUiState(
    val id: Long? = null,
    val name: String = "",
    val selectedDate: LocalDate? = null,
    val selectedTime: LocalTime? = null,
    val city: String = "",
    val latitude: String = "",
    val longitude: String = "",
    
    val isSaving: Boolean = false,
    val isLookingUp: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
) {
    val dobLabel: String get() = selectedDate?.format(DateTimeFormatter.ISO_LOCAL_DATE) ?: "Select Date"
    val tobLabel: String get() = selectedTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "Select Time"
    
    val canSave: Boolean get() = name.isNotBlank() && 
            selectedDate != null && 
            selectedTime != null && 
            city.isNotBlank() &&
            latitude.toDoubleOrNull() != null &&
            longitude.toDoubleOrNull() != null
}

@HiltViewModel
class AstroProfileViewModel @Inject constructor(
    private val createProfileUseCase: CreateProfileUseCase,
    private val repository: AstroRepository,
    private val keyProvider: com.jugaad.core.security.keystore.AstroKeyProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(AstroProfileUiState())
    val uiState: StateFlow<AstroProfileUiState> = _uiState.asStateFlow()

    init {
        loadExistingProfile()
    }

    private fun loadExistingProfile() {
        viewModelScope.launch {
            val profiles = repository.observeAllUserProfiles().firstOrNull()
            val profile = profiles?.firstOrNull() ?: return@launch
            
            // Decrypt birth payload to restore state
            val plainBytes = keyProvider.decryptBirthPayload(
                profile.encryptedBirthPayload,
                profile.birthPayloadIv
            )
            val json = JSONObject(String(plainBytes))
            plainBytes.fill(0)

            _uiState.update { state ->
                state.copy(
                    id = profile.id,
                    name = "User", // Name is hashed in DB, could use a placeholder or add a nickname field
                    selectedDate = LocalDate.parse(json.optString("dob", "1990-01-01")),
                    selectedTime = LocalTime.parse(json.optString("tob", "12:00")),
                    city = json.optString("city", ""),
                    latitude = json.optDouble("lat").toString(),
                    longitude = json.optDouble("lon").toString()
                )
            }
        }
    }

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name) }
    fun onCityChange(city: String) = _uiState.update { it.copy(city = city) }
    fun onLatChange(lat: String) = _uiState.update { it.copy(latitude = lat) }
    fun onLonChange(lon: String) = _uiState.update { it.copy(longitude = lon) }

    fun lookupCoordinates() {
        val cityQuery = _uiState.value.city
        if (cityQuery.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLookingUp = true, error = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val encoded = URLEncoder.encode(cityQuery, "UTF-8")
                    val url = URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.setRequestProperty("User-Agent", "JugaadHome/1.0")
                    connection.connectTimeout = 5000
                    
                    val text = connection.inputStream.bufferedReader().readText()
                    val array = JSONArray(text)
                    if (array.length() > 0) {
                        val obj = array.getJSONObject(0)
                        Pair(obj.getString("lat"), obj.getString("lon"))
                    } else null
                }
            }.onSuccess { coords ->
                if (coords != null) {
                    _uiState.update { it.copy(latitude = coords.first, longitude = coords.second, isLookingUp = false) }
                } else {
                    _uiState.update { it.copy(error = "Location not found", isLookingUp = false) }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = "Lookup failed: check connection", isLookingUp = false) }
            }
        }
    }

    fun onDateSelected(millis: Long?) {
        val date = millis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun onTimeSelected(hour: Int, minute: Int) {
        _uiState.update { it.copy(selectedTime = LocalTime.of(hour, minute)) }
    }

    fun saveProfile() {
        val state = _uiState.value
        if (!state.canSave) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching {
                // If we have an existing ID, we should ideally update, 
                // but CreateProfileUseCase currently always creates new.
                // For Phase 5, we'll just allow it to create/replace.
                createProfileUseCase.execute(
                    ProfileInput(
                        name = state.name,
                        dob  = state.selectedDate!!.toString(),
                        tob  = state.selectedTime!!.toString(),
                        city = state.city,
                        lat  = state.latitude.toDouble(),
                        lon  = state.longitude.toDouble()
                    )
                )
                _uiState.update { it.copy(isSaving = false, success = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "Failed to save") }
            }
        }
    }
}
