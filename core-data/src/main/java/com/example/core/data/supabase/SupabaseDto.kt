package com.example.core.data.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GridCoordDto(
    val x: Int,
    val y: Int
)

@Serializable
data class CustomTileDto(
    val x: Int,
    val y: Int,
    val type: String = "GRASS",
    val texture: String = "DEFAULT"
)

@Serializable
data class RoadSegmentDto(
    val id: String,
    val x: Int,
    val y: Int,
    @SerialName("width_cells") val widthCells: Int = 1,
    @SerialName("height_cells") val heightCells: Int = 1,
    val lanes: Int = 2,
    @SerialName("has_sidewalk_border") val hasSidewalkBorder: Boolean = true,
    @SerialName("has_crosswalk") val hasCrosswalk: Boolean = false,
    @SerialName("has_parking_bay") val hasParkingBay: Boolean = false,
    @SerialName("street_name_ar") val streetNameAr: String? = null,
    @SerialName("surface_type") val surfaceType: String = "DEFAULT"
)

@Serializable
data class LightingConfigDto(
    val phase: String = "DAY",
    val sunAzimuthDeg: Float = 42f,
    val ambientDimming: Float = 0f,
    val nightLampAutoOn: Boolean = true
)

@Serializable
data class TreeLayoutDto(
    val id: String,
    val x: Int,
    val y: Int,
    val scale: Float = 1.0f,
    val variant: Int = 0
)

@Serializable
data class LampPostLayoutDto(
    val id: String,
    val x: Int,
    val y: Int
)

@Serializable
data class PaletteDto(
    val wallLeftHex: Long,
    val wallRightHex: Long,
    val roofLeftHex: Long,
    val roofRightHex: Long,
    val roofTrimHex: Long,
    val doorHex: Long,
    val windowHex: Long = 0xFFE3F4FA
)

@Serializable
data class CityLayoutDto(
    val id: String,
    val version: Int = 1,
    @SerialName("grid_width") val gridWidth: Int,
    @SerialName("grid_height") val gridHeight: Int,
    val roads: List<GridCoordDto> = emptyList(),
    val sidewalks: List<GridCoordDto> = emptyList(),
    val parks: List<GridCoordDto> = emptyList(),
    val trees: List<TreeLayoutDto> = emptyList(),
    @SerialName("lamp_posts") val lampPosts: List<LampPostLayoutDto> = emptyList(),
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("custom_tiles") val customTiles: List<CustomTileDto> = emptyList(),
    @SerialName("road_segments") val roadSegments: List<RoadSegmentDto> = emptyList(),
    @SerialName("lighting_config") val lightingConfig: LightingConfigDto = LightingConfigDto()
)

@Serializable
data class PlotDto(
    val id: String,
    @SerialName("layout_id") val layoutId: String = "main_city",
    @SerialName("plot_code") val plotCode: String,
    @SerialName("grid_x") val gridX: Int,
    @SerialName("grid_y") val gridY: Int,
    val status: String,
    @SerialName("width_cells") val widthCells: Int = 1,
    @SerialName("height_cells") val heightCells: Int = 1,
    @SerialName("rotation_deg") val rotationDeg: Int = 0,
    @SerialName("district_id") val districtId: String? = null,
    @SerialName("ground_type") val groundType: String = "PLOT_LOT",
    @SerialName("texture_variant") val textureVariant: String = "DEFAULT"
)

@Serializable
data class HousePublicDto(
    @SerialName("house_id") val houseId: String,
    @SerialName("plot_id") val plotId: String,
    @SerialName("resident_name") val residentName: String,
    @SerialName("country_code") val countryCode: String,
    val country: String,
    @SerialName("flag_stripes") val flagStripes: List<Long> = emptyList(),
    @SerialName("flag_vertical") val flagVertical: Boolean = false,
    @SerialName("family_size") val familySize: Int? = null,
    @SerialName("house_color") val houseColor: String,
    @SerialName("palette_json") val paletteJson: PaletteDto,
    @SerialName("grid_x") val gridX: Int,
    @SerialName("grid_y") val gridY: Int,
    @SerialName("public_role") val publicRole: String? = null,
    @SerialName("public_about") val publicAbout: String? = null,
    @SerialName("approved_phone") val approvedPhone: String? = null,
    @SerialName("approved_email") val approvedEmail: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("width_cells") val widthCells: Int = 1,
    @SerialName("height_cells") val heightCells: Int = 1,
    @SerialName("rotation_deg") val rotationDeg: Int = 0,
    @SerialName("design_style") val designStyle: String = "CLASSIC_HOME",
    @SerialName("upgrade_level") val upgradeLevel: Int = 1,
    @SerialName("has_lit_windows") val hasLitWindows: Boolean = true,
    @SerialName("asset_key") val assetKey: String = "house_classic",
    @SerialName("house_title") val houseTitle: String? = null,
    @SerialName("public_status") val publicStatus: String? = null
)

