package com.example.cityengine.model

import androidx.annotation.DrawableRes
import com.example.core.city.R

data class ArabCountrySpec(
    val isoCode: String,
    val nameAr: String,
    val nameEn: String,
    val flagStyle: CountryFlagStyle,
    @DrawableRes val drawableResId: Int
)

/**
 * Single authoritative unified catalog for all 22 Arab League countries and their official flags,
 * shared identically across Explorer (:app), Admin (:app-admin), Renderer (:core-city), and Data (:core-data).
 */
object ArabCountriesRegistry {

    val allCountries: List<ArabCountrySpec> = listOf(
        spec("SA", "المملكة العربية السعودية", "Saudi Arabia", listOf(0xFF006C35, 0xFFFFFFFF, 0xFF006C35), R.drawable.flag_sa),
        spec("YE", "اليمن", "Yemen", listOf(0xFFCE1126, 0xFFFFFFFF, 0xFF000000), R.drawable.flag_ye),
        spec("BH", "البحرين", "Bahrain", listOf(0xFFFFFFFF, 0xFFCE1126), R.drawable.flag_bh, isVerticalStripes = true),
        spec("KW", "الكويت", "Kuwait", listOf(0xFF007A3D, 0xFFFFFFFF, 0xFFCE1126), R.drawable.flag_kw),
        spec("OM", "عُمان", "Oman", listOf(0xFFFFFFFF, 0xFFDB161B, 0xFF008000), R.drawable.flag_om),
        spec("QA", "قطر", "Qatar", listOf(0xFFFFFFFF, 0xFF8D1B3D), R.drawable.flag_qa, isVerticalStripes = true),
        spec("AE", "الإمارات العربية المتحدة", "United Arab Emirates", listOf(0xFF00732F, 0xFFFFFFFF, 0xFF000000), R.drawable.flag_ae),
        spec("JO", "الأردن", "Jordan", listOf(0xFF000000, 0xFFFFFFFF, 0xFF007A3D), R.drawable.flag_jo),
        spec("SY", "سوريا", "Syria", listOf(0xFF007A3D, 0xFFFFFFFF, 0xFF000000), R.drawable.flag_sy),
        spec("LB", "لبنان", "Lebanon", listOf(0xFFED1C24, 0xFFFFFFFF, 0xFFED1C24), R.drawable.flag_lb),
        spec("PS", "فلسطين", "Palestine", listOf(0xFF000000, 0xFFFFFFFF, 0xFF007A3D), R.drawable.flag_ps),
        spec("IQ", "العراق", "Iraq", listOf(0xFFCE1126, 0xFFFFFFFF, 0xFF000000), R.drawable.flag_iq),
        spec("EG", "مصر", "Egypt", listOf(0xFFCE1126, 0xFFFFFFFF, 0xFF000000), R.drawable.flag_eg),
        spec("SD", "السودان", "Sudan", listOf(0xFFD21034, 0xFFFFFFFF, 0xFF000000), R.drawable.flag_sd),
        spec("LY", "ليبيا", "Libya", listOf(0xFFE70013, 0xFF000000, 0xFF239E46), R.drawable.flag_ly),
        spec("TN", "تونس", "Tunisia", listOf(0xFFE70013, 0xFFFFFFFF, 0xFFE70013), R.drawable.flag_tn),
        spec("DZ", "الجزائر", "Algeria", listOf(0xFF006633, 0xFFFFFFFF), R.drawable.flag_dz, isVerticalStripes = true),
        spec("MA", "المغرب", "Morocco", listOf(0xFFC1272D, 0xFF006233, 0xFFC1272D), R.drawable.flag_ma),
        spec("MR", "موريتانيا", "Mauritania", listOf(0xFFD01C1F, 0xFF00A95C, 0xFFD01C1F), R.drawable.flag_mr),
        spec("SO", "الصومال", "Somalia", listOf(0xFF4189DD, 0xFFFFFFFF, 0xFF4189DD), R.drawable.flag_so),
        spec("DJ", "جيبوتي", "Djibouti", listOf(0xFF6AB2E7, 0xFF12AD2B), R.drawable.flag_dj),
        spec("KM", "جزر القمر", "Comoros", listOf(0xFFFFC61E, 0xFFFFFFFF, 0xFFCE1126, 0xFF3A75C4), R.drawable.flag_km)
    )

