package com.example.cityengine.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.GridCoord
import com.example.cityengine.projection.IsoProjection

/**
 * Renders terrain tiles, roads, lane markings, sidewalks, park plaza, plot pads, and the 2.5D island skirt.
 * Reuses pre-allocated [Path] objects to avoid per-frame allocations in DrawScope.
 */
class RoadRenderer(
    private val projection: IsoProjection
) {
    private val tilePath = Path()
    private val innerPath = Path()
    private val skirtPath = Path()

    private val grassLight = Color(0xFF86BC6B)
    private val grassDark = Color(0xFF7CB262)
    private val grassStroke = Color(0xFF6FA356)

    private val parkColor = Color(0xFF5FA657)
    private val parkRingColor = Color(0xFFD9CFBC)
    private val parkWaterColor = Color(0xFF53A8C4)
    private val parkWaterRim = Color(0xFFEDE6D6)

    private val sidewalkTop = Color(0xFFDFD6C5)
    private val sidewalkEdge = Color(0xFFC5BAA6)

    private val roadAsphalt = Color(0xFF424C54)
    private val roadEdge = Color(0xFF333B42)
    private val roadStripe = Color(0xFFEBE8DF)

    private val plotPad = Color(0xFFE8DCC6)
    private val plotPadBorder = Color(0xFFCBBBA0)

    private val islandSkirtLeft = Color(0xFF8C7355)
    private val islandSkirtRight = Color(0xFF705940)

    fun drawIslandSkirt(scope: DrawScope, gridWidth: Int, gridHeight: Int) {
        val thickness = 22f
        val leftCorner = projection.gridToWorld(0f, gridHeight.toFloat())
        val bottomCorner = projection.gridToWorld(gridWidth.toFloat(), gridHeight.toFloat())
        val rightCorner = projection.gridToWorld(gridWidth.toFloat(), 0f)

        // Left-front earth edge
        skirtPath.reset()
        skirtPath.moveTo(leftCorner.x, leftCorner.y)
        skirtPath.lineTo(bottomCorner.x, bottomCorner.y)
        skirtPath.lineTo(bottomCorner.x, bottomCorner.y + thickness)
        skirtPath.lineTo(leftCorner.x, leftCorner.y + thickness)
        skirtPath.close()
        scope.drawPath(skirtPath, islandSkirtLeft)

        // Right-front earth edge
        skirtPath.reset()
        skirtPath.moveTo(bottomCorner.x, bottomCorner.y)
        skirtPath.lineTo(rightCorner.x, rightCorner.y)
        skirtPath.lineTo(rightCorner.x, rightCorner.y + thickness)
        skirtPath.lineTo(bottomCorner.x, bottomCorner.y + thickness)
        skirtPath.close()
        scope.drawPath(skirtPath, islandSkirtRight)
    }

    fun drawTile(scope: DrawScope, coord: GridCoord, type: CityTileType, scene: CitySceneState) {
        val corners = projection.tileCornersWorld(coord)
        tilePath.reset()
        tilePath.moveTo(corners[0].x, corners[0].y)
        tilePath.lineTo(corners[1].x, corners[1].y)
        tilePath.lineTo(corners[2].x, corners[2].y)
        tilePath.lineTo(corners[3].x, corners[3].y)
        tilePath.close()

        when (type) {
            CityTileType.GRASS -> {
                val fill = if ((coord.x + coord.y) % 2 == 0) grassLight else grassDark
                scope.drawPath(tilePath, fill)
                scope.drawPath(tilePath, grassStroke, style = Stroke(width = 0.8f))
            }

            CityTileType.PARK_GREEN -> {
                scope.drawPath(tilePath, parkColor)
                scope.drawPath(tilePath, Color(0xFF4F9248), style = Stroke(width = 1f))
            }

            CityTileType.SIDEWALK -> {
                scope.drawPath(tilePath, sidewalkTop)
                scope.drawPath(tilePath, sidewalkEdge, style = Stroke(width = 1.4f))
            }

            CityTileType.PLOT_LOT -> {
                scope.drawPath(tilePath, plotPad)
                scope.drawPath(tilePath, plotPadBorder, style = Stroke(width = 1.8f))
                drawInsetDiamond(scope, coord, 0.10f, Color(0xFFDCCEB5), strokeWidth = 1f)
            }

            CityTileType.ROAD -> {
                scope.drawPath(tilePath, roadAsphalt)
                scope.drawPath(tilePath, roadEdge, style = Stroke(width = 1f))
                drawRoadMarkings(scope, coord, scene)
            }
        }
    }

    fun drawCentralParkPlaza(scope: DrawScope) {
        // Central fountain plaza centered at grid (6, 6) inside the 2x2 park (5..6, 5..6)
        val center = projection.gridToWorld(6f, 6f)
        scope.drawOval(
            color = parkRingColor,
            topLeft = Offset(center.x - 36f, center.y - 18f),
            size = androidx.compose.ui.geometry.Size(72f, 36f)
        )
        scope.drawOval(
            color = parkWaterRim,
            topLeft = Offset(center.x - 24f, center.y - 12f),
            size = androidx.compose.ui.geometry.Size(48f, 24f)
        )
        scope.drawOval(
            color = parkWaterColor,
            topLeft = Offset(center.x - 19f, center.y - 9.5f),
            size = androidx.compose.ui.geometry.Size(38f, 19f)
        )
    }

    fun drawPlotHighlight(
        scope: DrawScope,
        coord: GridCoord,
        isAvailable: Boolean,
        phaseRadians: Float
    ) {
        val pulse = 0.5f + 0.5f * kotlin.math.sin(phaseRadians)
        val inset = 0.06f + pulse * 0.02f
        val x0 = coord.x + inset
        val y0 = coord.y + inset
        val x1 = coord.x + 1f - inset
        val y1 = coord.y + 1f - inset

        val p0 = projection.gridToWorld(x0, y0)
        val p1 = projection.gridToWorld(x1, y0)
        val p2 = projection.gridToWorld(x1, y1)
        val p3 = projection.gridToWorld(x0, y1)

        innerPath.reset()
        innerPath.moveTo(p0.x, p0.y)
        innerPath.lineTo(p1.x, p1.y)
        innerPath.lineTo(p2.x, p2.y)
        innerPath.lineTo(p3.x, p3.y)
        innerPath.close()

        val fillColor = if (isAvailable) Color(0x4526A69A) else Color(0x40EF6C00)
        val strokeColor = if (isAvailable) Color(0xFF00897B) else Color(0xFFE65100)
        scope.drawPath(innerPath, fillColor)
        scope.drawPath(innerPath, strokeColor, style = Stroke(width = 3.2f))
    }

    private fun drawRoadMarkings(scope: DrawScope, coord: GridCoord, scene: CitySceneState) {
        val leftRoad = scene.tiles[GridCoord(coord.x - 1, coord.y)] == CityTileType.ROAD
        val rightRoad = scene.tiles[GridCoord(coord.x + 1, coord.y)] == CityTileType.ROAD
        val upRoad = scene.tiles[GridCoord(coord.x, coord.y - 1)] == CityTileType.ROAD
        val downRoad = scene.tiles[GridCoord(coord.x, coord.y + 1)] == CityTileType.ROAD

        val isIntersection = (leftRoad || rightRoad) && (upRoad || downRoad)
        if (isIntersection) return

        if (leftRoad || rightRoad) {
            val start = projection.gridToWorld(coord.x + 0.25f, coord.y + 0.5f)
            val end = projection.gridToWorld(coord.x + 0.75f, coord.y + 0.5f)
            scope.drawLine(
                color = roadStripe,
                start = Offset(start.x, start.y),
                end = Offset(end.x, end.y),
                strokeWidth = 2.4f,
                cap = StrokeCap.Round
            )
        } else if (upRoad || downRoad) {
            val start = projection.gridToWorld(coord.x + 0.5f, coord.y + 0.25f)
            val end = projection.gridToWorld(coord.x + 0.5f, coord.y + 0.75f)
            scope.drawLine(
                color = roadStripe,
                start = Offset(start.x, start.y),
                end = Offset(end.x, end.y),
                strokeWidth = 2.4f,
                cap = StrokeCap.Round
            )
        }
    }

    private fun drawInsetDiamond(
        scope: DrawScope,
        coord: GridCoord,
        inset: Float,
        strokeColor: Color,
        strokeWidth: Float
    ) {
        val x0 = coord.x + inset
        val y0 = coord.y + inset
        val x1 = coord.x + 1f - inset
        val y1 = coord.y + 1f - inset

        val p0 = projection.gridToWorld(x0, y0)
        val p1 = projection.gridToWorld(x1, y0)
        val p2 = projection.gridToWorld(x1, y1)
        val p3 = projection.gridToWorld(x0, y1)

        innerPath.reset()
        innerPath.moveTo(p0.x, p0.y)
        innerPath.lineTo(p1.x, p1.y)
        innerPath.lineTo(p2.x, p2.y)
        innerPath.lineTo(p3.x, p3.y)
        innerPath.close()
        scope.drawPath(innerPath, strokeColor, style = Stroke(width = strokeWidth))
    }
}
