package com.example.cityengine

import com.example.cityengine.model.GridCoord
import com.example.cityengine.world.StarterCityMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CityHitTestingTest {

    @Test
    fun `tapping each house center on screen hits the exact house`() {
        val scene = StarterCityMap.createScene()
        val engine = CityEngine(initialGridWidth = scene.gridWidth, initialGridHeight = scene.gridHeight)
        val focus = engine.initialFocusWorld(scene)
        engine.cameraController.updateViewport(1080f, 1920f, focus)

        for (house in scene.houses) {
            val worldCenter = engine.projection.tileCenterWorld(house.coord)
            // Tap slightly above base center (middle of house wall/roof body)
            val screen = engine.cameraController.worldToScreen(worldCenter.x, worldCenter.y - 20f)
            val hit = engine.findHouseAtScreenPoint(screen.x, screen.y, scene)
            assertEquals("Expected to hit ${house.id}", house.id, hit?.id)
        }
    }

    @Test
    fun `tapping house remains accurate after camera pan and pinch zoom`() {
        val scene = StarterCityMap.createScene()
        val engine = CityEngine(initialGridWidth = scene.gridWidth, initialGridHeight = scene.gridHeight)
        engine.cameraController.updateViewport(1080f, 1920f, engine.initialFocusWorld(scene))

        engine.cameraController.panBy(180f, -120f)
        engine.cameraController.zoomAt(zoomFactor = 1.75f, focalScreenX = 400f, focalScreenY = 850f)

        val targetHouse = scene.houses.first { it.id == "house_07" }
        val worldCenter = engine.projection.tileCenterWorld(targetHouse.coord)
        val screen = engine.cameraController.worldToScreen(worldCenter.x, worldCenter.y - 25f)

        val hit = engine.findHouseAtScreenPoint(screen.x, screen.y, scene)
        assertEquals(targetHouse.id, hit?.id)
    }

    @Test
    fun `tapping street or central park returns null`() {
        val scene = StarterCityMap.createScene()
        val engine = CityEngine(initialGridWidth = scene.gridWidth, initialGridHeight = scene.gridHeight)
        engine.cameraController.updateViewport(1080f, 1920f, engine.initialFocusWorld(scene))

        val roadCenter = engine.projection.tileCenterWorld(GridCoord(3, 4))
        val roadScreen = engine.cameraController.worldToScreen(roadCenter.x, roadCenter.y)
        assertNull(engine.findHouseAtScreenPoint(roadScreen.x, roadScreen.y, scene))

        val parkCenter = engine.projection.tileCenterWorld(GridCoord(5, 5))
        val parkScreen = engine.cameraController.worldToScreen(parkCenter.x, parkCenter.y)
        assertNull(engine.findHouseAtScreenPoint(parkScreen.x, parkScreen.y, scene))
    }

    @Test
    fun `tapping designated plot tile returns exact Plot and street returns null`() {
        val scene = StarterCityMap.createScene()
        val engine = CityEngine(initialGridWidth = scene.gridWidth, initialGridHeight = scene.gridHeight)
        engine.cameraController.updateViewport(1080f, 1920f, engine.initialFocusWorld(scene))

        val plot10 = scene.plots.first { it.id == "plot_10" }
        val plotWorld = engine.projection.tileCenterWorld(plot10.coord)
        val plotScreen = engine.cameraController.worldToScreen(plotWorld.x, plotWorld.y)
        assertEquals("plot_10", engine.findPlotAtScreenPoint(plotScreen.x, plotScreen.y, scene)?.id)

        val streetWorld = engine.projection.tileCenterWorld(GridCoord(3, 5))
        val streetScreen = engine.cameraController.worldToScreen(streetWorld.x, streetWorld.y)
        assertNull(engine.findPlotAtScreenPoint(streetScreen.x, streetScreen.y, scene))
    }
}
