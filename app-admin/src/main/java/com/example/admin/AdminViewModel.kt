package com.example.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cityengine.model.BuildingRotation
import com.example.cityengine.model.CityLightingState
import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.DistrictZone
import com.example.cityengine.model.EnvironmentProp
import com.example.cityengine.model.EnvironmentPropType
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.GridFootprint
import com.example.cityengine.model.House
import com.example.cityengine.model.HousePalette
import com.example.cityengine.model.LightSourceType
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.model.PublicBuilding
import com.example.cityengine.model.PublicBuildingType
import com.example.cityengine.model.RoadSegment
import com.example.cityengine.world.ValidationResult
import com.example.core.data.history.CityEditorHistoryState
import com.example.core.data.local.LocalCityAdminRepository
import com.example.core.data.model.AdminCitySnapshot
import com.example.core.data.model.AdminHouseDraft
import com.example.core.data.model.AdminOperationResult
import com.example.core.data.model.CityDataState
import com.example.core.data.model.DistrictDraft
import com.example.core.data.model.EnvironmentPropDraft
import com.example.core.data.model.HouseRequest
import com.example.core.data.model.HouseRequestStatus
import com.example.core.data.model.LightSourceDraft
import com.example.core.data.model.PublicBuildingDraft
import com.example.core.data.model.RoadSegmentDraft
import com.example.core.data.model.TilePaintChange
import com.example.core.data.repository.CityAdminRepository
import com.example.core.data.supabase.AdminAuthManager
import com.example.core.data.supabase.AdminAuthState
import com.example.core.data.supabase.AndroidConnectivityChecker
import com.example.core.data.supabase.SupabaseAdminAuthRepository
import com.example.core.data.supabase.SupabaseCityAdminRepository
import com.example.core.data.supabase.SupabaseConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AdminTab { DASHBOARD, HOUSES, PLOTS, REQUESTS, MAP }
enum class PlotFilter { ALL, AVAILABLE, OCCUPIED, UNAVAILABLE }

data class AdminUiState(
    val authState: AdminAuthState = AdminAuthState.AuthenticatedAdmin("local-test", "local-test@madina.local", "بيئة الاختبار المحلية"),
    val requiresAuth: Boolean = false,
    val activeTab: AdminTab = AdminTab.DASHBOARD,
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    val isOffline: Boolean = false,
    val offlineMessage: String? = null,
    val errorMessage: String? = null,
    val snapshot: AdminCitySnapshot? = null,
    val houseSearchQuery: String = "",
    val plotFilter: PlotFilter = PlotFilter.ALL,
    val requestFilter: HouseRequestStatus? = null,
    val selectedHouseId: String? = null,
    val selectedPlotId: String? = null,
    val statusFeedback: String? = null,
    val editor: AdminEditorUiState = AdminEditorUiState(),
    val historyState: CityEditorHistoryState = CityEditorHistoryState()
) {
    val sceneWithSelection: CitySceneState?
        get() = snapshot?.scene?.copy(
            selectedHouseId = selectedHouseId,
            selectedPlotId = selectedPlotId,
            selectedBuildingId = editor.selectedBuildingId,
            selectedDistrictId = editor.selectedDistrictId,
            selectedPropId = editor.selectedPropId,
            selectedRoadId = editor.selectedRoadId,
            multiSelectedPlotIds = editor.multiSelectedPlotIds,
            multiSelectedCoords = editor.multiSelectedCoords,
            editorPreview = editor.pendingPlacement?.overlay
        )

    val filteredHouses: List<House>
        get() {
            val all = snapshot?.scene?.houses.orEmpty()
            val q = houseSearchQuery.trim()
            if (q.isEmpty()) return all
            return all.filter { h ->
                h.ownerName.contains(q, true) || h.plotId.contains(q, true) || h.flag.countryName.contains(q, true)
            }
        }

    val filteredPlots: List<Plot>
        get() = when (plotFilter) {
            PlotFilter.ALL -> snapshot?.plots.orEmpty()
            PlotFilter.AVAILABLE -> snapshot?.plots.orEmpty().filter { it.status == PlotStatus.AVAILABLE }
            PlotFilter.OCCUPIED -> snapshot?.plots.orEmpty().filter { it.status == PlotStatus.OCCUPIED }
            PlotFilter.UNAVAILABLE -> snapshot?.plots.orEmpty().filter { it.status == PlotStatus.UNAVAILABLE }
        }

    val availablePlots: List<Plot> get() = snapshot?.plots.orEmpty().filter { it.status == PlotStatus.AVAILABLE }
    val filteredRequests: List<HouseRequest>
        get() {
            val all = snapshot?.houseRequests.orEmpty()
            return requestFilter?.let { filter -> all.filter { it.status == filter } } ?: all
        }
    val pendingRequestsCount: Int
        get() = snapshot?.houseRequests.orEmpty().count {
            it.status != HouseRequestStatus.COMPLETED && it.status != HouseRequestStatus.REJECTED
        }
    val selectedHouse: House? get() = selectedHouseId?.let { id -> snapshot?.scene?.houses?.firstOrNull { it.id == id } }
    val selectedPlot: Plot? get() = selectedPlotId?.let { id -> snapshot?.plots?.firstOrNull { it.id == id } }
    val selectedBuilding: PublicBuilding? get() = editor.selectedBuildingId?.let { id -> snapshot?.scene?.publicBuildings?.firstOrNull { it.id == id } }
    val selectedProp: EnvironmentProp? get() = editor.selectedPropId?.let { id -> snapshot?.scene?.environmentProps?.firstOrNull { it.id == id } }
    val selectedRoad: RoadSegment? get() = editor.selectedRoadId?.let { id -> snapshot?.scene?.roadSegments?.firstOrNull { it.id == id } }
    val selectedDistrict: DistrictZone? get() = editor.selectedDistrictId?.let { id -> snapshot?.scene?.districts?.firstOrNull { it.id == id } }
    val hasInspectorSelection: Boolean
        get() = selectedHouse != null || selectedPlot != null || selectedBuilding != null ||
            selectedProp != null || selectedRoad != null || editor.selectedRoadCoord != null || selectedDistrict != null
}

