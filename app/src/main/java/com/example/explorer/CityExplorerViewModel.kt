package com.example.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.House
import com.example.core.data.local.LocalCityExplorerRepository
import com.example.core.data.model.CityDataState
import com.example.core.data.model.PublicHouseDetails
import com.example.core.data.repository.CityExplorerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CityExplorerUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val scene: CitySceneState? = null,
    val houseDetailsById: Map<String, PublicHouseDetails> = emptyMap(),
    val selectedHouse: PublicHouseDetails? = null,
    val isTestData: Boolean = true
)

class CityExplorerViewModel(
    private val repository: CityExplorerRepository = LocalCityExplorerRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CityExplorerUiState())
    val uiState: StateFlow<CityExplorerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeCitySnapshot().collect { dataState ->
                when (dataState) {
                    is CityDataState.Loading -> {
                        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                    }
                    is CityDataState.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = dataState.message
                            )
                        }
                    }
                    is CityDataState.Success -> {
                        val snapshot = dataState.data
                        _uiState.update { current ->
                            val currentSelectedId = current.selectedHouse?.houseId
                            val preservedDetails = currentSelectedId?.let { snapshot.houseDetailsById[it] }
                            val sceneWithSelection = snapshot.scene.copy(
                                selectedHouseId = preservedDetails?.houseId
                            )
                            current.copy(
                                isLoading = false,
                                errorMessage = null,
                                scene = sceneWithSelection,
                                houseDetailsById = snapshot.houseDetailsById,
                                selectedHouse = preservedDetails,
                                isTestData = snapshot.isTestData
                            )
                        }
                    }
                }
            }
        }
    }

    fun onHouseTapped(house: House?) {
        selectHouseById(house?.id)
    }

    /**
     * Selects a house by ID and resolves its privacy-filtered public details.
     * Kept clean for both canvas taps and future search-result focus integration.
     */
    fun selectHouseById(houseId: String?) {
        _uiState.update { current ->
            val currentScene = current.scene ?: return@update current
            if (houseId == null) {
                return@update current.copy(
                    scene = currentScene.copy(selectedHouseId = null),
                    selectedHouse = null
                )
            }
            val details = current.houseDetailsById[houseId]
            val resolvedId = if (details != null) houseId else null
            current.copy(
                scene = currentScene.copy(selectedHouseId = resolvedId),
                selectedHouse = details
            )
        }
    }

    fun clearSelection() {
        selectHouseById(null)
    }

    fun retryLoad() {
        viewModelScope.launch {
            repository.refresh()
        }
    }
}
