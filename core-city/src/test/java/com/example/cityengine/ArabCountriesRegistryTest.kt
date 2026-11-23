package com.example.cityengine

import com.example.cityengine.model.ArabCountriesRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabCountriesRegistryTest {

    @Test
    fun `registry contains all 22 arab countries with unique codes names and distinct drawables`() {
        val all = ArabCountriesRegistry.allCountries
        assertEquals(22, all.size)

        val expectedIso = setOf(
            "SA", "YE", "BH", "KW", "OM", "QA", "AE", "JO", "SY", "LB", "PS",
            "IQ", "EG", "SD", "LY", "TN", "DZ", "MA", "MR", "SO", "DJ", "KM"
        )
        assertEquals(expectedIso, all.map { it.isoCode }.toSet())
        assertEquals(22, all.map { it.drawableResId }.toSet().size)

        all.forEach { spec ->
            assertTrue(spec.nameAr.isNotBlank())
            assertTrue(spec.nameEn.isNotBlank())
            assertEquals(spec.isoCode, spec.flagStyle.countryCode)
            assertEquals(spec.drawableResId, spec.flagStyle.drawableResId)
            val assetKey = ArabCountriesRegistry.assetKeyFor(spec.isoCode)
            assertEquals("flag_${spec.isoCode.lowercase()}", assetKey)
            assertEquals(spec.isoCode, ArabCountriesRegistry.parseCountryCodeFromAssetKey(assetKey))
        }
    }

    @Test
    fun `resolves aliases and case insensitive codes accurately`() {
        val sy = ArabCountriesRegistry.findByCode("sy")
        assertNotNull(sy)
        assertEquals("SY", sy!!.isoCode)
        assertEquals("سوريا", sy.nameAr)

        val uae = ArabCountriesRegistry.findByNameOrCode(null, "الإمارات")
        assertNotNull(uae)
        assertEquals("AE", uae!!.isoCode)

        val ksa = ArabCountriesRegistry.resolveFlagStyle("sa", "السعودية")
        assertEquals("SA", ksa.countryCode)
        assertNotNull(ksa.drawableResId)
    }
}
