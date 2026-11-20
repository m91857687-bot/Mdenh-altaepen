package com.example.cityengine.model

enum class PlotStatus {
    AVAILABLE,
    OCCUPIED
}

enum class CityTileType {
    GRASS,
    PARK_GREEN,
    ROAD,
    SIDEWALK,
    PLOT_LOT
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
    val houseId: String? = null
)

data class CountryFlagStyle(
    val countryCode: String,
    val countryName: String,
    val stripeColors: List<Long>,
    val isVerticalStripes: Boolean = false
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
    val palette: HousePalette
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
    val selectedPlotId: String? = null
)

sealed interface CityInteractionEvent {
    data class HouseSelected(val house: House) : CityInteractionEvent
    data class PlotSelected(val plot: Plot) : CityInteractionEvent
    data object SelectionCleared : CityInteractionEvent
}
