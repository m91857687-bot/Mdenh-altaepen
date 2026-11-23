package com.example.admin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.admin.AdminBuilderDialogState
import com.example.admin.AdminEditorUiState
import com.example.admin.AdminUiState
import com.example.admin.AdminViewModel
import com.example.admin.EditorSubTool
import com.example.cityengine.CityEngine
import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.House
import com.example.cityengine.model.Plot
import com.example.cityengine.ui.CityCanvas
import com.example.core.data.model.AdminOperationResult

@Composable
fun AdminMapScreen(
    engine: CityEngine,
    scene: CitySceneState,
    uiState: AdminUiState,
    viewModel: AdminViewModel,
    onBuildOnPlot: (Plot) -> Unit,
    onEditHouse: (House) -> Unit,
    onChangeColor: (House) -> Unit,
    onMoveHouse: (House) -> Unit,
    onDeleteHouse: (House) -> Unit,
    modifier: Modifier = Modifier
) {
    val editor: AdminEditorUiState = uiState.editor
    val snapshot = uiState.snapshot
    val isSelectMode = editor.subTool == EditorSubTool.SELECT_INSPECT
    val isDragActive = editor.subTool.isTileBrush && editor.dragPaintEnabled

    Box(modifier = modifier.fillMaxSize()) {
        CityCanvas(
            engine = engine,
            scene = scene,
            onHouseTapped = viewModel::onMapHouseTapped,
            onPlotTapped = viewModel::onMapPlotTapped,
            onBuildingTapped = viewModel::onMapBuildingTapped,
            onPropTapped = viewModel::onMapPropTapped,
            onRoadTapped = viewModel::onMapRoadTapped,
            onGridCoordTapped = if (isSelectMode) null else viewModel::onGridCoordTapped,
            onGridCoordDragged = if (isDragActive) viewModel::onGridCoordDragged else null,
            isDragPlacementActive = isDragActive,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EditorTopActionBar(
                editorState = editor,
                historyState = uiState.historyState,
                showDegradedServerBadge = snapshot != null && !snapshot.isLocalTestData && !snapshot.builderCapabilities.builderTablesAvailable,
                onUndo = { viewModel.undoEditorAction() },
                onRedo = { viewModel.redoEditorAction() },
                onOpenLighting = { viewModel.openBuilderDialog(AdminBuilderDialogState.LightingSettings) },
                onOpenExpandGrid = { viewModel.openBuilderDialog(AdminBuilderDialogState.ExpandGrid) },
                onRecenter = {
                    val center = engine.initialFocusWorld(scene)
                    engine.cameraController.centerOnWorld(center.x, center.y, 1.0f)
                }
            )
            EditorToolDock(
                editorState = editor,
                onSelectCategory = viewModel::selectEditorCategory,
                onSelectSubTool = viewModel::selectEditorSubTool,
                onSelectBuildingType = viewModel::selectBuildingType,
                onSelectPropType = viewModel::selectPropType,
                onSelectFlagCountry = viewModel::selectFlagCountry,
                onSelectLightType = viewModel::selectLightType,
                onToggleDragPaint = viewModel::toggleDragPaint
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            editor.pendingPlacement?.let { pending ->
                EditorPendingPreviewBanner(
                    pending = pending,
                    onRotate = viewModel::rotateDraftClockwise,
                    onSetDimensions = viewModel::setDraftDimensions,
                    onCancel = viewModel::cancelPendingPlacement,
                    onConfirm = { viewModel.confirmPendingPlacement() }
                )
            }

            if (editor.multiSelectedPlotIds.isNotEmpty()) {
                EditorMultiSelectBar(
                    selectedCount = editor.multiSelectedPlotIds.size,
                    onSetAvailable = { viewModel.batchSetPlotsAvailability(true) },
                    onSetUnavailable = { viewModel.batchSetPlotsAvailability(false) },
                    onAssignDistrict = { viewModel.openBuilderDialog(AdminBuilderDialogState.AssignDistrict(editor.multiSelectedPlotIds.toList())) },
                    onDeleteSelected = { viewModel.batchDeleteSelectedPlots() },
                    onClear = viewModel::clearMultiSelection
                )
            }

            if (uiState.hasInspectorSelection && editor.pendingPlacement == null) {
                AdminMapInspectorCard(
                    selectedHouse = uiState.selectedHouse,
                    selectedPlot = uiState.selectedPlot,
                    selectedBuilding = uiState.selectedBuilding,
                    selectedProp = uiState.selectedProp,
                    selectedRoad = uiState.selectedRoad,
                    selectedRoadCoord = editor.selectedRoadCoord,
                    selectedDistrict = uiState.selectedDistrict,
                    onClearSelection = viewModel::clearMapSelection,
                    onBuildOnPlot = onBuildOnPlot,
                    onEditHouse = onEditHouse,
                    onChangeColor = onChangeColor,
                    onMoveHouse = onMoveHouse,
                    onDeleteHouse = onDeleteHouse,
                    onMovePlot = viewModel::startMovePlot,
                    onResizePlot = { p -> viewModel.openBuilderDialog(AdminBuilderDialogState.PlotResize(p)) },
                    onRotatePlot = { p -> viewModel.rotatePlot(p, p.rotation.rotatedClockwise()) },
                    onSplitPlot = { p, horiz -> viewModel.splitPlot(p, horiz) },
                    onMergePlot = { p -> viewModel.openBuilderDialog(AdminBuilderDialogState.PlotMerge(p)) },
                    onTogglePlotAvailability = { p -> viewModel.togglePlotAvailability(p) },
                    onAssignPlotDistrict = { p -> viewModel.openBuilderDialog(AdminBuilderDialogState.AssignDistrict(listOf(p.id))) },
                    onDeletePlot = { p -> viewModel.deletePlot(p) },
                    onEditBuilding = { b -> viewModel.openBuilderDialog(AdminBuilderDialogState.PublicBuildingEditor(b)) },
                    onMoveBuilding = viewModel::startMoveBuilding,
                    onRotateBuilding = { b ->
                        viewModel.upsertPublicBuilding(
                            com.example.core.data.model.PublicBuildingDraft(
                                b.id, b.districtId, b.nameAr, b.type, b.coord, b.widthCells, b.heightCells,
                                b.rotation.rotatedClockwise(), b.upgradeLevel, b.primaryColorHex, b.accentColorHex, b.assetKey, b.isLitAtNight,
                                descriptionAr = b.descriptionAr, publicInfoAr = b.publicInfoAr
                            )
                        )
                    },
                    onDeleteBuilding = { b -> viewModel.deletePublicBuilding(b) },
                    onEditProp = { prop -> viewModel.openBuilderDialog(AdminBuilderDialogState.PropDetailsEditor(prop)) },
                    onDeleteProp = { prop -> viewModel.deleteEnvironmentProp(prop) },
                    onEditRoad = { road, coord -> viewModel.openBuilderDialog(AdminBuilderDialogState.RoadSegmentEditor(road, coord)) },
                    onEraseRoadOrTile = { road, coord ->
                        if (road != null) viewModel.deleteRoadSegment(road.id, eraseTilesToGrass = true)
                        else viewModel.eraseTileToGrass(coord)
                    },
                    onEditDistrict = { d -> viewModel.openBuilderDialog(AdminBuilderDialogState.DistrictEditor(d)) },
                    onDeleteDistrict = { d -> viewModel.deleteDistrict(d.id) }
                )
            }
        }
    }

    if (snapshot != null) {
        AdminBuilderDialogHost(
            dialogState = editor.activeDialog,
            currentWidth = scene.gridWidth,
            currentHeight = scene.gridHeight,
            currentLighting = scene.lightingState,
            allPlots = snapshot.plots,
            districts = scene.districts,
            onDismiss = viewModel::dismissBuilderDialog,
            onExpandGrid = { w, h -> viewModel.expandCityGrid(w, h) { if (it is AdminOperationResult.Success) viewModel.dismissBuilderDialog() } },
            onSaveLighting = { st -> viewModel.setLightingState(st) { if (it is AdminOperationResult.Success) viewModel.dismissBuilderDialog() } },
            onSaveDistrict = { draft -> viewModel.upsertDistrict(draft) { if (it is AdminOperationResult.Success) viewModel.dismissBuilderDialog() } },
            onSaveBuilding = { draft -> viewModel.upsertPublicBuilding(draft) { if (it is AdminOperationResult.Success) viewModel.dismissBuilderDialog() } },
            onSaveRoad = { draft -> viewModel.upsertRoadSegment(draft) { if (it is AdminOperationResult.Success) viewModel.dismissBuilderDialog() } },
            onResizePlot = { p, w, h -> viewModel.resizePlot(p, w, h) { if (it is AdminOperationResult.Success) viewModel.dismissBuilderDialog() } },
            onMergePlots = { a, b -> viewModel.mergePlots(a, b) { if (it is AdminOperationResult.Success) viewModel.dismissBuilderDialog() } },
            onAssignDistrict = { ids, distId ->
                if (ids.size == 1) viewModel.assignPlotDistrict(ids.first(), distId) { if (it is AdminOperationResult.Success) viewModel.dismissBuilderDialog() }
                else viewModel.batchAssignPlotsDistrict(distId) { if (it is AdminOperationResult.Success) viewModel.dismissBuilderDialog() }
            },
            onSaveProp = { draft -> viewModel.upsertEnvironmentProp(draft) { if (it is AdminOperationResult.Success) viewModel.dismissBuilderDialog() } }
        )
    }
}
