package com.example.core.data.repository

import com.example.core.data.model.CityDataState
import com.example.core.data.model.CityExplorerSnapshot
import com.example.core.data.model.HouseRequestDraft
import com.example.core.data.model.HouseRequestSubmissionResult
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction for loading city map layout, public house/building details,
 * and submitting house building requests without exposing other citizens' data.
 */
interface CityExplorerRepository {
    fun observeCitySnapshot(): Flow<CityDataState<CityExplorerSnapshot>>
    suspend fun refresh()
    suspend fun submitHouseRequest(draft: HouseRequestDraft): HouseRequestSubmissionResult
}
