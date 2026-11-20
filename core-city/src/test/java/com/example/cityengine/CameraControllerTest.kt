package com.example.cityengine

import com.example.cityengine.camera.CameraController
import com.example.cityengine.model.WorldBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraControllerTest {

    private val bounds = WorldBounds(
        minX = -600f,
        maxX = 600f,
        minY = -100f,
        maxY = 900f
    )

    @Test
    fun `camera clamps pan within world bounds`() {
        val camera = CameraController(bounds, initialZoom = 1.0f)
        camera.updateViewport(width = 1080f, height = 1920f)
        camera.centerOnWorld(0f, 400f, targetZoom = 1.0f)

        // Pan massively in all directions
        camera.panBy(50_000f, 50_000f)
        var center = camera.currentCenterWorld()
        assertTrue("minX violated: ${center.x}", center.x >= bounds.minX - 0.01f)
        assertTrue("minY violated: ${center.y}", center.y >= bounds.minY - 0.01f)

        camera.panBy(-100_000f, -100_000f)
        center = camera.currentCenterWorld()
        assertTrue("maxX violated: ${center.x}", center.x <= bounds.maxX + 0.01f)
        assertTrue("maxY violated: ${center.y}", center.y <= bounds.maxY + 0.01f)
    }

    @Test
    fun `zoom is clamped between minZoom and maxZoom`() {
        val camera = CameraController(bounds, minZoom = 0.55f, maxZoom = 2.8f, initialZoom = 1.0f)
        camera.updateViewport(1080f, 1920f)

        camera.zoomAt(zoomFactor = 10f, focalScreenX = 540f, focalScreenY = 960f)
        assertEquals(2.8f, camera.zoom, 0.001f)

        camera.zoomAt(zoomFactor = 0.05f, focalScreenX = 540f, focalScreenY = 960f)
        assertEquals(0.55f, camera.zoom, 0.001f)
    }

    @Test
    fun `zoomAt preserves world point under focal screen coordinate`() {
        val camera = CameraController(bounds, minZoom = 0.5f, maxZoom = 3.0f, initialZoom = 1.0f)
        camera.updateViewport(1080f, 1920f)
        camera.centerOnWorld(50f, 400f, targetZoom = 1.0f)

        val focalX = 320f
        val focalY = 740f
        val worldBefore = camera.screenToWorld(focalX, focalY)

        camera.zoomAt(zoomFactor = 1.6f, focalScreenX = focalX, focalScreenY = focalY)
        val worldAfter = camera.screenToWorld(focalX, focalY)

        assertEquals(worldBefore.x, worldAfter.x, 0.05f)
        assertEquals(worldBefore.y, worldAfter.y, 0.05f)
    }

    @Test
    fun `screenToWorld and worldToScreen are exact inverses`() {
        val camera = CameraController(bounds, initialZoom = 1.45f)
        camera.updateViewport(1200f, 2000f)
        camera.centerOnWorld(120f, 350f, targetZoom = 1.45f)

        val screen = camera.worldToScreen(85f, 410f)
        val world = camera.screenToWorld(screen.x, screen.y)
        assertEquals(85f, world.x, 0.01f)
        assertEquals(410f, world.y, 0.01f)
    }
}
