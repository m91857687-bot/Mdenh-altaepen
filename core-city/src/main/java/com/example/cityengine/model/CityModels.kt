package com.example.cityengine.model

enum class PlotStatus {
    AVAILABLE,
    UNAVAILABLE,
    OCCUPIED
}

enum class CityTileType {
    GRASS,
    PARK_GREEN,
    ROAD,
    SIDEWALK,
    PLOT_LOT,
    SOIL,
    STONE,
    DECORATIVE_PAVING,
    FARMLAND,
    PLAZA,
    WATER
}

enum class TileTextureVariant {
    DEFAULT,
    CHECKER_STONE,
    COBBLE,
    TILE_GRID,
    FURROWED,
    ASPHALT_CROSSWALK,
    PARKING_BAY
}

enum class BuildingRotation(val degrees: Int) {
    DEG_0(0),
    DEG_90(90),
    DEG_180(180),
    DEG_270(270);

    fun rotatedClockwise(): BuildingRotation = when (this) {
        DEG_0 -> DEG_90
        DEG_90 -> DEG_180
        DEG_180 -> DEG_270
        DEG_270 -> DEG_0
    }

    companion object {
        fun fromDegrees(deg: Int): BuildingRotation {
            val norm = ((deg % 360) + 360) % 360
            return entries.firstOrNull { it.degrees == norm } ?: DEG_0
        }
    }
}

enum class HouseDesignStyle {
    CLASSIC_HOME,
    MODERN_VILLA,
    COURTYARD_HOUSE,
    TOWNHOME,
    COTTAGE
}

data class GridCoord(
    val x: Int,
    val y: Int
)

data class WorldPoint(
    val x: Float,
    val y: Float
)

data class GridPointF(
    val x: Float,
    val y: Float
)

data class WorldBounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float
)

data class Plot(
    val id: String,
    val coord: GridCoord,
    val status: PlotStatus,
    val houseId: String? = null,
    val widthCells: Int = 1,
    val heightCells: Int = 1,
    val rotation: BuildingRotation = BuildingRotation.DEG_0,
    val districtId: String? = null,
    val groundType: CityTileType = CityTileType.PLOT_LOT,
    val textureVariant: TileTextureVariant = TileTextureVariant.DEFAULT,
    val plotCode: String = id
) {
    val maxXExclusive: Int get() = coord.x + widthCells.coerceAtLeast(1)
    val maxYExclusive: Int get() = coord.y + heightCells.coerceAtLeast(1)

    fun effectiveWidth(): Int = if (rotation == BuildingRotation.DEG_90 || rotation == BuildingRotation.DEG_270) {
        heightCells.coerceAtLeast(1)
    } else {
        widthCells.coerceAtLeast(1)
    }

    fun effectiveHeight(): Int = if (rotation == BuildingRotation.DEG_90 || rotation == BuildingRotation.DEG_270) {
        widthCells.coerceAtLeast(1)
    } else {
        heightCells.coerceAtLeast(1)
    }

    fun occupiedCoords(): List<GridCoord> {
        val w = effectiveWidth()
        val h = effectiveHeight()
        return buildList(w * h) {
            for (dy in 0 until h) {
                for (dx in 0 until w) {
                    add(GridCoord(coord.x + dx, coord.y + dy))
                }
            }
        }
    }
}

data class CountryFlagStyle(
    val countryCode: String,
    val countryName: String,
    val stripeColors: List<Long>,
    val isVerticalStripes: Boolean = false,
    val drawableResId: Int? = null
)

data class HousePalette(
    val wallLeftHex: Long,
    val wallRightHex: Long,
    val roofLeftHex: Long,
    val roofRightHex: Long,
    val roofTrimHex: Long,
    val doorHex: Long,
    val windowHex: Long = 0xFFE3F4FA
)

data class House(
    val id: String,
    val plotId: String,
    val coord: GridCoord,
    val ownerName: String,
    val familyMembersCount: Int,
    val flag: CountryFlagStyle,
    val palette: HousePalette,
    val widthCells: Int = 1,
    val heightCells: Int = 1,
    val rotation: BuildingRotation = BuildingRotation.DEG_0,
    val designStyle: HouseDesignStyle = HouseDesignStyle.CLASSIC_HOME,
    val upgradeLevel: Int = 1,
    val hasLitWindows: Boolean = true,
    val assetKey: String = "house_classic",
    val houseTitle: String? = null,
    val publicStatus: String? = null,
    val allowOwnerNamePublic: Boolean = true
)

data class HouseColorPreset(
    val key: String,
    val labelAr: String,
    val palette: HousePalette
)

data class TreeItem(
    val id: String,
    val coord: GridCoord,
    val scale: Float = 1f,
    val variant: Int = 0
)

data class LampPostItem(
    val id: String,
    val coord: GridCoord
)

data class CitySceneState(
    val gridWidth: Int,
    val gridHeight: Int,
    val tiles: Map<GridCoord, CityTileType>,
    val plots: List<Plot>,
    val houses: List<House>,
    val trees: List<TreeItem>,
    val lampPosts: List<LampPostItem>,
    val selectedHouseId: String? = null,
    val selectedPlotId: String? = null,
    val districts: List<DistrictZone> = emptyList(),
    val tileTextures: Map<GridCoord, TileTextureVariant> = emptyMap(),
    val roadSegments: List<RoadSegment> = emptyList(),
    val publicBuildings: List<PublicBuilding> = emptyList(),
    val environmentProps: List<EnvironmentProp> = emptyList(),
    val lights: List<CityLightSource> = emptyList(),
    val lightingState: CityLightingState = CityLightingState(),
    val selectedBuildingId: String? = null,
    val selectedDistrictId: String? = null,
    val selectedPropId: String? = null,
    val selectedRoadId: String? = null,
    val multiSelectedPlotIds: Set<String> = emptySet(),
    val multiSelectedCoords: Set<GridCoord> = emptySet(),
    val editorPreview: EditorPreviewOverlay? = null
)

sealed interface CityInteractionEvent {
    data class HouseSelected(val house: House) : CityInteractionEvent
    data class PlotSelected(val plot: Plot) : CityInteractionEvent
    data class BuildingSelected(val building: PublicBuilding) : CityInteractionEvent
    data class PropSelected(val prop: EnvironmentProp) : CityInteractionEvent
    data class RoadSelected(val road: RoadSegment?, val coord: GridCoord) : CityInteractionEvent
    data object SelectionCleared : CityInteractionEvent
}
