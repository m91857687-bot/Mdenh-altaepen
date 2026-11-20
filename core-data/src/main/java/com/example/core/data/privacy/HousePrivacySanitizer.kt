package com.example.core.data.privacy

import com.example.core.data.model.HouseResidentSourceRecord
import com.example.core.data.model.PublicHouseDetails

/**
 * Enforces strict publication consent rules before any house record reaches the Explorer UI.
 * Phone numbers, emails, and optional notes are never exposed unless explicitly approved.
 */
object HousePrivacySanitizer {

    fun toPublicDetails(record: HouseResidentSourceRecord): PublicHouseDetails {
        val house = record.house

        val approvedPhone = if (record.allowPhonePublic) {
            record.rawPhoneNumber?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            null
        }

        val approvedEmail = if (record.allowEmailPublic) {
            record.rawEmail?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            null
        }

        val publicRole = if (record.allowPublicInfo) {
            record.publicNeighborhoodRole?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            null
        }

        val publicAbout = if (record.allowPublicInfo) {
            record.publicAbout?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            null
        }

        val visibleFamilyCount = if (record.showFamilyCountPublicly && house.familyMembersCount > 0) {
            house.familyMembersCount
        } else {
            null
        }

        return PublicHouseDetails(
            houseId = house.id,
            plotId = house.plotId,
            ownerName = house.ownerName.ifBlank { "غير متوفر" },
            countryCode = house.flag.countryCode,
            countryName = house.flag.countryName.ifBlank { "غير متوفر" },
            flag = house.flag,
            familyMembersCount = visibleFamilyCount,
            publicRole = publicRole,
            publicAbout = publicAbout,
            approvedPhone = approvedPhone,
            approvedEmail = approvedEmail,
            isTestData = record.isTestData
        )
    }
}
