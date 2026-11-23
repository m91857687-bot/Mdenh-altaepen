package com.example.core.data.model

import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.CountryFlagStyle
import com.example.cityengine.model.HousePalette
import com.example.cityengine.model.Plot

data class AdminHouseDraft(
    val targetPlotId: String,
    val ownerName: String,
    val familyMembersCount: Int,
    val flag: CountryFlagStyle,
    val palette: HousePalette,
    val publicNeighborhoodRole: String? = null,
    val publicAbout: String? = null,
    val allowPublicInfo: Boolean = true,
    val showFamilyCountPublicly: Boolean = true,
    val phoneNumber: String? = null,
    val email: String? = null,
    val additionalPhone: String? = null,
    val privateNotes: String? = null,
    val allowPhonePublic: Boolean = false,
    val allowEmailPublic: Boolean = false,
    val houseTitle: String? = null,
    val publicStatus: String? = null,
    val allowOwnerNamePublic: Boolean = true
)

data class AdminCitySnapshot(
    val scene: CitySceneState,
    val plots: List<Plot>,
    val recordsByHouseId: Map<String, HouseResidentSourceRecord>,
    val totalHouses: Int,
    val availablePlotsCount: Int,
    val occupiedPlotsCount: Int,
    val totalKnownResidents: Int,
    val isLocalTestData: Boolean = true,
    val layoutVersion: Int = 1,
    val builderCapabilities: CityBuilderServerCapabilities = CityBuilderServerCapabilities(),
    val houseRequests: List<HouseRequest> = emptyList()
)

sealed interface AdminOperationResult {
    data class Success(
        val message: String,
        val affectedHouseId: String? = null,
        val affectedPlotId: String? = null,
        val affectedDistrictId: String? = null,
        val affectedBuildingId: String? = null,
        val affectedPropId: String? = null,
        val affectedRoadId: String? = null,
        val affectedLightId: String? = null,
        val affectedRequestId: String? = null
    ) : AdminOperationResult

    data class Failure(val reason: String) : AdminOperationResult
}
