package com.example.explorer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.DistrictZone
import com.example.cityengine.model.EnvironmentProp
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.House
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PublicBuilding
import com.example.cityengine.model.RoadSegment
import com.example.core.data.local.ExplorerSnapshotCache
import com.example.core.data.local.LocalCityExplorerRepository
import com.example.core.data.model.CityDataState
import com.example.core.data.model.CityExplorerSnapshot
import com.example.core.data.model.HouseRequestDraft
import com.example.core.data.model.HouseRequestSubmissionResult
import com.example.core.data.model.PublicHouseDetails
import com.example.core.data.repository.CityExplorerRepository
import com.example.core.data.supabase.AndroidConnectivityChecker
import com.example.core.data.supabase.SupabaseCityExplorerRepository
import com.example.core.data.supabase.SupabaseConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class HouseRequestDialogState(
    val isOpen: Boolean = false,
    val isSubmitting: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

data class CityExplorerUiState(
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    val isOffline: Boolean = false,
    val errorMessage: String? = null,
    val offlineMessage: String? = null,
    val scene: CitySceneState? = null,
    val houseDetailsById: Map<String, PublicHouseDetails> = emptyMap(),
    val selectedHouse: PublicHouseDetails? = null,
    val selectedBuilding: PublicBuilding? = null,
    val selectedProp: EnvironmentProp? = null,
    val selectedPlot: Plot? = null,
    val selectedRoad: RoadSegment? = null,
    val selectedRoadCoord: GridCoord? = null,
    val selectedDistrict: DistrictZone? = null,
    val isTestData: Boolean = false,
    val houseRequestState: HouseRequestDialogState = HouseRequestDialogState(),
    val isDistrictsBrowserOpen: Boolean = false
) {
    val hasSelection: Boolean
        get() = selectedHouse != null ||
            selectedBuilding != null ||
            selectedProp != null ||
            selectedPlot != null ||
            selectedRoad != null ||
            selectedRoadCoord != null ||
            selectedDistrict != null

    fun selectedHouseSceneModel(): House? =
        selectedHouse?.let { sel -> scene?.houses?.firstOrNull { it.id == sel.houseId } }

    fun districtForCoord(coord: GridCoord): DistrictZone? =
        scene?.districts?.firstOrNull { it.contains(coord) }

    fun districtById(districtId: String?): DistrictZone? =
        districtId?.let { id -> scene?.districts?.firstOrNull { it.id == id } }
}

class CityExplorerViewModel(
    private val repository: CityExplorerRepository = LocalCityExplorerRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CityExplorerUiState())
    val uiState: StateFlow<CityExplorerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeCitySnapshot().collect { dataState ->
                when (dataState) {
                    is CityDataState.Loading -> _uiState.update {
                        it.copy(isLoading = true, isEmpty = false, errorMessage = null)
                    }
                    is CityDataState.Empty -> _uiState.update {
                        it.copy(
                            isLoading = false, isEmpty = true, isOffline = false,
                            errorMessage = null, offlineMessage = null, scene = null,
                            houseDetailsById = emptyMap(), selectedHouse = null,
                            selectedBuilding = null, selectedProp = null, selectedPlot = null,
                            selectedRoad = null, selectedRoadCoord = null, selectedDistrict = null
                        )
                    }
                    is CityDataState.Error -> _uiState.update {
                        it.copy(isLoading = false, isEmpty = false, isOffline = false, errorMessage = dataState.message)
                    }
                    is CityDataState.Offline -> {
                        val stale = dataState.staleData
                        _uiState.update { current ->
                            if (stale != null) {
                                applySnapshot(current, stale).copy(
                                    isLoading = false, isEmpty = false, isOffline = true,
                                    errorMessage = null, offlineMessage = dataState.message
                                )
                            } else {
                                current.copy(
                                    isLoading = false, isEmpty = false, isOffline = true,
                                    errorMessage = dataState.message, offlineMessage = dataState.message
                                )
                            }
                        }
                    }
                    is CityDataState.Success -> _uiState.update { current ->
                        applySnapshot(current, dataState.data).copy(
                            isLoading = false, isEmpty = false, isOffline = false,
                            errorMessage = null, offlineMessage = null
                        )
                    }
                }
            }
        }
    }

    private fun applySnapshot(
        current: CityExplorerUiState,
        snapshot: CityExplorerSnapshot
    ): CityExplorerUiState {
        val preservedHouse = current.selectedHouse?.houseId?.let { snapshot.houseDetailsById[it] }
        val preservedBuilding = current.selectedBuilding?.id?.let { id -> snapshot.scene.publicBuildings.firstOrNull { it.id == id } }
        val preservedProp = current.selectedProp?.id?.let { id -> snapshot.scene.environmentProps.firstOrNull { it.id == id } }
        val preservedPlot = current.selectedPlot?.id?.let { id -> snapshot.scene.plots.firstOrNull { it.id == id } }
        val preservedRoad = current.selectedRoad?.id?.let { id -> snapshot.scene.roadSegments.firstOrNull { it.id == id } }
        val preservedDistrict = current.selectedDistrict?.id?.let { id -> snapshot.scene.districts.firstOrNull { it.id == id } }

        val sceneWithSelection = snapshot.scene.copy(
            selectedHouseId = preservedHouse?.houseId,
            selectedBuildingId = preservedBuilding?.id,
            selectedPropId = preservedProp?.id,
            selectedPlotId = preservedPlot?.id,
            selectedRoadId = preservedRoad?.id,
            selectedDistrictId = preservedDistrict?.id
        )
        return current.copy(
            scene = sceneWithSelection,
            houseDetailsById = snapshot.houseDetailsById,
            selectedHouse = preservedHouse,
            selectedBuilding = preservedBuilding,
            selectedProp = preservedProp,
            selectedPlot = preservedPlot,
            selectedRoad = preservedRoad,
            selectedRoadCoord = if (preservedRoad != null || current.selectedRoadCoord != null && snapshot.scene.tiles[current.selectedRoadCoord] == CityTileType.ROAD) current.selectedRoadCoord else null,
            selectedDistrict = preservedDistrict,
            isTestData = snapshot.isTestData
        )
    }

    fun onHouseTapped(house: House?) {
        selectHouseById(house?.id)
    }

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
                scene = currentScene.copy(
                    selectedHouseId = resolvedId, selectedBuildingId = null,
                    selectedPropId = null, selectedPlotId = null, selectedRoadId = null, selectedDistrictId = null
                ),
                selectedHouse = details, selectedBuilding = null, selectedProp = null,
                selectedPlot = null, selectedRoad = null, selectedRoadCoord = null, selectedDistrict = null
            )
        }
    }

    fun onBuildingTapped(building: PublicBuilding?) {
        if (building == null) return
        _uiState.update { current ->
            val currentScene = current.scene ?: return@update current
            current.copy(
                scene = currentScene.copy(
                    selectedHouseId = null, selectedBuildingId = building.id,
                    selectedPropId = null, selectedPlotId = null, selectedRoadId = null, selectedDistrictId = null
                ),
                selectedHouse = null, selectedBuilding = building, selectedProp = null,
                selectedPlot = null, selectedRoad = null, selectedRoadCoord = null, selectedDistrict = null
            )
        }
    }

    fun onPropTapped(prop: EnvironmentProp?) {
        if (prop == null) return
        _uiState.update { current ->
            val currentScene = current.scene ?: return@update current
            current.copy(
                scene = currentScene.copy(
                    selectedHouseId = null, selectedBuildingId = null,
                    selectedPropId = prop.id, selectedPlotId = null, selectedRoadId = null, selectedDistrictId = null
                ),
                selectedHouse = null, selectedBuilding = null, selectedProp = prop,
                selectedPlot = null, selectedRoad = null, selectedRoadCoord = null, selectedDistrict = null
            )
        }
    }

    fun onPlotTapped(plot: Plot?) {
        if (plot == null) return
        _uiState.update { current ->
            val currentScene = current.scene ?: return@update current
            current.copy(
                scene = currentScene.copy(
                    selectedHouseId = null, selectedBuildingId = null,
                    selectedPropId = null, selectedPlotId = plot.id, selectedRoadId = null, selectedDistrictId = null
                ),
                selectedHouse = null, selectedBuilding = null, selectedProp = null,
                selectedPlot = plot, selectedRoad = null, selectedRoadCoord = null, selectedDistrict = null
            )
        }
    }

    fun onRoadTapped(road: RoadSegment?, coord: GridCoord) {
        val currentScene = _uiState.value.scene ?: return
        val isRoadTile = road != null || currentScene.tiles[coord] == CityTileType.ROAD
        if (!isRoadTile) {
            clearSelection()
            return
        }
        _uiState.update { current ->
            val scene = current.scene ?: return@update current
            current.copy(
                scene = scene.copy(
                    selectedHouseId = null, selectedBuildingId = null,
                    selectedPropId = null, selectedPlotId = null, selectedRoadId = road?.id, selectedDistrictId = null
                ),
                selectedHouse = null, selectedBuilding = null, selectedProp = null,
                selectedPlot = null, selectedRoad = road, selectedRoadCoord = coord, selectedDistrict = null
            )
        }
    }

    fun onDistrictSelected(district: DistrictZone?) {
        _uiState.update { current ->
            val currentScene = current.scene ?: return@update current
            current.copy(
                scene = currentScene.copy(
                    selectedHouseId = null, selectedBuildingId = null,
                    selectedPropId = null, selectedPlotId = null, selectedRoadId = null, selectedDistrictId = district?.id
                ),
                selectedHouse = null, selectedBuilding = null, selectedProp = null,
                selectedPlot = null, selectedRoad = null, selectedRoadCoord = null, selectedDistrict = district,
                isDistrictsBrowserOpen = false
            )
        }
    }

    fun clearSelection() {
        _uiState.update { current ->
            val currentScene = current.scene ?: return@update current
            current.copy(
                scene = currentScene.copy(
                    selectedHouseId = null, selectedBuildingId = null,
                    selectedPropId = null, selectedPlotId = null, selectedRoadId = null, selectedDistrictId = null
                ),
                selectedHouse = null, selectedBuilding = null, selectedProp = null,
                selectedPlot = null, selectedRoad = null, selectedRoadCoord = null, selectedDistrict = null
            )
        }
    }

    fun openHouseRequestDialog() {
        _uiState.update {
            it.copy(
                houseRequestState = HouseRequestDialogState(isOpen = true),
                isDistrictsBrowserOpen = false
            )
        }
    }

    fun closeHouseRequestDialog() {
        if (_uiState.value.houseRequestState.isSubmitting) return
        _uiState.update { it.copy(houseRequestState = HouseRequestDialogState()) }
    }

    fun submitHouseRequest(
        applicantName: String,
        phone: String?,
        email: String?,
        notes: String?
    ) {
        if (_uiState.value.houseRequestState.isSubmitting) return
        val draft = HouseRequestDraft(
            applicantName = applicantName,
            phone = phone,
            email = email,
            notes = notes
        )
        draft.validate()?.let { err ->
            _uiState.update {
                it.copy(houseRequestState = it.houseRequestState.copy(errorMessage = err, successMessage = null))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    houseRequestState = it.houseRequestState.copy(
                        isSubmitting = true,
                        errorMessage = null,
                        successMessage = null
                    )
                )
            }
            when (val res = repository.submitHouseRequest(draft)) {
                is HouseRequestSubmissionResult.Success -> _uiState.update {
                    it.copy(
                        houseRequestState = HouseRequestDialogState(
                            isOpen = true,
                            isSubmitting = false,
                            successMessage = res.message,
                            errorMessage = null
                        )
                    )
                }
                is HouseRequestSubmissionResult.Failure -> _uiState.update {
                    it.copy(
                        houseRequestState = it.houseRequestState.copy(
                            isSubmitting = false,
                            errorMessage = res.reason,
                            successMessage = null
                        )
                    )
                }
            }
        }
    }

    fun openDistrictsBrowser() {
        _uiState.update { it.copy(isDistrictsBrowserOpen = true) }
    }

    fun closeDistrictsBrowser() {
        _uiState.update { it.copy(isDistrictsBrowserOpen = false) }
    }

    fun retryLoad() {
        viewModelScope.launch { repository.refresh() }
    }

    companion object {
        fun factory(
            context: Context,
            supabaseUrl: String,
            supabaseAnonKey: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val snapshotCache = ExplorerSnapshotCache(
                    File(context.applicationContext.filesDir, "city_explorer_snapshot.json")
                )
                val repo = SupabaseCityExplorerRepository(
                    config = SupabaseConfig(supabaseUrl, supabaseAnonKey),
                    connectivityChecker = AndroidConnectivityChecker(context),
                    snapshotCache = snapshotCache
                )
                return CityExplorerViewModel(repository = repo) as T
            }
        }
    }
}
