package com.example.cityengine.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.cityengine.model.CityLightSource
import com.example.cityengine.model.CityLightingState
import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.LightSourceType
import com.example.cityengine.model.TimeOfDayPhase
import com.example.cityengine.performance.LodTier
import com.example.cityengine.performance.VisibleGridBounds
import com.example.cityengine.projection.IsoProjection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Lightweight, allocation-safe atmospheric lighting system for Compose DrawScope:
 * - Sky & ambient color grading for DAY, SUNSET, and NIGHT phases
 * - Directional cast shadows derived from sun azimuth
 * - Emissive window/entrance colors & isometric ground light pools (omni + directional cones)
 */
class LightingRenderer(
    private val projection: IsoProjection
) {
    private val conePath = Path()

    private val daySkyBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFFCDE6F2), Color(0xFFF3ECE0))
    )
    private val sunsetSkyBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF474973), Color(0xFFD97D54), Color(0xFFF6BE7A))
    )
    private val nightSkyBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0E1726), Color(0xFF1A2942), Color(0xFF233754))
    )

    fun drawSkyBackdrop(scope: DrawScope, lighting: CityLightingState) {
        val brush = when (lighting.phase) {
            TimeOfDayPhase.DAY -> daySkyBrush
            TimeOfDayPhase.SUNSET -> sunsetSkyBrush
            TimeOfDayPhase.NIGHT -> nightSkyBrush
        }
        scope.drawRect(brush = brush)
    }

    fun shadowOffset(lighting: CityLightingState): Offset {
        val rad = lighting.sunAzimuthDeg * (PI.toFloat() / 180f)
        val lengthFactor = when (lighting.phase) {
            TimeOfDayPhase.DAY -> 11f
            TimeOfDayPhase.SUNSET -> 16f
            TimeOfDayPhase.NIGHT -> 7f
        }
        return Offset(
            x = cos(rad) * lengthFactor,
            y = sin(rad) * (lengthFactor * 0.52f)
        )
    }

    fun shadowColor(lighting: CityLightingState): Color = when (lighting.phase) {
        TimeOfDayPhase.DAY -> Color(0x30141B1F)
        TimeOfDayPhase.SUNSET -> Color(0x3E2A1A26)
        TimeOfDayPhase.NIGHT -> Color(0x28050912)
    }

    fun resolveWindowColor(baseWindow: Color, lighting: CityLightingState, hasLitWindows: Boolean): Color {
        if (!hasLitWindows) return baseWindow
        return when (lighting.phase) {
            TimeOfDayPhase.DAY -> baseWindow
            TimeOfDayPhase.SUNSET -> Color(0xFFFFD87D)
            TimeOfDayPhase.NIGHT -> Color(0xFFFFE58F)
        }
    }

    fun drawAmbientAtmosphereOverlay(scope: DrawScope, lighting: CityLightingState) {
        val overlayAlpha = when (lighting.phase) {
            TimeOfDayPhase.DAY -> lighting.ambientDimming.coerceIn(0f, 0.35f)
            TimeOfDayPhase.SUNSET -> (0.14f + lighting.ambientDimming * 0.25f).coerceIn(0f, 0.45f)
            TimeOfDayPhase.NIGHT -> (0.34f + lighting.ambientDimming * 0.25f).coerceIn(0f, 0.58f)
        }
        if (overlayAlpha <= 0.01f) return
        val tint = when (lighting.phase) {
            TimeOfDayPhase.DAY -> Color(0xFF102030).copy(alpha = overlayAlpha)
            TimeOfDayPhase.SUNSET -> Color(0xFF4A2538).copy(alpha = overlayAlpha)
            TimeOfDayPhase.NIGHT -> Color(0xFF0A1429).copy(alpha = overlayAlpha)
        }
        scope.drawRect(color = tint)
    }

    fun drawLightPools(
        scope: DrawScope,
        scene: CitySceneState,
        visibleBounds: VisibleGridBounds
    ) {
        val lighting = scene.lightingState
        val isNightOrDusk = lighting.phase != TimeOfDayPhase.DAY
        if (!isNightOrDusk && scene.lights.none { it.isEnabled && it.type == LightSourceType.FOUNTAIN_GLOW }) {
            return
        }
        if (visibleBounds.lodTier == LodTier.LOW) return

        val phaseAlphaBoost = when (lighting.phase) {
            TimeOfDayPhase.DAY -> 0.20f
            TimeOfDayPhase.SUNSET -> 0.55f
            TimeOfDayPhase.NIGHT -> 0.92f
        }

        // Lamp post automatic pools during sunset/night
        if (lighting.nightLampAutoOn && isNightOrDusk) {
            for (i in scene.lampPosts.indices) {
                val lamp = scene.lampPosts[i]
                if (!visibleBounds.containsCoord(lamp.coord)) continue
                val center = projection.tileCenterWorld(lamp.coord)
                drawEllipticalPool(
                    scope = scope,
                    cx = center.x,
                    cy = center.y,
                    radius = 78f,
                    color = Color(0xFFFFDE7A),
                    alpha = 0.22f * phaseAlphaBoost
                )
            }
        }

        // Custom light sources
        for (i in scene.lights.indices) {
            val light = scene.lights[i]
            if (!light.isEnabled || !visibleBounds.containsCoord(light.coord)) continue
            drawLightSource(scope, light, phaseAlphaBoost)
        }
    }

    private fun drawLightSource(
        scope: DrawScope,
        light: CityLightSource,
        phaseAlphaBoost: Float
    ) {
        val center = projection.tileCenterWorld(light.coord)
        val wx = center.x + light.offsetX
        val wy = center.y + light.offsetY
        val baseColor = Color(light.colorHex)
        val alpha = (0.28f * light.intensity * phaseAlphaBoost).coerceIn(0.05f, 0.55f)

        if (light.coneAngleDeg < 300f) {
            val dirRad = light.directionDeg * (PI.toFloat() / 180f)
            val halfConeRad = (light.coneAngleDeg * 0.5f) * (PI.toFloat() / 180f)
            val r = light.radiusWorld
            val x1 = wx + cos(dirRad - halfConeRad) * r
            val y1 = wy + sin(dirRad - halfConeRad) * (r * 0.55f)
            val x2 = wx + cos(dirRad + halfConeRad) * r
            val y2 = wy + sin(dirRad + halfConeRad) * (r * 0.55f)

            conePath.reset()
            conePath.moveTo(wx, wy)
            conePath.lineTo(x1, y1)
            conePath.lineTo(x2, y2)
            conePath.close()
            scope.drawPath(conePath, baseColor.copy(alpha = alpha * 0.72f))
        }

        drawEllipticalPool(
            scope = scope,
            cx = wx,
            cy = wy,
            radius = light.radiusWorld,
            color = baseColor,
            alpha = alpha
        )
    }

    private fun drawEllipticalPool(
        scope: DrawScope,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Color,
        alpha: Float
    ) {
        // Two nested isometric ellipses approximate soft radial falloff with minimal GPU cost
        val outerW = radius * 2f
        val outerH = radius
        scope.drawOval(
            color = color.copy(alpha = alpha * 0.42f),
            topLeft = Offset(cx - radius, cy - outerH * 0.5f),
            size = Size(outerW, outerH)
        )
        val innerR = radius * 0.48f
        scope.drawOval(
            color = color.copy(alpha = alpha * 0.65f),
            topLeft = Offset(cx - innerR, cy - innerR * 0.5f),
            size = Size(innerR * 2f, innerR)
        )
    }
}
