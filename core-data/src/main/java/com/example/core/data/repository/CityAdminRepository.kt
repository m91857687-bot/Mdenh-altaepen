package com.example.core.data.repository

import com.example.core.data.model.AdminCitySnapshot
import com.example.core.data.model.AdminHouseDraft
import com.example.core.data.model.AdminOperationResult
import com.example.core.data.model.CityDataState
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.HousePalette
import com.example.cityengine.world.ValidationResult
import kotlinx.coroutines.flow.Flow

/**
 * Contract for city administration mutations.
 * Phase 3 provides a local in-memory test implementation; Phase 4 replaces it with Supabase
 * backed by server-side authentication and Row Level Security without modifying Admin UI.
 */
interface CityAdminRepository {
    fun observeAdminSnapshot(): Flow<CityDataState<AdminCitySnapshot>>
    suspend fun refresh() {}
    suspend fun addHouse(draft: AdminHouseDraft): AdminOperationResult
    suspend fun updateHouse(houseId: String, draft: AdminHouseDraft): AdminOperationResult
    suspend fun changeHouseColor(houseId: String, palette: HousePalette): AdminOperationResult
    suspend fun moveHouse(houseId: String, targetPlotId: String): AdminOperationResult
    suspend fun deleteHouse(houseId: String): AdminOperationResult
    fun validatePlotForBuild(plotId: String): ValidationResult
    fun validateCoordinateForBuild(coord: GridCoord): ValidationResult
}
