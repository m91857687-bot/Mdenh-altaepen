package com.example.core.data

import com.example.cityengine.model.CountryFlagStyle
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.House
import com.example.cityengine.model.HousePalette
import com.example.core.data.model.HouseResidentSourceRecord
import com.example.core.data.privacy.HousePrivacySanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HousePrivacySanitizerTest {

    private val baseHouse = House(
        id = "house_test",
        plotId = "plot_01",
        coord = GridCoord(1, 1),
        ownerName = "سالم العتيبي",
        familyMembersCount = 4,
        flag = CountryFlagStyle("SA", "المملكة العربية السعودية", listOf(0xFF167D46, 0xFFFFFFFF)),
        palette = HousePalette(0xFFFFFFFF, 0xFFE0E0E0, 0xFFC85A3A, 0xFF9F4025, 0xFF83321C, 0xFF5D3A29)
    )

    @Test
    fun `unapproved phone email and public info are strictly redacted`() {
        val record = HouseResidentSourceRecord(
            house = baseHouse,
            rawPhoneNumber = "+966501234567",
            rawEmail = "private@example.com",
            allowPhonePublic = false,
            allowEmailPublic = false,
            publicNeighborhoodRole = "منسق الحي",
            publicAbout = "نبذة خاصة",
            allowPublicInfo = false,
            showFamilyCountPublicly = false,
            isTestData = true
        )

        val publicDetails = HousePrivacySanitizer.toPublicDetails(record)

        assertNull(publicDetails.approvedPhone)
        assertNull(publicDetails.approvedEmail)
        assertNull(publicDetails.publicRole)
        assertNull(publicDetails.publicAbout)
        assertNull(publicDetails.familyMembersCount)
        assertFalse(publicDetails.hasApprovedContact)
        assertFalse(publicDetails.hasAdditionalPublicInfo)
        assertTrue(publicDetails.isTestData)
    }

    @Test
    fun `explicitly approved phone email and public info are exposed`() {
        val record = HouseResidentSourceRecord(
            house = baseHouse,
            rawPhoneNumber = " +966501234567 ",
            rawEmail = " public@example.com ",
            allowPhonePublic = true,
            allowEmailPublic = true,
            publicNeighborhoodRole = "منسق الحي",
            publicAbout = "أهلاً بالجميع",
            allowPublicInfo = true,
            showFamilyCountPublicly = true,
            isTestData = false
        )

        val publicDetails = HousePrivacySanitizer.toPublicDetails(record)

        assertEquals("+966501234567", publicDetails.approvedPhone)
        assertEquals("public@example.com", publicDetails.approvedEmail)
        assertEquals("منسق الحي", publicDetails.publicRole)
        assertEquals("أهلاً بالجميع", publicDetails.publicAbout)
        assertEquals(4, publicDetails.familyMembersCount)
        assertTrue(publicDetails.hasApprovedContact)
        assertTrue(publicDetails.hasAdditionalPublicInfo)
        assertFalse(publicDetails.isTestData)
    }

    @Test
    fun `blank owner or country falls back cleanly`() {
        val blankHouse = baseHouse.copy(
            ownerName = "",
            familyMembersCount = 0,
            flag = baseHouse.flag.copy(countryName = "")
        )
        val record = HouseResidentSourceRecord(house = blankHouse)
        val publicDetails = HousePrivacySanitizer.toPublicDetails(record)

        assertEquals("غير متوفر", publicDetails.ownerName)
        assertEquals("غير متوفر", publicDetails.countryName)
        assertNull(publicDetails.familyMembersCount)
    }
}
