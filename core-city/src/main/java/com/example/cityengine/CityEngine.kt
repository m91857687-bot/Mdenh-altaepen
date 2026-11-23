package com.example.cityengine

import com.example.cityengine.camera.CameraController
import com.example.cityengine.model.BuildingRotation
import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.DistrictZone
import com.example.cityengine.model.EnvironmentProp
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.GridFootprint
import com.example.cityengine.model.House
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PublicBuilding
import com.example.cityengine.model.RoadSegment
import com.example.cityengine.model.WorldPoint
import com.example.cityengine.performance.SpatialGridIndex
import com.example.cityengine.projection.IsoProjection
import com.example.cityengine.render.CityRenderer
import com.example.cityengine.render.EnvironmentPropRenderer
import com.example.cityengine.render.FlagRenderer
import com.example.cityengine.render.HouseRenderer
import com.example.cityengine.render.LightingRenderer
import com.example.cityengine.render.PublicBuildingRenderer
import com.example.cityengine.render.RoadRenderer
import com.example.cityengine.render.TreeRenderer

/**
 * Facade coordinating the Isometric 2.5D City Engine:
 * projection, dynamic camera bounds, spatial-indexed hit-testing, and depth-sorted rendering.
 */
class CityEngine(
    val projection: IsoProjection = IsoProjection(),
    initialGridWidth: Int = 12,
    initialGridHeight: Int = 12
) {
    val worldBounds = projection.computeWorldBounds(initialGridWidth, initialGridHeight)
    val cameraController = CameraController(worldBounds = worldBounds)

    private val flagRenderer = FlagRenderer()
    private val lightingRenderer = LightingRenderer(projection)
    val houseRenderer = HouseRenderer(projection, flagRenderer, lightingRenderer)
    val publicBuildingRenderer = PublicBuildingRenderer(projection, lightingRenderer)
    private val environmentPropRenderer = EnvironmentPropRenderer(projection, flagRenderer)
    private val roadRenderer = RoadRenderer(projection)
    private val treeRenderer = TreeRenderer(projection)
    val renderer = CityRenderer(
        projection = projection,
        roadRenderer = roadRenderer,
        houseRenderer = houseRenderer,
        treeRenderer = treeRenderer,
        lightingRenderer = lightingRenderer,
        publicBuildingRenderer = publicBuildingRenderer,
        environmentPropRenderer = environmentPropRenderer
    )

    private var indexedSceneRef: CitySceneState? = null
    private var indexedGridW: Int = initialGridWidth
    private var indexedGridH: Int = initialGridHeight
    private val hitHouseIndex = SpatialGridIndex<House>()
    private val hitBuildingIndex = SpatialGridIndex<PublicBuilding>()
    private val hitPlotIndex = SpatialGridIndex<Plot>()
    private val hitPropIndex = SpatialGridIndex<EnvironmentProp>()

    fun initialFocusWorld(scene: CitySceneState): WorldPoint {
        return projection.gridToWorld(scene.gridWidth * 0.5f, scene.gridHeight * 0.5f)
    }

    fun syncSceneBounds(scene: CitySceneState) {
        if (indexedGridW != scene.gridWidth || indexedGridH != scene.gridHeight) {
            indexedGridW = scene.gridWidth
            indexedGridH = scene.gridHeight
            cameraController.updateWorldBounds(
                projection.computeWorldBounds(scene.gridWidth, scene.gridHeight)
            )
        }
    }

    fun screenToGridCoord(screenX: Float, screenY: Float): GridCoord {
        val worldPoint = cameraController.screenToWorld(screenX, screenY)
        return projection.worldToGridCoord(worldPoint.x, worldPoint.y)
    }

    /**
     * Performs accurate 2.5D front-to-back hit-testing on houses using each house's full
     * roof+wall+base silhouette polygon in world coordinates and SpatialGridIndex culling.
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
        ensureHitIndices(scene)
        val approxCoord = projection.worldToGridCoord(worldX, worldY)
        val candidates = hitHouseIndex.queryRange(
            minX = approxCoord.x - 3,
            maxX = approxCoord.x + 3,
            minY = approxCoord.y - 3,
            maxY = approxCoord.y + 3
        )
        val pool = if (candidates.isNotEmpty()) candidates else scene.houses
        val sortedFrontToBack = pool.sortedByDescending { h ->
            val rotated = h.rotation == BuildingRotation.DEG_90 || h.rotation == BuildingRotation.DEG_270
            val effW = if (rotated) h.heightCells else h.widthCells
            val effH = if (rotated) h.widthCells else h.heightCells
            projection.footprintDepthKey(h.coord, effW, effH)
        }
        for (house in sortedFrontToBack) {
            val polygon = houseRenderer.computeHitPolygon(house)
            if (isPointInPolygon(worldX, worldY, polygon.vertices)) {
                return house
            }
        }
        return null
    }

    fun findPublicBuildingAtScreenPoint(
        screenX: Float,
        screenY: Float,
        scene: CitySceneState
    ): PublicBuilding? {
        val worldPoint = cameraController.screenToWorld(screenX, screenY)
        return findPublicBuildingAtWorldPoint(worldPoint.x, worldPoint.y, scene)
    }

    fun findPublicBuildingAtWorldPoint(
        worldX: Float,
        worldY: Float,
        scene: CitySceneState
    ): PublicBuilding? {
        if (scene.publicBuildings.isEmpty()) return null
        ensureHitIndices(scene)
        val approxCoord = projection.worldToGridCoord(worldX, worldY)
        val candidates = hitBuildingIndex.queryRange(
            minX = approxCoord.x - 4,
            maxX = approxCoord.x + 4,
            minY = approxCoord.y - 4,
            maxY = approxCoord.y + 4
        )
        val pool = if (candidates.isNotEmpty()) candidates else scene.publicBuildings
        val sortedFrontToBack = pool.sortedByDescending { b ->
            val fp = b.footprint()
            projection.footprintDepthKey(b.coord, fp.effectiveWidth(), fp.effectiveHeight())
        }
        for (building in sortedFrontToBack) {
            val polygon = publicBuildingRenderer.computeHitPolygon(building)
            if (isPointInPolygon(worldX, worldY, polygon.vertices)) {
                return building
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
        ensureHitIndices(scene)
        val coord = projection.worldToGridCoord(worldX, worldY)
        val candidates = hitPlotIndex.queryRange(coord.x, coord.x, coord.y, coord.y)
        val pool = if (candidates.isNotEmpty()) candidates else scene.plots
        return pool.firstOrNull { plot ->
            GridFootprint(plot.coord, plot.widthCells, plot.heightCells, plot.rotation).contains(coord)
        }
    }

    fun findEnvironmentPropAtScreenPoint(
        screenX: Float,
        screenY: Float,
        scene: CitySceneState
    ): EnvironmentProp? {
        val worldPoint = cameraController.screenToWorld(screenX, screenY)
        return findEnvironmentPropAtWorldPoint(worldPoint.x, worldPoint.y, scene)
    }

    fun findEnvironmentPropAtWorldPoint(
        worldX: Float,
        worldY: Float,
        scene: CitySceneState
    ): EnvironmentProp? {
        if (scene.environmentProps.isEmpty()) return null
        ensureHitIndices(scene)
        val coord = projection.worldToGridCoord(worldX, worldY)
        val candidates = hitPropIndex.queryRange(coord.x - 1, coord.x + 1, coord.y - 1, coord.y + 1)
        val pool = if (candidates.isNotEmpty()) candidates else scene.environmentProps
        return pool.sortedByDescending { prop ->
            val fp = prop.footprint()
            projection.footprintDepthKey(prop.coord, fp.effectiveWidth(), fp.effectiveHeight())
        }.firstOrNull { prop ->
            prop.footprint().contains(coord)
        }
    }

    fun findRoadSegmentAtScreenPoint(
        screenX: Float,
        screenY: Float,
        scene: CitySceneState
    ): RoadSegment? {
        val coord = screenToGridCoord(screenX, screenY)
        return findRoadSegmentAtCoord(coord, scene)
    }

    fun findRoadSegmentAtCoord(coord: GridCoord, scene: CitySceneState): RoadSegment? {
        return scene.roadSegments.lastOrNull { seg ->
            coord.x in seg.coord.x until (seg.coord.x + seg.widthCells.coerceAtLeast(1)) &&
                coord.y in seg.coord.y until (seg.coord.y + seg.heightCells.coerceAtLeast(1))
        }
    }

    fun findDistrictAtCoord(coord: GridCoord, scene: CitySceneState): DistrictZone? {
        return scene.districts.firstOrNull { it.contains(coord) }
    }

    private fun ensureHitIndices(scene: CitySceneState) {
        if (indexedSceneRef === scene) return
        indexedSceneRef = scene
        hitHouseIndex.clear()
        hitBuildingIndex.clear()
        hitPlotIndex.clear()
        hitPropIndex.clear()

        for (i in scene.houses.indices) {
            val h = scene.houses[i]
            hitHouseIndex.insertFootprint(GridFootprint(h.coord, h.widthCells, h.heightCells, h.rotation), h)
        }
        for (i in scene.publicBuildings.indices) {
            val b = scene.publicBuildings[i]
            hitBuildingIndex.insertFootprint(b.footprint(), b)
        }
        for (i in scene.plots.indices) {
            val p = scene.plots[i]
            hitPlotIndex.insertFootprint(GridFootprint(p.coord, p.widthCells, p.heightCells, p.rotation), p)
        }
        for (i in scene.environmentProps.indices) {
            val prop = scene.environmentProps[i]
            hitPropIndex.insertFootprint(prop.footprint(), prop)
        }
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
