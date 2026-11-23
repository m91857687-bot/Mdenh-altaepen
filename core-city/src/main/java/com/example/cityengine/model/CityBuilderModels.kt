package com.example.cityengine.model

data class GridFootprint(
    val origin: GridCoord,
    val widthCells: Int = 1,
    val heightCells: Int = 1,
    val rotation: BuildingRotation = BuildingRotation.DEG_0
) {
    fun effectiveWidth(): Int =
        if (rotation == BuildingRotation.DEG_90 || rotation == BuildingRotation.DEG_270) {
            heightCells.coerceAtLeast(1)
        } else {
            widthCells.coerceAtLeast(1)
        }

    fun effectiveHeight(): Int =
        if (rotation == BuildingRotation.DEG_90 || rotation == BuildingRotation.DEG_270) {
            widthCells.coerceAtLeast(1)
        } else {
            heightCells.coerceAtLeast(1)
        }

    val maxXExclusive: Int get() = origin.x + effectiveWidth()
    val maxYExclusive: Int get() = origin.y + effectiveHeight()

    fun overlaps(other: GridFootprint): Boolean {
        return origin.x < other.maxXExclusive &&
            maxXExclusive > other.origin.x &&
            origin.y < other.maxYExclusive &&
            maxYExclusive > other.origin.y
    }

    fun contains(coord: GridCoord): Boolean {
        return coord.x in origin.x until maxXExclusive &&
            coord.y in origin.y until maxYExclusive
    }

    fun allCoords(): List<GridCoord> {
        val w = effectiveWidth()
        val h = effectiveHeight()
        return buildList(w * h) {
            for (dy in 0 until h) {
                for (dx in 0 until w) {
                    add(GridCoord(origin.x + dx, origin.y + dy))
                }
            }
        }
    }
}

data class DistrictZone(
    val id: String,
    val nameAr: String,
    val code: String,
    val themeColorHex: Long,
    val origin: GridCoord,
    val widthCells: Int,
    val heightCells: Int,
    val ambientStyle: String = "RESIDENTIAL"
) {
    fun contains(coord: GridCoord): Boolean {
        return coord.x in origin.x until (origin.x + widthCells) &&
            coord.y in origin.y until (origin.y + heightCells)
    }
}

enum class PublicBuildingType(val labelAr: String, val defaultWidth: Int, val defaultHeight: Int) {
    PRESIDENTIAL_PALACE("القصر الرئاسي", 3, 3),
    CENTRAL_BANK("البنك المركزي", 2, 2),
    GOVERNMENT_HALL("مبنى الحكومة", 2, 2),
    MUNICIPALITY("البلدية", 2, 2),
    HOSPITAL("المستشفى", 2, 2),
    SCHOOL_UNIVERSITY("مدرسة وجامعة", 2, 2),
    COMMERCIAL_MALL("سوق ومركز تجاري", 2, 2),
    RESTAURANT_CAFE("مطعم ومقهى", 1, 1),
    FUEL_STATION("محطة وقود", 2, 1),
    RECREATION_CENTER("منشأة ترفيهية", 2, 2),
    MINE_INDUSTRIAL("منجم ومرفق صناعي", 2, 2),
    CUSTOM_LANDMARK("معلم مخصص", 1, 1)
}

data class PublicBuilding(
    val id: String,
    val nameAr: String,
    val type: PublicBuildingType,
    val coord: GridCoord,
    val widthCells: Int = type.defaultWidth,
    val heightCells: Int = type.defaultHeight,
    val rotation: BuildingRotation = BuildingRotation.DEG_0,
    val upgradeLevel: Int = 1,
    val primaryColorHex: Long = 0xFFEFE7D8,
    val accentColorHex: Long = 0xFF2D6A8E,
    val assetKey: String = "building_${type.name.lowercase()}",
    val districtId: String? = null,
    val isLitAtNight: Boolean = true,
    val descriptionAr: String? = null,
    val publicInfoAr: String? = null
) {
    fun footprint(): GridFootprint = GridFootprint(coord, widthCells, heightCells, rotation)
}

enum class EnvironmentPropType(val labelAr: String) {
    TREE_BROADLEAF("شجرة كثيفة"),
    TREE_CYPRESS("شجرة سرو"),
    PALM_TREE("نخلة"),
    LAMP_POST("عمود إنارة"),
    BENCH("مقعد"),
    FOUNTAIN("نافورة"),
    STATUE("تمثال ومعلم"),
    ROAD_SIGN("لوحة إرشادية"),
    FENCE_SEGMENT("سياج"),
    GATE_ARCH("بوابة"),
    PARKING_LOT("موقف سيارات"),
    FLOWER_BED("حوض زهور"),
    GARDEN_GAZEBO("مظلة حديقة"),
    SIDEWALK_PLANTER("أحواض رصيف"),
    CROSSWALK_MARKER("معبر مشاة"),
    CITY_FLAG("علم دولة عربية"),
    SHRUB_BUSH("شجيرة"),
    FLOWER_CLUSTER("زهور ونباتات"),
    GREEN_LAWN("مساحة خضراء"),
    PUBLIC_CLOCK("ساعة عامة"),
    TRASH_BIN("سلة نظافة"),
    SAFETY_BARRIER("حاجز مروري"),
    ORNAMENT_ARCH("زخرفة وقوس"),
    SEASONAL_DECOR("زينة واحتفالات")
}

enum class PropLayer {
    GROUND_OVERLAY,
    STRUCTURE,
    LIGHT_FIXTURE
}

data class EnvironmentProp(
    val id: String,
    val type: EnvironmentPropType,
    val coord: GridCoord,
    val widthCells: Int = 1,
    val heightCells: Int = 1,
    val rotation: BuildingRotation = BuildingRotation.DEG_0,
    val scale: Float = 1f,
    val variant: Int = 0,
    val labelAr: String? = null,
    val layer: PropLayer = PropLayer.STRUCTURE,
    val primaryColorHex: Long = 0xFF4F934D,
    val assetKey: String = "prop_${type.name.lowercase()}"
) {
    fun footprint(): GridFootprint = GridFootprint(coord, widthCells, heightCells, rotation)
}

data class RoadSegment(
    val id: String,
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

enum class LightSourceType {
    STREET_LAMP,
    WINDOW_GLOW,
    ENTRANCE_LIGHT,
    FACADE_FLOOD,
    FOUNTAIN_GLOW,
    PARK_LANTERN,
    SIGN_BACKLIGHT
}

data class CityLightSource(
    val id: String,
    val type: LightSourceType,
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

enum class TimeOfDayPhase(val labelAr: String) {
    DAY("نهار"),
    SUNSET("غروب"),
    NIGHT("ليل")
}

data class CityLightingState(
    val phase: TimeOfDayPhase = TimeOfDayPhase.DAY,
    val sunAzimuthDeg: Float = 42f,
    val ambientDimming: Float = 0f,
    val nightLampAutoOn: Boolean = true
)

enum class EditorPreviewKind {
    FOOTPRINT,
    DISTRICT,
    ROAD_LINE,
    TILE_BRUSH
}

data class EditorPreviewOverlay(
    val coords: List<GridCoord>,
    val origin: GridCoord,
    val widthCells: Int = 1,
    val heightCells: Int = 1,
    val rotation: BuildingRotation = BuildingRotation.DEG_0,
    val isValid: Boolean,
    val kind: EditorPreviewKind = EditorPreviewKind.FOOTPRINT,
    val accentColorHex: Long? = null,
    val label: String? = null
)

