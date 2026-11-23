package com.example.cityengine.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.cityengine.assets.CityAssetCache
import com.example.cityengine.camera.CameraController
import com.example.cityengine.model.BuildingRotation
import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.EnvironmentProp
import com.example.cityengine.model.EnvironmentPropType
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.House
import com.example.cityengine.model.LampPostItem
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PropLayer
import com.example.cityengine.model.PublicBuilding
import com.example.cityengine.model.TreeItem
import com.example.cityengine.performance.SpatialGridIndex
import com.example.cityengine.performance.ViewportCuller
import com.example.cityengine.projection.IsoProjection

private sealed interface DepthDrawable {
    val depth: Int

    data class HouseDrawable(override val depth: Int, val house: House) : DepthDrawable
    data class PublicBuildingDrawable(override val depth: Int, val building: PublicBuilding) : DepthDrawable
    data class TreeDrawable(override val depth: Int, val tree: TreeItem) : DepthDrawable
    data class LampDrawable(override val depth: Int, val lamp: LampPostItem) : DepthDrawable
    data class PropDrawable(override val depth: Int, val prop: EnvironmentProp) : DepthDrawable
}

/**
 * Coordinates the full 2.5D Isometric scene rendering inside DrawScope with:
 * - Viewport culling & zoom-based Level of Detail (LOD)
 * - SpatialGridIndex lookups for large multi-district cities
 * - Day / Sunset / Night atmospheric lighting & emissive windows
 * - Depth-sorted residential houses, civic buildings, and environment props
 */
