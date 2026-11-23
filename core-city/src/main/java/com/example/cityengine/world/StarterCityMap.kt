package com.example.cityengine.world

import com.example.cityengine.assets.CityAssetRegistry
import com.example.cityengine.model.BuildingRotation
import com.example.cityengine.model.CityLightSource
import com.example.cityengine.model.CityLightingState
import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.CountryFlagStyle
import com.example.cityengine.model.DistrictZone
import com.example.cityengine.model.EnvironmentProp
import com.example.cityengine.model.EnvironmentPropType
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.House
import com.example.cityengine.model.HouseColorPreset
import com.example.cityengine.model.HouseDesignStyle
import com.example.cityengine.model.HousePalette
import com.example.cityengine.model.LampPostItem
import com.example.cityengine.model.LightSourceType
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.model.PropLayer
import com.example.cityengine.model.PublicBuilding
import com.example.cityengine.model.PublicBuildingType
import com.example.cityengine.model.RoadSegment
import com.example.cityengine.model.TileTextureVariant
import com.example.cityengine.model.TimeOfDayPhase
import com.example.cityengine.model.TreeItem

/**
 * Generates both:
 * 1) [createScene]: the clean Phase-1 12x12 starter neighborhood (100% compatible with existing seed & tests).
 * 2) [createExpandedShowcaseCity]: an expanded multi-district 26x26 showcase demonstrating civic buildings,
 *    multi-cell plots, diverse residential styles & upgrade levels, environment props, and interactive lighting.
 */
object StarterCityMap {

    const val GRID_WIDTH = 12
    const val GRID_HEIGHT = 12

    private val plotSpecs = listOf(
        "plot_01" to GridCoord(1, 1),
        "plot_02" to GridCoord(5, 1),
        "plot_03" to GridCoord(6, 1),
        "plot_04" to GridCoord(10, 1),
        "plot_05" to GridCoord(1, 5),
        "plot_06" to GridCoord(1, 6),
        "plot_07" to GridCoord(10, 5),
        "plot_08" to GridCoord(10, 6),
        "plot_09" to GridCoord(1, 10),
        "plot_10" to GridCoord(5, 10),
        "plot_11" to GridCoord(6, 10),
        "plot_12" to GridCoord(10, 10)
    )

    private val parkCoords = setOf(
        GridCoord(5, 5),
        GridCoord(6, 5),
        GridCoord(5, 6),
        GridCoord(6, 6)
    )

    private val sidewalkCoords = setOf(
        GridCoord(4, 4), GridCoord(5, 4), GridCoord(6, 4), GridCoord(7, 4),
        GridCoord(4, 5), GridCoord(7, 5),
        GridCoord(4, 6), GridCoord(7, 6),
        GridCoord(4, 7), GridCoord(5, 7), GridCoord(6, 7), GridCoord(7, 7),
        GridCoord(1, 2), GridCoord(2, 2), GridCoord(4, 2), GridCoord(5, 2), GridCoord(6, 2), GridCoord(7, 2), GridCoord(9, 2), GridCoord(10, 2),
        GridCoord(1, 9), GridCoord(2, 9), GridCoord(4, 9), GridCoord(5, 9), GridCoord(6, 9), GridCoord(7, 9), GridCoord(9, 9), GridCoord(10, 9),
        GridCoord(2, 4), GridCoord(2, 5), GridCoord(2, 6), GridCoord(2, 7),
        GridCoord(9, 4), GridCoord(9, 5), GridCoord(9, 6), GridCoord(9, 7)
    )

