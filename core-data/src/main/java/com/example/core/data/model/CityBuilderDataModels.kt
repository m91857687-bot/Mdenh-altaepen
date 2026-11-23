package com.example.core.data.model

import com.example.cityengine.model.BuildingRotation
import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.EnvironmentPropType
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.LightSourceType
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.model.PropLayer
import com.example.cityengine.model.PublicBuildingType
import com.example.cityengine.model.TileTextureVariant

data class CityBuilderServerCapabilities(
    val builderTablesAvailable: Boolean = false,
    val customLayoutColumnsAvailable: Boolean = false
)

data class PlotCreateDraft(
    val id: String? = null,
    val plotCode: String? = null,
    val coord: GridCoord,
    val widthCells: Int = 1,
    val heightCells: Int = 1,
    val rotation: BuildingRotation = BuildingRotation.DEG_0,
    val status: PlotStatus = PlotStatus.AVAILABLE,
    val districtId: String? = null,
    val groundType: CityTileType = CityTileType.PLOT_LOT,
    val textureVariant: TileTextureVariant = TileTextureVariant.DEFAULT
)

data class DistrictDraft(
    val id: String? = null,
    val nameAr: String,
    val code: String,
    val themeColorHex: Long = 0xFF2E8B7D,
    val origin: GridCoord,
    val widthCells: Int = 4,
    val heightCells: Int = 4,
    val ambientStyle: String = "RESIDENTIAL"
)

data class PublicBuildingDraft(
    val id: String? = null,
    val districtId: String? = null,
    val nameAr: String,
    val type: PublicBuildingType,
    val coord: GridCoord,
    val widthCells: Int = type.defaultWidth,
    val heightCells: Int = type.defaultHeight,
    val rotation: BuildingRotation = BuildingRotation.DEG_0,
    val upgradeLevel: Int = 1,
    val primaryColorHex: Long = 0xFFEFE7D8,
    val accentColorHex: Long = 0xFF2D6A8E,
    val assetKey: String,
    val isLitAtNight: Boolean = true,
    val descriptionAr: String? = null,
    val publicInfoAr: String? = null
)

data class RoadSegmentDraft(
    val id: String? = null,
    val coord: GridCoord,
    val widthCells: Int = 1,
    val heightCells: Int = 1,
    val lanes: Int = 2,
    val hasSidewalkBorder: Boolean = true,
    val hasCrosswalk: Boolean = false,
    val hasParkingBay: Boolean = false,
    val streetNameAr: String? = null,
    val surfaceType: TileTextureVariant = TileTextureVariant.DEFAULT
)

data class EnvironmentPropDraft(
    val id: String? = null,
    val type: EnvironmentPropType,
    val coord: GridCoord,
    val widthCells: Int = 1,
    val heightCells: Int = 1,
    val rotation: BuildingRotation = BuildingRotation.DEG_0,
    val scale: Float = 1.0f,
    val variant: Int = 0,
    val labelAr: String? = null,
    val layer: PropLayer = PropLayer.STRUCTURE,
    val primaryColorHex: Long = 0xFF4F934D,
    val assetKey: String
)

data class LightSourceDraft(
    val id: String? = null,
    val type: LightSourceType = LightSourceType.STREET_LAMP,
    val coord: GridCoord,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val colorHex: Long = 0xFFFFE082,
    val intensity: Float = 1.0f,
    val radiusWorld: Float = 96f,
    val directionDeg: Float = 0f,
    val coneAngleDeg: Float = 360f,
    val isEnabled: Boolean = true,
    val linkedEntityId: String? = null
)

data class TilePaintChange(
    val coord: GridCoord,
    val tileType: CityTileType,
    val texture: TileTextureVariant = TileTextureVariant.DEFAULT
)
