package com.example.cityengine.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.cityengine.model.CountryFlagStyle
import kotlin.math.sin

/**
 * Renders an animated country flag above a house roof peak using smooth sine-wave cloth motion
 * driven strictly in DrawScope (zero Compose recompositions).
 */
class FlagRenderer {
    private val bandPath = Path()
    private val outlinePath = Path()

    private val poleColor = Color(0xFF4B555B)
    private val finialColor = Color(0xFFE7B345)
    private val flagBorderColor = Color(0x44000000)

    fun drawFlag(
        scope: DrawScope,
        anchorX: Float,
        anchorY: Float,
        flag: CountryFlagStyle,
        timeSeconds: Float,
        phaseOffset: Float
    ) {
        val poleHeight = 25f
        val flagWidth = 20f
        val flagHeight = 12f
        val segments = 6
        val poleTopY = anchorY - poleHeight

        // Pole shadow & metallic pole
        scope.drawLine(
            color = poleColor,
            start = Offset(anchorX, anchorY),
            end = Offset(anchorX, poleTopY),
            strokeWidth = 2.0f,
            cap = StrokeCap.Round
        )
        scope.drawCircle(
            color = finialColor,
            radius = 2.1f,
            center = Offset(anchorX, poleTopY - 1f)
        )

        val colors = flag.stripeColors
        if (colors.isEmpty()) return

        if (flag.isVerticalStripes && colors.size >= 2) {
            drawVerticalStripedFlag(scope, anchorX, poleTopY + 1.5f, flagWidth, flagHeight, segments, colors, timeSeconds, phaseOffset)
        } else {
            drawHorizontalStripedFlag(scope, anchorX, poleTopY + 1.5f, flagWidth, flagHeight, segments, colors, timeSeconds, phaseOffset)
        }

        // Crisp border around waving banner
        outlinePath.reset()
        for (i in 0..segments) {
            val u = i / segments.toFloat()
            val x = anchorX + u * flagWidth
            val y = poleTopY + 1.5f + waveOffset(u, timeSeconds, phaseOffset)
            if (i == 0) outlinePath.moveTo(x, y) else outlinePath.lineTo(x, y)
        }
        for (i in segments downTo 0) {
            val u = i / segments.toFloat()
            val x = anchorX + u * flagWidth
            val y = poleTopY + 1.5f + flagHeight + waveOffset(u, timeSeconds, phaseOffset)
            outlinePath.lineTo(x, y)
        }
        outlinePath.close()
        scope.drawPath(outlinePath, flagBorderColor, style = Stroke(width = 0.8f))
    }

    private fun drawHorizontalStripedFlag(
        scope: DrawScope,
        startX: Float,
        startY: Float,
        width: Float,
        height: Float,
        segments: Int,
        colors: List<Long>,
        timeSeconds: Float,
        phaseOffset: Float
    ) {
        val bandHeight = height / colors.size
        for (bandIdx in colors.indices) {
            val topOff = bandIdx * bandHeight
            val botOff = (bandIdx + 1) * bandHeight
            bandPath.reset()
            for (i in 0..segments) {
                val u = i / segments.toFloat()
                val x = startX + u * width
                val y = startY + topOff + waveOffset(u, timeSeconds, phaseOffset)
                if (i == 0) bandPath.moveTo(x, y) else bandPath.lineTo(x, y)
            }
            for (i in segments downTo 0) {
                val u = i / segments.toFloat()
                val x = startX + u * width
                val y = startY + botOff + waveOffset(u, timeSeconds, phaseOffset)
                bandPath.lineTo(x, y)
            }
            bandPath.close()
            scope.drawPath(bandPath, Color(colors[bandIdx]))
        }
    }

    private fun drawVerticalStripedFlag(
        scope: DrawScope,
        startX: Float,
        startY: Float,
        width: Float,
        height: Float,
        segments: Int,
        colors: List<Long>,
        timeSeconds: Float,
        phaseOffset: Float
    ) {
        val splitPoint = 0.36f
        val leftSteps = 2
        // Left hoist strip
        bandPath.reset()
        for (i in 0..leftSteps) {
            val u = (i / leftSteps.toFloat()) * splitPoint
            val x = startX + u * width
            val y = startY + waveOffset(u, timeSeconds, phaseOffset)
            if (i == 0) bandPath.moveTo(x, y) else bandPath.lineTo(x, y)
        }
        for (i in leftSteps downTo 0) {
            val u = (i / leftSteps.toFloat()) * splitPoint
            val x = startX + u * width
            val y = startY + height + waveOffset(u, timeSeconds, phaseOffset)
            bandPath.lineTo(x, y)
        }
        bandPath.close()
        scope.drawPath(bandPath, Color(colors[0]))

        // Right fly strip
        val rightSteps = segments - leftSteps
        bandPath.reset()
        for (i in 0..rightSteps) {
            val u = splitPoint + (i / rightSteps.toFloat()) * (1f - splitPoint)
            val x = startX + u * width
            val y = startY + waveOffset(u, timeSeconds, phaseOffset)
            if (i == 0) bandPath.moveTo(x, y) else bandPath.lineTo(x, y)
        }
        for (i in rightSteps downTo 0) {
            val u = splitPoint + (i / rightSteps.toFloat()) * (1f - splitPoint)
            val x = startX + u * width
            val y = startY + height + waveOffset(u, timeSeconds, phaseOffset)
            bandPath.lineTo(x, y)
        }
        bandPath.close()
        scope.drawPath(bandPath, Color(colors[1]))
    }

    private fun waveOffset(u: Float, phaseRadians: Float, phaseOffset: Float): Float {
        // Anchored at pole (u = 0 -> 0f), gentle seamless wave toward the fly tip (u = 1)
        return sin(phaseRadians - u * 4.2f + phaseOffset) * (u * 2.6f)
    }
}