    private val testPalettes = listOf(
        HousePalette(0xFFF6EFE2, 0xFFE3D7C3, 0xFFC85A3A, 0xFF9F4025, 0xFF83321C, 0xFF5D3A29),
        HousePalette(0xFFF1F5F8, 0xFFD9E3EB, 0xFF2D6A8E, 0xFF1F4E6B, 0xFF163A50, 0xFF4A3728),
        HousePalette(0xFFFBF3E4, 0xFFE8DAC0, 0xFFD49234, 0xFFB0731E, 0xFF8C5A14, 0xFF5E3A24),
        HousePalette(0xFFF2F5F0, 0xFFDDE5D9, 0xFF4F7A5A, 0xFF395B42, 0xFF2A4531, 0xFF543C2D),
        HousePalette(0xFFFAF0F0, 0xFFE8D4D4, 0xFFB5495B, 0xFF8F3343, 0xFF6E2431, 0xFF4F342C),
        HousePalette(0xFFF0F6F5, 0xFFD7E6E4, 0xFF2C7F78, 0xFF1D5E59, 0xFF144642, 0xFF5C3E2B),
        HousePalette(0xFFF7F2EA, 0xFFE4DACB, 0xFF6B5B8C, 0xFF4F426B, 0xFF3A3050, 0xFF4E3629),
        HousePalette(0xFFF5F3ED, 0xFFE0DDD2, 0xFFA66A3C, 0xFF834F28, 0xFF643B1B, 0xFF3F2C22),
        HousePalette(0xFFF2F4F8, 0xFFDCE0EA, 0xFF3F5E94, 0xFF2D4570, 0xFF1F3254, 0xFF583828),
        HousePalette(0xFFFAF4EB, 0xFFE6DBC9, 0xFFC46D45, 0xFF9E512E, 0xFF7D3E21, 0xFF4E3325),
        HousePalette(0xFFF3F7F2, 0xFFDDE7DA, 0xFF5D8A4E, 0xFF436937, 0xFF314F27, 0xFF563B2A),
        HousePalette(0xFFF8F1F4, 0xFFE4D6DC, 0xFF9C4B68, 0xFF7A364E, 0xFF5C2639, 0xFF4D3428)
    )

    private val starterFlagCodes = listOf(
        "SA", "EG", "JO", "MA", "AE", "KW", "QA", "OM", "TN", "LB", "BH", "PS"
    )

    private val testFlags: List<CountryFlagStyle> = starterFlagCodes.map { code ->
        com.example.cityengine.model.ArabCountriesRegistry.resolveFlagStyle(code)
    }

    private val testResidents = listOf(
        Triple("أبو عبدالله الحربي", 5, 0),
        Triple("م. طارق المنصور", 4, 1),
        Triple("د. ليلى العلي", 3, 2),
        Triple("يوسف الإدريسي", 6, 3),
        Triple("سالم المهيري", 4, 4),
        Triple("فهد الصباح", 5, 5),
        Triple("خالد الكواري", 3, 6),
        Triple("سعيد البلوشي", 4, 7),
        Triple("أمينة بن علي", 2, 8),
        Triple("رامي الخوري", 4, 9),
        Triple("نورة الدوسري", 3, 10),
        Triple("عمر البرغوثي", 5, 11)
    )

    val palettePresets: List<HouseColorPreset> = listOf(
        HouseColorPreset("terracotta", "طوبي دافئ", testPalettes[0]),
        HouseColorPreset("cobalt", "أزرق بحري", testPalettes[1]),
        HouseColorPreset("ochre", "ذهبي رملي", testPalettes[2]),
        HouseColorPreset("sage", "أخضر زيتوني", testPalettes[3]),
        HouseColorPreset("coral", "مرجاني هادئ", testPalettes[4]),
        HouseColorPreset("teal", "تركوازي", testPalettes[5]),
        HouseColorPreset("lavender", "بنفسجي حجري", testPalettes[6]),
        HouseColorPreset("sandstone", "خشبي دافئ", testPalettes[7])
    )

    val flagPresets: List<CountryFlagStyle> = com.example.cityengine.model.ArabCountriesRegistry.allFlagStyles

