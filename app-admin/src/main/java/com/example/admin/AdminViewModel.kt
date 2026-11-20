package com.example.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
import com.example.core.data.supabase.AdminDeviceAuthManager
import com.example.core.data.supabase.AdminDeviceAuthState
import com.example.core.data.supabase.AndroidConnectivityChecker
import com.example.core.data.supabase.KeystoreAdminDeviceCredentialStore
import com.example.core.data.supabase.SupabaseCityAdminRepository
import com.example.core.data.supabase.SupabaseConfig
import com.example.core.data.supabase.SupabaseDeviceAuthRepository
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
    val authState: AdminDeviceAuthState = AdminDeviceAuthState.Authorized(
        deviceId = "local-test",
        deviceName = "بيئة الاختبار المحلية"
    ),
    val requiresDeviceAuth: Boolean = false,
    val activeTab: AdminTab = AdminTab.DASHBOARD,
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    val isOffline: Boolean = false,
    val offlineMessage: String? = null,
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
    private val repository: CityAdminRepository = LocalCityAdminRepository(),
    private val authRepository: AdminDeviceAuthManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AdminUiState(
            authState = if (authRepository != null) {
                AdminDeviceAuthState.CheckingDevice
            } else {
                AdminDeviceAuthState.Authorized(
                    deviceId = "local-test",
                    deviceName = "بيئة الاختبار المحلية"
                )
            },
            requiresDeviceAuth = authRepository != null
        )
    )
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        if (authRepository != null) {
            viewModelScope.launch {
                authRepository.authState.collect { auth ->
                    _uiState.update { current ->
                        if (auth !is AdminDeviceAuthState.Authorized) {
                            current.copy(
                                authState = auth,
                                snapshot = null,
                                selectedHouseId = null,
                                selectedPlotId = null
                            )
                        } else {
                            current.copy(authState = auth)
                        }
                    }
                }
            }
            viewModelScope.launch {
                authRepository.verifyDeviceOnStartup()
            }
        }

        viewModelScope.launch {
            repository.observeAdminSnapshot().collect { dataState ->
                when (dataState) {
                    is CityDataState.Loading -> _uiState.update {
                        it.copy(isLoading = true, isEmpty = false, errorMessage = null)
                    }
                    is CityDataState.Empty -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            isEmpty = true,
                            isOffline = false,
                            errorMessage = null,
                            snapshot = null
                        )
                    }
                    is CityDataState.Error -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            isEmpty = false,
                            isOffline = false,
                            errorMessage = dataState.message
                        )
                    }
                    is CityDataState.Offline -> _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            isEmpty = false,
                            isOffline = true,
                            offlineMessage = dataState.message,
                            errorMessage = if (dataState.staleData == null) dataState.message else null,
                            snapshot = dataState.staleData ?: current.snapshot
                        )
                    }
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
                                isEmpty = false,
                                isOffline = false,
                                offlineMessage = null,
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

    fun activateDevice(activationCode: String, deviceName: String) {
        val authRepo = authRepository ?: return
        viewModelScope.launch {
            authRepo.activateDevice(activationCode, deviceName)
        }
    }

    fun retryDeviceVerification() {
        val authRepo = authRepository ?: return
        viewModelScope.launch {
            authRepo.verifyDeviceOnStartup()
        }
    }

    fun beginReEnrollmentFromRevoked() {
        authRepository?.beginReEnrollmentFromRevoked()
    }

    fun retryLoad() {
        viewModelScope.launch {
            val authRepo = authRepository
            if (authRepo != null && !authRepo.isAuthorizedDevice()) {
                authRepo.verifyDeviceOnStartup()
            } else {
                repository.refresh()
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

    companion object {
        fun factory(
            context: Context,
            supabaseUrl: String,
            supabaseAnonKey: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val config = SupabaseConfig(supabaseUrl, supabaseAnonKey)
                val connectivity = AndroidConnectivityChecker(context)
                val credentialStore = KeystoreAdminDeviceCredentialStore(context)
                val authRepo = SupabaseDeviceAuthRepository(
                    config = config,
                    credentialStore = credentialStore,
                    connectivityChecker = connectivity
                )
                val adminRepo = SupabaseCityAdminRepository(
                    config = config,
                    authRepository = authRepo,
                    connectivityChecker = connectivity
                )
                return AdminViewModel(
                    repository = adminRepo,
                    authRepository = authRepo
                ) as T
            }
        }
    }
}