@Serializable
data class HouseAdminDto(
    val id: String,
    @SerialName("plot_id") val plotId: String,
    @SerialName("resident_name") val residentName: String,
    @SerialName("country_code") val countryCode: String,
    val country: String,
    @SerialName("flag_stripes") val flagStripes: List<Long> = emptyList(),
    @SerialName("flag_vertical") val flagVertical: Boolean = false,
    @SerialName("family_size") val familySize: Int,
    @SerialName("house_color") val houseColor: String,
    @SerialName("palette_json") val paletteJson: PaletteDto,
    @SerialName("grid_x") val gridX: Int,
    @SerialName("grid_y") val gridY: Int,
    @SerialName("public_role") val publicRole: String? = null,
    @SerialName("public_about") val publicAbout: String? = null,
    @SerialName("allow_public_info") val allowPublicInfo: Boolean = true,
    @SerialName("show_family_count_publicly") val showFamilyCountPublicly: Boolean = true,
    @SerialName("width_cells") val widthCells: Int = 1,
    @SerialName("height_cells") val heightCells: Int = 1,
    @SerialName("rotation_deg") val rotationDeg: Int = 0,
    @SerialName("design_style") val designStyle: String = "CLASSIC_HOME",
    @SerialName("upgrade_level") val upgradeLevel: Int = 1,
    @SerialName("has_lit_windows") val hasLitWindows: Boolean = true,
    @SerialName("asset_key") val assetKey: String = "house_classic",
    @SerialName("house_title") val houseTitle: String? = null,
    @SerialName("public_status") val publicStatus: String? = null,
    @SerialName("allow_owner_name_public") val allowOwnerNamePublic: Boolean = true
)

@Serializable
data class HousePrivateDetailsDto(
    val id: String? = null,
    @SerialName("house_id") val houseId: String,
    val phone: String? = null,
    val email: String? = null,
    @SerialName("additional_phone") val additionalPhone: String? = null,
    @SerialName("private_notes") val privateNotes: String? = null,
    @SerialName("allow_phone_public") val allowPhonePublic: Boolean = false,
    @SerialName("allow_email_public") val allowEmailPublic: Boolean = false
)

@Serializable
data class DistrictDto(
    val id: String,
    @SerialName("layout_id") val layoutId: String = "main_city",
    @SerialName("name_ar") val nameAr: String,
    val code: String,
    @SerialName("theme_color_hex") val themeColorHex: Long = 0xFF2E8B7D,
    @SerialName("grid_x") val gridX: Int,
    @SerialName("grid_y") val gridY: Int,
    @SerialName("width_cells") val widthCells: Int = 1,
    @SerialName("height_cells") val heightCells: Int = 1,
    @SerialName("ambient_style") val ambientStyle: String = "RESIDENTIAL"
)

@Serializable
data class PublicBuildingDto(
    val id: String,
    @SerialName("layout_id") val layoutId: String = "main_city",
    @SerialName("district_id") val districtId: String? = null,
    @SerialName("name_ar") val nameAr: String,
    @SerialName("building_type") val buildingType: String = "CUSTOM_LANDMARK",
    @SerialName("grid_x") val gridX: Int,
    @SerialName("grid_y") val gridY: Int,
    @SerialName("width_cells") val widthCells: Int = 1,
    @SerialName("height_cells") val heightCells: Int = 1,
    @SerialName("rotation_deg") val rotationDeg: Int = 0,
    @SerialName("upgrade_level") val upgradeLevel: Int = 1,
    @SerialName("primary_color_hex") val primaryColorHex: Long = 0xFFEFE7D8,
    @SerialName("accent_color_hex") val accentColorHex: Long = 0xFF2D6A8E,
    @SerialName("asset_key") val assetKey: String = "building_custom_landmark",
    @SerialName("is_lit_at_night") val isLitAtNight: Boolean = true,
    @SerialName("description_ar") val descriptionAr: String? = null,
    @SerialName("public_info_ar") val publicInfoAr: String? = null
)

@Serializable
data class EnvironmentPropDto(
    val id: String,
    @SerialName("layout_id") val layoutId: String = "main_city",
    @SerialName("prop_type") val propType: String = "TREE_BROADLEAF",
    @SerialName("grid_x") val gridX: Int,
    @SerialName("grid_y") val gridY: Int,
    @SerialName("width_cells") val widthCells: Int = 1,
    @SerialName("height_cells") val heightCells: Int = 1,
    @SerialName("rotation_deg") val rotationDeg: Int = 0,
    val scale: Float = 1.0f,
    val variant: Int = 0,
    @SerialName("label_ar") val labelAr: String? = null,
    val layer: String = "STRUCTURE",
    @SerialName("primary_color_hex") val primaryColorHex: Long = 0xFF4F934D,
    @SerialName("asset_key") val assetKey: String = "prop_tree_broadleaf"
)

@Serializable
data class CityLightDto(
    val id: String,
    @SerialName("layout_id") val layoutId: String = "main_city",
    @SerialName("light_type") val lightType: String = "STREET_LAMP",
    @SerialName("grid_x") val gridX: Int,
    @SerialName("grid_y") val gridY: Int,
    @SerialName("offset_x") val offsetX: Float = 0f,
    @SerialName("offset_y") val offsetY: Float = 0f,
    @SerialName("color_hex") val colorHex: Long = 0xFFFFE082,
    val intensity: Float = 1.0f,
    @SerialName("radius_world") val radiusWorld: Float = 96f,
    @SerialName("direction_deg") val directionDeg: Float = 0f,
    @SerialName("cone_angle_deg") val coneAngleDeg: Float = 360f,
    @SerialName("is_enabled") val isEnabled: Boolean = true,
    @SerialName("linked_entity_id") val linkedEntityId: String? = null
)

@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String = "",
    val role: String = "VIEWER"
)

@Serializable
data class RpcMutationResultDto(
    @SerialName("house_id") val houseId: String? = null,
    @SerialName("plot_id") val plotId: String? = null,
    @SerialName("old_plot_id") val oldPlotId: String? = null,
    @SerialName("request_id") val requestId: String? = null,
    val status: String? = null
)

@Serializable
data class HouseRequestDto(
    val id: String,
    @SerialName("applicant_name") val applicantName: String,
    val phone: String? = null,
    val email: String? = null,
    val notes: String? = null,
    val status: String = "NEW",
    @SerialName("target_plot_id") val targetPlotId: String? = null,
    @SerialName("assigned_house_id") val assignedHouseId: String? = null,
    @SerialName("admin_notes") val adminNotes: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
