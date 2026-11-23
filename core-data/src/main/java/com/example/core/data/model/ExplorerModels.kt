package com.example.core.data.model

import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.CountryFlagStyle
import com.example.cityengine.model.House

/**
 * Internal/backend-aligned resident record holding both publishable info and private contact fields
 * guarded by explicit publication consent flags.
 */
data class HouseResidentSourceRecord(
    val house: House,
    val rawPhoneNumber: String? = null,
    val rawEmail: String? = null,
    val rawAdditionalPhone: String? = null,
    val privateNotes: String? = null,
    val allowPhonePublic: Boolean = false,
    val allowEmailPublic: Boolean = false,
    val publicNeighborhoodRole: String? = null,
    val publicAbout: String? = null,
    val allowPublicInfo: Boolean = true,
    val showFamilyCountPublicly: Boolean = true,
    val isTestData: Boolean = true,
    val allowOwnerNamePublic: Boolean = house.allowOwnerNamePublic,
    val houseTitle: String? = house.houseTitle,
    val publicStatus: String? = house.publicStatus
)

/**
 * Privacy-sanitized model safe for public display in app-explorer without an account.
 * Private fields remain strictly null unless explicit consent was granted.
 */
data class PublicHouseDetails(
    val houseId: String,
    val plotId: String,
    val ownerName: String,
    val countryCode: String,
    val countryName: String,
    val flag: CountryFlagStyle,
    val familyMembersCount: Int?,
    val publicRole: String?,
    val publicAbout: String?,
    val approvedPhone: String?,
    val approvedEmail: String?,
    val isTestData: Boolean,
    val allowOwnerNamePublic: Boolean = true,
    val houseTitle: String? = null,
    val publicStatus: String? = null
) {
    val visibleOwnerName: String?
        get() = if (allowOwnerNamePublic) ownerName.takeIf { it.isNotBlank() && it != "غير معروض" } else null

    val hasAdditionalPublicInfo: Boolean
        get() = publicRole != null || publicAbout != null || publicStatus != null

    val hasApprovedContact: Boolean
        get() = approvedPhone != null || approvedEmail != null
}

data class CityExplorerSnapshot(
    val scene: CitySceneState,
    val houseDetailsById: Map<String, PublicHouseDetails>,
    val isTestData: Boolean
)

sealed interface CityDataState<out T> {
    data object Loading : CityDataState<Nothing>
    data object Empty : CityDataState<Nothing>
    data class Success<T>(val data: T) : CityDataState<T>
    data class Offline<T>(val message: String, val staleData: T? = null) : CityDataState<T>
    data class Error(val message: String) : CityDataState<Nothing>
}
