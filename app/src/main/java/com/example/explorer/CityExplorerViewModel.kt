package com.example.explorer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.House
import com.example.core.data.local.LocalCityExplorerRepository
import com.example.core.data.model.CityDataState
import com.example.core.data.model.CityExplorerSnapshot
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

data class CityExplorerUiState(
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    val isOffline: Boolean = false,
    val errorMessage: String? = null,
    val offlineMessage: String? = null,
    val scene: CitySceneState? = null,
    val houseDetailsById: Map<String, PublicHouseDetails> = emptyMap(),
    val selectedHouse: PublicHouseDetails? = null,
    val isTestData: Boolean = false
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
                        _uiState.update {
                            it.copy(
                                isLoading = true,
                                isEmpty = false,
                                errorMessage = null
                            )
                        }
                    }
                    is CityDataState.Empty -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isEmpty = true,
                                isOffline = false,
                                errorMessage = null,
                                offlineMessage = null,
                                scene = null,
                                houseDetailsById = emptyMap(),
                                selectedHouse = null
                            )
                        }
                    }
                    is CityDataState.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isEmpty = false,
                                isOffline = false,
                                errorMessage = dataState.message
                            )
                        }
                    }
                    is CityDataState.Offline -> {
                        val stale = dataState.staleData
                        _uiState.update { current ->
                            if (stale != null) {
                                applySnapshot(current, stale).copy(
                                    isLoading = false,
                                    isEmpty = false,
                                    isOffline = true,
                                    errorMessage = null,
                                    offlineMessage = dataState.message
                                )
                            } else {
                                current.copy(
                                    isLoading = false,
                                    isEmpty = false,
                                    isOffline = true,
                                    errorMessage = dataState.message,
                                    offlineMessage = dataState.message
                                )
                            }
                        }
                    }
                    is CityDataState.Success -> {
                        _uiState.update { current ->
                            applySnapshot(current, dataState.data).copy(
                                isLoading = false,
                                isEmpty = false,
                                isOffline = false,
                                errorMessage = null,
                                offlineMessage = null
                            )
                        }
                    }
                }
            }
        }
    }

    private fun applySnapshot(
        current: CityExplorerUiState,
        snapshot: CityExplorerSnapshot
    ): CityExplorerUiState {
        val currentSelectedId = current.selectedHouse?.houseId
        val preservedDetails = currentSelectedId?.let { snapshot.houseDetailsById[it] }
        val sceneWithSelection = snapshot.scene.copy(
            selectedHouseId = preservedDetails?.houseId
        )
        return current.copy(
            scene = sceneWithSelection,
            houseDetailsById = snapshot.houseDetailsById,
            selectedHouse = preservedDetails,
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

    companion object {
        fun factory(
            context: Context,
            supabaseUrl: String,
            supabaseAnonKey: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repo = SupabaseCityExplorerRepository(
                    config = SupabaseConfig(supabaseUrl, supabaseAnonKey),
                    connectivityChecker = AndroidConnectivityChecker(context)
                )
                return CityExplorerViewModel(repository = repo) as T
            }
        }
    }
}
