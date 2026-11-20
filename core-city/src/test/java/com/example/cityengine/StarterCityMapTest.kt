package com.example.cityengine

import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.world.SpatialValidator
import com.example.cityengine.world.StarterCityMap
import com.example.cityengine.world.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterCityMapTest {

    @Test
    fun `starter map has exactly 12 valid non-overlapping plots and 12 distinct houses`() {
        val scene = StarterCityMap.createScene()

        assertEquals(12, scene.plots.size)
        assertEquals(12, scene.houses.size)
        assertEquals(12, scene.plots.map { it.id }.toSet().size)
        assertEquals(12, scene.plots.map { it.coord }.toSet().size)
        assertEquals(12, scene.houses.map { it.coord }.toSet().size)
        assertTrue(scene.plots.all { it.status == PlotStatus.OCCUPIED && it.houseId != null })

        val validator = SpatialValidator(scene.gridWidth, scene.gridHeight, scene.tiles)
        val validation = validator.validateCityLayout(scene.plots, scene.houses)
        assertTrue(
            "Expected valid starter map layout, got: $validation",
            validation is ValidationResult.Valid
        )
    }

    @Test
    fun `starter map includes roads sidewalks park trees and lamp posts without overlaps`() {
        val scene = StarterCityMap.createScene()
        val houseCoords = scene.houses.map { it.coord }.toSet()

        assertTrue(scene.tiles.values.any { it == CityTileType.ROAD })
        assertTrue(scene.tiles.values.any { it == CityTileType.SIDEWALK })
        assertTrue(scene.tiles.values.any { it == CityTileType.PARK_GREEN })
        assertTrue(scene.trees.isNotEmpty())
        assertTrue(scene.lampPosts.isNotEmpty())

        for (tree in scene.trees) {
            val tile = scene.tiles[tree.coord]
            assertTrue("Tree ${tree.id} cannot be on ROAD or PLOT_LOT", tile != CityTileType.ROAD && tile != CityTileType.PLOT_LOT)
            assertTrue("Tree ${tree.id} overlaps house", tree.coord !in houseCoords)
        }

        for (lamp in scene.lampPosts) {
            val tile = scene.tiles[lamp.coord]
            assertEquals("Lamp ${lamp.id} should sit on SIDEWALK", CityTileType.SIDEWALK, tile)
            assertTrue("Lamp ${lamp.id} overlaps house", lamp.coord !in houseCoords)
        }
    }
}
