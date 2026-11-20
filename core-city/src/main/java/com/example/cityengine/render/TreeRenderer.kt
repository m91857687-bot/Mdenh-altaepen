package com.example.cityengine.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.cityengine.model.LampPostItem
import com.example.cityengine.model.TreeItem
import com.example.cityengine.projection.IsoProjection

/**
 * Renders organized trees and street lamp posts with soft ground shadows.
 */
class TreeRenderer(
    private val projection: IsoProjection
) {
    private val shadowColor = Color(0x28121B1F)
    private val trunkColor = Color(0xFF6B442A)
    private val canopyDeep = Color(0xFF39763E)
    private val canopyMid = Color(0xFF4F934D)
    private val canopyLight = Color(0xFF72B664)

    private val lampBase = Color(0xFF2D363B)
    private val lampPole = Color(0xFF3E4A51)
    private val lampGlow = Color(0x45FFE082)
    private val lampBulb = Color(0xFFFFE599)

    fun drawTree(scope: DrawScope, tree: TreeItem) {
        val center = projection.tileCenterWorld(tree.coord)
        val s = tree.scale

        // Ground shadow
        scope.drawOval(
            color = shadowColor,
            topLeft = Offset(center.x - 10f * s, center.y - 4f * s),
            size = androidx.compose.ui.geometry.Size(26f * s, 13f * s)
        )

        // Trunk
        scope.drawLine(
            color = trunkColor,
            start = Offset(center.x, center.y + 1f),
            end = Offset(center.x, center.y - 18f * s),
            strokeWidth = 4.2f * s,
            cap = StrokeCap.Round
        )

        // Layered isometric canopy
        if (tree.variant == 0) {
            scope.drawCircle(canopyDeep, radius = 13f * s, center = Offset(center.x + 2f * s, center.y - 23f * s))
            scope.drawCircle(canopyMid, radius = 12f * s, center = Offset(center.x - 4f * s, center.y - 25f * s))
            scope.drawCircle(canopyLight, radius = 9.5f * s, center = Offset(center.x - 2f * s, center.y - 31f * s))
        } else {
            scope.drawCircle(canopyDeep, radius = 11.5f * s, center = Offset(center.x, center.y - 21f * s))
            scope.drawCircle(canopyMid, radius = 10f * s, center = Offset(center.x, center.y - 29f * s))
            scope.drawCircle(canopyLight, radius = 7.5f * s, center = Offset(center.x - 1.5f * s, center.y - 36f * s))
        }
    }

    fun drawLampPost(scope: DrawScope, lamp: LampPostItem) {
        val center = projection.tileCenterWorld(lamp.coord)

        scope.drawOval(
            color = shadowColor,
            topLeft = Offset(center.x - 4f, center.y - 2f),
            size = androidx.compose.ui.geometry.Size(14f, 7f)
        )

        // Pedestal & slender pole
        scope.drawCircle(color = lampBase, radius = 3.2f, center = Offset(center.x, center.y))
        scope.drawLine(
            color = lampPole,
            start = Offset(center.x, center.y),
            end = Offset(center.x, center.y - 30f),
            strokeWidth = 2.4f,
            cap = StrokeCap.Round
        )

        // Short lantern arm
        scope.drawLine(
            color = lampPole,
            start = Offset(center.x, center.y - 29f),
            end = Offset(center.x + 6f, center.y - 31f),
            strokeWidth = 2.0f,
            cap = StrokeCap.Round
        )

        // Warm lantern glow
        val bulbCenter = Offset(center.x + 6.5f, center.y - 29f)
        scope.drawCircle(color = lampGlow, radius = 8.5f, center = bulbCenter)
        scope.drawCircle(color = lampBulb, radius = 2.6f, center = bulbCenter)
    }
}
