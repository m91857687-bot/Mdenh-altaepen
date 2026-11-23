package com.example.cityengine.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.cityengine.model.CityLightingState
import com.example.cityengine.model.EnvironmentProp
import com.example.cityengine.model.EnvironmentPropType
import com.example.cityengine.model.TimeOfDayPhase
import com.example.cityengine.performance.LodTier
import com.example.cityengine.projection.IsoProjection
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders reusable environmental details: broadleaf/cypress/palm trees, benches, animated fountains,
 * statues, directional signs, fences, gates, parking bays, flowerbeds, gazebos, and crosswalk markers.
 */
class EnvironmentPropRenderer(
    private val projection: IsoProjection,
    private val flagRenderer: FlagRenderer = FlagRenderer()
) {
    private val path = Path()
    private val shadowColor = Color(0x28121B1F)

    fun drawProp(
        scope: DrawScope,
        prop: EnvironmentProp,
        timeSeconds: Float,
        lighting: CityLightingState,
        lod: LodTier,
        assetCache: com.example.cityengine.assets.CityAssetCache? = null
    ) {
        val fp = prop.footprint()
        val center = projection.footprintCenterWorld(prop.coord, fp.effectiveWidth(), fp.effectiveHeight())
        val s = prop.scale

        when (prop.type) {
            EnvironmentPropType.TREE_BROADLEAF, EnvironmentPropType.TREE_CYPRESS -> {
                drawStylizedTree(scope, center.x, center.y, s, if (prop.type == EnvironmentPropType.TREE_CYPRESS) 1 else 0)
            }
            EnvironmentPropType.PALM_TREE -> {
                drawPalmTree(scope, center.x, center.y, s, timeSeconds)
            }
            EnvironmentPropType.FOUNTAIN -> {
                drawFountain(scope, center.x, center.y, s, timeSeconds, lighting)
            }
            EnvironmentPropType.STATUE -> {
                drawStatue(scope, center.x, center.y, s)
            }
            EnvironmentPropType.BENCH -> {
                if (lod != LodTier.LOW) drawBench(scope, center.x, center.y, s)
            }
            EnvironmentPropType.ROAD_SIGN -> {
                if (lod != LodTier.LOW) drawRoadSign(scope, center.x, center.y, Color(prop.primaryColorHex))
            }
            EnvironmentPropType.FENCE_SEGMENT -> {
                if (lod != LodTier.LOW) drawFence(scope, prop)
            }
            EnvironmentPropType.GATE_ARCH -> {
                drawGateArch(scope, center.x, center.y, Color(prop.primaryColorHex))
            }
            EnvironmentPropType.PARKING_LOT -> {
                drawParkingLot(scope, prop)
            }
            EnvironmentPropType.FLOWER_BED, EnvironmentPropType.SIDEWALK_PLANTER -> {
                if (lod != LodTier.LOW) drawFlowerBed(scope, center.x, center.y, s)
            }
            EnvironmentPropType.GARDEN_GAZEBO -> {
                drawGazebo(scope, center.x, center.y, s)
            }
            EnvironmentPropType.CROSSWALK_MARKER -> {
                if (lod != LodTier.LOW) drawCrosswalk(scope, prop)
            }
            EnvironmentPropType.LAMP_POST -> {
                drawLampOrnament(scope, center.x, center.y, lighting)
            }
            EnvironmentPropType.CITY_FLAG -> {
                drawCityFlag(scope, prop, center.x, center.y, s, timeSeconds, assetCache)
            }
            EnvironmentPropType.SHRUB_BUSH -> {
                drawShrubBush(scope, center.x, center.y, s)
            }
            EnvironmentPropType.FLOWER_CLUSTER -> {
                if (lod != LodTier.LOW) drawFlowerCluster(scope, center.x, center.y, s)
            }
            EnvironmentPropType.GREEN_LAWN -> {
                drawGreenLawn(scope, prop)
            }
            EnvironmentPropType.PUBLIC_CLOCK -> {
                drawPublicClock(scope, center.x, center.y, s, lighting)
            }
            EnvironmentPropType.TRASH_BIN -> {
                if (lod != LodTier.LOW) drawTrashBin(scope, center.x, center.y, s)
            }
            EnvironmentPropType.SAFETY_BARRIER -> {
                if (lod != LodTier.LOW) drawSafetyBarrier(scope, center.x, center.y, s)
            }
            EnvironmentPropType.ORNAMENT_ARCH -> {
                drawOrnamentArch(scope, center.x, center.y, s, Color(prop.primaryColorHex))
            }
            EnvironmentPropType.SEASONAL_DECOR -> {
                drawSeasonalDecor(scope, center.x, center.y, s, timeSeconds, lighting)
            }
        }
    }

    private fun drawStylizedTree(scope: DrawScope, cx: Float, cy: Float, s: Float, variant: Int) {
        scope.drawOval(shadowColor, Offset(cx - 10f * s, cy - 4f * s), Size(26f * s, 13f * s))
        scope.drawLine(Color(0xFF6B442A), Offset(cx, cy + 1f), Offset(cx, cy - 18f * s), strokeWidth = 4.2f * s, cap = StrokeCap.Round)
        if (variant == 0) {
            scope.drawCircle(Color(0xFF39763E), 13f * s, Offset(cx + 2f * s, cy - 23f * s))
            scope.drawCircle(Color(0xFF4F934D), 12f * s, Offset(cx - 4f * s, cy - 25f * s))
            scope.drawCircle(Color(0xFF72B664), 9.5f * s, Offset(cx - 2f * s, cy - 31f * s))
        } else {
            scope.drawCircle(Color(0xFF2F6938), 10.5f * s, Offset(cx, cy - 20f * s))
            scope.drawCircle(Color(0xFF428445), 9f * s, Offset(cx, cy - 29f * s))
            scope.drawCircle(Color(0xFF62A358), 6.5f * s, Offset(cx, cy - 38f * s))
        }
    }

    private fun drawPalmTree(scope: DrawScope, cx: Float, cy: Float, s: Float, timeSeconds: Float) {
        scope.drawOval(shadowColor, Offset(cx - 12f * s, cy - 4f * s), Size(28f * s, 14f * s))
        val topX = cx + 4f * s + sin(timeSeconds * 0.8f) * 1.2f
        val topY = cy - 36f * s
        scope.drawLine(Color(0xFF8C6239), Offset(cx, cy), Offset(topX, topY), strokeWidth = 4.6f * s, cap = StrokeCap.Round)
        for (angleDeg in listOf(-150f, -110f, -65f, -20f, 25f, 165f)) {
            val rad = angleDeg * (Math.PI.toFloat() / 180f)
            val fx = topX + cos(rad) * 18f * s
            val fy = topY + sin(rad) * 11f * s + 5f * s
            scope.drawLine(Color(0xFF4A934A), Offset(topX, topY), Offset(fx, fy), strokeWidth = 3.5f * s, cap = StrokeCap.Round)
        }
        scope.drawCircle(Color(0xFF65B059), 4.5f * s, Offset(topX, topY))
    }

    private fun drawFountain(scope: DrawScope, cx: Float, cy: Float, s: Float, timeSeconds: Float, lighting: CityLightingState) {
        scope.drawOval(Color(0xFFD9CFBC), Offset(cx - 32f * s, cy - 16f * s), Size(64f * s, 32f * s))
        scope.drawOval(Color(0xFF4BA6C7), Offset(cx - 25f * s, cy - 12.5f * s), Size(50f * s, 25f * s))
        val ripple = 1f + 0.14f * sin(timeSeconds * 2f)
        scope.drawOval(
            color = Color(0x88CCF2FF),
            topLeft = Offset(cx - 15f * s * ripple, cy - 7.5f * s * ripple),
            size = Size(30f * s * ripple, 15f * s * ripple),
            style = Stroke(width = 1.6f)
        )
        scope.drawRect(Color(0xFFEDE6D6), Offset(cx - 5f * s, cy - 18f * s), Size(10f * s, 16f * s))
        val sprayColor = if (lighting.phase == TimeOfDayPhase.NIGHT) Color(0xFF8BE9FF) else Color(0xFFD6F6FF)
        scope.drawCircle(sprayColor, 5f * s, Offset(cx, cy - 22f * s))
    }

    private fun drawStatue(scope: DrawScope, cx: Float, cy: Float, s: Float) {
        scope.drawOval(shadowColor, Offset(cx - 12f * s, cy - 5f * s), Size(24f * s, 12f * s))
        scope.drawRect(Color(0xFFC5BAA6), Offset(cx - 9f * s, cy - 14f * s), Size(18f * s, 14f * s))
        scope.drawRect(Color(0xFFB3914B), Offset(cx - 4f * s, cy - 32f * s), Size(8f * s, 18f * s))
        scope.drawCircle(Color(0xFFD9B25F), 5f * s, Offset(cx, cy - 36f * s))
    }

    private fun drawBench(scope: DrawScope, cx: Float, cy: Float, s: Float) {
        scope.drawRect(Color(0xFF5D4037), Offset(cx - 10f * s, cy - 6f * s), Size(20f * s, 5f * s))
        scope.drawRect(Color(0xFF8D6E63), Offset(cx - 10f * s, cy - 11f * s), Size(20f * s, 4f * s))
    }

    private fun drawRoadSign(scope: DrawScope, cx: Float, cy: Float, color: Color) {
        scope.drawLine(Color(0xFF4A555C), Offset(cx, cy), Offset(cx, cy - 24f), strokeWidth = 2.4f)
        scope.drawRect(color, Offset(cx - 10f, cy - 28f), Size(20f, 10f))
        scope.drawRect(Color.White, Offset(cx - 10f, cy - 28f), Size(20f, 10f), style = Stroke(width = 1.1f))
    }

    private fun drawFence(scope: DrawScope, prop: EnvironmentProp) {
        val a = projection.gridToWorld(prop.coord.x + 0.1f, prop.coord.y + 0.5f)
        val b = projection.gridToWorld(prop.coord.x + 0.9f, prop.coord.y + 0.5f)
        scope.drawLine(Color(0xFF7D6B5D), Offset(a.x, a.y - 8f), Offset(b.x, b.y - 8f), strokeWidth = 3f)
        scope.drawLine(Color(0xFF5C4B3E), Offset(a.x, a.y), Offset(a.x, a.y - 12f), strokeWidth = 2.6f)
        scope.drawLine(Color(0xFF5C4B3E), Offset(b.x, b.y), Offset(b.x, b.y - 12f), strokeWidth = 2.6f)
    }

    private fun drawGateArch(scope: DrawScope, cx: Float, cy: Float, color: Color) {
        scope.drawRect(color, Offset(cx - 16f, cy - 28f), Size(6f, 28f))
        scope.drawRect(color, Offset(cx + 10f, cy - 28f), Size(6f, 28f))
        scope.drawRect(Color(0xFFD4AF37), Offset(cx - 18f, cy - 34f), Size(36f, 7f))
    }

    private fun drawParkingLot(scope: DrawScope, prop: EnvironmentProp) {
        val fp = prop.footprint()
        val corners = projection.footprintCornersWorld(prop.coord, fp.effectiveWidth(), fp.effectiveHeight(), inset = 0.06f)
        path.reset()
        path.moveTo(corners[0].x, corners[0].y)
        path.lineTo(corners[1].x, corners[1].y)
        path.lineTo(corners[2].x, corners[2].y)
        path.lineTo(corners[3].x, corners[3].y)
        path.close()
        scope.drawPath(path, Color(0xFF505A63))
        scope.drawPath(path, Color(0xFFEBE8DF), style = Stroke(width = 1.6f))
    }

    private fun drawFlowerBed(scope: DrawScope, cx: Float, cy: Float, s: Float) {
        scope.drawOval(Color(0xFF5D4037), Offset(cx - 14f * s, cy - 7f * s), Size(28f * s, 14f * s))
        scope.drawOval(Color(0xFF4F934D), Offset(cx - 12f * s, cy - 6f * s), Size(24f * s, 12f * s))
        scope.drawCircle(Color(0xFFE76F51), 2.5f * s, Offset(cx - 5f * s, cy - 1f * s))
        scope.drawCircle(Color(0xFFF4D35E), 2.5f * s, Offset(cx + 4f * s, cy))
        scope.drawCircle(Color(0xFFE94F88), 2.3f * s, Offset(cx, cy - 3f * s))
    }

    private fun drawGazebo(scope: DrawScope, cx: Float, cy: Float, s: Float) {
        scope.drawOval(shadowColor, Offset(cx - 16f * s, cy - 7f * s), Size(32f * s, 16f * s))
        scope.drawLine(Color(0xFFEDE6D6), Offset(cx - 11f * s, cy), Offset(cx - 11f * s, cy - 22f * s), strokeWidth = 2.4f)
        scope.drawLine(Color(0xFFEDE6D6), Offset(cx + 11f * s, cy), Offset(cx + 11f * s, cy - 22f * s), strokeWidth = 2.4f)
        path.reset()
        path.moveTo(cx - 18f * s, cy - 22f * s)
        path.lineTo(cx, cy - 38f * s)
        path.lineTo(cx + 18f * s, cy - 22f * s)
        path.close()
        scope.drawPath(path, Color(0xFFC85A3A))
    }

    private fun drawCrosswalk(scope: DrawScope, prop: EnvironmentProp) {
        for (s in listOf(0.24f, 0.44f, 0.64f, 0.80f)) {
            val a = projection.gridToWorld(prop.coord.x + s, prop.coord.y + 0.15f)
            val b = projection.gridToWorld(prop.coord.x + s, prop.coord.y + 0.85f)
            scope.drawLine(Color(0xFFF5F3EC), Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth = 2.6f, cap = StrokeCap.Round)
        }
    }

    private fun drawLampOrnament(scope: DrawScope, cx: Float, cy: Float, lighting: CityLightingState) {
        scope.drawCircle(Color(0xFF2D363B), 3.2f, Offset(cx, cy))
        scope.drawLine(Color(0xFF3E4A51), Offset(cx, cy), Offset(cx, cy - 30f), strokeWidth = 2.4f, cap = StrokeCap.Round)
        val bulbColor = if (lighting.phase == TimeOfDayPhase.DAY) Color(0xFFFFF1B8) else Color(0xFFFFDF70)
        scope.drawCircle(bulbColor.copy(alpha = 0.45f), 8.5f, Offset(cx + 5f, cy - 29f))
        scope.drawCircle(bulbColor, 2.8f, Offset(cx + 5f, cy - 29f))
    }

    private fun drawCityFlag(
        scope: DrawScope,
        prop: EnvironmentProp,
        cx: Float,
        cy: Float,
        s: Float,
        timeSeconds: Float,
        assetCache: com.example.cityengine.assets.CityAssetCache?
    ) {
        val countryIso = com.example.cityengine.model.ArabCountriesRegistry.parseCountryCodeFromAssetKey(prop.assetKey)
            ?: com.example.cityengine.model.ArabCountriesRegistry.allCountries.getOrNull(prop.variant)?.isoCode
        val spec = com.example.cityengine.model.ArabCountriesRegistry.findByNameOrCode(countryIso, prop.labelAr)
            ?: com.example.cityengine.model.ArabCountriesRegistry.allCountries.first()
        scope.drawOval(shadowColor, Offset(cx - 10f * s, cy - 4f * s), Size(20f * s, 9f * s))
        scope.drawOval(Color(0xFFD5CFC0), Offset(cx - 7f * s, cy - 3.5f * s), Size(14f * s, 7f * s))
        scope.drawOval(Color(0xFF7B7365), Offset(cx - 7f * s, cy - 3.5f * s), Size(14f * s, 7f * s), style = Stroke(width = 1.1f))
        val facingRight = prop.rotation != com.example.cityengine.model.BuildingRotation.DEG_180 &&
            prop.rotation != com.example.cityengine.model.BuildingRotation.DEG_270
        flagRenderer.drawFlag(
            scope = scope,
            anchorX = cx,
            anchorY = cy - 1f,
            flag = spec.flagStyle,
            timeSeconds = timeSeconds,
            phaseOffset = prop.coord.x * 0.7f + prop.coord.y * 1.1f,
            assetCache = assetCache,
            poleHeight = 36f * s,
            flagWidth = 26f * s,
            flagHeight = 17f * s,
            directionRight = facingRight
        )
    }

    private fun drawShrubBush(scope: DrawScope, cx: Float, cy: Float, s: Float) {
        scope.drawOval(shadowColor, Offset(cx - 11f * s, cy - 4f * s), Size(22f * s, 10f * s))
        scope.drawCircle(Color(0xFF376E3E), 7.5f * s, Offset(cx - 4f * s, cy - 6f * s))
        scope.drawCircle(Color(0xFF478849), 8f * s, Offset(cx + 4f * s, cy - 6f * s))
        scope.drawCircle(Color(0xFF63AA5C), 7f * s, Offset(cx, cy - 10f * s))
    }

    private fun drawFlowerCluster(scope: DrawScope, cx: Float, cy: Float, s: Float) {
        scope.drawOval(Color(0xFF4B8E4D), Offset(cx - 10f * s, cy - 5f * s), Size(20f * s, 10f * s))
        scope.drawCircle(Color(0xFFFF6B6B), 2.4f * s, Offset(cx - 5f * s, cy - 1f * s))
        scope.drawCircle(Color(0xFFFFD166), 2.4f * s, Offset(cx + 4f * s, cy - 2f * s))
        scope.drawCircle(Color(0xFFF78FB3), 2.2f * s, Offset(cx, cy + 1f * s))
        scope.drawCircle(Color(0xFFFFFFFF), 1.8f * s, Offset(cx - 1f * s, cy - 3f * s))
    }

    private fun drawGreenLawn(scope: DrawScope, prop: EnvironmentProp) {
        val fp = prop.footprint()
        val corners = projection.footprintCornersWorld(prop.coord, fp.effectiveWidth(), fp.effectiveHeight(), inset = 0.05f)
        path.reset()
        path.moveTo(corners[0].x, corners[0].y)
        path.lineTo(corners[1].x, corners[1].y)
        path.lineTo(corners[2].x, corners[2].y)
        path.lineTo(corners[3].x, corners[3].y)
        path.close()
        scope.drawPath(path, Color(0xFF5CA859))
        scope.drawPath(path, Color(0xFF81C774), style = Stroke(width = 1.4f))
    }

    private fun drawPublicClock(scope: DrawScope, cx: Float, cy: Float, s: Float, lighting: CityLightingState) {
        scope.drawOval(shadowColor, Offset(cx - 8f * s, cy - 3f * s), Size(16f * s, 8f * s))
        scope.drawRect(Color(0xFF3E4A52), Offset(cx - 2.5f * s, cy - 26f * s), Size(5f * s, 26f * s))
        val faceColor = if (lighting.phase == TimeOfDayPhase.NIGHT) Color(0xFFFFF6D6) else Color.White
        scope.drawCircle(Color(0xFF2B353C), 7.5f * s, Offset(cx, cy - 31f * s))
        scope.drawCircle(faceColor, 5.8f * s, Offset(cx, cy - 31f * s))
        scope.drawLine(Color(0xFF263238), Offset(cx, cy - 31f * s), Offset(cx, cy - 35f * s), strokeWidth = 1.4f)
        scope.drawLine(Color(0xFF263238), Offset(cx, cy - 31f * s), Offset(cx + 3.2f * s, cy - 31f * s), strokeWidth = 1.2f)
    }

    private fun drawTrashBin(scope: DrawScope, cx: Float, cy: Float, s: Float) {
        scope.drawOval(shadowColor, Offset(cx - 5f * s, cy - 2.5f * s), Size(10f * s, 5f * s))
        scope.drawRect(Color(0xFF2D6A4F), Offset(cx - 4f * s, cy - 12f * s), Size(8f * s, 11f * s))
        scope.drawRect(Color(0xFF1B4332), Offset(cx - 4.5f * s, cy - 14f * s), Size(9f * s, 2.5f * s))
    }

    private fun drawSafetyBarrier(scope: DrawScope, cx: Float, cy: Float, s: Float) {
        scope.drawRect(Color(0xFFE07A2E), Offset(cx - 11f * s, cy - 8f * s), Size(22f * s, 6f * s))
        scope.drawRect(Color.White, Offset(cx - 4f * s, cy - 8f * s), Size(6f * s, 6f * s))
        scope.drawRect(Color(0xFF37474F), Offset(cx - 9f * s, cy - 2f * s), Size(3f * s, 3f * s))
        scope.drawRect(Color(0xFF37474F), Offset(cx + 6f * s, cy - 2f * s), Size(3f * s, 3f * s))
    }

    private fun drawOrnamentArch(scope: DrawScope, cx: Float, cy: Float, s: Float, color: Color) {
        scope.drawRect(color, Offset(cx - 13f * s, cy - 22f * s), Size(4.5f * s, 22f * s))
        scope.drawRect(color, Offset(cx + 8.5f * s, cy - 22f * s), Size(4.5f * s, 22f * s))
        scope.drawArc(
            color = Color(0xFFD4AF37),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - 13f * s, cy - 34f * s),
            size = Size(26f * s, 24f * s),
            style = Stroke(width = 3.5f * s)
        )
    }

    private fun drawSeasonalDecor(scope: DrawScope, cx: Float, cy: Float, s: Float, timeSeconds: Float, lighting: CityLightingState) {
        scope.drawLine(Color(0xFF455A64), Offset(cx - 12f * s, cy - 22f * s), Offset(cx + 12f * s, cy - 22f * s), strokeWidth = 1.5f)
        val glowAlpha = if (lighting.phase == TimeOfDayPhase.NIGHT) 0.95f else 0.8f
        val colors = listOf(Color(0xFFFFD166), Color(0xFFEF476F), Color(0xFF06D6A0), Color(0xFF118AB2))
        colors.forEachIndexed { idx, col ->
            val bx = cx - 9f * s + idx * 6f * s
            val bob = sin(timeSeconds * 2f + idx) * 1.2f
            scope.drawCircle(col.copy(alpha = glowAlpha), 2.8f * s, Offset(bx, cy - 19f * s + bob))
        }
    }
}
