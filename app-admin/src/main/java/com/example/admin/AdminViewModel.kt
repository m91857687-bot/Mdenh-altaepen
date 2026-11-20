package com.example.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.House
import com.example.cityengine.model.HousePalette
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.world.ValidationResult
import com.example.core.data.local.LocalCityAdminRepository
import com.example.core.data.model.AdminCitySnapshot
import com.example.core.data.model.AdminHouseDraft
import com.example.core.data.model.AdminOperationResult
import com.example.core.data.model.CityDataState
import com.example.core.data.repository.CityAdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AdminTab {
    DASHBOARD,
    HOUSES,
    PLOTS,
    MAP
}

enum class PlotFilter {
    ALL,
    AVAILABLE,
    OCCUPIED
}

data class AdminUiState(
    val activeTab: AdminTab = AdminTab.DASHBOARD,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val snapshot: AdminCitySnapshot? = null,
    val houseSearchQuery: String = "",
    val plotFilter: PlotFilter = PlotFilter.ALL,
    val selectedHouseId: String? = null,
    val selectedPlotId: String? = null,
    val statusFeedback: String? = null
) {
    val sceneWithSelection: CitySceneState?
        get() = snapshot?.scene?.copy(
            selectedHouseId = selectedHouseId,
            selectedPlotId = selectedPlotId
        )

    val filteredHouses: List<House>
        get() {
            val all = snapshot?.scene?.houses.orEmpty()
            val q = houseSearchQuery.trim()
            if (q.isEmpty()) return all
            return all.filter { house ->
                house.ownerName.contains(q, ignoreCase = true) ||
                    house.plotId.contains(q, ignoreCase = true) ||
                    house.flag.countryName.contains(q, ignoreCase = true)
            }
        }

    val filteredPlots: List<Plot>
        get() {
            val all = snapshot?.plots.orEmpty()
            return when (plotFilter) {
                PlotFilter.ALL -> all
                PlotFilter.AVAILABLE -> all.filter { it.status == PlotStatus.AVAILABLE }
                PlotFilter.OCCUPIED -> all.filter { it.status == PlotStatus.OCCUPIED }
            }
        }

    val availablePlots: List<Plot>
        get() = snapshot?.plots.orEmpty().filter { it.status == PlotStatus.AVAILABLE }

    val selectedHouse: House?
        get() = selectedHouseId?.let { id -> snapshot?.scene?.houses?.firstOrNull { it.id == id } }

    val selectedPlot: Plot?
        get() = selectedPlotId?.let { id -> snapshot?.plots?.firstOrNull { it.id == id } }
}

class AdminViewModel(
    private val repository: CityAdminRepository = LocalCityAdminRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAdminSnapshot().collect { dataState ->
                when (dataState) {
                    is CityDataState.Loading -> _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                    is CityDataState.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = dataState.message) }
                    is CityDataState.Success -> {
                        val snap = dataState.data
                        _uiState.update { current ->
                            val keptHouseId = current.selectedHouseId?.takeIf { id ->
                                snap.scene.houses.any { it.id == id }
                            }
                            val keptPlotId = current.selectedPlotId?.takeIf { id ->
                                snap.plots.any { it.id == id }
                            }
                            current.copy(
                                isLoading = false,
                                errorMessage = null,
                                snapshot = snap,
                                selectedHouseId = keptHouseId,
                                selectedPlotId = keptPlotId
                            )
                        }
                    }
                }
            }
        }
    }

    fun selectTab(tab: AdminTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(houseSearchQuery = query) }
    }

    fun setPlotFilter(filter: PlotFilter) {
        _uiState.update { it.copy(plotFilter = filter) }
    }

    fun focusHouseOnMap(houseId: String) {
        _uiState.update {
            it.copy(
                activeTab = AdminTab.MAP,
                selectedHouseId = houseId,
                selectedPlotId = null
            )
        }
    }

    fun focusPlotOnMap(plotId: String) {
        val plot = _uiState.value.snapshot?.plots?.firstOrNull { it.id == plotId }
        _uiState.update {
            it.copy(
                activeTab = AdminTab.MAP,
                selectedPlotId = plotId,
                selectedHouseId = plot?.houseId
            )
        }
    }

    fun onMapHouseTapped(house: House?) {
        _uiState.update {
            it.copy(
                selectedHouseId = house?.id,
                selectedPlotId = house?.plotId
            )
        }
    }

    fun onMapPlotTapped(plot: Plot?) {
        _uiState.update {
            it.copy(
                selectedPlotId = plot?.id,
                selectedHouseId = plot?.houseId
            )
        }
    }

    fun clearMapSelection() {
        _uiState.update { it.copy(selectedHouseId = null, selectedPlotId = null) }
    }

    fun validatePlotForBuild(plotId: String): ValidationResult = repository.validatePlotForBuild(plotId)

    fun validateCoordinateForBuild(coord: GridCoord): ValidationResult = repository.validateCoordinateForBuild(coord)

    fun addHouse(draft: AdminHouseDraft, onComplete: (AdminOperationResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.addHouse(draft)
            handleOperationResult(result)
            onComplete(result)
        }
    }

    fun updateHouse(houseId: String, draft: AdminHouseDraft, onComplete: (AdminOperationResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.updateHouse(houseId, draft)
            handleOperationResult(result)
            onComplete(result)
        }
    }

    fun changeHouseColor(houseId: String, palette: HousePalette, onComplete: (AdminOperationResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.changeHouseColor(houseId, palette)
            handleOperationResult(result)
            onComplete(result)
        }
    }

    fun moveHouse(houseId: String, targetPlotId: String, onComplete: (AdminOperationResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.moveHouse(houseId, targetPlotId)
            handleOperationResult(result)
            onComplete(result)
        }
    }

    fun deleteHouse(houseId: String, onComplete: (AdminOperationResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.deleteHouse(houseId)
            handleOperationResult(result)
            onComplete(result)
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(statusFeedback = null) }
    }

    private fun handleOperationResult(result: AdminOperationResult) {
        when (result) {
            is AdminOperationResult.Success -> {
                _uiState.update {
                    it.copy(
                        statusFeedback = result.message,
                        selectedHouseId = result.affectedHouseId ?: it.selectedHouseId,
                        selectedPlotId = result.affectedPlotId ?: it.selectedPlotId
                    )
                }
            }
            is AdminOperationResult.Failure -> {
                _uiState.update { it.copy(statusFeedback = result.reason) }
            }
        }
    }
}
