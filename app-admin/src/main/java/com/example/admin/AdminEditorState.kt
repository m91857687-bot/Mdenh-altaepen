package com.example.admin

import com.example.cityengine.model.BuildingRotation
import com.example.cityengine.model.CityLightSource
import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.DistrictZone
import com.example.cityengine.model.EditorPreviewOverlay
import com.example.cityengine.model.EnvironmentProp
import com.example.cityengine.model.EnvironmentPropType
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.LightSourceType
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PropLayer
import com.example.cityengine.model.PublicBuilding
import com.example.cityengine.model.PublicBuildingType
import com.example.cityengine.model.RoadSegment
import com.example.cityengine.model.TileTextureVariant

enum class EditorToolCategory {
    SELECT,
    PLOTS,
    ROADS,
    DISTRICTS,
    BUILDINGS,
    PROPS_LIGHTS
}

enum class EditorSubTool(val category: EditorToolCategory) {
    SELECT_INSPECT(EditorToolCategory.SELECT),
    PLOT_CREATE(EditorToolCategory.PLOTS),
    PLOT_MOVE(EditorToolCategory.PLOTS),
    PLOT_MULTI_SELECT(EditorToolCategory.PLOTS),
    ROAD_PAINT(EditorToolCategory.ROADS),
    SIDEWALK_PAINT(EditorToolCategory.ROADS),
    CROSSWALK_PAINT(EditorToolCategory.ROADS),
    PARK_PAINT(EditorToolCategory.ROADS),
    PLAZA_PAINT(EditorToolCategory.ROADS),
    ERASE_TO_GRASS(EditorToolCategory.ROADS),
    ROAD_SEGMENT_PLACE(EditorToolCategory.ROADS),
    DISTRICT_PLACE(EditorToolCategory.DISTRICTS),
    PUBLIC_BUILDING_PLACE(EditorToolCategory.BUILDINGS),
    PUBLIC_BUILDING_MOVE(EditorToolCategory.BUILDINGS),
    PROP_PLACE(EditorToolCategory.PROPS_LIGHTS),
    LIGHT_PLACE(EditorToolCategory.PROPS_LIGHTS);

    val isTileBrush: Boolean
        get() = this == ROAD_PAINT ||
            this == SIDEWALK_PAINT ||
            this == CROSSWALK_PAINT ||
            this == PARK_PAINT ||
            this == PLAZA_PAINT ||
            this == ERASE_TO_GRASS
}

data class PendingPlacementDraft(
    val tool: EditorSubTool,
    val origin: GridCoord,
    val widthCells: Int = 1,
    val heightCells: Int = 1,
    val rotation: BuildingRotation = BuildingRotation.DEG_0,
    val overlay: EditorPreviewOverlay,
    val validationError: String? = null,
    val targetPlotId: String? = null,
    val targetBuildingId: String? = null,
    val brushCoords: Set<GridCoord> = emptySet()
)

sealed interface AdminBuilderDialogState {
    data object ExpandGrid : AdminBuilderDialogState
    data object LightingSettings : AdminBuilderDialogState
    data class DistrictEditor(val existing: DistrictZone? = null, val defaultOrigin: GridCoord = GridCoord(2, 2)) : AdminBuilderDialogState
    data class PublicBuildingEditor(val existing: PublicBuilding) : AdminBuilderDialogState
    data class RoadSegmentEditor(val existing: RoadSegment?, val coord: GridCoord) : AdminBuilderDialogState
    data class PlotResize(val plot: Plot) : AdminBuilderDialogState
    data class PlotMerge(val plot: Plot) : AdminBuilderDialogState
    data class AssignDistrict(val plotIds: List<String>) : AdminBuilderDialogState
    data class PropDetailsEditor(val prop: EnvironmentProp) : AdminBuilderDialogState
    data class LightDetailsEditor(val light: CityLightSource) : AdminBuilderDialogState
}

data class AdminEditorUiState(
    val category: EditorToolCategory = EditorToolCategory.SELECT,
    val subTool: EditorSubTool = EditorSubTool.SELECT_INSPECT,
    val draftWidth: Int = 1,
    val draftHeight: Int = 1,
    val draftRotation: BuildingRotation = BuildingRotation.DEG_0,
    val selectedBuildingType: PublicBuildingType = PublicBuildingType.HOSPITAL,
    val selectedPropType: EnvironmentPropType = EnvironmentPropType.TREE_BROADLEAF,
    val selectedFlagIso: String = "SA",
    val selectedLightType: LightSourceType = LightSourceType.STREET_LAMP,
    val activeDistrictId: String? = null,
    val dragPaintEnabled: Boolean = false,
    val movingPlotId: String? = null,
    val movingBuildingId: String? = null,
    val pendingPlacement: PendingPlacementDraft? = null,
    val multiSelectedPlotIds: Set<String> = emptySet(),
    val multiSelectedCoords: Set<GridCoord> = emptySet(),
    val selectedBuildingId: String? = null,
    val selectedDistrictId: String? = null,
    val selectedPropId: String? = null,
    val selectedRoadId: String? = null,
    val selectedRoadCoord: GridCoord? = null,
    val activeDialog: AdminBuilderDialogState? = null
) {
    fun brushTileSpec(): Pair<CityTileType, TileTextureVariant>? = when (subTool) {
        EditorSubTool.ROAD_PAINT -> CityTileType.ROAD to TileTextureVariant.DEFAULT
        EditorSubTool.SIDEWALK_PAINT -> CityTileType.SIDEWALK to TileTextureVariant.TILE_GRID
        EditorSubTool.CROSSWALK_PAINT -> CityTileType.ROAD to TileTextureVariant.ASPHALT_CROSSWALK
        EditorSubTool.PARK_PAINT -> CityTileType.PARK_GREEN to TileTextureVariant.DEFAULT
        EditorSubTool.PLAZA_PAINT -> CityTileType.PLAZA to TileTextureVariant.CHECKER_STONE
        EditorSubTool.ERASE_TO_GRASS -> CityTileType.GRASS to TileTextureVariant.DEFAULT
        else -> null
    }
}

fun defaultPropLayer(type: EnvironmentPropType): PropLayer = when (type) {
    EnvironmentPropType.PARKING_LOT,
    EnvironmentPropType.FLOWER_BED,
    EnvironmentPropType.GREEN_LAWN,
    EnvironmentPropType.CROSSWALK_MARKER -> PropLayer.GROUND_OVERLAY
    EnvironmentPropType.LAMP_POST -> PropLayer.LIGHT_FIXTURE
    else -> PropLayer.STRUCTURE
}
