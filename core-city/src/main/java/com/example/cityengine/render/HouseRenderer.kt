package com.example.cityengine.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.cityengine.model.BuildingRotation
import com.example.cityengine.model.CityLightingState
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.House
import com.example.cityengine.model.HouseDesignStyle
import com.example.cityengine.model.WorldPoint
import com.example.cityengine.performance.LodTier
import com.example.cityengine.projection.IsoProjection
import kotlin.math.sin

data class HouseWorldPolygon(
    val vertices: List<WorldPoint>
)

/**
 * Renders residential houses across 5 architectural styles, visible upgrade levels (1..3+),
 * multi-cell footprints, rotation-aware entrances, sunlit/sunset/night windows, and flags.
 */
class HouseRenderer(
    private val projection: IsoProjection,
    private val flagRenderer: FlagRenderer,
    private val lightingRenderer: LightingRenderer = LightingRenderer(projection)
) {
    private val path = Path()

    private val selectionFill = Color(0x3DF4B942)
    private val selectionStroke = Color(0xFFF4B942)
    private val selectionOuter = Color(0x99FFF7D6)
    private val windowFrame = Color(0xFF475761)
    private val doorHandle = Color(0xFFE7B84F)

    fun computeHitPolygon(
        coord: GridCoord,
        widthCells: Int = 1,
        heightCells: Int = 1,
        upgradeLevel: Int = 1
    ): HouseWorldPolygon {
        val w = widthCells.coerceAtLeast(1)
        val h = heightCells.coerceAtLeast(1)
        val wallH = wallHeightForLevel(upgradeLevel)
        val x0 = coord.x + INSET - EAVE_OVERHANG
        val y0 = coord.y + INSET - EAVE_OVERHANG
        val x1 = coord.x + w.toFloat() - INSET + EAVE_OVERHANG
        val y1 = coord.y + h.toFloat() - INSET + EAVE_OVERHANG

        val baseE = projection.gridToWorld(coord.x + w.toFloat() - INSET, coord.y + INSET)
        val baseS = projection.gridToWorld(coord.x + w.toFloat() - INSET, coord.y + h.toFloat() - INSET)
        val baseW = projection.gridToWorld(coord.x + INSET, coord.y + h.toFloat() - INSET)

        val eaveN = projection.gridToWorld(x0, y0)
        val eaveE = projection.gridToWorld(x1, y0)
        val eaveW = projection.gridToWorld(x0, y1)
        val center = projection.footprintCenterWorld(coord, w, h)
        val peak = WorldPoint(center.x, center.y - wallH - ROOF_PEAK_HEIGHT)

        return HouseWorldPolygon(
            vertices = listOf(
                peak,
                WorldPoint(eaveE.x, eaveE.y - wallH + 2f),
                baseE,
                baseS,
                baseW,
                WorldPoint(eaveW.x, eaveW.y - wallH + 2f),
                WorldPoint(eaveN.x, eaveN.y - wallH + 2f)
            )
        )
    }

    fun computeHitPolygon(house: House): HouseWorldPolygon = computeHitPolygon(
        coord = house.coord,
        widthCells = if (house.rotation == BuildingRotation.DEG_90 || house.rotation == BuildingRotation.DEG_270) house.heightCells else house.widthCells,
        heightCells = if (house.rotation == BuildingRotation.DEG_90 || house.rotation == BuildingRotation.DEG_270) house.widthCells else house.heightCells,
        upgradeLevel = house.upgradeLevel
    )

    fun drawSelectionHighlight(
        scope: DrawScope,
        coord: GridCoord,
        timeSeconds: Float,
        widthCells: Int = 1,
        heightCells: Int = 1
    ) {
        val pulse = 0.5f + 0.5f * sin(timeSeconds)
        val pad = 0.05f + pulse * 0.025f
        val corners = projection.footprintCornersWorld(coord, widthCells, heightCells, inset = pad)

        path.reset()
        path.moveTo(corners[0].x, corners[0].y)
        path.lineTo(corners[1].x, corners[1].y)
        path.lineTo(corners[2].x, corners[2].y)
        path.lineTo(corners[3].x, corners[3].y)
        path.close()

        scope.drawPath(path, selectionFill)
        scope.drawPath(path, selectionOuter, style = Stroke(width = 4.6f))
        scope.drawPath(path, selectionStroke, style = Stroke(width = 2.4f))
    }

    fun drawHouse(
        scope: DrawScope,
        house: House,
        timeSeconds: Float,
        isSelected: Boolean,
        lighting: CityLightingState = CityLightingState(),
        lod: LodTier = LodTier.HIGH,
        assetCache: com.example.cityengine.assets.CityAssetCache? = null
    ) {
        val w = if (house.rotation == BuildingRotation.DEG_90 || house.rotation == BuildingRotation.DEG_270) house.heightCells.coerceAtLeast(1) else house.widthCells.coerceAtLeast(1)
        val h = if (house.rotation == BuildingRotation.DEG_90 || house.rotation == BuildingRotation.DEG_270) house.widthCells.coerceAtLeast(1) else house.heightCells.coerceAtLeast(1)
        val wallHeight = wallHeightForLevel(house.upgradeLevel)

        val x0 = house.coord.x + INSET
        val y0 = house.coord.y + INSET
        val x1 = house.coord.x + w.toFloat() - INSET
        val y1 = house.coord.y + h.toFloat() - INSET

        val baseN = projection.gridToWorld(x0, y0)
        val baseE = projection.gridToWorld(x1, y0)
        val baseS = projection.gridToWorld(x1, y1)
        val baseW = projection.gridToWorld(x0, y1)

        // 1. Directional cast shadow
        val shadowOff = lightingRenderer.shadowOffset(lighting)
        path.reset()
        path.moveTo(baseN.x + shadowOff.x, baseN.y + shadowOff.y)
        path.lineTo(baseE.x + shadowOff.x, baseE.y + shadowOff.y)
        path.lineTo(baseS.x + shadowOff.x, baseS.y + shadowOff.y)
        path.lineTo(baseW.x + shadowOff.x, baseW.y + shadowOff.y)
        path.close()
        scope.drawPath(path, lightingRenderer.shadowColor(lighting))

        val wallLeft = Color(house.palette.wallLeftHex)
        val wallRight = Color(house.palette.wallRightHex)
        val roofLeft = Color(house.palette.roofLeftHex)
        val roofRight = Color(house.palette.roofRightHex)
        val roofTrim = Color(house.palette.roofTrimHex)
        val doorColor = Color(house.palette.doorHex)
        val windowColor = lightingRenderer.resolveWindowColor(
            baseWindow = Color(house.palette.windowHex),
            lighting = lighting,
            hasLitWindows = house.hasLitWindows
        )

        // 2. Plinth trim (architectural base)
        if (lod != LodTier.LOW) {
            drawWallBand(scope, baseW, baseS, wallHeight * 0.12f, Color(0x22000000))
            drawWallBand(scope, baseS, baseE, wallHeight * 0.12f, Color(0x30000000))
        }

        // 3. Front-left wall (baseW -> baseS)
        path.reset()
        path.moveTo(baseW.x, baseW.y)
        path.lineTo(baseS.x, baseS.y)
        path.lineTo(baseS.x, baseS.y - wallHeight)
        path.lineTo(baseW.x, baseW.y - wallHeight)
        path.close()
        scope.drawPath(path, wallLeft)

        // 4. Front-right wall (baseS -> baseE)
        path.reset()
        path.moveTo(baseS.x, baseS.y)
        path.lineTo(baseE.x, baseE.y)
        path.lineTo(baseE.x, baseE.y - wallHeight)
        path.lineTo(baseS.x, baseS.y - wallHeight)
        path.close()
        scope.drawPath(path, wallRight)

        scope.drawLine(
            color = Color(0x2E000000),
            start = Offset(baseS.x, baseS.y),
            end = Offset(baseS.x, baseS.y - wallHeight),
            strokeWidth = 1.3f
        )

        // 5. Doors, windows & multi-story upgrade details
        if (lod != LodTier.LOW) {
            val doorOnRightWall = house.rotation == BuildingRotation.DEG_90 || house.rotation == BuildingRotation.DEG_180
            if (!doorOnRightWall) {
                drawDoorAndFrontWindows(scope, baseW, baseS, baseE, wallHeight, doorColor, windowColor, house.upgradeLevel, lod)
            } else {
                drawDoorAndFrontWindows(scope, baseS, baseE, baseW, wallHeight, doorColor, windowColor, house.upgradeLevel, lod, swapSide = true)
            }
        }

        // 6. Architectural roof by design style
        val roofPeak = drawRoofByStyle(
            scope = scope,
            house = house,
            x0 = x0,
            y0 = y0,
            x1 = x1,
            y1 = y1,
            w = w,
            h = h,
            wallHeight = wallHeight,
            roofLeft = roofLeft,
            roofRight = roofRight,
            roofTrim = roofTrim,
            lod = lod
        )

        if (isSelected) {
            scope.drawCircle(color = selectionStroke, radius = 4.4f, center = Offset(roofPeak.x, roofPeak.y))
        }

        // 7. Waving country flag on roof peak
        val phaseOffset = house.coord.x * 0.85f + house.coord.y * 1.25f
        flagRenderer.drawFlag(
            scope = scope,
            anchorX = roofPeak.x,
            anchorY = roofPeak.y,
            flag = house.flag,
            timeSeconds = timeSeconds,
            phaseOffset = phaseOffset,
            assetCache = assetCache
        )
    }

    private fun drawDoorAndFrontWindows(
        scope: DrawScope,
        doorStart: WorldPoint,
        doorEnd: WorldPoint,
        otherEnd: WorldPoint,
        wallHeight: Float,
        doorColor: Color,
        windowColor: Color,
        upgradeLevel: Int,
        lod: LodTier,
        swapSide: Boolean = false
    ) {
        val doorTopV = (22.4f / wallHeight).coerceIn(0.32f, 0.58f)
        drawWallQuad(scope, doorStart, doorEnd, 0.24f, 0.48f, 0f, doorTopV, wallHeight, doorColor, Color(0xFF3A2518))
        if (lod == LodTier.HIGH) {
            val handlePos = interpolateWallPoint(doorStart, doorEnd, 0.43f, doorTopV * 0.48f, wallHeight)
            scope.drawCircle(color = doorHandle, radius = 1.3f, center = handlePos)
        }
        drawIsoWindow(scope, doorStart, doorEnd, 0.62f, 0.84f, 0.22f, 0.48f, wallHeight, windowColor, lod)

        val winStart = if (swapSide) otherEnd else doorEnd
        val winEnd = if (swapSide) doorStart else otherEnd
        drawIsoWindow(scope, winStart, winEnd, 0.16f, 0.40f, 0.22f, 0.48f, wallHeight, windowColor, lod)
        drawIsoWindow(scope, winStart, winEnd, 0.58f, 0.82f, 0.22f, 0.48f, wallHeight, windowColor, lod)

        // Upgrade level 2+ adds upper-story windows & cornice belt; level 3+ adds balcony railing
        if (upgradeLevel >= 2) {
            drawWallQuad(scope, doorStart, doorEnd, 0f, 1f, 0.56f, 0.60f, wallHeight, Color(0x33000000), Color(0x44000000))
            drawIsoWindow(scope, doorStart, doorEnd, 0.22f, 0.46f, 0.66f, 0.88f, wallHeight, windowColor, lod)
            drawIsoWindow(scope, doorStart, doorEnd, 0.62f, 0.84f, 0.66f, 0.88f, wallHeight, windowColor, lod)
            drawIsoWindow(scope, winStart, winEnd, 0.18f, 0.42f, 0.66f, 0.88f, wallHeight, windowColor, lod)
            drawIsoWindow(scope, winStart, winEnd, 0.58f, 0.82f, 0.66f, 0.88f, wallHeight, windowColor, lod)
        }
        if (upgradeLevel >= 3 && lod == LodTier.HIGH) {
            // Upper terrace balcony band
            drawWallQuad(scope, winStart, winEnd, 0.14f, 0.86f, 0.56f, 0.64f, wallHeight, Color(0xFFD9CFBC), Color(0xFF6B5D4D))
        }
    }

    private fun drawRoofByStyle(
        scope: DrawScope,
        house: House,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        w: Int,
        h: Int,
        wallHeight: Float,
        roofLeft: Color,
        roofRight: Color,
        roofTrim: Color,
        lod: LodTier
    ): WorldPoint {
        val center = projection.footprintCenterWorld(house.coord, w, h)
        val eaveN = projection.gridToWorld(x0 - EAVE_OVERHANG, y0 - EAVE_OVERHANG)
        val eaveE = projection.gridToWorld(x1 + EAVE_OVERHANG, y0 - EAVE_OVERHANG)
        val eaveS = projection.gridToWorld(x1 + EAVE_OVERHANG, y1 + EAVE_OVERHANG)
        val eaveW = projection.gridToWorld(x0 - EAVE_OVERHANG, y1 + EAVE_OVERHANG)
        val eaveY = -wallHeight + 2f

        return when (house.designStyle) {
            HouseDesignStyle.MODERN_VILLA, HouseDesignStyle.COURTYARD_HOUSE -> {
                // Flat architectural terrace roof with crisp parapet rim
                path.reset()
                path.moveTo(eaveN.x, eaveN.y + eaveY)
                path.lineTo(eaveE.x, eaveE.y + eaveY)
                path.lineTo(eaveS.x, eaveS.y + eaveY)
                path.lineTo(eaveW.x, eaveW.y + eaveY)
                path.close()
                scope.drawPath(path, roofLeft)
                scope.drawPath(path, roofTrim, style = Stroke(width = 2.6f))

                if (house.designStyle == HouseDesignStyle.COURTYARD_HOUSE && lod != LodTier.LOW) {
                    val inner = projection.footprintCornersWorld(house.coord, w, h, inset = 0.28f)
                    path.reset()
                    path.moveTo(inner[0].x, inner[0].y + eaveY)
                    path.lineTo(inner[1].x, inner[1].y + eaveY)
                    path.lineTo(inner[2].x, inner[2].y + eaveY)
                    path.lineTo(inner[3].x, inner[3].y + eaveY)
                    path.close()
                    scope.drawPath(path, roofRight)
                }
                WorldPoint(center.x, center.y - wallHeight - 8f)
            }

            HouseDesignStyle.CLASSIC_HOME, HouseDesignStyle.TOWNHOME, HouseDesignStyle.COTTAGE -> {
                val extraPitch = if (house.designStyle == HouseDesignStyle.COTTAGE) 6f else 0f
                val peakX = center.x
                val peakY = center.y - wallHeight - ROOF_PEAK_HEIGHT - extraPitch

                drawRoofTriangle(scope, eaveW.x, eaveW.y + eaveY, eaveN.x, eaveN.y + eaveY, peakX, peakY, roofLeft, roofTrim)
                drawRoofTriangle(scope, eaveN.x, eaveN.y + eaveY, eaveE.x, eaveE.y + eaveY, peakX, peakY, roofRight, roofTrim)
                drawRoofTriangle(scope, eaveW.x, eaveW.y + eaveY, eaveS.x, eaveS.y + eaveY, peakX, peakY, roofLeft, roofTrim)
                drawRoofTriangle(scope, eaveS.x, eaveS.y + eaveY, eaveE.x, eaveE.y + eaveY, peakX, peakY, roofRight, roofTrim)
                WorldPoint(peakX, peakY)
            }
        }
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
        wallHeight: Float,
        glassColor: Color,
        lod: LodTier
    ) {
        drawWallQuad(scope, start, end, uStart, uEnd, vBottom, vTop, wallHeight, glassColor, windowFrame)
        if (lod != LodTier.HIGH) return
        val uMid = (uStart + uEnd) * 0.5f
        val vMid = (vBottom + vTop) * 0.5f
        scope.drawLine(
            color = windowFrame,
            start = interpolateWallPoint(start, end, uMid, vBottom, wallHeight),
            end = interpolateWallPoint(start, end, uMid, vTop, wallHeight),
            strokeWidth = 0.9f
        )
        scope.drawLine(
            color = windowFrame,
            start = interpolateWallPoint(start, end, uStart, vMid, wallHeight),
            end = interpolateWallPoint(start, end, uEnd, vMid, wallHeight),
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
        wallHeight: Float,
        fill: Color,
        stroke: Color
    ) {
        val bl = interpolateWallPoint(start, end, uStart, vBottom, wallHeight)
        val br = interpolateWallPoint(start, end, uEnd, vBottom, wallHeight)
        val tr = interpolateWallPoint(start, end, uEnd, vTop, wallHeight)
        val tl = interpolateWallPoint(start, end, uStart, vTop, wallHeight)

        path.reset()
        path.moveTo(bl.x, bl.y)
        path.lineTo(br.x, br.y)
        path.lineTo(tr.x, tr.y)
        path.lineTo(tl.x, tl.y)
        path.close()

        scope.drawPath(path, fill)
        scope.drawPath(path, stroke, style = Stroke(width = 1.1f))
    }

    private fun drawWallBand(
        scope: DrawScope,
        start: WorldPoint,
        end: WorldPoint,
        heightPx: Float,
        color: Color
    ) {
        path.reset()
        path.moveTo(start.x, start.y)
        path.lineTo(end.x, end.y)
        path.lineTo(end.x, end.y - heightPx)
        path.lineTo(start.x, start.y - heightPx)
        path.close()
        scope.drawPath(path, color)
    }

    private fun interpolateWallPoint(
        start: WorldPoint,
        end: WorldPoint,
        u: Float,
        v: Float,
        wallHeight: Float
    ): Offset {
        val x = start.x + (end.x - start.x) * u
        val y = start.y + (end.y - start.y) * u - wallHeight * v
        return Offset(x, y)
    }

    companion object {
        const val INSET = 0.16f
        const val EAVE_OVERHANG = 0.05f
        const val WALL_HEIGHT = 40f
        const val ROOF_PEAK_HEIGHT = 32f

        fun wallHeightForLevel(upgradeLevel: Int): Float = when {
            upgradeLevel >= 3 -> 58f
            upgradeLevel == 2 -> 49f
            else -> WALL_HEIGHT
        }
    }
}
