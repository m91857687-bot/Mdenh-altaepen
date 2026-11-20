package com.example.cityengine.projection

import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.GridPointF
import com.example.cityengine.model.WorldBounds
import com.example.cityengine.model.WorldPoint
import kotlin.math.floor

/**
 * 2:1 Isometric projection (tileWidth == 2 * tileHeight) for Madinat Al-Tayibin.
 * Pure math class free of Android/Compose UI dependencies for easy sharing and unit testing.
 */
class IsoProjection(
    val tileWidth: Float = DEFAULT_TILE_WIDTH,
    val tileHeight: Float = DEFAULT_TILE_HEIGHT
) {
    init {
        require(tileWidth > 0f && tileHeight > 0f) { "Tile dimensions must be positive." }
        require(kotlin.math.abs(tileWidth - 2f * tileHeight) < 0.001f) {
            "Isometric 2:1 ratio required: tileWidth ($tileWidth) must be 2 * tileHeight ($tileHeight)."
        }
    }

    val halfTileWidth: Float = tileWidth * 0.5f
    val halfTileHeight: Float = tileHeight * 0.5f

    fun gridToWorld(gridX: Float, gridY: Float): WorldPoint {
        val worldX = (gridX - gridY) * halfTileWidth
        val worldY = (gridX + gridY) * halfTileHeight
        return WorldPoint(worldX, worldY)
    }

    fun gridToWorld(coord: GridCoord): WorldPoint = gridToWorld(coord.x.toFloat(), coord.y.toFloat())

    fun tileCenterWorld(coord: GridCoord): WorldPoint = gridToWorld(coord.x + 0.5f, coord.y + 0.5f)

    fun worldToGrid(worldX: Float, worldY: Float): GridPointF {
        val gx = (worldX / halfTileWidth + worldY / halfTileHeight) * 0.5f
        val gy = (worldY / halfTileHeight - worldX / halfTileWidth) * 0.5f
        return GridPointF(gx, gy)
    }

    fun worldToGridCoord(worldX: Float, worldY: Float): GridCoord {
        val grid = worldToGrid(worldX, worldY)
        return GridCoord(floor(grid.x).toInt(), floor(grid.y).toInt())
    }

    /**
     * Returns the 4 diamond corners in clockwise order: Top, Right, Bottom, Left.
     */
    fun tileCornersWorld(coord: GridCoord): Array<WorldPoint> {
        val x = coord.x.toFloat()
        val y = coord.y.toFloat()
        return arrayOf(
            gridToWorld(x, y),
            gridToWorld(x + 1f, y),
            gridToWorld(x + 1f, y + 1f),
            gridToWorld(x, y + 1f)
        )
    }

    fun depthKey(coord: GridCoord): Int = (coord.x + coord.y) * 1024 + coord.x

    fun computeWorldBounds(gridWidth: Int, gridHeight: Int, padding: Float = tileWidth): WorldBounds {
        val top = gridToWorld(0f, 0f)
        val right = gridToWorld(gridWidth.toFloat(), 0f)
        val bottom = gridToWorld(gridWidth.toFloat(), gridHeight.toFloat())
        val left = gridToWorld(0f, gridHeight.toFloat())

        val minX = minOf(top.x, right.x, bottom.x, left.x) - padding
        val maxX = maxOf(top.x, right.x, bottom.x, left.x) + padding
        val minY = minOf(top.y, right.y, bottom.y, left.y) - padding
        val maxY = maxOf(top.y, right.y, bottom.y, left.y) + padding
        return WorldBounds(minX, maxX, minY, maxY)
    }

    companion object {
        const val DEFAULT_TILE_WIDTH = 148f
        const val DEFAULT_TILE_HEIGHT = 74f
    }
}