    fun createScene(): CitySceneState {
        val plotCoords = plotSpecs.map { it.second }.toSet()
        val tiles = buildMap {
            for (y in 0 until GRID_HEIGHT) {
                for (x in 0 until GRID_WIDTH) {
                    val coord = GridCoord(x, y)
                    val type = when {
                        x == 3 || x == 8 || y == 3 || y == 8 -> CityTileType.ROAD
                        coord in plotCoords -> CityTileType.PLOT_LOT
                        coord in parkCoords -> CityTileType.PARK_GREEN
                        coord in sidewalkCoords -> CityTileType.SIDEWALK
                        else -> CityTileType.GRASS
                    }
                    put(coord, type)
                }
            }
        }

        val houses = plotSpecs.mapIndexed { index, (plotId, coord) ->
            val (ownerName, familyCount, styleIdx) = testResidents[index]
            House(
                id = "house_${(index + 1).toString().padStart(2, '0')}",
                plotId = plotId,
                coord = coord,
                ownerName = ownerName,
                familyMembersCount = familyCount,
                flag = testFlags[styleIdx],
                palette = testPalettes[styleIdx]
            )
        }

        val plots = plotSpecs.mapIndexed { index, (plotId, coord) ->
            Plot(
                id = plotId,
                coord = coord,
                status = PlotStatus.OCCUPIED,
                houseId = houses[index].id
            )
        }

        val validator = SpatialValidator(GRID_WIDTH, GRID_HEIGHT, tiles)
        val plotManager = PlotManager(plots, houses, validator)

        val trees = listOf(
            TreeItem("tree_01", GridCoord(0, 0), 1.00f, 0),
            TreeItem("tree_02", GridCoord(2, 0), 0.92f, 1),
            TreeItem("tree_03", GridCoord(4, 0), 1.05f, 0),
            TreeItem("tree_04", GridCoord(7, 0), 0.95f, 1),
            TreeItem("tree_05", GridCoord(9, 0), 1.02f, 0),
            TreeItem("tree_06", GridCoord(11, 0), 0.94f, 1),
            TreeItem("tree_07", GridCoord(0, 4), 1.00f, 1),
            TreeItem("tree_08", GridCoord(0, 7), 0.96f, 0),
            TreeItem("tree_09", GridCoord(11, 4), 0.98f, 0),
            TreeItem("tree_10", GridCoord(11, 7), 1.04f, 1),
            TreeItem("tree_11", GridCoord(5, 5), 1.10f, 0),
            TreeItem("tree_12", GridCoord(6, 6), 1.06f, 1),
            TreeItem("tree_13", GridCoord(0, 11), 0.96f, 0),
            TreeItem("tree_14", GridCoord(2, 11), 1.02f, 1),
            TreeItem("tree_15", GridCoord(4, 11), 0.94f, 0),
            TreeItem("tree_16", GridCoord(7, 11), 1.00f, 1),
            TreeItem("tree_17", GridCoord(9, 11), 0.95f, 0),
            TreeItem("tree_18", GridCoord(11, 11), 1.04f, 1)
        )

        val lampPosts = listOf(
            LampPostItem("lamp_01", GridCoord(2, 2)),
            LampPostItem("lamp_02", GridCoord(4, 2)),
            LampPostItem("lamp_03", GridCoord(7, 2)),
            LampPostItem("lamp_04", GridCoord(9, 2)),
            LampPostItem("lamp_05", GridCoord(2, 4)),
            LampPostItem("lamp_06", GridCoord(9, 4)),
            LampPostItem("lamp_07", GridCoord(2, 7)),
            LampPostItem("lamp_08", GridCoord(9, 7)),
            LampPostItem("lamp_09", GridCoord(2, 9)),
            LampPostItem("lamp_10", GridCoord(4, 9)),
            LampPostItem("lamp_11", GridCoord(7, 9)),
            LampPostItem("lamp_12", GridCoord(9, 9))
        )

        return CitySceneState(
            gridWidth = GRID_WIDTH,
            gridHeight = GRID_HEIGHT,
            tiles = tiles,
            plots = plotManager.plots,
            houses = plotManager.houses,
            trees = trees,
            lampPosts = lampPosts,
            selectedHouseId = null
        )
    }

