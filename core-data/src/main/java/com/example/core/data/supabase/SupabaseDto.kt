package com.example.core.data.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GridCoordDto(
    val x: Int,
    val y: Int
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
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class PlotDto(
    val id: String,
    @SerialName("layout_id") val layoutId: String = "main_city",
    @SerialName("plot_code") val plotCode: String,
    @SerialName("grid_x") val gridX: Int,
    @SerialName("grid_y") val gridY: Int,
    val status: String
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
    @SerialName("updated_at") val updatedAt: String? = null
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
    @SerialName("show_family_count_publicly") val showFamilyCountPublicly: Boolean = true
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
data class ProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String = "",
    val role: String = "VIEWER"
)

@Serializable
data class RpcMutationResultDto(
    @SerialName("house_id") val houseId: String? = null,
    @SerialName("plot_id") val plotId: String? = null,
    @SerialName("old_plot_id") val oldPlotId: String? = null
)
