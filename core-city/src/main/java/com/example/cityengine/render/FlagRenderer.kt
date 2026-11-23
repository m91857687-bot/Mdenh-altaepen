package com.example.cityengine.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.cityengine.assets.CityAssetCache
import com.example.cityengine.model.CountryFlagStyle
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Renders animated country flags using VectorDrawable official emblems sliced smoothly for cloth wave
 * motion when [CityAssetCache] is available, with clean geometric stripe fallback in headless tests.
 */
class FlagRenderer {
    private val bandPath = Path()
    private val outlinePath = Path()

    private val poleColor = Color(0xFF4B555B)
    private val finialColor = Color(0xFFE7B345)
    private val flagBorderColor = Color(0x55000000)

    fun drawFlag(
        scope: DrawScope,
        anchorX: Float,
        anchorY: Float,
        flag: CountryFlagStyle,
        timeSeconds: Float,
        phaseOffset: Float,
        assetCache: CityAssetCache? = null,
        poleHeight: Float = 25f,
        flagWidth: Float = 21f,
        flagHeight: Float = 14f,
        directionRight: Boolean = true
    ) {
        val segments = 8
        val poleTopY = anchorY - poleHeight
        val sign = if (directionRight) 1f else -1f

        scope.drawLine(
            color = poleColor,
            start = Offset(anchorX, anchorY),
            end = Offset(anchorX, poleTopY),
            strokeWidth = 2.1f,
            cap = StrokeCap.Round
        )
        scope.drawCircle(
            color = finialColor,
            radius = 2.2f,
            center = Offset(anchorX, poleTopY - 1f)
        )

        val flagStartY = poleTopY + 1.5f
        val flagBitmap = assetCache?.getOrLoadFlagBitmap(flag.countryCode)
        if (flagBitmap != null) {
            drawWavingBitmapFlag(
                scope = scope,
                bitmap = flagBitmap,
                anchorX = anchorX,
                startY = flagStartY,
                width = flagWidth,
                height = flagHeight,
                segments = segments,
                timeSeconds = timeSeconds,
                phaseOffset = phaseOffset,
                directionSign = sign
            )
        } else {
            val colors = flag.stripeColors
            if (colors.isEmpty()) return
            if (flag.isVerticalStripes && colors.size >= 2) {
                drawVerticalStripedFlag(
                    scope = scope,
                    startX = anchorX,
                    startY = flagStartY,
                    width = flagWidth * sign,
                    height = flagHeight,
                    segments = segments,
                    colors = colors,
                    timeSeconds = timeSeconds,
                    phaseOffset = phaseOffset
                )
            } else {
                drawHorizontalStripedFlag(
                    scope = scope,
                    startX = anchorX,
                    startY = flagStartY,
                    width = flagWidth * sign,
                    height = flagHeight,
                    segments = segments,
                    colors = colors,
                    timeSeconds = timeSeconds,
                    phaseOffset = phaseOffset
                )
            }
        }

        // Crisp border around waving banner
        outlinePath.reset()
        for (i in 0..segments) {
            val u = i / segments.toFloat()
            val x = anchorX + u * flagWidth * sign
            val y = flagStartY + waveOffset(u, timeSeconds, phaseOffset)
            if (i == 0) outlinePath.moveTo(x, y) else outlinePath.lineTo(x, y)
        }
        for (i in segments downTo 0) {
            val u = i / segments.toFloat()
            val x = anchorX + u * flagWidth * sign
            val y = flagStartY + flagHeight + waveOffset(u, timeSeconds, phaseOffset)
            outlinePath.lineTo(x, y)
        }
        outlinePath.close()
        scope.drawPath(outlinePath, flagBorderColor, style = Stroke(width = 0.85f))
    }

    private fun drawWavingBitmapFlag(
        scope: DrawScope,
        bitmap: ImageBitmap,
        anchorX: Float,
        startY: Float,
        width: Float,
        height: Float,
        segments: Int,
        timeSeconds: Float,
        phaseOffset: Float,
        directionSign: Float
    ) {
        val srcW = bitmap.width
        val srcH = bitmap.height
        if (srcW <= 0 || srcH <= 0) return
        val stripDstH = height.roundToInt().coerceAtLeast(1)

        for (i in 0 until segments) {
            val u0 = i / segments.toFloat()
            val u1 = (i + 1) / segments.toFloat()
            val srcX0 = (u0 * srcW).roundToInt().coerceIn(0, srcW - 1)
            val srcX1 = (u1 * srcW).roundToInt().coerceIn(srcX0 + 1, srcW)
            val dstX0 = anchorX + (if (directionSign >= 0f) u0 * width else -(u1 * width))
            val dstX1 = anchorX + (if (directionSign >= 0f) u1 * width else -(u0 * width))
            val stripW = (dstX1 - dstX0).roundToInt().coerceAtLeast(1)
            val waveY = waveOffset((u0 + u1) * 0.5f, timeSeconds, phaseOffset)

            scope.drawImage(
                image = bitmap,
                srcOffset = IntOffset(srcX0, 0),
                srcSize = IntSize((srcX1 - srcX0).coerceAtLeast(1), srcH),
                dstOffset = IntOffset(dstX0.roundToInt(), (startY + waveY).roundToInt()),
                dstSize = IntSize(stripW, stripDstH)
            )
        }
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

        val rightSteps = (segments - leftSteps).coerceAtLeast(1)
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
        return sin(phaseRadians - u * 4.2f + phaseOffset) * (u * 2.5f)
    }
}
