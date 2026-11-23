package com.example.cityengine.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.DistrictZone
import com.example.cityengine.model.EditorPreviewOverlay
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.model.RoadSegment
import com.example.cityengine.model.TileTextureVariant
import com.example.cityengine.performance.LodTier
import com.example.cityengine.projection.IsoProjection

/**
 * Renders rich textured isometric terrain tiles, connected road networks with intersections/crosswalks,
 * multi-cell plot pads, park plazas, water basins, and the 2.5D island earth skirt.
 */
class RoadRenderer(
    private val projection: IsoProjection
) {
    private val tilePath = Path()
    private val innerPath = Path()
    private val skirtPath = Path()

    private val grassLight = Color(0xFF88BE6D)
    private val grassDark = Color(0xFF7CB262)
    private val grassStroke = Color(0xFF6FA356)

    private val parkColor = Color(0xFF5FA657)
    private val parkRingColor = Color(0xFFD9CFBC)
    private val parkWaterColor = Color(0xFF53A8C4)
    private val parkWaterRim = Color(0xFFEDE6D6)

    private val soilLight = Color(0xFFB38B59)
    private val soilDark = Color(0xFFA27A4B)
    private val farmlandGreen = Color(0xFF7FA650)

    private val stoneLight = Color(0xFFC9C4B9)
    private val stoneDark = Color(0xFFB8B2A6)
    private val plazaLight = Color(0xFFE7DFCF)
    private val plazaAccent = Color(0xFFD5C8B0)

    private val sidewalkTop = Color(0xFFDFD6C5)
    private val sidewalkEdge = Color(0xFFC2B6A1)

    private val roadAsphalt = Color(0xFF424C54)
    private val roadEdge = Color(0xFF333B42)
    private val roadStripe = Color(0xFFEBE8DF)

    private val plotAvailablePad = Color(0xFFEAE0CC)
    private val plotUnavailablePad = Color(0xFFD0CEC9)
    private val plotOccupiedPad = Color(0xFFE1D4BC)
    private val plotPadBorder = Color(0xFFC2B298)

    private val waterDeep = Color(0xFF3C8DAD)
    private val waterRipple = Color(0x66A8E4F7)

    private val islandSkirtLeft = Color(0xFF8C7355)
    private val islandSkirtRight = Color(0xFF705940)

    fun drawIslandSkirt(scope: DrawScope, gridWidth: Int, gridHeight: Int) {
        val thickness = 24f
        val leftCorner = projection.gridToWorld(0f, gridHeight.toFloat())
        val bottomCorner = projection.gridToWorld(gridWidth.toFloat(), gridHeight.toFloat())
        val rightCorner = projection.gridToWorld(gridWidth.toFloat(), 0f)

        skirtPath.reset()
        skirtPath.moveTo(leftCorner.x, leftCorner.y)
        skirtPath.lineTo(bottomCorner.x, bottomCorner.y)
        skirtPath.lineTo(bottomCorner.x, bottomCorner.y + thickness)
        skirtPath.lineTo(leftCorner.x, leftCorner.y + thickness)
        skirtPath.close()
        scope.drawPath(skirtPath, islandSkirtLeft)

        skirtPath.reset()
        skirtPath.moveTo(bottomCorner.x, bottomCorner.y)
        skirtPath.lineTo(rightCorner.x, rightCorner.y)
        skirtPath.lineTo(rightCorner.x, rightCorner.y + thickness)
        skirtPath.lineTo(bottomCorner.x, bottomCorner.y + thickness)
        skirtPath.close()
        scope.drawPath(skirtPath, islandSkirtRight)
    }

    fun drawTile(
        scope: DrawScope,
        coord: GridCoord,
        type: CityTileType,
        scene: CitySceneState,
        lod: LodTier = LodTier.HIGH
    ) {
        val corners = projection.tileCornersWorld(coord)
        tilePath.reset()
        tilePath.moveTo(corners[0].x, corners[0].y)
        tilePath.lineTo(corners[1].x, corners[1].y)
        tilePath.lineTo(corners[2].x, corners[2].y)
        tilePath.lineTo(corners[3].x, corners[3].y)
        tilePath.close()

        val texture = scene.tileTextures[coord] ?: TileTextureVariant.DEFAULT
        val isEven = (coord.x + coord.y) % 2 == 0

        when (type) {
            CityTileType.GRASS -> {
                scope.drawPath(tilePath, if (isEven) grassLight else grassDark)
                if (lod != LodTier.LOW) {
                    scope.drawPath(tilePath, grassStroke, style = Stroke(width = 0.8f))
                }
            }

            CityTileType.PARK_GREEN -> {
                scope.drawPath(tilePath, parkColor)
                if (lod != LodTier.LOW) {
                    scope.drawPath(tilePath, Color(0xFF4F9248), style = Stroke(width = 1f))
                }
            }

            CityTileType.SOIL -> {
                scope.drawPath(tilePath, if (isEven) soilLight else soilDark)
                if (lod != LodTier.LOW) {
                    scope.drawPath(tilePath, Color(0xFF8B653B), style = Stroke(width = 0.9f))
                }
            }

            CityTileType.FARMLAND -> {
                scope.drawPath(tilePath, soilLight)
                if (lod != LodTier.LOW) {
                    for (step in listOf(0.25f, 0.5f, 0.75f)) {
                        val a = projection.gridToWorld(coord.x + 0.12f, coord.y + step)
                        val b = projection.gridToWorld(coord.x + 0.88f, coord.y + step)
                        scope.drawLine(farmlandGreen, Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth = 3.2f, cap = StrokeCap.Round)
                    }
                }
            }

            CityTileType.STONE -> {
                scope.drawPath(tilePath, if (isEven) stoneLight else stoneDark)
                if (lod != LodTier.LOW) {
                    scope.drawPath(tilePath, Color(0xFF9C968A), style = Stroke(width = 1.1f))
                    drawInsetDiamond(scope, coord, 0.18f, Color(0x55FFFFFF), strokeWidth = 0.9f)
                }
            }

            CityTileType.DECORATIVE_PAVING, CityTileType.PLAZA -> {
                val base = if (texture == TileTextureVariant.CHECKER_STONE && !isEven) plazaAccent else plazaLight
                scope.drawPath(tilePath, base)
                if (lod != LodTier.LOW) {
                    scope.drawPath(tilePath, Color(0xFFBFA886), style = Stroke(width = 1.1f))
                    if (lod == LodTier.HIGH) {
                        drawInsetDiamond(scope, coord, 0.22f, Color(0x55B89E7A), strokeWidth = 1f)
                    }
                }
            }

            CityTileType.WATER -> {
                scope.drawPath(tilePath, waterDeep)
                if (lod != LodTier.LOW) {
                    val c = projection.tileCenterWorld(coord)
                    scope.drawLine(waterRipple, Offset(c.x - 16f, c.y), Offset(c.x + 16f, c.y), strokeWidth = 1.8f, cap = StrokeCap.Round)
                }
            }

            CityTileType.SIDEWALK -> {
                scope.drawPath(tilePath, sidewalkTop)
                if (lod != LodTier.LOW) {
                    scope.drawPath(tilePath, sidewalkEdge, style = Stroke(width = 1.4f))
                    if (lod == LodTier.HIGH && texture == TileTextureVariant.COBBLE) {
                        drawInsetDiamond(scope, coord, 0.16f, Color(0x408F836F), strokeWidth = 0.9f)
                    }
                }
            }

            CityTileType.PLOT_LOT -> {
                scope.drawPath(tilePath, plotAvailablePad)
                if (lod != LodTier.LOW) {
                    scope.drawPath(tilePath, plotPadBorder, style = Stroke(width = 1.6f))
                    drawInsetDiamond(scope, coord, 0.10f, Color(0xFFDCCEB5), strokeWidth = 1f)
                }
            }

            CityTileType.ROAD -> {
                scope.drawPath(tilePath, roadAsphalt)
                if (lod != LodTier.LOW) {
                    scope.drawPath(tilePath, roadEdge, style = Stroke(width = 1f))
                    drawRoadMarkings(scope, coord, scene, texture)
                }
            }
        }
    }

    fun drawPlotFootprintPad(scope: DrawScope, plot: Plot, lod: LodTier) {
        if (plot.effectiveWidth() == 1 && plot.effectiveHeight() == 1 && plot.status == PlotStatus.OCCUPIED) return
        val w = plot.effectiveWidth()
        val h = plot.effectiveHeight()
        val corners = projection.footprintCornersWorld(plot.coord, w, h, inset = 0.04f)
        innerPath.reset()
        innerPath.moveTo(corners[0].x, corners[0].y)
        innerPath.lineTo(corners[1].x, corners[1].y)
        innerPath.lineTo(corners[2].x, corners[2].y)
        innerPath.lineTo(corners[3].x, corners[3].y)
        innerPath.close()

        val fill = when (plot.status) {
            PlotStatus.AVAILABLE -> plotAvailablePad
            PlotStatus.UNAVAILABLE -> plotUnavailablePad
            PlotStatus.OCCUPIED -> plotOccupiedPad
        }
        scope.drawPath(innerPath, fill)
        if (lod != LodTier.LOW) {
            val border = when (plot.status) {
                PlotStatus.AVAILABLE -> Color(0xFF2E8B7D)
                PlotStatus.UNAVAILABLE -> Color(0xFF7E858C)
                PlotStatus.OCCUPIED -> plotPadBorder
            }
            scope.drawPath(innerPath, border, style = Stroke(width = 1.8f))
        }
    }

    fun drawCentralParkPlaza(scope: DrawScope) {
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
        plot: Plot,
        phaseRadians: Float
    ) {
        val pulse = 0.5f + 0.5f * kotlin.math.sin(phaseRadians)
        val inset = 0.06f + pulse * 0.02f
        val corners = projection.footprintCornersWorld(
            coord = plot.coord,
            widthCells = plot.effectiveWidth(),
            heightCells = plot.effectiveHeight(),
            inset = inset
        )
        innerPath.reset()
        innerPath.moveTo(corners[0].x, corners[0].y)
        innerPath.lineTo(corners[1].x, corners[1].y)
        innerPath.lineTo(corners[2].x, corners[2].y)
        innerPath.lineTo(corners[3].x, corners[3].y)
        innerPath.close()

        val (fillColor, strokeColor) = when (plot.status) {
            PlotStatus.AVAILABLE -> Color(0x4526A69A) to Color(0xFF00897B)
            PlotStatus.UNAVAILABLE -> Color(0x45607D8B) to Color(0xFF455A64)
            PlotStatus.OCCUPIED -> Color(0x40EF6C00) to Color(0xFFE65100)
        }
        scope.drawPath(innerPath, fillColor)
        scope.drawPath(innerPath, strokeColor, style = Stroke(width = 3.2f))
    }

    fun drawPlotHighlight(
        scope: DrawScope,
        coord: GridCoord,
        isAvailable: Boolean,
        phaseRadians: Float
    ) {
        drawPlotHighlight(
            scope = scope,
            plot = Plot("temp", coord, if (isAvailable) PlotStatus.AVAILABLE else PlotStatus.OCCUPIED),
            phaseRadians = phaseRadians
        )
    }

    private fun drawRoadMarkings(
        scope: DrawScope,
        coord: GridCoord,
        scene: CitySceneState,
        texture: TileTextureVariant
    ) {
        val leftRoad = scene.tiles[GridCoord(coord.x - 1, coord.y)] == CityTileType.ROAD
        val rightRoad = scene.tiles[GridCoord(coord.x + 1, coord.y)] == CityTileType.ROAD
        val upRoad = scene.tiles[GridCoord(coord.x, coord.y - 1)] == CityTileType.ROAD
        val downRoad = scene.tiles[GridCoord(coord.x, coord.y + 1)] == CityTileType.ROAD

        val isIntersection = (leftRoad || rightRoad) && (upRoad || downRoad)
        if (isIntersection) {
            drawInsetDiamond(scope, coord, 0.36f, Color(0x22EBE8DF), strokeWidth = 1.2f)
            return
        }

        if (texture == TileTextureVariant.ASPHALT_CROSSWALK) {
            for (s in listOf(0.22f, 0.42f, 0.62f, 0.80f)) {
                val a = projection.gridToWorld(coord.x + s, coord.y + 0.16f)
                val b = projection.gridToWorld(coord.x + s, coord.y + 0.84f)
                scope.drawLine(roadStripe, Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth = 2.6f, cap = StrokeCap.Round)
            }
            return
        }

        if (leftRoad || rightRoad) {
            val start = projection.gridToWorld(coord.x + 0.25f, coord.y + 0.5f)
            val end = projection.gridToWorld(coord.x + 0.75f, coord.y + 0.5f)
            scope.drawLine(roadStripe, Offset(start.x, start.y), Offset(end.x, end.y), strokeWidth = 2.4f, cap = StrokeCap.Round)
        } else if (upRoad || downRoad) {
            val start = projection.gridToWorld(coord.x + 0.5f, coord.y + 0.25f)
            val end = projection.gridToWorld(coord.x + 0.5f, coord.y + 0.75f)
            scope.drawLine(roadStripe, Offset(start.x, start.y), Offset(end.x, end.y), strokeWidth = 2.4f, cap = StrokeCap.Round)
        }
    }

    private val labelTextPaint by lazy {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 18f
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
    }

    private val labelBgPaint by lazy {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(185, 24, 34, 42)
            style = android.graphics.Paint.Style.FILL
        }
    }

    fun drawDistrictOverlay(
        scope: DrawScope,
        district: DistrictZone,
        isSelected: Boolean,
        lod: LodTier
    ) {
        val corners = projection.footprintCornersWorld(
            coord = district.origin,
            widthCells = district.widthCells.coerceAtLeast(1),
            heightCells = district.heightCells.coerceAtLeast(1),
            inset = 0.04f
        )
        innerPath.reset()
        innerPath.moveTo(corners[0].x, corners[0].y)
        innerPath.lineTo(corners[1].x, corners[1].y)
        innerPath.lineTo(corners[2].x, corners[2].y)
        innerPath.lineTo(corners[3].x, corners[3].y)
        innerPath.close()

        val base = Color(district.themeColorHex)
        scope.drawPath(innerPath, base.copy(alpha = if (isSelected) 0.22f else 0.07f))
        if (lod != LodTier.LOW || isSelected) {
            scope.drawPath(
                innerPath,
                base.copy(alpha = if (isSelected) 0.92f else 0.45f),
                style = Stroke(width = if (isSelected) 3.4f else 1.8f)
            )
        }
    }

    fun drawStreetLabel(scope: DrawScope, segment: RoadSegment, lod: LodTier) {
        val label = segment.streetNameAr?.trim().orEmpty()
        if (label.isEmpty() || lod == LodTier.LOW) return
        val center = projection.footprintCenterWorld(
            segment.coord,
            segment.widthCells.coerceAtLeast(1),
            segment.heightCells.coerceAtLeast(1)
        )
        runCatching {
            val canvas = scope.drawContext.canvas.nativeCanvas
            val textW = labelTextPaint.measureText(label)
            val padX = 12f
            val halfH = 14f
            canvas.drawRoundRect(
                center.x - textW * 0.5f - padX,
                center.y - halfH,
                center.x + textW * 0.5f + padX,
                center.y + halfH,
                12f,
                12f,
                labelBgPaint
            )
            canvas.drawText(label, center.x, center.y + 6f, labelTextPaint)
        }
    }

    fun drawCoordHighlight(
        scope: DrawScope,
        coord: GridCoord,
        fillColor: Color,
        strokeColor: Color,
        strokeWidth: Float = 2.6f
    ) {
        val corners = projection.footprintCornersWorld(coord, 1, 1, inset = 0.05f)
        innerPath.reset()
        innerPath.moveTo(corners[0].x, corners[0].y)
        innerPath.lineTo(corners[1].x, corners[1].y)
        innerPath.lineTo(corners[2].x, corners[2].y)
        innerPath.lineTo(corners[3].x, corners[3].y)
        innerPath.close()
        scope.drawPath(innerPath, fillColor)
        scope.drawPath(innerPath, strokeColor, style = Stroke(width = strokeWidth))
    }

    fun drawEditorPreview(
        scope: DrawScope,
        preview: EditorPreviewOverlay,
        phaseRadians: Float
    ) {
        val pulse = 0.5f + 0.5f * kotlin.math.sin(phaseRadians)
        val fillColor = if (preview.isValid) {
            Color(0xFF10B981).copy(alpha = 0.28f + pulse * 0.14f)
        } else {
            Color(0xFFEF4444).copy(alpha = 0.34f + pulse * 0.14f)
        }
        val strokeColor = if (preview.isValid) Color(0xFF059669) else Color(0xFFDC2626)

        if (preview.coords.size > 1 && (preview.widthCells <= 1 && preview.heightCells <= 1)) {
            for (i in preview.coords.indices) {
                drawCoordHighlight(scope, preview.coords[i], fillColor, strokeColor, strokeWidth = 3f)
            }
            return
        }

        val fp = com.example.cityengine.model.GridFootprint(
            origin = preview.origin,
            widthCells = preview.widthCells.coerceAtLeast(1),
            heightCells = preview.heightCells.coerceAtLeast(1),
            rotation = preview.rotation
        )
        val corners = projection.footprintCornersWorld(
            coord = preview.origin,
            widthCells = fp.effectiveWidth(),
            heightCells = fp.effectiveHeight(),
            inset = 0.04f
        )
        innerPath.reset()
        innerPath.moveTo(corners[0].x, corners[0].y)
        innerPath.lineTo(corners[1].x, corners[1].y)
        innerPath.lineTo(corners[2].x, corners[2].y)
        innerPath.lineTo(corners[3].x, corners[3].y)
        innerPath.close()
        scope.drawPath(innerPath, fillColor)
        scope.drawPath(innerPath, strokeColor, style = Stroke(width = 3.6f))
    }

    private fun drawInsetDiamond(
        scope: DrawScope,
        coord: GridCoord,
        inset: Float,
        strokeColor: Color,
        strokeWidth: Float
    ) {
        val corners = projection.footprintCornersWorld(coord, 1, 1, inset)
        innerPath.reset()
        innerPath.moveTo(corners[0].x, corners[0].y)
        innerPath.lineTo(corners[1].x, corners[1].y)
        innerPath.lineTo(corners[2].x, corners[2].y)
        innerPath.lineTo(corners[3].x, corners[3].y)
        innerPath.close()
        scope.drawPath(innerPath, strokeColor, style = Stroke(width = strokeWidth))
    }
}
