package com.example.cityengine.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.cityengine.assets.CityAssetCache
import com.example.cityengine.assets.CityAssetRegistry
import com.example.cityengine.model.CityLightingState
import com.example.cityengine.model.PublicBuilding
import com.example.cityengine.model.PublicBuildingType
import com.example.cityengine.model.WorldPoint
import com.example.cityengine.performance.LodTier
import com.example.cityengine.projection.IsoProjection

/**
 * Renders civic, government, commercial, service, recreational, and industrial public buildings
 * with multi-cell 2.5D architectural geometry plus lazy-cached VectorDrawable emblems.
 */
class PublicBuildingRenderer(
    private val projection: IsoProjection,
    private val lightingRenderer: LightingRenderer
) {
    private val path = Path()

    fun computeHitPolygon(building: PublicBuilding): HouseWorldPolygon {
        val fp = building.footprint()
        val w = fp.effectiveWidth()
        val h = fp.effectiveHeight()
        val heightPx = buildingHeightPx(building)
        val baseN = projection.gridToWorld(building.coord.x + 0.08f, building.coord.y + 0.08f)
        val baseE = projection.gridToWorld(building.coord.x + w - 0.08f, building.coord.y + 0.08f)
        val baseS = projection.gridToWorld(building.coord.x + w - 0.08f, building.coord.y + h - 0.08f)
        val baseW = projection.gridToWorld(building.coord.x + 0.08f, building.coord.y + h - 0.08f)
        return HouseWorldPolygon(
            vertices = listOf(
                WorldPoint(baseN.x, baseN.y - heightPx),
                WorldPoint(baseE.x, baseE.y - heightPx),
                baseE,
                baseS,
                baseW,
                WorldPoint(baseW.x, baseW.y - heightPx)
            )
        )
    }

    fun drawBuilding(
        scope: DrawScope,
        building: PublicBuilding,
        isSelected: Boolean,
        lighting: CityLightingState,
        lod: LodTier,
        assetCache: CityAssetCache? = null
    ) {
        val fp = building.footprint()
        val w = fp.effectiveWidth()
        val h = fp.effectiveHeight()
        val wallHeight = buildingHeightPx(building)

        val baseN = projection.gridToWorld(building.coord.x + 0.09f, building.coord.y + 0.09f)
        val baseE = projection.gridToWorld(building.coord.x + w - 0.09f, building.coord.y + 0.09f)
        val baseS = projection.gridToWorld(building.coord.x + w - 0.09f, building.coord.y + h - 0.09f)
        val baseW = projection.gridToWorld(building.coord.x + 0.09f, building.coord.y + h - 0.09f)

        // 1. Ground shadow
        val shadow = lightingRenderer.shadowOffset(lighting)
        path.reset()
        path.moveTo(baseN.x + shadow.x, baseN.y + shadow.y)
        path.lineTo(baseE.x + shadow.x, baseE.y + shadow.y)
        path.lineTo(baseS.x + shadow.x, baseS.y + shadow.y)
        path.lineTo(baseW.x + shadow.x, baseW.y + shadow.y)
        path.close()
        scope.drawPath(path, lightingRenderer.shadowColor(lighting))

        val primary = Color(building.primaryColorHex)
        val leftShade = primary
        val rightShade = primary.copy(
            red = (primary.red * 0.87f).coerceIn(0f, 1f),
            green = (primary.green * 0.87f).coerceIn(0f, 1f),
            blue = (primary.blue * 0.87f).coerceIn(0f, 1f)
        )
        val accent = Color(building.accentColorHex)
        val windowColor = lightingRenderer.resolveWindowColor(
            baseWindow = Color(0xFFBFE4F4),
            lighting = lighting,
            hasLitWindows = building.isLitAtNight
        )

        // 2. Main structural isometric block
        drawExtrudedFace(scope, baseW, baseS, wallHeight, leftShade)
        drawExtrudedFace(scope, baseS, baseE, wallHeight, rightShade)

        // Roof deck
        path.reset()
        path.moveTo(baseN.x, baseN.y - wallHeight)
        path.lineTo(baseE.x, baseE.y - wallHeight)
        path.lineTo(baseS.x, baseS.y - wallHeight)
        path.lineTo(baseW.x, baseW.y - wallHeight)
        path.close()
        scope.drawPath(path, accent.copy(alpha = 0.25f))
        scope.drawPath(path, primary)
        scope.drawPath(path, accent, style = Stroke(width = 2.6f))

        // 3. Type-specific architectural details (skipped when zoomed far out at LOW LOD)
        val center = projection.footprintCenterWorld(building.coord, w, h)
        if (lod != LodTier.LOW) {
            drawArchitecturalFacadeWindows(scope, baseW, baseS, wallHeight, windowColor, accent)
            drawArchitecturalFacadeWindows(scope, baseS, baseE, wallHeight, windowColor, accent)
            drawTypeSpecialty(scope, building, center, wallHeight, accent, primary, baseN, baseE, baseS, baseW)
        }

        // 4. Crisp VectorDrawable civic emblem overlay from Asset Cache
        if (lod != LodTier.LOW && assetCache != null) {
            val bitmap = assetCache.getOrLoadByAssetKey(building.assetKey)
                ?: assetCache.getOrLoadByAssetKey(CityAssetRegistry.keyFor(building.type))
            if (bitmap != null) {
                val badgeSize = if (w >= 3) 38 else 28
                val bx = (center.x - badgeSize * 0.5f).toInt()
                val by = (center.y - wallHeight - badgeSize * 0.6f).toInt()
                scope.drawImage(
                    image = bitmap,
                    dstOffset = IntOffset(bx, by),
                    dstSize = IntSize(badgeSize, badgeSize)
                )
            }
        }

        if (isSelected) {
            path.reset()
            path.moveTo(baseN.x, baseN.y)
            path.lineTo(baseE.x, baseE.y)
            path.lineTo(baseS.x, baseS.y)
            path.lineTo(baseW.x, baseW.y)
            path.close()
            scope.drawPath(path, Color(0xFFF4B942), style = Stroke(width = 3.6f))
        }
    }

    private fun drawTypeSpecialty(
        scope: DrawScope,
        building: PublicBuilding,
        center: WorldPoint,
        wallHeight: Float,
        accent: Color,
        primary: Color,
        baseN: WorldPoint,
        baseE: WorldPoint,
        baseS: WorldPoint,
        baseW: WorldPoint
    ) {
        val roofY = center.y - wallHeight
        when (building.type) {
            PublicBuildingType.PRESIDENTIAL_PALACE -> {
                // Grand central dome + golden finial
                scope.drawOval(accent.copy(alpha = 0.35f), topLeft = Offset(center.x - 36f, roofY - 18f), size = Size(72f, 36f))
                scope.drawArc(
                    color = accent,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(center.x - 28f, roofY - 40f),
                    size = Size(56f, 44f)
                )
                scope.drawCircle(Color(0xFFFFE082), radius = 3.5f, center = Offset(center.x, roofY - 42f))
            }

            PublicBuildingType.CENTRAL_BANK, PublicBuildingType.GOVERNMENT_HALL, PublicBuildingType.MUNICIPALITY -> {
                // Classical roof crown / clock cupola
                scope.drawRect(accent, topLeft = Offset(center.x - 16f, roofY - 22f), size = Size(32f, 22f))
                scope.drawCircle(Color(0xFFFFF8E1), radius = 5.5f, center = Offset(center.x, roofY - 11f))
            }

            PublicBuildingType.HOSPITAL -> {
                // Crimson cross sign + helipad ring
                scope.drawCircle(Color(0xFFFFFFFF), radius = 14f, center = Offset(center.x, roofY - 2f))
                scope.drawRect(Color(0xFFC62828), topLeft = Offset(center.x - 3f, roofY - 10f), size = Size(6f, 16f))
                scope.drawRect(Color(0xFFC62828), topLeft = Offset(center.x - 8f, roofY - 5f), size = Size(16f, 6f))
            }

            PublicBuildingType.FUEL_STATION -> {
                // Cantilevered service canopy stripe
                scope.drawLine(accent, Offset(baseW.x, baseW.y - wallHeight), Offset(baseE.x, baseE.y - wallHeight), strokeWidth = 5f)
            }

            PublicBuildingType.MINE_INDUSTRIAL -> {
                // Steel mining headframe & ore hopper silhouette
                scope.drawLine(Color(0xFF3E4A51), Offset(center.x - 18f, roofY), Offset(center.x, roofY - 34f), strokeWidth = 4f)
                scope.drawLine(Color(0xFF3E4A51), Offset(center.x + 18f, roofY), Offset(center.x, roofY - 34f), strokeWidth = 4f)
                scope.drawCircle(accent, radius = 6f, center = Offset(center.x, roofY - 34f))
            }

            PublicBuildingType.RECREATION_CENTER -> {
                // Arched stadium dome
                scope.drawOval(accent.copy(alpha = 0.85f), topLeft = Offset(center.x - 32f, roofY - 16f), size = Size(64f, 32f))
                scope.drawOval(primary.copy(alpha = 0.8f), topLeft = Offset(center.x - 18f, roofY - 9f), size = Size(36f, 18f))
            }

            else -> {
                scope.drawOval(accent.copy(alpha = 0.65f), topLeft = Offset(center.x - 14f, roofY - 7f), size = Size(28f, 14f))
            }
        }
    }

    private fun drawArchitecturalFacadeWindows(
        scope: DrawScope,
        start: WorldPoint,
        end: WorldPoint,
        wallHeight: Float,
        windowColor: Color,
        accent: Color
    ) {
        for (row in listOf(0.28f, 0.62f)) {
            for (u in listOf(0.18f, 0.45f, 0.72f)) {
                val p0 = Offset(start.x + (end.x - start.x) * u, start.y + (end.y - start.y) * u - wallHeight * row)
                val p1 = Offset(start.x + (end.x - start.x) * (u + 0.14f), start.y + (end.y - start.y) * (u + 0.14f) - wallHeight * row)
                path.reset()
                path.moveTo(p0.x, p0.y)
                path.lineTo(p1.x, p1.y)
                path.lineTo(p1.x, p1.y - wallHeight * 0.22f)
                path.lineTo(p0.x, p0.y - wallHeight * 0.22f)
                path.close()
                scope.drawPath(path, windowColor)
                scope.drawPath(path, accent.copy(alpha = 0.55f), style = Stroke(width = 0.9f))
            }
        }
    }

    private fun drawExtrudedFace(
        scope: DrawScope,
        start: WorldPoint,
        end: WorldPoint,
        height: Float,
        color: Color
    ) {
        path.reset()
        path.moveTo(start.x, start.y)
        path.lineTo(end.x, end.y)
        path.lineTo(end.x, end.y - height)
        path.lineTo(start.x, start.y - height)
        path.close()
        scope.drawPath(path, color)
    }

    private fun buildingHeightPx(building: PublicBuilding): Float {
        val base = when (building.type) {
            PublicBuildingType.PRESIDENTIAL_PALACE -> 72f
            PublicBuildingType.CENTRAL_BANK, PublicBuildingType.GOVERNMENT_HALL, PublicBuildingType.HOSPITAL -> 62f
            PublicBuildingType.FUEL_STATION, PublicBuildingType.RESTAURANT_CAFE -> 36f
            else -> 54f
        }
        return base + ((building.upgradeLevel - 1).coerceIn(0, 3) * 10f)
    }
}
