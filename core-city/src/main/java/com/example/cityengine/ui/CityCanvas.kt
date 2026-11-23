package com.example.cityengine.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.cityengine.CityEngine
import com.example.cityengine.assets.CityAssetCache
import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.EnvironmentProp
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.House
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PublicBuilding
import com.example.cityengine.model.RoadSegment
import kotlin.math.PI
import kotlin.math.hypot

/**
 * High-performance Compose Canvas host for [CityEngine].
 * - Camera state and flag/fountain animation State<Float> are read ONLY inside the Canvas draw lambda,
 *   preventing Compose recompositions on pan, zoom, or animation ticks.
 * - Uses bounded [CityAssetCache] for lazy VectorDrawable rendering.
 */
@Composable
fun CityCanvas(
    engine: CityEngine,
    scene: CitySceneState,
    onHouseTapped: (House?) -> Unit,
    modifier: Modifier = Modifier,
    onPlotTapped: ((Plot?) -> Unit)? = null,
    onBuildingTapped: ((PublicBuilding?) -> Unit)? = null,
    onPropTapped: ((EnvironmentProp?) -> Unit)? = null,
    onRoadTapped: ((RoadSegment?, GridCoord) -> Unit)? = null,
    onGridCoordTapped: ((GridCoord) -> Unit)? = null,
    onGridCoordDragged: ((GridCoord) -> Unit)? = null,
    isDragPlacementActive: Boolean = false
) {
    val context = LocalContext.current
    val assetCache = remember(context) { CityAssetCache(context.applicationContext) }
    DisposableEffect(assetCache) {
        onDispose { assetCache.clear() }
    }

    LaunchedEffect(scene.gridWidth, scene.gridHeight) {
        engine.syncSceneBounds(scene)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "city_wave")
    val wavePhase = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flag_phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("city_canvas")
            .onSizeChanged { size ->
                engine.syncSceneBounds(scene)
                engine.cameraController.updateViewport(
                    width = size.width.toFloat(),
                    height = size.height.toFloat(),
                    initialCenterWorld = engine.initialFocusWorld(scene)
                )
            }
            .pointerInput(scene, isDragPlacementActive) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val downPosition = firstDown.position
                    var totalMove = 0f
                    var isMultiTouch = false
                    var lastPosition = downPosition
                    var lastDraggedCoord: GridCoord? = if (isDragPlacementActive && onGridCoordDragged != null) {
                        engine.screenToGridCoord(downPosition.x, downPosition.y).also { coord ->
                            onGridCoordDragged(coord)
                        }
                    } else {
                        null
                    }

                    do {
                        val event = awaitPointerEvent()
                        val pressedChanges = event.changes.filter { it.pressed }

                        if (pressedChanges.size >= 2) {
                            isMultiTouch = true
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)
                            if (zoomChange != 1f || panChange.x != 0f || panChange.y != 0f) {
                                engine.cameraController.zoomAt(
                                    zoomFactor = zoomChange,
                                    focalScreenX = centroid.x,
                                    focalScreenY = centroid.y,
                                    panDeltaX = panChange.x,
                                    panDeltaY = panChange.y
                                )
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } else if (pressedChanges.size == 1 && !isMultiTouch) {
                            val change = pressedChanges.first()
                            val dx = change.position.x - change.previousPosition.x
                            val dy = change.position.y - change.previousPosition.y
                            totalMove += hypot(dx, dy)
                            lastPosition = change.position
                            if (isDragPlacementActive && onGridCoordDragged != null) {
                                val hovered = engine.screenToGridCoord(lastPosition.x, lastPosition.y)
                                if (hovered != lastDraggedCoord) {
                                    lastDraggedCoord = hovered
                                    onGridCoordDragged(hovered)
                                }
                                if (dx != 0f || dy != 0f) change.consume()
                            } else if (totalMove > touchSlop && (dx != 0f || dy != 0f)) {
                                engine.cameraController.panBy(dx, dy)
                                change.consume()
                            }
                        } else if (pressedChanges.size == 1 && isMultiTouch) {
                            lastPosition = pressedChanges.first().position
                        }
                    } while (event.changes.any { it.pressed })

                    if (!isMultiTouch && totalMove <= touchSlop) {
                        val tappedCoord = engine.screenToGridCoord(lastPosition.x, lastPosition.y)
                        if (onGridCoordTapped != null) {
                            onGridCoordTapped(tappedCoord)
                            return@awaitEachGesture
                        }

                        val tappedHouse = engine.findHouseAtScreenPoint(
                            screenX = lastPosition.x,
                            screenY = lastPosition.y,
                            scene = scene
                        )
                        if (tappedHouse != null) {
                            onHouseTapped(tappedHouse)
                            onBuildingTapped?.invoke(null)
                            onPropTapped?.invoke(null)
                            onRoadTapped?.invoke(null, tappedCoord)
                        } else {
                            onHouseTapped(null)
                            val tappedBuilding = if (onBuildingTapped != null) {
                                engine.findPublicBuildingAtScreenPoint(
                                    screenX = lastPosition.x,
                                    screenY = lastPosition.y,
                                    scene = scene
                                )
                            } else {
                                null
                            }
                            if (tappedBuilding != null) {
                                onBuildingTapped?.invoke(tappedBuilding)
                                onPlotTapped?.invoke(null)
                                onPropTapped?.invoke(null)
                                onRoadTapped?.invoke(null, tappedCoord)
                            } else {
                                onBuildingTapped?.invoke(null)
                                val tappedProp = if (onPropTapped != null) {
                                    engine.findEnvironmentPropAtScreenPoint(
                                        screenX = lastPosition.x,
                                        screenY = lastPosition.y,
                                        scene = scene
                                    )
                                } else {
                                    null
                                }
                                if (tappedProp != null) {
                                    onPropTapped?.invoke(tappedProp)
                                    onPlotTapped?.invoke(null)
                                    onRoadTapped?.invoke(null, tappedCoord)
                                } else {
                                    onPropTapped?.invoke(null)
                                    val tappedPlot = if (onPlotTapped != null) {
                                        engine.findPlotAtScreenPoint(
                                            screenX = lastPosition.x,
                                            screenY = lastPosition.y,
                                            scene = scene
                                        )
                                    } else {
                                        null
                                    }
                                    if (tappedPlot != null) {
                                        onPlotTapped?.invoke(tappedPlot)
                                        onRoadTapped?.invoke(null, tappedCoord)
                                    } else {
                                        onPlotTapped?.invoke(null)
                                        if (onRoadTapped != null) {
                                            val seg = engine.findRoadSegmentAtCoord(tappedCoord, scene)
                                            if (seg != null || scene.tiles[tappedCoord] == CityTileType.ROAD) {
                                                onRoadTapped(seg, tappedCoord)
                                            } else {
                                                onRoadTapped(null, tappedCoord)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        // Read draw-phase states inside DrawScope only -> zero recompositions during animation/camera moves
        @Suppress("UNUSED_VARIABLE")
        val camTick = engine.cameraController.drawVersion
        val phaseRadians = wavePhase.value
        engine.renderer.render(
            scope = this,
            scene = scene,
            camera = engine.cameraController,
            timeSeconds = phaseRadians,
            assetCache = assetCache
        )
    }
}
