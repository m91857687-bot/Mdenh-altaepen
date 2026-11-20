package com.example.cityengine.camera

import androidx.compose.runtime.mutableLongStateOf
import com.example.cityengine.model.WorldBounds
import com.example.cityengine.model.WorldPoint

/**
 * Manages camera pan, focal-point pinch zoom, world bounds clamping, and screen/world transforms.
 * Reading [drawVersion] strictly inside Compose DrawScope updates frames without triggering recomposition.
 */
class CameraController(
    private val worldBounds: WorldBounds,
    val minZoom: Float = 0.55f,
    val maxZoom: Float = 2.8f,
    initialZoom: Float = 1.05f
) {
    var panX: Float = 0f
        private set
    var panY: Float = 0f
        private set
    var zoom: Float = initialZoom.coerceIn(minZoom, maxZoom)
        private set
    var viewportWidth: Float = 0f
        private set
    var viewportHeight: Float = 0f
        private set

    private val _drawVersion = mutableLongStateOf(0L)
    val drawVersion: Long
        get() = _drawVersion.longValue

    private var hasInitializedCenter = false

    fun updateViewport(width: Float, height: Float, initialCenterWorld: WorldPoint? = null) {
        if (width <= 0f || height <= 0f) return
        val changed = viewportWidth != width || viewportHeight != height
        viewportWidth = width
        viewportHeight = height
        if (!hasInitializedCenter && initialCenterWorld != null) {
            hasInitializedCenter = true
            val fitScale = minOf(
                width / ((worldBounds.maxX - worldBounds.minX).coerceAtLeast(1f) * 0.72f),
                height / ((worldBounds.maxY - worldBounds.minY).coerceAtLeast(1f) * 0.85f)
            ).coerceIn(0.75f, 1.35f)
            centerOnWorld(initialCenterWorld.x, initialCenterWorld.y, fitScale)
        } else if (changed) {
            clampToWorldBounds()
            notifyDraw()
        }
    }

    fun centerOnWorld(worldX: Float, worldY: Float, targetZoom: Float = zoom) {
        zoom = targetZoom.coerceIn(minZoom, maxZoom)
        panX = -worldX * zoom
        panY = -worldY * zoom
        clampToWorldBounds()
        notifyDraw()
    }

    fun panBy(deltaScreenX: Float, deltaScreenY: Float) {
        panX += deltaScreenX
        panY += deltaScreenY
        clampToWorldBounds()
        notifyDraw()
    }

    /**
     * Scales the camera by [zoomFactor] while keeping the world coordinate under
     * ([focalScreenX], [focalScreenY]) anchored to the same screen position (+ optional pan delta).
     */
    fun zoomAt(
        zoomFactor: Float,
        focalScreenX: Float,
        focalScreenY: Float,
        panDeltaX: Float = 0f,
        panDeltaY: Float = 0f
    ) {
        if (zoomFactor <= 0f || !zoomFactor.isFinite()) return

        val worldBefore = screenToWorld(focalScreenX, focalScreenY)
        val newZoom = (zoom * zoomFactor).coerceIn(minZoom, maxZoom)

        zoom = newZoom
        panX = (focalScreenX + panDeltaX) - viewportWidth * 0.5f - worldBefore.x * newZoom
        panY = (focalScreenY + panDeltaY) - viewportHeight * 0.5f - worldBefore.y * newZoom

        clampToWorldBounds()
        notifyDraw()
    }

    fun screenToWorld(screenX: Float, screenY: Float): WorldPoint {
        val wx = (screenX - viewportWidth * 0.5f - panX) / zoom
        val wy = (screenY - viewportHeight * 0.5f - panY) / zoom
        return WorldPoint(wx, wy)
    }

    fun worldToScreen(worldX: Float, worldY: Float): WorldPoint {
        val sx = worldX * zoom + panX + viewportWidth * 0.5f
        val sy = worldY * zoom + panY + viewportHeight * 0.5f
        return WorldPoint(sx, sy)
    }

    fun currentCenterWorld(): WorldPoint {
        return WorldPoint(
            x = -panX / zoom,
            y = -panY / zoom
        )
    }

    private fun clampToWorldBounds() {
        val centerWorldX = (-panX / zoom).coerceIn(worldBounds.minX, worldBounds.maxX)
        val centerWorldY = (-panY / zoom).coerceIn(worldBounds.minY, worldBounds.maxY)
        panX = -centerWorldX * zoom
        panY = -centerWorldY * zoom
    }

    private fun notifyDraw() {
        _drawVersion.longValue = _drawVersion.longValue + 1L
    }
}
