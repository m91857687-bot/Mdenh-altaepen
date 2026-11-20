package com.example.cityengine.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.House
import com.example.cityengine.model.WorldPoint
import com.example.cityengine.projection.IsoProjection
import kotlin.math.sin

/**
 * Shared 2.5D house geometry used identically by [HouseRenderer] and hit-testing in [CityEngine].
 */
data class HouseWorldPolygon(
    val vertices: List<WorldPoint>
)

class HouseRenderer(
    private val projection: IsoProjection,
    private val flagRenderer: FlagRenderer
) {
    private val path = Path()

    private val shadowColor = Color(0x30141B1F)
    private val selectionFill = Color(0x3DF4B942)
    private val selectionStroke = Color(0xFFF4B942)
    private val selectionOuter = Color(0x99FFF7D6)
    private val windowFrame = Color(0xFF475761)
    private val doorHandle = Color(0xFFE7B84F)

    fun computeHitPolygon(coord: GridCoord): HouseWorldPolygon {
        val x0 = coord.x + INSET - EAVE_OVERHANG
        val y0 = coord.y + INSET - EAVE_OVERHANG
        val x1 = coord.x + 1f - INSET + EAVE_OVERHANG
        val y1 = coord.y + 1f - INSET + EAVE_OVERHANG

        val baseE = projection.gridToWorld(coord.x + 1f - INSET, coord.y + INSET)
        val baseS = projection.gridToWorld(coord.x + 1f - INSET, coord.y + 1f - INSET)
        val baseW = projection.gridToWorld(coord.x + INSET, coord.y + 1f - INSET)

        val eaveN = projection.gridToWorld(x0, y0)
        val eaveE = projection.gridToWorld(x1, y0)
        val eaveW = projection.gridToWorld(x0, y1)
        val center = projection.tileCenterWorld(coord)
        val peak = WorldPoint(center.x, center.y - WALL_HEIGHT - ROOF_PEAK_HEIGHT)

        // Outer silhouette clockwise: roofPeak -> eaveN? Wait: peak is above eaveN, so clockwise contour:
        return HouseWorldPolygon(
            vertices = listOf(
                peak,
                WorldPoint(eaveE.x, eaveE.y - WALL_HEIGHT + 2f),
                baseE,
                baseS,
                baseW,
                WorldPoint(eaveW.x, eaveW.y - WALL_HEIGHT + 2f),
                WorldPoint(eaveN.x, eaveN.y - WALL_HEIGHT + 2f)
            )
        )
    }

    fun drawSelectionHighlight(
        scope: DrawScope,
        coord: GridCoord,
        timeSeconds: Float
    ) {
        val pulse = 0.5f + 0.5f * sin(timeSeconds)
        val pad = 0.05f + pulse * 0.025f
        val c0 = projection.gridToWorld(coord.x + pad, coord.y + pad)
        val c1 = projection.gridToWorld(coord.x + 1f - pad, coord.y + pad)
        val c2 = projection.gridToWorld(coord.x + 1f - pad, coord.y + 1f - pad)
        val c3 = projection.gridToWorld(coord.x + pad, coord.y + 1f - pad)

        path.reset()
        path.moveTo(c0.x, c0.y)
        path.lineTo(c1.x, c1.y)
        path.lineTo(c2.x, c2.y)
        path.lineTo(c3.x, c3.y)
        path.close()

        scope.drawPath(path, selectionFill)
        scope.drawPath(path, selectionOuter, style = Stroke(width = 4.6f))
        scope.drawPath(path, selectionStroke, style = Stroke(width = 2.4f))
    }

    fun drawHouse(
        scope: DrawScope,
        house: House,
        timeSeconds: Float,
        isSelected: Boolean
    ) {
        val x0 = house.coord.x + INSET
        val y0 = house.coord.y + INSET
        val x1 = house.coord.x + 1f - INSET
        val y1 = house.coord.y + 1f - INSET

        val baseN = projection.gridToWorld(x0, y0)
        val baseE = projection.gridToWorld(x1, y0)
        val baseS = projection.gridToWorld(x1, y1)
        val baseW = projection.gridToWorld(x0, y1)

        // 1. Soft isometric ground shadow cast toward bottom-right
        val sx = 11f
        val sy = 5.5f
        path.reset()
        path.moveTo(baseN.x + sx, baseN.y + sy)
        path.lineTo(baseE.x + sx, baseE.y + sy)
        path.lineTo(baseS.x + sx, baseS.y + sy)
        path.lineTo(baseW.x + sx, baseW.y + sy)
        path.close()
        scope.drawPath(path, shadowColor)

        val wallLeft = Color(house.palette.wallLeftHex)
        val wallRight = Color(house.palette.wallRightHex)
        val roofLeft = Color(house.palette.roofLeftHex)
        val roofRight = Color(house.palette.roofRightHex)
        val roofTrim = Color(house.palette.roofTrimHex)
        val doorColor = Color(house.palette.doorHex)
        val windowColor = Color(house.palette.windowHex)

        // 2. Front-left wall (baseW -> baseS)
        path.reset()
        path.moveTo(baseW.x, baseW.y)
        path.lineTo(baseS.x, baseS.y)
        path.lineTo(baseS.x, baseS.y - WALL_HEIGHT)
        path.lineTo(baseW.x, baseW.y - WALL_HEIGHT)
        path.close()
        scope.drawPath(path, wallLeft)

        // 3. Front-right wall (baseS -> baseE)
        path.reset()
        path.moveTo(baseS.x, baseS.y)
        path.lineTo(baseE.x, baseE.y)
        path.lineTo(baseE.x, baseE.y - WALL_HEIGHT)
        path.lineTo(baseS.x, baseS.y - WALL_HEIGHT)
        path.close()
        scope.drawPath(path, wallRight)

        // Wall corner & edge definition
        scope.drawLine(
            color = Color(0x28000000),
            start = Offset(baseS.x, baseS.y),
            end = Offset(baseS.x, baseS.y - WALL_HEIGHT),
            strokeWidth = 1.2f
        )

        // 4. Isometric Door & Window on Front-Left Wall
        drawWallQuad(
            scope = scope,
            start = baseW,
            end = baseS,
            uStart = 0.24f,
            uEnd = 0.48f,
            vBottom = 0f,
            vTop = 0.56f,
            fill = doorColor,
            stroke = Color(0xFF3A2518)
        )
        // Tiny door handle
        val handlePos = interpolateWallPoint(baseW, baseS, 0.43f, 0.27f)
        scope.drawCircle(color = doorHandle, radius = 1.3f, center = handlePos)

        // Front-left window beside door
        drawIsoWindow(
            scope = scope,
            start = baseW,
            end = baseS,
            uStart = 0.62f,
            uEnd = 0.84f,
            vBottom = 0.30f,
            vTop = 0.64f,
            glassColor = windowColor
        )

        // 5. Two Isometric Windows on Front-Right Wall
        drawIsoWindow(
            scope = scope,
            start = baseS,
            end = baseE,
            uStart = 0.16f,
            uEnd = 0.40f,
            vBottom = 0.30f,
            vTop = 0.64f,
            glassColor = windowColor
        )
        drawIsoWindow(
            scope = scope,
            start = baseS,
            end = baseE,
            uStart = 0.58f,
            uEnd = 0.82f,
            vBottom = 0.30f,
            vTop = 0.64f,
            glassColor = windowColor
        )

        // 6. Pitched Isometric Pyramid Roof with slight eaves overhang
        val ex0 = x0 - EAVE_OVERHANG
        val ey0 = y0 - EAVE_OVERHANG
        val ex1 = x1 + EAVE_OVERHANG
        val ey1 = y1 + EAVE_OVERHANG

        val eaveN = projection.gridToWorld(ex0, ey0)
        val eaveE = projection.gridToWorld(ex1, ey0)
        val eaveS = projection.gridToWorld(ex1, ey1)
        val eaveW = projection.gridToWorld(ex0, ey1)
        val eaveY = -WALL_HEIGHT + 2f

        val center = projection.tileCenterWorld(house.coord)
        val peakX = center.x
        val peakY = center.y - WALL_HEIGHT - ROOF_PEAK_HEIGHT

        // Back-left roof face
        drawRoofTriangle(scope, eaveW.x, eaveW.y + eaveY, eaveN.x, eaveN.y + eaveY, peakX, peakY, roofLeft, roofTrim)
        // Back-right roof face
        drawRoofTriangle(scope, eaveN.x, eaveN.y + eaveY, eaveE.x, eaveE.y + eaveY, peakX, peakY, roofRight, roofTrim)
        // Front-left roof face (sunlit)
        drawRoofTriangle(scope, eaveW.x, eaveW.y + eaveY, eaveS.x, eaveS.y + eaveY, peakX, peakY, roofLeft, roofTrim)
        // Front-right roof face (shaded)
        drawRoofTriangle(scope, eaveS.x, eaveS.y + eaveY, eaveE.x, eaveE.y + eaveY, peakX, peakY, roofRight, roofTrim)

        if (isSelected) {
            scope.drawCircle(
                color = selectionStroke,
                radius = 4.2f,
                center = Offset(peakX, peakY)
            )
        }

        // 7. Waving Country Flag on Roof Peak
        val phaseOffset = house.coord.x * 0.85f + house.coord.y * 1.25f
        flagRenderer.drawFlag(
            scope = scope,
            anchorX = peakX,
            anchorY = peakY,
            flag = house.flag,
            timeSeconds = timeSeconds,
            phaseOffset = phaseOffset
        )
    }

    private fun drawRoofTriangle(
        scope: DrawScope,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        px: Float,
        py: Float,
        fill: Color,
        trim: Color
    ) {
        path.reset()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(px, py)
        path.close()
        scope.drawPath(path, fill)
        scope.drawPath(path, trim, style = Stroke(width = 1.4f))
    }

    private fun drawIsoWindow(
        scope: DrawScope,
        start: WorldPoint,
        end: WorldPoint,
        uStart: Float,
        uEnd: Float,
        vBottom: Float,
        vTop: Float,
        glassColor: Color
    ) {
        drawWallQuad(scope, start, end, uStart, uEnd, vBottom, vTop, glassColor, windowFrame)
        val uMid = (uStart + uEnd) * 0.5f
        val vMid = (vBottom + vTop) * 0.5f
        scope.drawLine(
            color = windowFrame,
            start = interpolateWallPoint(start, end, uMid, vBottom),
            end = interpolateWallPoint(start, end, uMid, vTop),
            strokeWidth = 0.9f
        )
        scope.drawLine(
            color = windowFrame,
            start = interpolateWallPoint(start, end, uStart, vMid),
            end = interpolateWallPoint(start, end, uEnd, vMid),
            strokeWidth = 0.9f
        )
    }

    private fun drawWallQuad(
        scope: DrawScope,
        start: WorldPoint,
        end: WorldPoint,
        uStart: Float,
        uEnd: Float,
        vBottom: Float,
        vTop: Float,
        fill: Color,
        stroke: Color
    ) {
        val bl = interpolateWallPoint(start, end, uStart, vBottom)
        val br = interpolateWallPoint(start, end, uEnd, vBottom)
        val tr = interpolateWallPoint(start, end, uEnd, vTop)
        val tl = interpolateWallPoint(start, end, uStart, vTop)

        path.reset()
        path.moveTo(bl.x, bl.y)
        path.lineTo(br.x, br.y)
        path.lineTo(tr.x, tr.y)
        path.lineTo(tl.x, tl.y)
        path.close()

        scope.drawPath(path, fill)
        scope.drawPath(path, stroke, style = Stroke(width = 1.1f))
    }

    private fun interpolateWallPoint(
        start: WorldPoint,
        end: WorldPoint,
        u: Float,
        v: Float
    ): Offset {
        val x = start.x + (end.x - start.x) * u
        val y = start.y + (end.y - start.y) * u - WALL_HEIGHT * v
        return Offset(x, y)
    }

    companion object {
        const val INSET = 0.16f
        const val EAVE_OVERHANG = 0.05f
        const val WALL_HEIGHT = 40f
        const val ROOF_PEAK_HEIGHT = 32f
    }
}
