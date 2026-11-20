package com.example.cityengine.world

import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.CountryFlagStyle
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.House
import com.example.cityengine.model.HouseColorPreset
import com.example.cityengine.model.HousePalette
import com.example.cityengine.model.LampPostItem
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.model.TreeItem

/**
 * Generates the Phase 1 starter neighborhood for Madinat Al-Tayibin:
 * - 12x12 clean grid
 * - 12 designated plots with 12 equal-size test houses (explicitly test data for Phase 1)
 * - Connected asphalt streets, stone sidewalks, central green park, organized trees, and lamp posts
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
        // Promenade around central park
        GridCoord(4, 4), GridCoord(5, 4), GridCoord(6, 4), GridCoord(7, 4),
        GridCoord(4, 5), GridCoord(7, 5),
        GridCoord(4, 6), GridCoord(7, 6),
        GridCoord(4, 7), GridCoord(5, 7), GridCoord(6, 7), GridCoord(7, 7),
        // North & South avenue frontages
        GridCoord(1, 2), GridCoord(2, 2), GridCoord(4, 2), GridCoord(5, 2), GridCoord(6, 2), GridCoord(7, 2), GridCoord(9, 2), GridCoord(10, 2),
        GridCoord(1, 9), GridCoord(2, 9), GridCoord(4, 9), GridCoord(5, 9), GridCoord(6, 9), GridCoord(7, 9), GridCoord(9, 9), GridCoord(10, 9),
        // West & East street frontages
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

    private val testFlags = listOf(
        CountryFlagStyle("SA", "المملكة العربية السعودية", listOf(0xFF167D46, 0xFFFFFFFF, 0xFF167D46)),
        CountryFlagStyle("EG", "مصر", listOf(0xFFCE1126, 0xFFFFFFFF, 0xFF1E1E1E)),
        CountryFlagStyle("JO", "الأردن", listOf(0xFF1E1E1E, 0xFFFFFFFF, 0xFF007A3D)),
        CountryFlagStyle("MA", "المغرب", listOf(0xFFC1272D, 0xFF006233, 0xFFC1272D)),
        CountryFlagStyle("AE", "الإمارات", listOf(0xFF00732F, 0xFFFFFFFF, 0xFF1E1E1E)),
        CountryFlagStyle("KW", "الكويت", listOf(0xFF007A3D, 0xFFFFFFFF, 0xFFCE1126)),
        CountryFlagStyle("QA", "قطر", listOf(0xFFFFFFFF, 0xFF8D1B3D), isVerticalStripes = true),
        CountryFlagStyle("OM", "عُمان", listOf(0xFFFFFFFF, 0xFFDB161B, 0xFF008000)),
        CountryFlagStyle("TN", "تونس", listOf(0xFFE70013, 0xFFFFFFFF, 0xFFE70013)),
        CountryFlagStyle("LB", "لبنان", listOf(0xFFED1C24, 0xFFFFFFFF, 0xFFED1C24)),
        CountryFlagStyle("BH", "البحرين", listOf(0xFFFFFFFF, 0xFFCE1126), isVerticalStripes = true),
        CountryFlagStyle("PS", "فلسطين", listOf(0xFF1E1E1E, 0xFFFFFFFF, 0xFF007A3D))
    )

    // Explicit Phase 1 test resident data for verifying selection and info card display
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

    val flagPresets: List<CountryFlagStyle> = testFlags

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
}
