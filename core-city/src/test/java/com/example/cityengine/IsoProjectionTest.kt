package com.example.cityengine

import com.example.cityengine.model.GridCoord
import com.example.cityengine.projection.IsoProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IsoProjectionTest {

    private val projection = IsoProjection()

    @Test
    fun `tile ratio is strictly 2 to 1`() {
        assertEquals(2f, projection.tileWidth / projection.tileHeight, 0.0001f)
    }

    @Test
    fun `grid to world and inverse world to grid round-trip accurately`() {
        val testPoints = listOf(
            0f to 0f,
            3.5f to 2.25f,
            11.8f to 9.4f,
            6f to 6f,
            1f to 10f
        )

        for ((gx, gy) in testPoints) {
            val world = projection.gridToWorld(gx, gy)
            val back = projection.worldToGrid(world.x, world.y)
            assertEquals("gridX mismatch for ($gx, $gy)", gx, back.x, 0.001f)
            assertEquals("gridY mismatch for ($gx, $gy)", gy, back.y, 0.001f)
        }
    }

    @Test
    fun `world to grid coord maps tile center back to exact integer coord`() {
        for (y in 0 until 12) {
            for (x in 0 until 12) {
                val coord = GridCoord(x, y)
                val center = projection.tileCenterWorld(coord)
                val resolved = projection.worldToGridCoord(center.x, center.y)
                assertEquals(coord, resolved)
            }
        }
    }

    @Test
    fun `depth key increases forward along isometric axes`() {
        val back = projection.depthKey(GridCoord(2, 2))
        val rightNeighbor = projection.depthKey(GridCoord(3, 2))
        val downNeighbor = projection.depthKey(GridCoord(2, 3))
        val frontDiagonal = projection.depthKey(GridCoord(3, 3))

        assertTrue(rightNeighbor > back)
        assertTrue(downNeighbor > back)
        assertTrue(frontDiagonal > rightNeighbor)
        assertTrue(frontDiagonal > downNeighbor)
    }
}
