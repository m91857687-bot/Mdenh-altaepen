package com.example.cityengine

import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.CountryFlagStyle
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.House
import com.example.cityengine.model.HousePalette
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.world.PlotManager
import com.example.cityengine.world.SpatialValidator
import com.example.cityengine.world.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialValidatorTest {

    private val tiles = buildMap {
        for (y in 0 until 6) {
            for (x in 0 until 6) {
                val coord = GridCoord(x, y)
                val type = when {
                    x == 2 || y == 2 -> CityTileType.ROAD
                    coord == GridCoord(1, 1) || coord == GridCoord(4, 4) -> CityTileType.PLOT_LOT
                    else -> CityTileType.GRASS
                }
                put(coord, type)
            }
        }
    }

    private val validator = SpatialValidator(6, 6, tiles)
    private val sampleFlag = CountryFlagStyle("SA", "السعودية", listOf(0xFF167D46, 0xFFFFFFFF))
    private val samplePalette = HousePalette(0xFFFFFFFF, 0xFFE0E0E0, 0xFFC85A3A, 0xFF9F4025, 0xFF83321C, 0xFF5D3A29)

    @Test
    fun `rejects placing house on road or grass or out of bounds`() {
        val roadPlot = Plot("plot_bad_road", GridCoord(2, 1), PlotStatus.AVAILABLE)
        val grassPlot = Plot("plot_bad_grass", GridCoord(0, 0), PlotStatus.AVAILABLE)
        val outPlot = Plot("plot_bad_oob", GridCoord(6, 1), PlotStatus.AVAILABLE)

        assertTrue(validator.validateCanPlaceHouse(roadPlot, emptyList(), listOf(roadPlot)) is ValidationResult.Invalid)
        assertTrue(validator.validateCanPlaceHouse(grassPlot, emptyList(), listOf(grassPlot)) is ValidationResult.Invalid)
        assertTrue(validator.validateCanPlaceHouse(outPlot, emptyList(), listOf(outPlot)) is ValidationResult.Invalid)
    }

    @Test
    fun `rejects overlapping houses or occupied plots`() {
        val plot1 = Plot("plot_1", GridCoord(1, 1), PlotStatus.AVAILABLE)
        val plot2 = Plot("plot_2", GridCoord(4, 4), PlotStatus.AVAILABLE)
        val manager = PlotManager(listOf(plot1, plot2), emptyList(), validator)

        val house1 = House("house_1", "plot_1", GridCoord(1, 1), "سالم", 4, sampleFlag, samplePalette)
        assertTrue(manager.assignHouseToPlot(house1) is ValidationResult.Valid)
        assertEquals(PlotStatus.OCCUPIED, manager.getPlotById("plot_1")?.status)

        val overlappingHouse = House("house_2", "plot_1", GridCoord(1, 1), "علي", 3, sampleFlag, samplePalette)
        assertTrue(manager.assignHouseToPlot(overlappingHouse) is ValidationResult.Invalid)
    }

    @Test
    fun `moving house updates old plot to AVAILABLE and target plot to OCCUPIED`() {
        val plot1 = Plot("plot_1", GridCoord(1, 1), PlotStatus.AVAILABLE)
        val plot2 = Plot("plot_2", GridCoord(4, 4), PlotStatus.AVAILABLE)
        val manager = PlotManager(listOf(plot1, plot2), emptyList(), validator)

        val house1 = House("house_1", "plot_1", GridCoord(1, 1), "سالم", 4, sampleFlag, samplePalette)
        manager.assignHouseToPlot(house1)

        val moveResult = manager.moveHouse("house_1", "plot_2")
        assertTrue(moveResult is ValidationResult.Valid)
        assertEquals(PlotStatus.AVAILABLE, manager.getPlotById("plot_1")?.status)
        assertEquals(null, manager.getPlotById("plot_1")?.houseId)
        assertEquals(PlotStatus.OCCUPIED, manager.getPlotById("plot_2")?.status)
        assertEquals("house_1", manager.getPlotById("plot_2")?.houseId)
        assertEquals(GridCoord(4, 4), manager.getHouseById("house_1")?.coord)
    }
}
