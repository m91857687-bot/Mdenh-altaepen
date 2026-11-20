package com.example.cityengine

import com.example.cityengine.camera.CameraController
import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.House
import com.example.cityengine.model.Plot
import com.example.cityengine.model.WorldPoint
import com.example.cityengine.projection.IsoProjection
import com.example.cityengine.render.CityRenderer
import com.example.cityengine.render.FlagRenderer
import com.example.cityengine.render.HouseRenderer
import com.example.cityengine.render.RoadRenderer
import com.example.cityengine.render.TreeRenderer

/**
 * Facade coordinating the Isometric 2.5D City Engine:
 * projection, camera, depth-sorted rendering, and accurate 2.5D house hit-testing.
 */
class CityEngine(
    val projection: IsoProjection = IsoProjection(),
    initialGridWidth: Int = 12,
    initialGridHeight: Int = 12
) {
    val worldBounds = projection.computeWorldBounds(initialGridWidth, initialGridHeight)
    val cameraController = CameraController(worldBounds = worldBounds)

    private val flagRenderer = FlagRenderer()
    val houseRenderer = HouseRenderer(projection, flagRenderer)
    private val roadRenderer = RoadRenderer(projection)
    private val treeRenderer = TreeRenderer(projection)
    val renderer = CityRenderer(projection, roadRenderer, houseRenderer, treeRenderer)

    fun initialFocusWorld(scene: CitySceneState): WorldPoint {
        return projection.gridToWorld(scene.gridWidth * 0.5f, scene.gridHeight * 0.5f)
    }

    /**
     * Performs accurate 2.5D front-to-back hit-testing on houses using each house's full
     * roof+wall+base silhouette polygon in world coordinates.
     */
    fun findHouseAtScreenPoint(
        screenX: Float,
        screenY: Float,
        scene: CitySceneState
    ): House? {
        val worldPoint = cameraController.screenToWorld(screenX, screenY)
        return findHouseAtWorldPoint(worldPoint.x, worldPoint.y, scene)
    }

    fun findHouseAtWorldPoint(
        worldX: Float,
        worldY: Float,
        scene: CitySceneState
    ): House? {
        // Test front-most houses first (descending isometric depth)
        val sortedFrontToBack = scene.houses.sortedByDescending { projection.depthKey(it.coord) }
        for (house in sortedFrontToBack) {
            val polygon = houseRenderer.computeHitPolygon(house.coord)
            if (isPointInPolygon(worldX, worldY, polygon.vertices)) {
                return house
            }
        }
        return null
    }

    fun findPlotAtScreenPoint(
        screenX: Float,
        screenY: Float,
        scene: CitySceneState
    ): Plot? {
        val worldPoint = cameraController.screenToWorld(screenX, screenY)
        return findPlotAtWorldPoint(worldPoint.x, worldPoint.y, scene)
    }

    fun findPlotAtWorldPoint(
        worldX: Float,
        worldY: Float,
        scene: CitySceneState
    ): Plot? {
        val coord = projection.worldToGridCoord(worldX, worldY)
        return scene.plots.firstOrNull { it.coord == coord }
    }

    companion object {
        fun isPointInPolygon(px: Float, py: Float, vertices: List<WorldPoint>): Boolean {
            if (vertices.size < 3) return false
            var inside = false
            var j = vertices.lastIndex
            for (i in vertices.indices) {
                val xi = vertices[i].x
                val yi = vertices[i].y
                val xj = vertices[j].x
                val yj = vertices[j].y

                val intersects = ((yi > py) != (yj > py)) &&
                    (px < (xj - xi) * (py - yi) / ((yj - yi).takeIf { it != 0f } ?: 1e-6f) + xi)
                if (intersects) inside = !inside
                j = i
            }
            return inside
        }
    }
}