class AdminViewModel(
    private val repository: CityAdminRepository = LocalCityAdminRepository(),
    private val authRepository: AdminAuthManager? = null
) : ViewModel() {

    private val editorDelegate = AdminEditorDelegate(repository)

    private val _uiState = MutableStateFlow(
        AdminUiState(
            authState = if (authRepository != null) AdminAuthState.CheckingSession
            else AdminAuthState.AuthenticatedAdmin("local-test", "local-test@madina.local", "بيئة الاختبار المحلية"),
            requiresAuth = authRepository != null
        )
    )
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            editorDelegate.historyManager.state.collect { hist ->
                _uiState.update { it.copy(historyState = hist) }
            }
        }
        if (authRepository != null) {
            viewModelScope.launch {
                authRepository.authState.collect { auth ->
                    _uiState.update { cur ->
                        if (auth !is AdminAuthState.AuthenticatedAdmin) {
                            editorDelegate.historyManager.clear()
                            cur.copy(authState = auth, snapshot = null, selectedHouseId = null, selectedPlotId = null, editor = AdminEditorUiState())
                        } else cur.copy(authState = auth)
                    }
                }
            }
            viewModelScope.launch { authRepository.restoreAndVerifySession() }
        }

        viewModelScope.launch {
            repository.observeAdminSnapshot().collect { dataState ->
                when (dataState) {
                    is CityDataState.Loading -> _uiState.update { it.copy(isLoading = true, isEmpty = false, errorMessage = null) }
                    is CityDataState.Empty -> _uiState.update { it.copy(isLoading = false, isEmpty = true, isOffline = false, errorMessage = null, snapshot = null) }
                    is CityDataState.Error -> _uiState.update { it.copy(isLoading = false, isEmpty = false, isOffline = false, errorMessage = dataState.message) }
                    is CityDataState.Offline -> _uiState.update { cur ->
                        val ok = !cur.requiresAuth || cur.authState is AdminAuthState.AuthenticatedAdmin
                        cur.copy(isLoading = false, isEmpty = false, isOffline = true, offlineMessage = dataState.message, errorMessage = if (dataState.staleData == null) dataState.message else null, snapshot = if (ok) dataState.staleData ?: cur.snapshot else null)
                    }
                    is CityDataState.Success -> _uiState.update { cur ->
                        val snap = dataState.data
                        val ok = !cur.requiresAuth || cur.authState is AdminAuthState.AuthenticatedAdmin
                        val keptHouse = if (ok) cur.selectedHouseId?.takeIf { id -> snap.scene.houses.any { it.id == id } } else null
                        val keptPlot = if (ok) cur.selectedPlotId?.takeIf { id -> snap.plots.any { it.id == id } } else null
                        val keptBld = if (ok) cur.editor.selectedBuildingId?.takeIf { id -> snap.scene.publicBuildings.any { it.id == id } } else null
                        val keptProp = if (ok) cur.editor.selectedPropId?.takeIf { id -> snap.scene.environmentProps.any { it.id == id } } else null
                        val keptDist = if (ok) cur.editor.selectedDistrictId?.takeIf { id -> snap.scene.districts.any { it.id == id } } else null
                        val keptMulti = cur.editor.multiSelectedPlotIds.filterTo(mutableSetOf()) { id -> snap.plots.any { it.id == id } }
                        cur.copy(
                            isLoading = false, isEmpty = false, isOffline = false, offlineMessage = null, errorMessage = null,
                            snapshot = if (ok) snap else null, selectedHouseId = keptHouse, selectedPlotId = keptPlot,
                            editor = cur.editor.copy(
                                selectedBuildingId = keptBld, selectedPropId = keptProp, selectedDistrictId = keptDist,
                                multiSelectedPlotIds = keptMulti,
                                multiSelectedCoords = snap.plots.filter { it.id in keptMulti }.flatMap { it.occupiedCoords() }.toSet()
                            )
                        )
                    }
                }
            }
        }
    }

    // Auth & core navigation
    fun signIn(email: String, password: String) { authRepository?.let { viewModelScope.launch { it.signIn(email, password) } } }
    fun signOut() { authRepository?.let { viewModelScope.launch { it.signOut() } } }
    fun retrySessionRestore() { authRepository?.let { viewModelScope.launch { it.restoreAndVerifySession() } } }
    fun retryLoad() { viewModelScope.launch { if (authRepository != null && !authRepository.isAuthenticatedAdmin()) authRepository.restoreAndVerifySession() else repository.refresh() } }
    fun selectTab(tab: AdminTab) { _uiState.update { it.copy(activeTab = tab) } }
    fun updateSearchQuery(query: String) { _uiState.update { it.copy(houseSearchQuery = query) } }
    fun setPlotFilter(filter: PlotFilter) { _uiState.update { it.copy(plotFilter = filter) } }
    fun setRequestFilter(filter: HouseRequestStatus?) { _uiState.update { it.copy(requestFilter = filter) } }
    fun clearFeedback() { _uiState.update { it.copy(statusFeedback = null) } }

    fun focusHouseOnMap(houseId: String) = _uiState.update {
        it.copy(activeTab = AdminTab.MAP, selectedHouseId = houseId, selectedPlotId = null, editor = it.editor.copy(category = EditorToolCategory.SELECT, subTool = EditorSubTool.SELECT_INSPECT, selectedBuildingId = null, selectedPropId = null, selectedRoadId = null, selectedRoadCoord = null, selectedDistrictId = null, pendingPlacement = null))
    }

    fun focusPlotOnMap(plotId: String) {
        val plot = _uiState.value.snapshot?.plots?.firstOrNull { it.id == plotId }
        _uiState.update {
            it.copy(activeTab = AdminTab.MAP, selectedPlotId = plotId, selectedHouseId = plot?.houseId, editor = it.editor.copy(category = EditorToolCategory.SELECT, subTool = EditorSubTool.SELECT_INSPECT, selectedBuildingId = null, selectedPropId = null, selectedRoadId = null, selectedRoadCoord = null, selectedDistrictId = null, pendingPlacement = null))
        }
    }

    // Map inspector selection handlers
    fun onMapHouseTapped(house: House?) = _uiState.update {
        it.copy(selectedHouseId = house?.id, selectedPlotId = house?.plotId, editor = it.editor.copy(selectedBuildingId = null, selectedPropId = null, selectedRoadId = null, selectedRoadCoord = null, selectedDistrictId = null))
    }
    fun onMapPlotTapped(plot: Plot?) = _uiState.update {
        it.copy(selectedPlotId = plot?.id, selectedHouseId = plot?.houseId, editor = it.editor.copy(selectedBuildingId = null, selectedPropId = null, selectedRoadId = null, selectedRoadCoord = null, selectedDistrictId = null))
    }
    fun onMapBuildingTapped(building: PublicBuilding?) = _uiState.update {
        it.copy(selectedHouseId = null, selectedPlotId = null, editor = it.editor.copy(selectedBuildingId = building?.id, selectedPropId = null, selectedRoadId = null, selectedRoadCoord = null, selectedDistrictId = null))
    }
    fun onMapPropTapped(prop: EnvironmentProp?) = _uiState.update {
        it.copy(selectedHouseId = null, selectedPlotId = null, editor = it.editor.copy(selectedPropId = prop?.id, selectedBuildingId = null, selectedRoadId = null, selectedRoadCoord = null, selectedDistrictId = null))
    }
    fun onMapRoadTapped(road: RoadSegment?, coord: GridCoord) {
        val tile = _uiState.value.snapshot?.scene?.tiles?.get(coord)
        val hasRoadOrEditableTile = road != null || (tile != null && tile != CityTileType.GRASS)
        _uiState.update {
            it.copy(selectedHouseId = null, selectedPlotId = null, editor = it.editor.copy(selectedRoadId = road?.id, selectedRoadCoord = if (hasRoadOrEditableTile) coord else null, selectedBuildingId = null, selectedPropId = null, selectedDistrictId = null))
        }
    }
    fun onMapDistrictSelected(district: DistrictZone?) = _uiState.update {
        it.copy(selectedHouseId = null, selectedPlotId = null, editor = it.editor.copy(selectedDistrictId = district?.id, selectedBuildingId = null, selectedPropId = null, selectedRoadId = null, selectedRoadCoord = null))
    }
    fun clearMapSelection() = _uiState.update {
        it.copy(selectedHouseId = null, selectedPlotId = null, editor = it.editor.copy(selectedBuildingId = null, selectedPropId = null, selectedRoadId = null, selectedRoadCoord = null, selectedDistrictId = null, pendingPlacement = null))
    }

    // Editor toolbar & tools
    fun selectEditorCategory(category: EditorToolCategory) {
        val defaultTool = when (category) {
            EditorToolCategory.SELECT -> EditorSubTool.SELECT_INSPECT
            EditorToolCategory.PLOTS -> EditorSubTool.PLOT_CREATE
            EditorToolCategory.ROADS -> EditorSubTool.ROAD_PAINT
            EditorToolCategory.DISTRICTS -> EditorSubTool.DISTRICT_PLACE
            EditorToolCategory.BUILDINGS -> EditorSubTool.PUBLIC_BUILDING_PLACE
            EditorToolCategory.PROPS_LIGHTS -> EditorSubTool.PROP_PLACE
        }
        selectEditorSubTool(defaultTool)
    }

    fun selectEditorSubTool(subTool: EditorSubTool) = _uiState.update { cur ->
        val (defW, defH) = when (subTool) {
            EditorSubTool.DISTRICT_PLACE -> 4 to 4
            EditorSubTool.ROAD_SEGMENT_PLACE -> 3 to 1
            EditorSubTool.PUBLIC_BUILDING_PLACE -> cur.editor.selectedBuildingType.defaultWidth to cur.editor.selectedBuildingType.defaultHeight
            else -> 1 to 1
        }
        cur.copy(
            selectedHouseId = null, selectedPlotId = null,
            editor = cur.editor.copy(
                category = subTool.category, subTool = subTool, draftWidth = defW, draftHeight = defH,
                draftRotation = BuildingRotation.DEG_0, pendingPlacement = null,
                movingPlotId = if (subTool == EditorSubTool.PLOT_MOVE) cur.editor.movingPlotId else null,
                movingBuildingId = if (subTool == EditorSubTool.PUBLIC_BUILDING_MOVE) cur.editor.movingBuildingId else null,
                selectedBuildingId = null, selectedPropId = null, selectedRoadId = null, selectedRoadCoord = null, selectedDistrictId = null
            )
        )
    }

    fun selectBuildingType(type: PublicBuildingType) = _uiState.update { cur ->
        val ed = cur.editor.copy(selectedBuildingType = type, draftWidth = type.defaultWidth, draftHeight = type.defaultHeight, subTool = EditorSubTool.PUBLIC_BUILDING_PLACE, category = EditorToolCategory.BUILDINGS)
        val refreshed = cur.editor.pendingPlacement?.let { p -> cur.snapshot?.scene?.let { sc -> editorDelegate.buildPreview(p.origin, sc, ed) } }
        cur.copy(editor = ed.copy(pendingPlacement = refreshed))
    }
    fun selectPropType(type: EnvironmentPropType) = _uiState.update { cur -> cur.copy(editor = cur.editor.copy(selectedPropType = type, subTool = EditorSubTool.PROP_PLACE, category = EditorToolCategory.PROPS_LIGHTS, pendingPlacement = null)) }
    fun selectFlagCountry(isoCode: String) = _uiState.update { cur ->
        val ed = cur.editor.copy(selectedFlagIso = isoCode, selectedPropType = EnvironmentPropType.CITY_FLAG, subTool = EditorSubTool.PROP_PLACE, category = EditorToolCategory.PROPS_LIGHTS)
        val refreshed = cur.editor.pendingPlacement?.let { p -> cur.snapshot?.scene?.let { sc -> editorDelegate.buildPreview(p.origin, sc, ed) } }
        cur.copy(editor = ed.copy(pendingPlacement = refreshed))
    }
    fun selectLightType(type: LightSourceType) = _uiState.update { cur -> cur.copy(editor = cur.editor.copy(selectedLightType = type, subTool = EditorSubTool.LIGHT_PLACE, category = EditorToolCategory.PROPS_LIGHTS, pendingPlacement = null)) }
    fun setDraftDimensions(w: Int, h: Int) = _uiState.update { cur ->
        val ed = cur.editor.copy(draftWidth = w.coerceIn(1, 8), draftHeight = h.coerceIn(1, 8))
        val refreshed = cur.editor.pendingPlacement?.let { p -> cur.snapshot?.scene?.let { sc -> editorDelegate.buildPreview(p.origin, sc, ed) } }
        cur.copy(editor = ed.copy(pendingPlacement = refreshed))
    }
    fun rotateDraftClockwise() = _uiState.update { cur ->
        val ed = cur.editor.copy(draftRotation = cur.editor.draftRotation.rotatedClockwise())
        val refreshed = cur.editor.pendingPlacement?.let { p -> cur.snapshot?.scene?.let { sc -> editorDelegate.buildPreview(p.origin, sc, ed) } }
        cur.copy(editor = ed.copy(pendingPlacement = refreshed))
    }
    fun toggleDragPaint() = _uiState.update { cur -> cur.copy(editor = cur.editor.copy(dragPaintEnabled = !cur.editor.dragPaintEnabled, pendingPlacement = null)) }
    fun startMovePlot(plot: Plot) = _uiState.update { cur -> cur.copy(selectedPlotId = null, selectedHouseId = null, editor = cur.editor.copy(category = EditorToolCategory.PLOTS, subTool = EditorSubTool.PLOT_MOVE, movingPlotId = plot.id, pendingPlacement = null)) }
    fun startMoveBuilding(building: PublicBuilding) = _uiState.update { cur -> cur.copy(editor = cur.editor.copy(category = EditorToolCategory.BUILDINGS, subTool = EditorSubTool.PUBLIC_BUILDING_MOVE, movingBuildingId = building.id, selectedBuildingId = null, pendingPlacement = null)) }

    // Canvas grid tap & drag
    fun onGridCoordTapped(coord: GridCoord) {
        val state = _uiState.value
        val snap = state.snapshot ?: return
        val ed = state.editor
        if (ed.subTool == EditorSubTool.PLOT_MULTI_SELECT) {
            val hitPlot = snap.plots.firstOrNull { GridFootprint(it.coord, it.widthCells, it.heightCells, it.rotation).contains(coord) }
            if (hitPlot != null) {
                val updatedIds = if (hitPlot.id in ed.multiSelectedPlotIds) ed.multiSelectedPlotIds - hitPlot.id else ed.multiSelectedPlotIds + hitPlot.id
                val updatedCoords = snap.plots.filter { it.id in updatedIds }.flatMap { it.occupiedCoords() }.toSet()
                _uiState.update { it.copy(editor = it.editor.copy(multiSelectedPlotIds = updatedIds, multiSelectedCoords = updatedCoords)) }
            }
            return
        }
        val preview = editorDelegate.buildPreview(coord, snap.scene, ed)
        if (ed.subTool.isTileBrush && !ed.dragPaintEnabled) {
            if (preview.overlay.isValid) {
                viewModelScope.launch {
                    val res = editorDelegate.commitPlacement(preview, ed, snap)
                    handleOperationResult(res)
                }
            } else {
                _uiState.update { it.copy(statusFeedback = preview.validationError, editor = it.editor.copy(pendingPlacement = preview)) }
            }
            return
        }
        _uiState.update { it.copy(editor = it.editor.copy(pendingPlacement = preview)) }
    }

    fun onGridCoordDragged(coord: GridCoord) {
        val state = _uiState.value
        val snap = state.snapshot ?: return
        val ed = state.editor
        if (!ed.subTool.isTileBrush || !ed.dragPaintEnabled) return
        val existing = ed.pendingPlacement?.brushCoords.orEmpty()
        if (coord in existing) return
        val preview = editorDelegate.buildPreview(coord, snap.scene, ed, existing)
        _uiState.update { it.copy(editor = it.editor.copy(pendingPlacement = preview)) }
    }

    fun confirmPendingPlacement(onComplete: (AdminOperationResult) -> Unit = {}) {
        val state = _uiState.value
        val pending = state.editor.pendingPlacement ?: return
        val snap = state.snapshot ?: return
        viewModelScope.launch {
            val res = editorDelegate.commitPlacement(pending, state.editor, snap)
            handleOperationResult(res)
            if (res is AdminOperationResult.Success) {
                _uiState.update { cur ->
                    cur.copy(editor = cur.editor.copy(pendingPlacement = null, subTool = if (pending.tool == EditorSubTool.PLOT_MOVE || pending.tool == EditorSubTool.PUBLIC_BUILDING_MOVE) EditorSubTool.SELECT_INSPECT else cur.editor.subTool, category = if (pending.tool == EditorSubTool.PLOT_MOVE || pending.tool == EditorSubTool.PUBLIC_BUILDING_MOVE) EditorToolCategory.SELECT else cur.editor.category, movingPlotId = null, movingBuildingId = null))
                }
            }
            onComplete(res)
        }
    }

    fun cancelPendingPlacement() = _uiState.update { it.copy(editor = it.editor.copy(pendingPlacement = null)) }
    fun clearMultiSelection() = _uiState.update { it.copy(editor = it.editor.copy(multiSelectedPlotIds = emptySet(), multiSelectedCoords = emptySet())) }

    // Undo / Redo
    fun undoEditorAction(onComplete: (AdminOperationResult) -> Unit = {}) {
        viewModelScope.launch {
            val res = editorDelegate.historyManager.undo(_uiState.value.snapshot)
            handleOperationResult(res)
            onComplete(res)
        }
    }
    fun redoEditorAction(onComplete: (AdminOperationResult) -> Unit = {}) {
        viewModelScope.launch {
            val res = editorDelegate.historyManager.redo(_uiState.value.snapshot)
            handleOperationResult(res)
            onComplete(res)
        }
    }

    // Dialog & inspector builder mutations
    fun openBuilderDialog(dialog: AdminBuilderDialogState) = _uiState.update { it.copy(editor = it.editor.copy(activeDialog = dialog)) }
    fun dismissBuilderDialog() = _uiState.update { it.copy(editor = it.editor.copy(activeDialog = null)) }
    fun expandCityGrid(w: Int, h: Int, onDone: (AdminOperationResult) -> Unit = {}) = runRepoOp(onDone) { repository.expandCityGrid(w, h) }
    fun resizePlot(plot: Plot, w: Int, h: Int, onDone: (AdminOperationResult) -> Unit = {}) = runRepoOp(onDone) { editorDelegate.executeResizePlot(plot, w, h) }
    fun rotatePlot(plot: Plot, rot: BuildingRotation, onDone: (AdminOperationResult) -> Unit = {}) = runRepoOp(onDone) { editorDelegate.executeRotatePlot(plot, rot) }
    fun splitPlot(plot: Plot, horizontal: Boolean, onDone: (AdminOperationResult) -> Unit = {}) = runRepoOp(onDone) { repository.splitPlot(plot.id, horizontal) }
    fun mergePlots(firstId: String, secondId: String, onDone: (AdminOperationResult) -> Unit = {}) = runRepoOp(onDone) { repository.mergePlots(firstId, secondId) }
    fun togglePlotAvailability(plot: Plot, onDone: (AdminOperationResult) -> Unit = {}) = runRepoOp(onDone) { editorDelegate.executeTogglePlotAvailability(plot, plot.status == PlotStatus.UNAVAILABLE) }
    fun assignPlotDistrict(plotId: String, districtId: String?, onDone: (AdminOperationResult) -> Unit = {}) = runRepoOp(onDone) { repository.assignPlotDistrict(plotId, districtId) }
    fun deletePlot(plot: Plot, onDone: (AdminOperationResult) -> Unit = {}) = runRepoOp(onDone) { editorDelegate.executeDeletePlot(plot) }
    fun batchSetPlotsAvailability(available: Boolean, onDone: (AdminOperationResult) -> Unit = {}) {
        val ids = _uiState.value.editor.multiSelectedPlotIds.toList()
        runRepoOp(onDone) { repository.batchUpdatePlotAvailability(ids, available) }
    }
    fun batchAssignPlotsDistrict(districtId: String?, onDone: (AdminOperationResult) -> Unit = {}) {
        val ids = _uiState.value.editor.multiSelectedPlotIds.toList()
        runRepoOp(onDone) { repository.batchAssignPlotsDistrict(ids, districtId) }
    }
    fun batchDeleteSelectedPlots(onDone: (AdminOperationResult) -> Unit = {}) {
        val ids = _uiState.value.editor.multiSelectedPlotIds.toList()
        runRepoOp(onDone) {
            val res = repository.batchDeletePlots(ids)
            if (res is AdminOperationResult.Success) clearMultiSelection()
            res
        }
    }
    fun upsertDistrict(draft: DistrictDraft, onDone: (AdminOperationResult) -> Unit = {}) {
        val snap = _uiState.value.snapshot ?: return
        runRepoOp(onDone) { editorDelegate.executeUpsertDistrict(draft, snap) }
    }
    fun deleteDistrict(districtId: String, onDone: (AdminOperationResult) -> Unit = {}) = runRepoOp(onDone) { repository.deleteDistrict(districtId) }
    fun upsertPublicBuilding(draft: PublicBuildingDraft, onDone: (AdminOperationResult) -> Unit = {}) {
        val snap = _uiState.value.snapshot ?: return
        runRepoOp(onDone) { editorDelegate.executeUpsertPublicBuilding(draft, snap) }
    }
    fun deletePublicBuilding(building: PublicBuilding, onDone: (AdminOperationResult) -> Unit = {}) = runRepoOp(onDone) { editorDelegate.executeDeletePublicBuilding(building) }
    fun upsertRoadSegment(draft: RoadSegmentDraft, onDone: (AdminOperationResult) -> Unit = {}) {
        val snap = _uiState.value.snapshot ?: return
        runRepoOp(onDone) { editorDelegate.executeUpsertRoadSegment(draft, snap) }
    }
    fun deleteRoadSegment(roadId: String, eraseTilesToGrass: Boolean, onDone: (AdminOperationResult) -> Unit = {}) = runRepoOp(onDone) { repository.deleteRoadSegment(roadId, eraseTilesToGrass) }
    fun eraseTileToGrass(coord: GridCoord, onDone: (AdminOperationResult) -> Unit = {}) {
        val snap = _uiState.value.snapshot ?: return
        runRepoOp(onDone) { editorDelegate.executePaintTiles(listOf(TilePaintChange(coord, CityTileType.GRASS)), snap) }
    }
    fun upsertEnvironmentProp(draft: EnvironmentPropDraft, onDone: (AdminOperationResult) -> Unit = {}) {
        val snap = _uiState.value.snapshot ?: return
        runRepoOp(onDone) { editorDelegate.executeUpsertProp(draft, snap) }
    }
    fun deleteEnvironmentProp(prop: EnvironmentProp, onDone: (AdminOperationResult) -> Unit = {}) = runRepoOp(onDone) { editorDelegate.executeDeleteProp(prop) }
    fun upsertLightSource(draft: LightSourceDraft, onDone: (AdminOperationResult) -> Unit = {}) {
        val snap = _uiState.value.snapshot ?: return
        runRepoOp(onDone) { editorDelegate.executeUpsertLight(draft, snap) }
    }
    fun deleteLightSource(lightId: String, onDone: (AdminOperationResult) -> Unit = {}) = runRepoOp(onDone) { repository.deleteLightSource(lightId) }
    fun setLightingState(lightingState: CityLightingState, onDone: (AdminOperationResult) -> Unit = {}) {
        val prior = _uiState.value.snapshot?.scene?.lightingState ?: CityLightingState()
        runRepoOp(onDone) { editorDelegate.executeSetLighting(lightingState, prior) }
    }

    // House operations & validation (preserved Phase 1 / Phase 4)
    fun validatePlotForBuild(plotId: String): ValidationResult = repository.validatePlotForBuild(plotId)
    fun validateCoordinateForBuild(coord: GridCoord): ValidationResult = repository.validateCoordinateForBuild(coord)
    fun addHouse(draft: AdminHouseDraft, onComplete: (AdminOperationResult) -> Unit = {}) = runRepoOp(onComplete) { repository.addHouse(draft) }
    fun updateHouse(houseId: String, draft: AdminHouseDraft, onComplete: (AdminOperationResult) -> Unit = {}) = runRepoOp(onComplete) { repository.updateHouse(houseId, draft) }
    fun changeHouseColor(houseId: String, palette: HousePalette, onComplete: (AdminOperationResult) -> Unit = {}) = runRepoOp(onComplete) { repository.changeHouseColor(houseId, palette) }
    fun moveHouse(houseId: String, targetPlotId: String, onComplete: (AdminOperationResult) -> Unit = {}) = runRepoOp(onComplete) { repository.moveHouse(houseId, targetPlotId) }
    fun deleteHouse(houseId: String, onComplete: (AdminOperationResult) -> Unit = {}) = runRepoOp(onComplete) { repository.deleteHouse(houseId) }
    fun updateHouseRequestStatus(requestId: String, status: HouseRequestStatus, adminNotes: String? = null, targetPlotId: String? = null, onComplete: (AdminOperationResult) -> Unit = {}) =
        runRepoOp(onComplete) { repository.updateHouseRequestStatus(requestId, status, adminNotes, targetPlotId) }
    fun assignRequestPlotAndBuildHouse(requestId: String, houseDraft: AdminHouseDraft, onComplete: (AdminOperationResult) -> Unit = {}) =
        runRepoOp(onComplete) { repository.assignRequestPlotAndBuildHouse(requestId, houseDraft) }

    private fun runRepoOp(onComplete: (AdminOperationResult) -> Unit, block: suspend () -> AdminOperationResult) {
        viewModelScope.launch {
            val res = block()
            handleOperationResult(res)
            onComplete(res)
        }
    }

    private fun handleOperationResult(result: AdminOperationResult) {
        when (result) {
            is AdminOperationResult.Success -> _uiState.update { cur ->
                cur.copy(
                    statusFeedback = result.message,
                    selectedHouseId = result.affectedHouseId ?: cur.selectedHouseId,
                    selectedPlotId = result.affectedPlotId ?: cur.selectedPlotId,
                    editor = cur.editor.copy(
                        selectedDistrictId = result.affectedDistrictId ?: cur.editor.selectedDistrictId,
                        selectedBuildingId = result.affectedBuildingId ?: cur.editor.selectedBuildingId,
                        selectedPropId = result.affectedPropId ?: cur.editor.selectedPropId,
                        selectedRoadId = result.affectedRoadId ?: cur.editor.selectedRoadId
                    )
                )
            }
            is AdminOperationResult.Failure -> _uiState.update { it.copy(statusFeedback = result.reason) }
        }
    }

    companion object {
        fun factory(context: Context, supabaseUrl: String, supabaseAnonKey: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val config = SupabaseConfig(supabaseUrl, supabaseAnonKey)
                    val connectivity = AndroidConnectivityChecker(context)
                    val authRepo = SupabaseAdminAuthRepository(config = config, connectivityChecker = connectivity)
                    val adminRepo = SupabaseCityAdminRepository(config = config, authRepository = authRepo, connectivityChecker = connectivity)
                    return AdminViewModel(repository = adminRepo, authRepository = authRepo) as T
                }
            }
    }
}
