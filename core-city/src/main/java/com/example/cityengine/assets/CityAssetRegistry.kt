package com.example.cityengine.assets

import com.example.cityengine.model.EnvironmentPropType
import com.example.cityengine.model.HouseDesignStyle
import com.example.cityengine.model.PublicBuildingType
import com.example.cityengine.performance.LodTier
import com.example.core.city.R

enum class AssetCategory {
    RESIDENTIAL_HOUSE,
    PUBLIC_BUILDING,
    ENVIRONMENT_PROP,
    TERRAIN_TILE
}

data class CityAssetSpec(
    val key: String,
    val labelAr: String,
    val category: AssetCategory,
    val drawableResId: Int? = null,
    val defaultWidthCells: Int = 1,
    val defaultHeightCells: Int = 1,
    val supportsRotation: Boolean = true,
    val minDetailLod: LodTier = LodTier.LOW
)

/**
 * Organized catalog mapping logical city elements to bundled Android VectorDrawable assets
 * and architectural rendering metadata. Purely declarative and extensible without touching core loop.
 */
object CityAssetRegistry {

    private val entries: Map<String, CityAssetSpec> = listOf(
        // Residential styles
        CityAssetSpec("house_classic", "بيت كلاسيكي", AssetCategory.RESIDENTIAL_HOUSE, null, 1, 1),
        CityAssetSpec("house_modern_villa", "فيلا حديثة", AssetCategory.RESIDENTIAL_HOUSE, null, 1, 1),
        CityAssetSpec("house_courtyard", "بيت فناء عربي", AssetCategory.RESIDENTIAL_HOUSE, null, 1, 1),
        CityAssetSpec("house_townhome", "منزل متلاصق", AssetCategory.RESIDENTIAL_HOUSE, null, 1, 1),
        CityAssetSpec("house_cottage", "بيت ريفي", AssetCategory.RESIDENTIAL_HOUSE, null, 1, 1),

        // Public & civic buildings backed by crisp VectorDrawable emblems
        CityAssetSpec(
            key = "building_presidential_palace",
            labelAr = "القصر الرئاسي",
            category = AssetCategory.PUBLIC_BUILDING,
            drawableResId = R.drawable.asset_building_palace,
            defaultWidthCells = 3,
            defaultHeightCells = 3
        ),
        CityAssetSpec(
            key = "building_central_bank",
            labelAr = "البنك المركزي",
            category = AssetCategory.PUBLIC_BUILDING,
            drawableResId = R.drawable.asset_building_bank,
            defaultWidthCells = 2,
            defaultHeightCells = 2
        ),
        CityAssetSpec(
            key = "building_government_hall",
            labelAr = "مبنى الحكومة",
            category = AssetCategory.PUBLIC_BUILDING,
            drawableResId = R.drawable.asset_building_government,
            defaultWidthCells = 2,
            defaultHeightCells = 2
        ),
        CityAssetSpec(
            key = "building_municipality",
            labelAr = "البلدية",
            category = AssetCategory.PUBLIC_BUILDING,
            drawableResId = R.drawable.asset_building_government,
            defaultWidthCells = 2,
            defaultHeightCells = 2
        ),
        CityAssetSpec(
            key = "building_hospital",
            labelAr = "المستشفى",
            category = AssetCategory.PUBLIC_BUILDING,
            drawableResId = R.drawable.asset_building_hospital,
            defaultWidthCells = 2,
            defaultHeightCells = 2
        ),
        CityAssetSpec(
            key = "building_school_university",
            labelAr = "مدرسة وجامعة",
            category = AssetCategory.PUBLIC_BUILDING,
            drawableResId = R.drawable.asset_building_school,
            defaultWidthCells = 2,
            defaultHeightCells = 2
        ),
        CityAssetSpec(
            key = "building_commercial_mall",
            labelAr = "سوق ومركز تجاري",
            category = AssetCategory.PUBLIC_BUILDING,
            drawableResId = R.drawable.asset_building_market,
            defaultWidthCells = 2,
            defaultHeightCells = 2
        ),
        CityAssetSpec(
            key = "building_restaurant_cafe",
            labelAr = "مطعم ومقهى",
            category = AssetCategory.PUBLIC_BUILDING,
            drawableResId = R.drawable.asset_building_cafe,
            defaultWidthCells = 1,
            defaultHeightCells = 1
        ),
        CityAssetSpec(
            key = "building_fuel_station",
            labelAr = "محطة وقود",
            category = AssetCategory.PUBLIC_BUILDING,
            drawableResId = R.drawable.asset_building_fuel,
            defaultWidthCells = 2,
            defaultHeightCells = 1
        ),
        CityAssetSpec(
            key = "building_recreation_center",
            labelAr = "منشأة ترفيهية",
            category = AssetCategory.PUBLIC_BUILDING,
            drawableResId = R.drawable.asset_building_recreation,
            defaultWidthCells = 2,
            defaultHeightCells = 2
        ),
        CityAssetSpec(
            key = "building_mine_industrial",
            labelAr = "منجم ومرفق صناعي",
            category = AssetCategory.PUBLIC_BUILDING,
            drawableResId = R.drawable.asset_building_mine,
            defaultWidthCells = 2,
            defaultHeightCells = 2
        ),
        CityAssetSpec(
            key = "building_custom_landmark",
            labelAr = "معلم مخصص",
            category = AssetCategory.PUBLIC_BUILDING,
            drawableResId = R.drawable.asset_building_government,
            defaultWidthCells = 1,
            defaultHeightCells = 1
        ),

        // Environment props
        CityAssetSpec("prop_tree_broadleaf", "شجرة كثيفة", AssetCategory.ENVIRONMENT_PROP, R.drawable.asset_env_tree, 1, 1, supportsRotation = false),
        CityAssetSpec("prop_tree_cypress", "شجرة سرو", AssetCategory.ENVIRONMENT_PROP, R.drawable.asset_env_tree, 1, 1, supportsRotation = false),
        CityAssetSpec("prop_palm_tree", "نخلة", AssetCategory.ENVIRONMENT_PROP, R.drawable.asset_env_tree, 1, 1, supportsRotation = false),
        CityAssetSpec("prop_fountain", "نافورة", AssetCategory.ENVIRONMENT_PROP, R.drawable.asset_env_fountain, 1, 1, supportsRotation = false),
        CityAssetSpec("prop_lamp_post", "عمود إنارة", AssetCategory.ENVIRONMENT_PROP, null, 1, 1, minDetailLod = LodTier.MEDIUM),
        CityAssetSpec("prop_bench", "مقعد", AssetCategory.ENVIRONMENT_PROP, null, 1, 1, minDetailLod = LodTier.MEDIUM),
        CityAssetSpec("prop_statue", "تمثال ومعلم", AssetCategory.ENVIRONMENT_PROP, null, 1, 1),
        CityAssetSpec("prop_road_sign", "لوحة إرشادية", AssetCategory.ENVIRONMENT_PROP, null, 1, 1, minDetailLod = LodTier.MEDIUM),
        CityAssetSpec("prop_fence_segment", "سياج", AssetCategory.ENVIRONMENT_PROP, null, 1, 1, minDetailLod = LodTier.MEDIUM),
        CityAssetSpec("prop_gate_arch", "بوابة", AssetCategory.ENVIRONMENT_PROP, null, 1, 1),
        CityAssetSpec("prop_parking_lot", "موقف سيارات", AssetCategory.ENVIRONMENT_PROP, null, 1, 1),
        CityAssetSpec("prop_flower_bed", "حوض زهور", AssetCategory.ENVIRONMENT_PROP, null, 1, 1, minDetailLod = LodTier.MEDIUM),
        CityAssetSpec("prop_garden_gazebo", "مظلة حديقة", AssetCategory.ENVIRONMENT_PROP, null, 1, 1),
        CityAssetSpec("prop_sidewalk_planter", "أحواض رصيف", AssetCategory.ENVIRONMENT_PROP, null, 1, 1, minDetailLod = LodTier.MEDIUM),
        CityAssetSpec("prop_crosswalk_marker", "معبر مشاة", AssetCategory.ENVIRONMENT_PROP, null, 1, 1, minDetailLod = LodTier.MEDIUM),
        CityAssetSpec("prop_city_flag", "علم دولة عربية", AssetCategory.ENVIRONMENT_PROP, null, 1, 1),
        CityAssetSpec("prop_shrub_bush", "شجيرة", AssetCategory.ENVIRONMENT_PROP, null, 1, 1, supportsRotation = false),
        CityAssetSpec("prop_flower_cluster", "زهور ونباتات", AssetCategory.ENVIRONMENT_PROP, null, 1, 1, supportsRotation = false, minDetailLod = LodTier.MEDIUM),
        CityAssetSpec("prop_green_lawn", "مساحة خضراء", AssetCategory.ENVIRONMENT_PROP, null, 1, 1, supportsRotation = false),
        CityAssetSpec("prop_public_clock", "ساعة عامة", AssetCategory.ENVIRONMENT_PROP, null, 1, 1),
        CityAssetSpec("prop_trash_bin", "سلة نظافة", AssetCategory.ENVIRONMENT_PROP, null, 1, 1, minDetailLod = LodTier.MEDIUM),
        CityAssetSpec("prop_safety_barrier", "حاجز مروري", AssetCategory.ENVIRONMENT_PROP, null, 1, 1, minDetailLod = LodTier.MEDIUM),
        CityAssetSpec("prop_ornament_arch", "زخرفة وقوس", AssetCategory.ENVIRONMENT_PROP, null, 1, 1),
        CityAssetSpec("prop_seasonal_decor", "زينة واحتفالات", AssetCategory.ENVIRONMENT_PROP, null, 1, 1)
    ).associateBy { it.key }

    fun get(key: String): CityAssetSpec? = entries[key]

    fun allSpecs(): List<CityAssetSpec> = entries.values.toList()

    fun keyFor(style: HouseDesignStyle): String = when (style) {
        HouseDesignStyle.CLASSIC_HOME -> "house_classic"
        HouseDesignStyle.MODERN_VILLA -> "house_modern_villa"
        HouseDesignStyle.COURTYARD_HOUSE -> "house_courtyard"
        HouseDesignStyle.TOWNHOME -> "house_townhome"
        HouseDesignStyle.COTTAGE -> "house_cottage"
    }

    fun keyFor(type: PublicBuildingType): String = "building_${type.name.lowercase()}"

    fun keyFor(type: EnvironmentPropType): String = "prop_${type.name.lowercase()}"
}