    private val byIso: Map<String, ArabCountrySpec> = allCountries.associateBy { it.isoCode }

    private val byNormalizedName: Map<String, ArabCountrySpec> = buildMap {
        for (item in allCountries) {
            put(normalizeName(item.nameAr), item)
            put(normalizeName(item.nameEn), item)
        }
        // Helpful common Arabic/English aliases
        byIso["AE"]?.let { put(normalizeName("الإمارات"), it); put(normalizeName("UAE"), it) }
        byIso["OM"]?.let { put(normalizeName("عمان"), it) }
        byIso["SA"]?.let { put(normalizeName("السعودية"), it) }
        byIso["PS"]?.let { put(normalizeName("دولة فلسطين"), it) }
    }

    val allFlagStyles: List<CountryFlagStyle> = allCountries.map { it.flagStyle }

    fun findByCode(code: String?): ArabCountrySpec? {
        val norm = code?.trim()?.uppercase().orEmpty()
        if (norm.isEmpty()) return null
        return byIso[norm]
    }

    fun findByNameOrCode(code: String?, name: String?): ArabCountrySpec? {
        return findByCode(code) ?: name?.let { byNormalizedName[normalizeName(it)] }
    }

    fun resolveFlagStyle(
        countryCode: String?,
        countryName: String? = null,
        fallbackColors: List<Long> = emptyList(),
        isVerticalStripes: Boolean = false
    ): CountryFlagStyle {
        val official = findByNameOrCode(countryCode, countryName)
        if (official != null) {
            return official.flagStyle.copy(
                countryName = countryName?.takeIf { it.isNotBlank() } ?: official.nameAr
            )
        }
        return CountryFlagStyle(
            countryCode = countryCode?.trim()?.uppercase().orEmpty().ifBlank { "AR" },
            countryName = countryName?.trim().orEmpty().ifBlank { "غير محدد" },
            stripeColors = fallbackColors.ifEmpty { listOf(0xFF007A3D, 0xFFFFFFFF, 0xFF000000) },
            isVerticalStripes = isVerticalStripes,
            drawableResId = null
        )
    }

    @DrawableRes
    fun drawableResFor(countryCode: String?): Int? = findByCode(countryCode)?.drawableResId

    fun assetKeyFor(countryCode: String): String {
        val normalized = findByCode(countryCode)?.isoCode ?: countryCode.trim().uppercase()
        return "flag_${normalized.lowercase()}"
    }

    fun parseCountryCodeFromAssetKey(assetKey: String?): String? {
        val clean = assetKey?.trim()?.lowercase().orEmpty()
        if (!clean.startsWith("flag_")) return null
        val code = clean.removePrefix("flag_").uppercase()
        return findByCode(code)?.isoCode
    }

    private fun spec(
        isoCode: String,
        nameAr: String,
        nameEn: String,
        stripeColors: List<Long>,
        @DrawableRes drawableResId: Int,
        isVerticalStripes: Boolean = false
    ): ArabCountrySpec {
        return ArabCountrySpec(
            isoCode = isoCode,
            nameAr = nameAr,
            nameEn = nameEn,
            flagStyle = CountryFlagStyle(
                countryCode = isoCode,
                countryName = nameAr,
                stripeColors = stripeColors,
                isVerticalStripes = isVerticalStripes,
                drawableResId = drawableResId
            ),
            drawableResId = drawableResId
        )
    }

    private fun normalizeName(input: String): String {
        return input.trim()
            .lowercase()
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ُ", "")
            .replace("َ", "")
            .replace("ِ", "")
    }
}