    /**
     * Expanded 26x26 multi-district city preset demonstrating all Phase-1 engine capabilities:
     * civic institutions, varied house designs & upgrade levels, multi-cell plots, textured plazas,
     * environment props, and day/sunset/night lighting fixtures.
     */
    fun createExpandedShowcaseCity(phase: TimeOfDayPhase = TimeOfDayPhase.DAY): CitySceneState {
        var scene = CityLayoutEditor.expandGrid(createScene(), newWidth = 26, newHeight = 26)

        val districts = listOf(
            DistrictZone("dist_heritage", "حي التراث", "D1", 0xFF3A7D5C, GridCoord(0, 0), 12, 12, "RESIDENTIAL"),
            DistrictZone("dist_civic", "الحي الحكومي والمالي", "D2", 0xFF2D6A8E, GridCoord(13, 0), 13, 12, "CIVIC"),
            DistrictZone("dist_commercial", "حي السوق والخدمات", "D3", 0xFFD49234, GridCoord(0, 13), 12, 13, "COMMERCIAL"),
            DistrictZone("dist_garden_industry", "حي الحدائق والمناجم", "D4", 0xFF6B5B8C, GridCoord(13, 13), 13, 13, "MIXED")
        )
        districts.forEach { scene = CityLayoutEditor.addDistrict(scene, it) }

        // Main boulevards extending across expanded 26x26 grid
        val avenueCoords = buildList {
            for (i in 0 until 26) {
                add(GridCoord(12, i))
                add(GridCoord(i, 12))
                add(GridCoord(20, i))
                add(GridCoord(i, 20))
            }
        }
        scene = CityLayoutEditor.paintTiles(scene, avenueCoords, CityTileType.ROAD, TileTextureVariant.DEFAULT)

        val civicPlazaCoords = buildList {
            for (y in 2..10) {
                for (x in 14..18) {
                    add(GridCoord(x, y))
                }
            }
        }
        scene = CityLayoutEditor.paintTiles(scene, civicPlazaCoords, CityTileType.PLAZA, TileTextureVariant.CHECKER_STONE)

        val farmlandCoords = buildList {
            for (y in 22..24) {
                for (x in 1..6) {
                    add(GridCoord(x, y))
                }
            }
        }
        scene = CityLayoutEditor.paintTiles(scene, farmlandCoords, CityTileType.FARMLAND, TileTextureVariant.FURROWED)

        // Add multi-cell plots in new districts
        val extraPlots = listOf(
            Plot("plot_13", GridCoord(2, 14), PlotStatus.AVAILABLE, widthCells = 2, heightCells = 1, districtId = "dist_commercial", textureVariant = TileTextureVariant.TILE_GRID),
            Plot("plot_14", GridCoord(5, 14), PlotStatus.AVAILABLE, widthCells = 2, heightCells = 2, districtId = "dist_commercial", textureVariant = TileTextureVariant.CHECKER_STONE),
            Plot("plot_15", GridCoord(14, 14), PlotStatus.UNAVAILABLE, widthCells = 2, heightCells = 1, districtId = "dist_garden_industry", textureVariant = TileTextureVariant.COBBLE)
        )
        for (p in extraPlots) {
            scene = CityLayoutEditor.placePlot(scene, p).second
        }

        // Upgrade sample residential homes visually
        val styledHouses = scene.houses.mapIndexed { idx, h ->
            val style = HouseDesignStyle.entries[idx % HouseDesignStyle.entries.size]
            val level = (idx % 3) + 1
            h.copy(
                designStyle = style,
                upgradeLevel = level,
                assetKey = CityAssetRegistry.keyFor(style)
            )
        }
        scene = scene.copy(houses = styledHouses)

        // Public civic, commercial & industrial buildings
        val publicBuildings = listOf(
            PublicBuilding("bld_palace", "القصر الرئاسي", PublicBuildingType.PRESIDENTIAL_PALACE, GridCoord(14, 2), 3, 3, upgradeLevel = 3, primaryColorHex = 0xFFF7EEDB, accentColorHex = 0xFFD4AF37),
            PublicBuilding("bld_bank", "البنك المركزي", PublicBuildingType.CENTRAL_BANK, GridCoord(14, 6), 2, 2, upgradeLevel = 2, primaryColorHex = 0xFFE3EBF1, accentColorHex = 0xFF284B63),
            PublicBuilding("bld_gov", "مبنى الحكومة", PublicBuildingType.GOVERNMENT_HALL, GridCoord(17, 6), 2, 2, upgradeLevel = 2, primaryColorHex = 0xFFE8EEF2, accentColorHex = 0xFF4A6FA5),
            PublicBuilding("bld_muni", "دار البلدية", PublicBuildingType.MUNICIPALITY, GridCoord(22, 2), 2, 2, upgradeLevel = 2, primaryColorHex = 0xFFF4EDE0, accentColorHex = 0xFF3C6E71),
            PublicBuilding("bld_hospital", "مستشفى المدينة", PublicBuildingType.HOSPITAL, GridCoord(22, 6), 2, 2, upgradeLevel = 2, primaryColorHex = 0xFFF8FAFC, accentColorHex = 0xFFC62828),
            PublicBuilding("bld_school", "جامعة الطيبين", PublicBuildingType.SCHOOL_UNIVERSITY, GridCoord(1, 17), 2, 2, upgradeLevel = 2, primaryColorHex = 0xFFFFF4E3, accentColorHex = 0xFFC85A3A),
            PublicBuilding("bld_mall", "السوق المركزي", PublicBuildingType.COMMERCIAL_MALL, GridCoord(8, 14), 2, 2, upgradeLevel = 2, primaryColorHex = 0xFFF2ECE1, accentColorHex = 0xFF2D6A4F),
            PublicBuilding("bld_cafe", "مقهى المجلس", PublicBuildingType.RESTAURANT_CAFE, GridCoord(4, 17), 1, 1, upgradeLevel = 1, primaryColorHex = 0xFFF6EFE2, accentColorHex = 0xFFA44A3F),
            PublicBuilding("bld_fuel", "محطة الطاقة والوقود", PublicBuildingType.FUEL_STATION, GridCoord(6, 17), 2, 1, upgradeLevel = 1, primaryColorHex = 0xFFE5E8EC, accentColorHex = 0xFFE07A5F),
            PublicBuilding("bld_rec", "النادي الترفيهي", PublicBuildingType.RECREATION_CENTER, GridCoord(14, 16), 2, 2, upgradeLevel = 2, primaryColorHex = 0xFFE7F2F0, accentColorHex = 0xFF2A9D8F),
            PublicBuilding("bld_mine", "منجم الحجر والمعادن", PublicBuildingType.MINE_INDUSTRIAL, GridCoord(22, 15), 2, 2, upgradeLevel = 2, primaryColorHex = 0xFF8C7A6B, accentColorHex = 0xFFD99B26)
        )
        for (b in publicBuildings) {
            scene = CityLayoutEditor.placePublicBuilding(scene, b).second
        }

        // Rich environmental props across plazas, boulevards & gardens
        val props = listOf(
            EnvironmentProp("prop_fountain_civic", EnvironmentPropType.FOUNTAIN, GridCoord(17, 4), scale = 1.15f, layer = PropLayer.STRUCTURE),
            EnvironmentProp("prop_statue_civic", EnvironmentPropType.STATUE, GridCoord(18, 3), labelAr = "نُصب التأسيس", layer = PropLayer.STRUCTURE),
            EnvironmentProp("prop_palm_01", EnvironmentPropType.PALM_TREE, GridCoord(13, 1), scale = 1.1f),
            EnvironmentProp("prop_palm_02", EnvironmentPropType.PALM_TREE, GridCoord(13, 5), scale = 1.05f),
            EnvironmentProp("prop_palm_03", EnvironmentPropType.PALM_TREE, GridCoord(13, 9), scale = 1.1f),
            EnvironmentProp("prop_bench_01", EnvironmentPropType.BENCH, GridCoord(16, 5), rotation = BuildingRotation.DEG_90),
            EnvironmentProp("prop_flower_01", EnvironmentPropType.FLOWER_BED, GridCoord(18, 2)),
            EnvironmentProp("prop_gazebo_01", EnvironmentPropType.GARDEN_GAZEBO, GridCoord(17, 16)),
            EnvironmentProp("prop_sign_01", EnvironmentPropType.ROAD_SIGN, GridCoord(11, 13), labelAr = "الحي الحكومي"),
            EnvironmentProp("prop_gate_01", EnvironmentPropType.GATE_ARCH, GridCoord(13, 13)),
            EnvironmentProp("prop_fence_01", EnvironmentPropType.FENCE_SEGMENT, GridCoord(21, 15)),
            EnvironmentProp("prop_parking_01", EnvironmentPropType.PARKING_LOT, GridCoord(8, 17), widthCells = 2, heightCells = 1, layer = PropLayer.GROUND_OVERLAY),
            EnvironmentProp("prop_crosswalk_01", EnvironmentPropType.CROSSWALK_MARKER, GridCoord(12, 5), layer = PropLayer.GROUND_OVERLAY)
        )
        for (prop in props) {
            scene = CityLayoutEditor.placeEnvironmentProp(scene, prop).second
        }

        val roadSegments = listOf(
            RoadSegment("road_main_ew", GridCoord(0, 12), widthCells = 26, heightCells = 1, lanes = 2, hasCrosswalk = true, streetNameAr = "جادة الطيبين الرئيسية"),
            RoadSegment("road_main_ns", GridCoord(12, 0), widthCells = 1, heightCells = 26, lanes = 2, hasCrosswalk = true, streetNameAr = "شارع الرئاسة")
        )

        val extraLights = listOf(
            CityLightSource("light_palace_facade", LightSourceType.FACADE_FLOOD, GridCoord(15, 3), colorHex = 0xFFFFDF8A, intensity = 1.25f, radiusWorld = 160f, directionDeg = 45f, coneAngleDeg = 95f),
            CityLightSource("light_civic_fountain", LightSourceType.FOUNTAIN_GLOW, GridCoord(17, 4), colorHex = 0xFF7BE4FF, intensity = 1.0f, radiusWorld = 105f),
            CityLightSource("light_hospital", LightSourceType.ENTRANCE_LIGHT, GridCoord(22, 7), colorHex = 0xFFFFB4B4, intensity = 0.9f, radiusWorld = 110f),
            CityLightSource("light_mall", LightSourceType.SIGN_BACKLIGHT, GridCoord(8, 15), colorHex = 0xFFFFD166, intensity = 1.1f, radiusWorld = 120f)
        )

        return scene.copy(
            roadSegments = roadSegments,
            lights = extraLights,
            lightingState = CityLightingState(phase = phase)
        )
    }
}
