package com.example.cityengine.performance

import com.example.cityengine.camera.CameraController
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.GridFootprint
import com.example.cityengine.projection.IsoProjection
import kotlin.math.ceil
import kotlin.math.floor

enum class LodTier {
    HIGH,
    MEDIUM,
    LOW
}

data class VisibleGridBounds(
    val minX: Int,
    val maxX: Int,
    val minY: Int,
    val maxY: Int,
    val lodTier: LodTier
) {
    fun containsCoord(coord: GridCoord): Boolean {
        return coord.x in minX..maxX && coord.y in minY..maxY
    }

    fun overlapsFootprint(footprint: GridFootprint): Boolean {
        return footprint.origin.x <= maxX &&
            (footprint.maxXExclusive - 1) >= minX &&
            footprint.origin.y <= maxY &&
            (footprint.maxYExclusive - 1) >= minY
    }

    fun overlapsRect(x: Int, y: Int, widthCells: Int, heightCells: Int): Boolean {
        return x <= maxX &&
            (x + widthCells - 1) >= minX &&
            y <= maxY &&
            (y + heightCells - 1) >= minY
    }
}

/**
 * Computes conservative isometric grid visibility bounds from camera screen bounds
 * and selects the rendering Level of Detail (LOD) tier for smooth 60 FPS on large cities.
 */
object ViewportCuller {

    fun resolveLod(zoom: Float): LodTier = when {
        zoom >= 1.10f -> LodTier.HIGH
        zoom >= 0.72f -> LodTier.MEDIUM
        else -> LodTier.LOW
    }

    fun computeVisibleGridBounds(
        projection: IsoProjection,
        camera: CameraController,
        gridWidth: Int,
        gridHeight: Int,
        marginTiles: Int = 3
    ): VisibleGridBounds {
        val lod = resolveLod(camera.zoom)
        if (camera.viewportWidth <= 0f || camera.viewportHeight <= 0f || gridWidth <= 0 || gridHeight <= 0) {
            return VisibleGridBounds(0, (gridWidth - 1).coerceAtLeast(0), 0, (gridHeight - 1).coerceAtLeast(0), lod)
        }

        // Include generous vertical world padding below & above screen so tall roofs/palaces/lights near edges never pop out
        val extraTopWorld = 180f / camera.zoom.coerceAtLeast(0.25f)
        val extraBottomWorld = 90f / camera.zoom.coerceAtLeast(0.25f)

        val tl = camera.screenToWorld(0f, -extraBottomWorld)
        val tr = camera.screenToWorld(camera.viewportWidth, -extraBottomWorld)
        val bl = camera.screenToWorld(0f, camera.viewportHeight + extraTopWorld)
        val br = camera.screenToWorld(camera.viewportWidth, camera.viewportHeight + extraTopWorld)

        val g0 = projection.worldToGrid(tl.x, tl.y)
        val g1 = projection.worldToGrid(tr.x, tr.y)
        val g2 = projection.worldToGrid(bl.x, bl.y)
        val g3 = projection.worldToGrid(br.x, br.y)

        val minX = (floor(minOf(g0.x, g1.x, g2.x, g3.x)).toInt() - marginTiles).coerceIn(0, gridWidth - 1)
        val maxX = (ceil(maxOf(g0.x, g1.x, g2.x, g3.x)).toInt() + marginTiles).coerceIn(0, gridWidth - 1)
        val minY = (floor(minOf(g0.y, g1.y, g2.y, g3.y)).toInt() - marginTiles).coerceIn(0, gridHeight - 1)
        val maxY = (ceil(maxOf(g0.y, g1.y, g2.y, g3.y)).toInt() + marginTiles).coerceIn(0, gridHeight - 1)

        return VisibleGridBounds(minX, maxX, minY, maxY, lod)
    }
}
