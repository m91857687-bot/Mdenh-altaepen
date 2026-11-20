package com.example.core.data.repository

import com.example.core.data.model.CityDataState
import com.example.core.data.model.CityExplorerSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction for loading city map layout and public house details.
 * Phase 2 uses a structured local source; Phase 3+ swaps in Supabase without UI or engine changes.
 */
interface CityExplorerRepository {
    fun observeCitySnapshot(): Flow<CityDataState<CityExplorerSnapshot>>
    suspend fun refresh()
}