class CityRenderer(
    private val projection: IsoProjection,
    private val roadRenderer: RoadRenderer,
    private val houseRenderer: HouseRenderer,
    private val treeRenderer: TreeRenderer,
    private val lightingRenderer: LightingRenderer = LightingRenderer(projection),
    private val publicBuildingRenderer: PublicBuildingRenderer = PublicBuildingRenderer(projection, lightingRenderer),
    private val environmentPropRenderer: EnvironmentPropRenderer = EnvironmentPropRenderer(projection)
) {
    private var cachedSceneRef: CitySceneState? = null
    private val houseIndex = SpatialGridIndex<House>()
    private val buildingIndex = SpatialGridIndex<PublicBuilding>()
    private val plotIndex = SpatialGridIndex<Plot>()
    private val treeIndex = SpatialGridIndex<TreeItem>()
    private val lampIndex = SpatialGridIndex<LampPostItem>()
    private val groundPropIndex = SpatialGridIndex<EnvironmentProp>()
    private val structurePropIndex = SpatialGridIndex<EnvironmentProp>()

    private val visibleDepthObjects = ArrayList<DepthDrawable>(128)
    private val visibleGroundProps = ArrayList<EnvironmentProp>(32)

    fun render(
        scope: DrawScope,
        scene: CitySceneState,
        camera: CameraController,
        timeSeconds: Float,
        assetCache: CityAssetCache? = null
    ) {
        ensureSpatialCache(scene)

        val visibleBounds = ViewportCuller.computeVisibleGridBounds(
            projection = projection,
            camera = camera,
            gridWidth = scene.gridWidth,
            gridHeight = scene.gridHeight
        )
        val lod = visibleBounds.lodTier
        val lighting = scene.lightingState

        // 1. Atmospheric sky backdrop
        lightingRenderer.drawSkyBackdrop(scope, lighting)

        // Apply camera transform (world -> screen) once for the entire scene
        scope.withTransform({
            translate(
                left = camera.viewportWidth * 0.5f + camera.panX,
                top = camera.viewportHeight * 0.5f + camera.panY
            )
            scale(scaleX = camera.zoom, scaleY = camera.zoom, pivot = Offset.Zero)
        }) {
            // 2. 2.5D Island earth skirt
            roadRenderer.drawIslandSkirt(this, scene.gridWidth, scene.gridHeight)

            // 3. Viewport-culled ground terrain, roads, sidewalks, plazas, water, and plots
            for (y in visibleBounds.minY..visibleBounds.maxY) {
                for (x in visibleBounds.minX..visibleBounds.maxX) {
                    val coord = GridCoord(x, y)
                    val type = scene.tiles[coord] ?: continue
                    roadRenderer.drawTile(this, coord, type, scene, lod)
                }
            }

            // 3b. District zone boundaries & subtle zone tint
            for (i in scene.districts.indices) {
                val district = scene.districts[i]
                if (visibleBounds.overlapsRect(
                        district.origin.x,
                        district.origin.y,
                        district.widthCells,
                        district.heightCells
                    )
                ) {
                    roadRenderer.drawDistrictOverlay(
                        scope = this,
                        district = district,
                        isSelected = district.id == scene.selectedDistrictId,
                        lod = lod
                    )
                }
            }

            // 4. Multi-cell plot pads & availability borders
            val visiblePlots = plotIndex.queryRange(
                visibleBounds.minX,
                visibleBounds.maxX,
                visibleBounds.minY,
                visibleBounds.maxY
            )
            for (i in visiblePlots.indices) {
                roadRenderer.drawPlotFootprintPad(this, visiblePlots[i], lod)
            }

            // 5. Legacy central park fountain plaza (only if starter park doesn't define custom fountain prop)
            val hasStarterPark = scene.tiles[GridCoord(5, 5)] == CityTileType.PARK_GREEN &&
                scene.tiles[GridCoord(6, 6)] == CityTileType.PARK_GREEN
            val hasCustomParkFountain = scene.environmentProps.any {
                it.type == EnvironmentPropType.FOUNTAIN && it.coord.x in 5..6 && it.coord.y in 5..6
            }
            if (hasStarterPark && !hasCustomParkFountain &&
                visibleBounds.overlapsRect(5, 5, 2, 2)
            ) {
                roadRenderer.drawCentralParkPlaza(this)
            }

            // 6. Ground-overlay environment props (parking bays, crosswalks)
            val queriedGround = groundPropIndex.queryRange(
                visibleBounds.minX,
                visibleBounds.maxX,
                visibleBounds.minY,
                visibleBounds.maxY
            )
            visibleGroundProps.clear()
            for (i in queriedGround.indices) {
                visibleGroundProps.add(queriedGround[i])
            }
            visibleGroundProps.sortBy { prop ->
                val fp = prop.footprint()
                projection.footprintDepthKey(prop.coord, fp.effectiveWidth(), fp.effectiveHeight())
            }
            for (i in visibleGroundProps.indices) {
                environmentPropRenderer.drawProp(this, visibleGroundProps[i], timeSeconds, lighting, lod, assetCache)
            }

            // 7. Ground light pools (during sunset/night or illuminated fountains)
            lightingRenderer.drawLightPools(this, scene, visibleBounds)

            // 8. Selection & multi-selection highlights
            for (coord in scene.multiSelectedCoords) {
                if (visibleBounds.containsCoord(coord)) {
                    roadRenderer.drawCoordHighlight(
                        scope = this,
                        coord = coord,
                        fillColor = Color(0x402196F3),
                        strokeColor = Color(0xFF1976D2)
                    )
                }
            }
            for (plotId in scene.multiSelectedPlotIds) {
                val mp = scene.plots.firstOrNull { it.id == plotId }
                if (mp != null) {
                    roadRenderer.drawPlotHighlight(this, mp, timeSeconds)
                }
            }

            val selectedRoadId = scene.selectedRoadId
            if (selectedRoadId != null) {
                val seg = scene.roadSegments.firstOrNull { it.id == selectedRoadId }
                if (seg != null) {
                    for (dy in 0 until seg.heightCells.coerceAtLeast(1)) {
                        for (dx in 0 until seg.widthCells.coerceAtLeast(1)) {
                            roadRenderer.drawCoordHighlight(
                                scope = this,
                                coord = GridCoord(seg.coord.x + dx, seg.coord.y + dy),
                                fillColor = Color(0x44F4B942),
                                strokeColor = Color(0xFFE09F1F),
                                strokeWidth = 3f
                            )
                        }
                    }
                }
            }

            val selectedPropId = scene.selectedPropId
            if (selectedPropId != null) {
                val selectedProp = scene.environmentProps.firstOrNull { it.id == selectedPropId }
                if (selectedProp != null) {
                    roadRenderer.drawCoordHighlight(
                        scope = this,
                        coord = selectedProp.coord,
                        fillColor = Color(0x4426A69A),
                        strokeColor = Color(0xFF00796B),
                        strokeWidth = 3f
                    )
                }
            }

            val selectedPlotId = scene.selectedPlotId
            if (selectedPlotId != null) {
                val selectedPlot = scene.plots.firstOrNull { it.id == selectedPlotId }
                if (selectedPlot != null) {
                    roadRenderer.drawPlotHighlight(this, selectedPlot, timeSeconds)
                }
            }

            val selectedHouseId = scene.selectedHouseId
            if (selectedHouseId != null) {
                val selectedHouse = scene.houses.firstOrNull { it.id == selectedHouseId }
                if (selectedHouse != null) {
                    val rotated = selectedHouse.rotation == BuildingRotation.DEG_90 ||
                        selectedHouse.rotation == BuildingRotation.DEG_270
                    val effW = if (rotated) selectedHouse.heightCells else selectedHouse.widthCells
                    val effH = if (rotated) selectedHouse.widthCells else selectedHouse.heightCells
                    houseRenderer.drawSelectionHighlight(
                        scope = this,
                        coord = selectedHouse.coord,
                        timeSeconds = timeSeconds,
                        widthCells = effW,
                        heightCells = effH
                    )
                }
            }

            val selectedBuildingId = scene.selectedBuildingId

            // 9. Depth-sorted visible 2.5D structures (Houses, Public Buildings, Props, Trees, Lamps)
            visibleDepthObjects.clear()

            val visibleHouses = houseIndex.queryRange(
                visibleBounds.minX, visibleBounds.maxX, visibleBounds.minY, visibleBounds.maxY
            )
            for (i in visibleHouses.indices) {
                val h = visibleHouses[i]
                val rotated = h.rotation == BuildingRotation.DEG_90 || h.rotation == BuildingRotation.DEG_270
                val effW = if (rotated) h.heightCells else h.widthCells
                val effH = if (rotated) h.widthCells else h.heightCells
                visibleDepthObjects.add(
                    DepthDrawable.HouseDrawable(projection.footprintDepthKey(h.coord, effW, effH), h)
                )
            }

            val visibleBuildings = buildingIndex.queryRange(
                visibleBounds.minX, visibleBounds.maxX, visibleBounds.minY, visibleBounds.maxY
            )
            for (i in visibleBuildings.indices) {
                val b = visibleBuildings[i]
                val fp = b.footprint()
                visibleDepthObjects.add(
                    DepthDrawable.PublicBuildingDrawable(
                        projection.footprintDepthKey(b.coord, fp.effectiveWidth(), fp.effectiveHeight()),
                        b
                    )
                )
            }

            val visibleTrees = treeIndex.queryRange(
                visibleBounds.minX, visibleBounds.maxX, visibleBounds.minY, visibleBounds.maxY
            )
            for (i in visibleTrees.indices) {
                val t = visibleTrees[i]
                visibleDepthObjects.add(DepthDrawable.TreeDrawable(projection.depthKey(t.coord), t))
            }

            val visibleLamps = lampIndex.queryRange(
                visibleBounds.minX, visibleBounds.maxX, visibleBounds.minY, visibleBounds.maxY
            )
            for (i in visibleLamps.indices) {
                val l = visibleLamps[i]
                visibleDepthObjects.add(DepthDrawable.LampDrawable(projection.depthKey(l.coord), l))
            }

            val visibleProps = structurePropIndex.queryRange(
                visibleBounds.minX, visibleBounds.maxX, visibleBounds.minY, visibleBounds.maxY
            )
            for (i in visibleProps.indices) {
                val p = visibleProps[i]
                val fp = p.footprint()
                visibleDepthObjects.add(
                    DepthDrawable.PropDrawable(
                        projection.footprintDepthKey(p.coord, fp.effectiveWidth(), fp.effectiveHeight()),
                        p
                    )
                )
            }

            visibleDepthObjects.sortBy { it.depth }

            for (i in visibleDepthObjects.indices) {
                when (val item = visibleDepthObjects[i]) {
                    is DepthDrawable.HouseDrawable -> {
                        houseRenderer.drawHouse(
                            scope = this,
                            house = item.house,
                            timeSeconds = timeSeconds,
                            isSelected = item.house.id == selectedHouseId,
                            lighting = lighting,
                            lod = lod,
                            assetCache = assetCache
                        )
                    }
                    is DepthDrawable.PublicBuildingDrawable -> {
                        publicBuildingRenderer.drawBuilding(
                            scope = this,
                            building = item.building,
                            isSelected = item.building.id == selectedBuildingId,
                            lighting = lighting,
                            lod = lod,
                            assetCache = assetCache
                        )
                    }
                    is DepthDrawable.TreeDrawable -> treeRenderer.drawTree(this, item.tree)
                    is DepthDrawable.LampDrawable -> treeRenderer.drawLampPost(this, item.lamp)
                    is DepthDrawable.PropDrawable -> environmentPropRenderer.drawProp(
                        scope = this,
                        prop = item.prop,
                        timeSeconds = timeSeconds,
                        lighting = lighting,
                        lod = lod,
                        assetCache = assetCache
                    )
                }
            }

            // 9b. Named street badges above visible road segments
            for (i in scene.roadSegments.indices) {
                val seg = scene.roadSegments[i]
                if (visibleBounds.overlapsRect(seg.coord.x, seg.coord.y, seg.widthCells, seg.heightCells)) {
                    roadRenderer.drawStreetLabel(this, seg, lod)
                }
            }

            // 9c. Interactive Editor translucent preview overlay
            scene.editorPreview?.let { preview ->
                roadRenderer.drawEditorPreview(this, preview, timeSeconds)
            }
        }

        // 10. Screen-space sunset/night ambient atmosphere tint
        lightingRenderer.drawAmbientAtmosphereOverlay(scope, lighting)
    }

    private fun ensureSpatialCache(scene: CitySceneState) {
        if (cachedSceneRef === scene) return
        cachedSceneRef = scene

        houseIndex.clear()
        buildingIndex.clear()
        plotIndex.clear()
        treeIndex.clear()
        lampIndex.clear()
        groundPropIndex.clear()
        structurePropIndex.clear()

        for (i in scene.houses.indices) {
            val h = scene.houses[i]
            val fp = com.example.cityengine.model.GridFootprint(h.coord, h.widthCells, h.heightCells, h.rotation)
            houseIndex.insertFootprint(fp, h)
        }
        for (i in scene.publicBuildings.indices) {
            val b = scene.publicBuildings[i]
            buildingIndex.insertFootprint(b.footprint(), b)
        }
        for (i in scene.plots.indices) {
            val p = scene.plots[i]
            val fp = com.example.cityengine.model.GridFootprint(p.coord, p.widthCells, p.heightCells, p.rotation)
            plotIndex.insertFootprint(fp, p)
        }
        for (i in scene.trees.indices) {
            val t = scene.trees[i]
            treeIndex.insert(t.coord, t)
        }
        for (i in scene.lampPosts.indices) {
            val l = scene.lampPosts[i]
            lampIndex.insert(l.coord, l)
        }
        for (i in scene.environmentProps.indices) {
            val p = scene.environmentProps[i]
            if (p.layer == PropLayer.GROUND_OVERLAY) {
                groundPropIndex.insertFootprint(p.footprint(), p)
            } else {
                structurePropIndex.insertFootprint(p.footprint(), p)
            }
        }
    }
}
