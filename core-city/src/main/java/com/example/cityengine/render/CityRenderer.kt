package com.example.cityengine.render

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.cityengine.camera.CameraController
import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.House
import com.example.cityengine.model.LampPostItem
import com.example.cityengine.model.TreeItem
import com.example.cityengine.projection.IsoProjection

private sealed interface DepthDrawable {
    val depth: Int

    data class HouseDrawable(override val depth: Int, val house: House) : DepthDrawable
    data class TreeDrawable(override val depth: Int, val tree: TreeItem) : DepthDrawable
    data class LampDrawable(override val depth: Int, val lamp: LampPostItem) : DepthDrawable
}

/**
 * Coordinates the full 2.5D Isometric scene rendering inside DrawScope with pre-sorted layers
 * and zero heavy allocations during draw frames.
 */
class CityRenderer(
    private val projection: IsoProjection,
    private val roadRenderer: RoadRenderer,
    private val houseRenderer: HouseRenderer,
    private val treeRenderer: TreeRenderer
) {
    private val skyBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFFDAEBF2), Color(0xFFF3ECE0))
    )

    private var cachedSceneRef: CitySceneState? = null
    private var sortedTiles: List<GridCoord> = emptyList()
    private var sortedObjects: List<DepthDrawable> = emptyList()

    fun render(
        scope: DrawScope,
        scene: CitySceneState,
        camera: CameraController,
        timeSeconds: Float
    ) {
        ensureSortedCache(scene)

        // Soft ambient backdrop
        scope.drawRect(brush = skyBrush)

        // Apply camera transform (world -> screen) once for the entire scene
        scope.withTransform({
            translate(
                left = camera.viewportWidth * 0.5f + camera.panX,
                top = camera.viewportHeight * 0.5f + camera.panY
            )
            scale(scaleX = camera.zoom, scaleY = camera.zoom, pivot = androidx.compose.ui.geometry.Offset.Zero)
        }) {
            // 1. 2.5D Island earth skirt
            roadRenderer.drawIslandSkirt(this, scene.gridWidth, scene.gridHeight)

            // 2. Ground terrain, roads, sidewalks, park, and plot pads
            for (i in sortedTiles.indices) {
                val coord = sortedTiles[i]
                val type = scene.tiles[coord] ?: continue
                roadRenderer.drawTile(this, coord, type, scene)
            }

            // 3. Central park plaza feature
            roadRenderer.drawCentralParkPlaza(this)

            // 4. Selection ground highlight underneath selected plot or selected house
            val selectedPlotId = scene.selectedPlotId
            if (selectedPlotId != null) {
                val selectedPlot = scene.plots.firstOrNull { it.id == selectedPlotId }
                if (selectedPlot != null) {
                    roadRenderer.drawPlotHighlight(
                        scope = this,
                        coord = selectedPlot.coord,
                        isAvailable = selectedPlot.status == com.example.cityengine.model.PlotStatus.AVAILABLE,
                        phaseRadians = timeSeconds
                    )
                }
            }

            val selectedId = scene.selectedHouseId
            if (selectedId != null) {
                val selectedHouse = scene.houses.firstOrNull { it.id == selectedId }
                if (selectedHouse != null) {
                    houseRenderer.drawSelectionHighlight(this, selectedHouse.coord, timeSeconds)
                }
            }

            // 5. Depth-sorted 2.5D structures (Houses, Trees, Lamp Posts)
            for (i in sortedObjects.indices) {
                when (val item = sortedObjects[i]) {
                    is DepthDrawable.HouseDrawable -> {
                        houseRenderer.drawHouse(
                            scope = this,
                            house = item.house,
                            timeSeconds = timeSeconds,
                            isSelected = item.house.id == selectedId
                        )
                    }
                    is DepthDrawable.TreeDrawable -> treeRenderer.drawTree(this, item.tree)
                    is DepthDrawable.LampDrawable -> treeRenderer.drawLampPost(this, item.lamp)
                }
            }
        }
    }

    private fun ensureSortedCache(scene: CitySceneState) {
        if (cachedSceneRef === scene) return
        cachedSceneRef = scene

        sortedTiles = scene.tiles.keys.sortedBy { projection.depthKey(it) }

        val combined = ArrayList<DepthDrawable>(
            scene.houses.size + scene.trees.size + scene.lampPosts.size
        )
        scene.houses.forEach { combined.add(DepthDrawable.HouseDrawable(projection.depthKey(it.coord), it)) }
        scene.trees.forEach { combined.add(DepthDrawable.TreeDrawable(projection.depthKey(it.coord), it)) }
        scene.lampPosts.forEach { combined.add(DepthDrawable.LampDrawable(projection.depthKey(it.coord), it)) }
        combined.sortBy { it.depth }
        sortedObjects = combined
    }
}
