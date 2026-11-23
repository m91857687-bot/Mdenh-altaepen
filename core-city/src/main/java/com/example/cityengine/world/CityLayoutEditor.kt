package com.example.cityengine.world

import com.example.cityengine.model.CityLightSource
import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.DistrictZone
import com.example.cityengine.model.EnvironmentProp
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.model.PublicBuilding
import com.example.cityengine.model.RoadSegment
import com.example.cityengine.model.TileTextureVariant

/**
 * Pure, immutable scene-expansion & element-placement utilities allowing the city
 * to grow dynamically beyond 12x12 and accept new districts, tiles, buildings, props, and lights
 * without altering core engine loops.
 */
object CityLayoutEditor {

    fun expandGrid(
        scene: CitySceneState,
        newWidth: Int,
        newHeight: Int,
        defaultTileType: CityTileType = CityTileType.GRASS
    ): CitySceneState {
        require(newWidth >= scene.gridWidth && newHeight >= scene.gridHeight) {
            "New dimensions (${newWidth}x${newHeight}) must be >= current (${scene.gridWidth}x${scene.gridHeight})."
        }
        val expandedTiles = HashMap<GridCoord, CityTileType>(newWidth * newHeight)
        expandedTiles.putAll(scene.tiles)
        for (y in 0 until newHeight) {
            for (x in 0 until newWidth) {
                val coord = GridCoord(x, y)
                if (coord !in expandedTiles) {
                    expandedTiles[coord] = defaultTileType
                }
            }
        }
        return scene.copy(
            gridWidth = newWidth,
            gridHeight = newHeight,
            tiles = expandedTiles
        )
    }

    fun addDistrict(scene: CitySceneState, district: DistrictZone): CitySceneState {
        val updated = scene.districts.filterNot { it.id == district.id } + district
        return scene.copy(districts = updated)
    }

    fun paintTiles(
        scene: CitySceneState,
        coords: Iterable<GridCoord>,
        tileType: CityTileType,
        textureVariant: TileTextureVariant? = null
    ): CitySceneState {
        val nextTiles = scene.tiles.toMutableMap()
        val nextTextures = scene.tileTextures.toMutableMap()
        for (coord in coords) {
            if (coord.x in 0 until scene.gridWidth && coord.y in 0 until scene.gridHeight) {
                nextTiles[coord] = tileType
                if (textureVariant != null) {
                    nextTextures[coord] = textureVariant
                }
            }
        }
        return scene.copy(tiles = nextTiles, tileTextures = nextTextures)
    }

    fun addRoadSegment(scene: CitySceneState, segment: RoadSegment): CitySceneState {
        val roadCoords = buildList {
            for (dy in 0 until segment.heightCells.coerceAtLeast(1)) {
                for (dx in 0 until segment.widthCells.coerceAtLeast(1)) {
                    add(GridCoord(segment.coord.x + dx, segment.coord.y + dy))
                }
            }
        }
        val painted = paintTiles(scene, roadCoords, CityTileType.ROAD, segment.surfaceType)
        val segments = painted.roadSegments.filterNot { it.id == segment.id } + segment
        return painted.copy(roadSegments = segments)
    }

    fun placePublicBuilding(
        scene: CitySceneState,
        building: PublicBuilding
    ): Pair<ValidationResult, CitySceneState> {
        val validator = SpatialValidator(scene.gridWidth, scene.gridHeight, scene.tiles)
        val existingOthers = scene.publicBuildings.filterNot { it.id == building.id }
        val check = validator.validateCanPlaceBuilding(building, existingOthers, scene.plots, scene.houses)
        if (check is ValidationResult.Invalid) return check to scene
        return ValidationResult.Valid to scene.copy(publicBuildings = existingOthers + building)
    }

    fun placeEnvironmentProp(
        scene: CitySceneState,
        prop: EnvironmentProp
    ): Pair<ValidationResult, CitySceneState> {
        val validator = SpatialValidator(scene.gridWidth, scene.gridHeight, scene.tiles)
        val check = validator.validateCanPlaceProp(prop, scene.houses, scene.publicBuildings)
        if (check is ValidationResult.Invalid) return check to scene
        val updated = scene.environmentProps.filterNot { it.id == prop.id } + prop
        return ValidationResult.Valid to scene.copy(environmentProps = updated)
    }

