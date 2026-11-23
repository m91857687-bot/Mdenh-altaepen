package com.example.core.data.repository

import com.example.cityengine.model.BuildingRotation
import com.example.cityengine.model.CityLightingState
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.HousePalette
import com.example.cityengine.world.ValidationResult
import com.example.core.data.model.AdminCitySnapshot
import com.example.core.data.model.AdminHouseDraft
import com.example.core.data.model.AdminOperationResult
import com.example.core.data.model.CityDataState
import com.example.core.data.model.DistrictDraft
import com.example.core.data.model.EnvironmentPropDraft
import com.example.core.data.model.LightSourceDraft
import com.example.core.data.model.PlotCreateDraft
import com.example.core.data.model.PublicBuildingDraft
import com.example.core.data.model.RoadSegmentDraft
import com.example.core.data.model.TilePaintChange
import kotlinx.coroutines.flow.Flow

/**
 * Contract for city administration mutations (houses + full Phase 2 City Builder operations).
 */
interface CityAdminRepository {
    fun observeAdminSnapshot(): Flow<CityDataState<AdminCitySnapshot>>
    suspend fun refresh() {}

    // Residential houses
    suspend fun addHouse(draft: AdminHouseDraft): AdminOperationResult
    suspend fun updateHouse(houseId: String, draft: AdminHouseDraft): AdminOperationResult
    suspend fun changeHouseColor(houseId: String, palette: HousePalette): AdminOperationResult
    suspend fun moveHouse(houseId: String, targetPlotId: String): AdminOperationResult
    suspend fun deleteHouse(houseId: String): AdminOperationResult
    fun validatePlotForBuild(plotId: String): ValidationResult
    fun validateCoordinateForBuild(coord: GridCoord): ValidationResult

    // Grid & plots
    suspend fun expandCityGrid(newWidth: Int, newHeight: Int): AdminOperationResult
    suspend fun createPlot(draft: PlotCreateDraft): AdminOperationResult
    suspend fun resizePlot(plotId: String, widthCells: Int, heightCells: Int): AdminOperationResult
    suspend fun rotatePlot(plotId: String, rotation: BuildingRotation): AdminOperationResult
    suspend fun movePlot(plotId: String, newCoord: GridCoord): AdminOperationResult
    suspend fun splitPlot(plotId: String, splitHorizontally: Boolean): AdminOperationResult
    suspend fun mergePlots(firstPlotId: String, secondPlotId: String): AdminOperationResult
    suspend fun setPlotAvailability(plotId: String, available: Boolean): AdminOperationResult
    suspend fun assignPlotDistrict(plotId: String, districtId: String?): AdminOperationResult
    suspend fun deletePlot(plotId: String): AdminOperationResult
    suspend fun batchUpdatePlotAvailability(plotIds: List<String>, available: Boolean): AdminOperationResult
    suspend fun batchAssignPlotsDistrict(plotIds: List<String>, districtId: String?): AdminOperationResult
    suspend fun batchDeletePlots(plotIds: List<String>): AdminOperationResult

    // Roads, surfaces, districts
    suspend fun paintTiles(changes: List<TilePaintChange>): AdminOperationResult
    suspend fun upsertRoadSegment(draft: RoadSegmentDraft): AdminOperationResult
    suspend fun deleteRoadSegment(roadId: String, eraseTilesToGrass: Boolean = false): AdminOperationResult
    suspend fun upsertDistrict(draft: DistrictDraft): AdminOperationResult
    suspend fun deleteDistrict(districtId: String): AdminOperationResult

    // Public buildings, environment props & lighting
    suspend fun upsertPublicBuilding(draft: PublicBuildingDraft): AdminOperationResult
    suspend fun deletePublicBuilding(buildingId: String): AdminOperationResult
    suspend fun upsertEnvironmentProp(draft: EnvironmentPropDraft): AdminOperationResult
    suspend fun deleteEnvironmentProp(propId: String): AdminOperationResult
    suspend fun upsertLightSource(draft: LightSourceDraft): AdminOperationResult
    suspend fun deleteLightSource(lightId: String): AdminOperationResult
    suspend fun setLightingState(lightingState: CityLightingState): AdminOperationResult

    // House building requests lifecycle
    suspend fun updateHouseRequestStatus(
        requestId: String,
        status: com.example.core.data.model.HouseRequestStatus,
        adminNotes: String? = null,
        targetPlotId: String? = null
    ): AdminOperationResult

    suspend fun assignRequestPlotAndBuildHouse(
        requestId: String,
        houseDraft: AdminHouseDraft
    ): AdminOperationResult
}