    fun placePlot(
        scene: CitySceneState,
        plot: Plot
    ): Pair<ValidationResult, CitySceneState> {
        val validator = SpatialValidator(scene.gridWidth, scene.gridHeight, scene.tiles)
        val others = scene.plots.filterNot { it.id == plot.id }
        val preCheck = validator.validateNewPlotArea(plot, others, scene.publicBuildings, scene.houses)
        if (preCheck is ValidationResult.Invalid) return preCheck to scene
        val prepared = paintTiles(scene, plot.occupiedCoords(), plot.groundType, plot.textureVariant)
        return ValidationResult.Valid to prepared.copy(plots = others + plot)
    }

    fun removePlot(scene: CitySceneState, plotId: String): Pair<ValidationResult, CitySceneState> {
        val target = scene.plots.firstOrNull { it.id == plotId }
            ?: return ValidationResult.Invalid("القطعة $plotId غير موجودة.") to scene
        if (target.status == PlotStatus.OCCUPIED || target.houseId != null) {
            return ValidationResult.Invalid("لا يمكن حذف قطعة مشغولة ببيت (${target.id}). احذف البيت أو انقله أولاً.") to scene
        }
        val resetScene = paintTiles(scene, target.occupiedCoords(), CityTileType.GRASS, TileTextureVariant.DEFAULT)
        return ValidationResult.Valid to resetScene.copy(plots = resetScene.plots.filterNot { it.id == plotId })
    }

    fun removePublicBuilding(scene: CitySceneState, buildingId: String): CitySceneState {
        return scene.copy(publicBuildings = scene.publicBuildings.filterNot { it.id == buildingId })
    }

    fun removeEnvironmentProp(scene: CitySceneState, propId: String): CitySceneState {
        return scene.copy(environmentProps = scene.environmentProps.filterNot { it.id == propId })
    }

    fun removeDistrict(scene: CitySceneState, districtId: String): CitySceneState {
        return scene.copy(
            districts = scene.districts.filterNot { it.id == districtId },
            plots = scene.plots.map { if (it.districtId == districtId) it.copy(districtId = null) else it },
            publicBuildings = scene.publicBuildings.map { if (it.districtId == districtId) it.copy(districtId = null) else it }
        )
    }

    fun removeRoadSegment(scene: CitySceneState, segmentId: String): CitySceneState {
        return scene.copy(roadSegments = scene.roadSegments.filterNot { it.id == segmentId })
    }

    fun eraseTilesToGrass(
        scene: CitySceneState,
        coords: Set<GridCoord>
    ): Pair<ValidationResult, CitySceneState> {
        val validator = SpatialValidator(scene.gridWidth, scene.gridHeight, scene.tiles)
        val check = validator.validateCanPaintRoadOrSidewalk(coords, scene.plots, scene.houses, scene.publicBuildings)
        if (check is ValidationResult.Invalid) return check to scene
        val painted = paintTiles(scene, coords, CityTileType.GRASS, TileTextureVariant.DEFAULT)
        val remainingSegments = painted.roadSegments.filterNot { seg ->
            val segCoords = buildList {
                for (dy in 0 until seg.heightCells.coerceAtLeast(1)) {
                    for (dx in 0 until seg.widthCells.coerceAtLeast(1)) {
                        add(GridCoord(seg.coord.x + dx, seg.coord.y + dy))
                    }
                }
            }
            segCoords.all { it in coords }
        }
        val remainingTrees = painted.trees.filterNot { it.coord in coords }
        val remainingLamps = painted.lampPosts.filterNot { it.coord in coords }
        return ValidationResult.Valid to painted.copy(
            roadSegments = remainingSegments,
            trees = remainingTrees,
            lampPosts = remainingLamps
        )
    }

    fun upsertLightSource(scene: CitySceneState, light: CityLightSource): CitySceneState {
        val updated = scene.lights.filterNot { it.id == light.id } + light
        return scene.copy(lights = updated)
    }

    fun removeLightSource(scene: CitySceneState, lightId: String): CitySceneState {
        return scene.copy(lights = scene.lights.filterNot { it.id == lightId })
    }
}
