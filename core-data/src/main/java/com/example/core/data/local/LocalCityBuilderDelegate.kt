package com.example.core.data.local

import com.example.cityengine.model.BuildingRotation
import com.example.cityengine.model.CityLightSource
import com.example.cityengine.model.CityLightingState
import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.DistrictZone
import com.example.cityengine.model.EnvironmentProp
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.GridFootprint
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.model.PublicBuilding
import com.example.cityengine.model.RoadSegment
import com.example.cityengine.world.CityLayoutEditor
import com.example.cityengine.world.PlotManager
import com.example.cityengine.world.SpatialValidator
import com.example.cityengine.world.ValidationResult
import com.example.core.data.model.AdminOperationResult
import com.example.core.data.model.DistrictDraft
import com.example.core.data.model.EnvironmentPropDraft
import com.example.core.data.model.LightSourceDraft
import com.example.core.data.model.PlotCreateDraft
import com.example.core.data.model.PublicBuildingDraft
import com.example.core.data.model.RoadSegmentDraft
import com.example.core.data.model.TilePaintChange

/**
 * Modular Phase 2 local builder delegate backing [LocalCityAdminRepository] for deterministic offline tests.
 */
internal class LocalCityBuilderDelegate(
    private val getScene: () -> CitySceneState,
    private val updateScene: (CitySceneState) -> Unit
) {
    private var nextSeq = 100

    private fun nextId(prefix: String): String = "${prefix}_${nextSeq++}"

    private fun validator(scene: CitySceneState = getScene()) =
        SpatialValidator(scene.gridWidth, scene.gridHeight, scene.tiles)

    private fun plotManager(scene: CitySceneState = getScene()) =
        PlotManager(scene.plots, scene.houses, validator(scene))

    fun expandCityGrid(newWidth: Int, newHeight: Int): AdminOperationResult {
        val cur = getScene()
        if (newWidth < cur.gridWidth || newHeight < cur.gridHeight) {
            return AdminOperationResult.Failure("لا يمكن تقليص أبعاد المدينة عن (${cur.gridWidth}×${cur.gridHeight}).")
        }
        updateScene(CityLayoutEditor.expandGrid(cur, newWidth, newHeight))
        return AdminOperationResult.Success("تم توسيع خريطة المدينة إلى ${newWidth}×${newHeight}.")
    }

    fun createPlot(draft: PlotCreateDraft): AdminOperationResult {
        val cur = getScene()
        val id = draft.id?.takeIf { it.isNotBlank() } ?: nextId("plot")
        val code = draft.plotCode?.takeIf { it.isNotBlank() } ?: id.uppercase()
        val plot = Plot(
            id = id,
            coord = draft.coord,
            status = draft.status,
            widthCells = draft.widthCells.coerceAtLeast(1),
            heightCells = draft.heightCells.coerceAtLeast(1),
            rotation = draft.rotation,
            districtId = draft.districtId,
            groundType = draft.groundType,
            textureVariant = draft.textureVariant,
            plotCode = code
        )
        val (res, updated) = CityLayoutEditor.placePlot(cur, plot)
        return when (res) {
            is ValidationResult.Invalid -> AdminOperationResult.Failure(res.reason)
            is ValidationResult.Valid -> {
                updateScene(updated)
                AdminOperationResult.Success("تم إنشاء القطعة $code بنجاح.", affectedPlotId = id)
            }
        }
    }

    fun resizePlot(plotId: String, widthCells: Int, heightCells: Int): AdminOperationResult {
        if (widthCells <= 0 || heightCells <= 0) {
            return AdminOperationResult.Failure("أبعاد القطعة يجب أن تكون 1×1 على الأقل.")
        }
        val cur = getScene()
        val existing = cur.plots.firstOrNull { it.id == plotId }
            ?: return AdminOperationResult.Failure("القطعة $plotId غير موجودة.")
        val candidate = existing.copy(widthCells = widthCells, heightCells = heightCells)
        val clearedOld = cur.tiles.toMutableMap().apply {
            existing.occupiedCoords().forEach { put(it, CityTileType.GRASS) }
        }
        val check = SpatialValidator(cur.gridWidth, cur.gridHeight, clearedOld).validateNewPlotArea(
            candidate,
            cur.plots.filterNot { it.id == plotId },
            cur.publicBuildings,
            cur.houses.filterNot { it.id == existing.houseId }
        )
        return when (check) {
            is ValidationResult.Invalid -> AdminOperationResult.Failure(check.reason)
            is ValidationResult.Valid -> {
                candidate.occupiedCoords().forEach { clearedOld[it] = candidate.groundType }
                val updatedPlots = cur.plots.map { if (it.id == plotId) candidate else it }
                val updatedHouses = cur.houses.map { house ->
                    if (house.id == existing.houseId) house.copy(widthCells = widthCells, heightCells = heightCells) else house
                }
                updateScene(cur.copy(plots = updatedPlots, houses = updatedHouses, tiles = clearedOld))
                AdminOperationResult.Success("تم تعديل أبعاد القطعة.", affectedPlotId = plotId)
            }
        }
    }

    fun rotatePlot(plotId: String, rotation: BuildingRotation): AdminOperationResult {
        val cur = getScene()
        val existing = cur.plots.firstOrNull { it.id == plotId }
            ?: return AdminOperationResult.Failure("القطعة $plotId غير موجودة.")
        val candidate = existing.copy(rotation = rotation)
        val newTiles = cur.tiles.toMutableMap().apply {
            existing.occupiedCoords().forEach { put(it, CityTileType.GRASS) }
        }
        val check = SpatialValidator(cur.gridWidth, cur.gridHeight, newTiles).validateNewPlotArea(
            candidate,
            cur.plots.filterNot { it.id == plotId },
            cur.publicBuildings,
            cur.houses.filterNot { it.id == existing.houseId }
        )
        return when (check) {
            is ValidationResult.Invalid -> AdminOperationResult.Failure(check.reason)
            is ValidationResult.Valid -> {
                candidate.occupiedCoords().forEach { newTiles[it] = candidate.groundType }
                val updatedPlots = cur.plots.map { if (it.id == plotId) candidate else it }
                val updatedHouses = cur.houses.map { house ->
                    if (house.id == existing.houseId) house.copy(rotation = rotation) else house
                }
                updateScene(cur.copy(plots = updatedPlots, houses = updatedHouses, tiles = newTiles))
                AdminOperationResult.Success("تم تدوير القطعة إلى ${rotation.degrees}°.", affectedPlotId = plotId)
            }
        }
    }

    fun movePlot(plotId: String, newCoord: GridCoord): AdminOperationResult {
        val cur = getScene()
        val existing = cur.plots.firstOrNull { it.id == plotId }
            ?: return AdminOperationResult.Failure("القطعة $plotId غير موجودة.")
        if (existing.houseId != null || existing.status == PlotStatus.OCCUPIED) {
            return AdminOperationResult.Failure("لا يمكن نقل قطعة مشغولة ببيت.")
        }
        val without = cur.copy(
            plots = cur.plots.filterNot { it.id == plotId },
            tiles = cur.tiles.toMutableMap().apply {
                existing.occupiedCoords().forEach { put(it, CityTileType.GRASS) }
            }
        )
        val moved = existing.copy(coord = newCoord)
        val (res, updated) = CityLayoutEditor.placePlot(without, moved)
        return when (res) {
            is ValidationResult.Invalid -> AdminOperationResult.Failure(res.reason)
            is ValidationResult.Valid -> {
                updateScene(updated)
                AdminOperationResult.Success("تم نقل القطعة إلى (${newCoord.x}, ${newCoord.y}).", affectedPlotId = plotId)
            }
        }
    }

    fun splitPlot(plotId: String, splitHorizontally: Boolean): AdminOperationResult {
        val cur = getScene()
        val pm = plotManager(cur)
        val (res, created) = pm.splitPlot(plotId, splitHorizontally, "${plotId}_a", "${plotId}_b")
        return when (res) {
            is ValidationResult.Invalid -> AdminOperationResult.Failure(res.reason)
            is ValidationResult.Valid -> {
                updateScene(cur.copy(plots = pm.plots))
                AdminOperationResult.Success("تم تقسيم القطعة إلى ${created.size} قطع.", affectedPlotId = created.firstOrNull()?.id)
            }
        }
    }

    fun mergePlots(firstPlotId: String, secondPlotId: String): AdminOperationResult {
        val cur = getScene()
        val pm = plotManager(cur)
        val (res, merged) = pm.mergePlots(firstPlotId, secondPlotId, nextId("plot_m"))
        return when (res) {
            is ValidationResult.Invalid -> AdminOperationResult.Failure(res.reason)
            is ValidationResult.Valid -> {
                updateScene(cur.copy(plots = pm.plots))
                AdminOperationResult.Success("تم دمج القطعتين بنجاح.", affectedPlotId = merged?.id)
            }
        }
    }

    fun setPlotAvailability(plotId: String, available: Boolean): AdminOperationResult {
        val cur = getScene()
        val pm = plotManager(cur)
        return when (val res = pm.setPlotAvailability(plotId, available)) {
            is ValidationResult.Invalid -> AdminOperationResult.Failure(res.reason)
            is ValidationResult.Valid -> {
                updateScene(cur.copy(plots = pm.plots))
                AdminOperationResult.Success(if (available) "تم إتاحة القطعة للبناء." else "تم تعطيل القطعة مؤقتًا.", affectedPlotId = plotId)
            }
        }
    }

    fun assignPlotDistrict(plotId: String, districtId: String?): AdminOperationResult {
        val cur = getScene()
        if (cur.plots.none { it.id == plotId }) return AdminOperationResult.Failure("القطعة $plotId غير موجودة.")
        updateScene(cur.copy(plots = cur.plots.map { if (it.id == plotId) it.copy(districtId = districtId) else it }))
        return AdminOperationResult.Success("تم تحديث الحي التابع للقطعة.", affectedPlotId = plotId, affectedDistrictId = districtId)
    }

    fun deletePlot(plotId: String): AdminOperationResult {
        val (res, updated) = CityLayoutEditor.removePlot(getScene(), plotId)
        return when (res) {
            is ValidationResult.Invalid -> AdminOperationResult.Failure(res.reason)
            is ValidationResult.Valid -> {
                updateScene(updated)
                AdminOperationResult.Success("تم حذف القطعة بنجاح.", affectedPlotId = plotId)
            }
        }
    }

    fun batchUpdatePlotAvailability(plotIds: List<String>, available: Boolean): AdminOperationResult {
        var cur = getScene()
        var updatedCount = 0
        val newPlots = cur.plots.map { plot ->
            if (plot.id in plotIds && plot.houseId == null && plot.status != PlotStatus.OCCUPIED) {
                updatedCount++
                plot.copy(status = if (available) PlotStatus.AVAILABLE else PlotStatus.UNAVAILABLE)
            } else {
                plot
            }
        }
        if (updatedCount == 0) return AdminOperationResult.Failure("لا توجد قطع غير مشغولة ضمن التحديد.")
        updateScene(cur.copy(plots = newPlots))
        return AdminOperationResult.Success("تم تحديث حالة $updatedCount قطعة.")
    }

    fun batchAssignPlotsDistrict(plotIds: List<String>, districtId: String?): AdminOperationResult {
        val cur = getScene()
        val count = cur.plots.count { it.id in plotIds }
        if (count == 0) return AdminOperationResult.Failure("لم يتم العثور على قطع محددة.")
        updateScene(cur.copy(plots = cur.plots.map { if (it.id in plotIds) it.copy(districtId = districtId) else it }))
        return AdminOperationResult.Success("تم ربط $count قطعة بالحي بنجاح.", affectedDistrictId = districtId)
    }

    fun batchDeletePlots(plotIds: List<String>): AdminOperationResult {
        var cur = getScene()
        var deleted = 0
        for (id in plotIds) {
            val (res, next) = CityLayoutEditor.removePlot(cur, id)
            if (res is ValidationResult.Valid) {
                cur = next
                deleted++
            }
        }
        if (deleted == 0) return AdminOperationResult.Failure("تعذر حذف القطع المحددة (قد تكون مشغولة).")
        updateScene(cur)
        return AdminOperationResult.Success("تم حذف $deleted قطعة بنجاح.")
    }

    fun paintTiles(changes: List<TilePaintChange>): AdminOperationResult {
        if (changes.isEmpty()) return AdminOperationResult.Failure("لا توجد مربعات محددة للتعديل.")
        var cur = getScene()
        val grassCoords = changes.filter { it.tileType == CityTileType.GRASS }.map { it.coord }.toSet()
        if (grassCoords.isNotEmpty()) {
            val (eraseRes, erasedScene) = CityLayoutEditor.eraseTilesToGrass(cur, grassCoords)
            if (eraseRes is ValidationResult.Invalid) return AdminOperationResult.Failure(eraseRes.reason)
            cur = erasedScene
        }
        for (change in changes.filter { it.tileType != CityTileType.GRASS }) {
            val v = validator(cur)
            if (change.tileType == CityTileType.ROAD || change.tileType == CityTileType.SIDEWALK || change.tileType == CityTileType.PARK_GREEN) {
                val res = v.validateCanPaintRoadOrSidewalk(listOf(change.coord), cur.plots, cur.houses, cur.publicBuildings)
                if (res is ValidationResult.Invalid) return AdminOperationResult.Failure(res.reason)
            }
            cur = CityLayoutEditor.paintTiles(cur, listOf(change.coord), change.tileType, change.texture)
        }
        updateScene(cur)
        return AdminOperationResult.Success("تم تحديث ${changes.size} مربع على الخريطة.")
    }

    fun upsertRoadSegment(draft: RoadSegmentDraft): AdminOperationResult {
        val cur = getScene()
        val id = draft.id?.takeIf { it.isNotBlank() } ?: nextId("road")
        val segment = RoadSegment(
            id = id,
            coord = draft.coord,
            widthCells = draft.widthCells.coerceAtLeast(1),
            heightCells = draft.heightCells.coerceAtLeast(1),
            lanes = draft.lanes.coerceIn(1, 4),
            hasSidewalkBorder = draft.hasSidewalkBorder,
            hasCrosswalk = draft.hasCrosswalk,
            hasParkingBay = draft.hasParkingBay,
            streetNameAr = draft.streetNameAr?.trim()?.takeIf { it.isNotEmpty() },
            surfaceType = draft.surfaceType
        )
        val coordsToPaint = buildList {
            for (dy in 0 until segment.heightCells) {
                for (dx in 0 until segment.widthCells) {
                    add(GridCoord(segment.coord.x + dx, segment.coord.y + dy))
                }
            }
        }
        val check = validator(cur).validateCanPaintRoadOrSidewalk(coordsToPaint, cur.plots, cur.houses, cur.publicBuildings)
        if (check is ValidationResult.Invalid) return AdminOperationResult.Failure(check.reason)
        val next = CityLayoutEditor.addRoadSegment(cur, segment)
        updateScene(next)
        return AdminOperationResult.Success("تم حفظ مسار الشارع بنجاح.", affectedRoadId = id)
    }

    fun deleteRoadSegment(roadId: String, eraseTilesToGrass: Boolean): AdminOperationResult {
        val cur = getScene()
        val seg = cur.roadSegments.firstOrNull { it.id == roadId }
            ?: return AdminOperationResult.Failure("مسار الشارع غير موجود.")
        var updated = CityLayoutEditor.removeRoadSegment(cur, roadId)
        if (eraseTilesToGrass) {
            val coords = buildSet {
                for (dy in 0 until seg.heightCells.coerceAtLeast(1)) {
                    for (dx in 0 until seg.widthCells.coerceAtLeast(1)) {
                        add(GridCoord(seg.coord.x + dx, seg.coord.y + dy))
                    }
                }
            }
            val (res, erased) = CityLayoutEditor.eraseTilesToGrass(updated, coords)
            if (res is ValidationResult.Invalid) return AdminOperationResult.Failure(res.reason)
            updated = erased
        }
        updateScene(updated)
        return AdminOperationResult.Success("تم حذف الشارع.", affectedRoadId = roadId)
    }

    fun upsertDistrict(draft: DistrictDraft): AdminOperationResult {
        if (draft.nameAr.isBlank() || draft.code.isBlank()) {
            return AdminOperationResult.Failure("يرجى إدخال اسم الحي ورمزه.")
        }
        val cur = getScene()
        val id = draft.id?.takeIf { it.isNotBlank() } ?: nextId("dist")
        val district = DistrictZone(
            id = id,
            nameAr = draft.nameAr.trim(),
            code = draft.code.trim().uppercase(),
            themeColorHex = draft.themeColorHex,
            origin = draft.origin,
            widthCells = draft.widthCells.coerceAtLeast(1),
            heightCells = draft.heightCells.coerceAtLeast(1),
            ambientStyle = draft.ambientStyle
        )
        if (!validator(cur).isFootprintWithinBounds(GridFootprint(district.origin, district.widthCells, district.heightCells))) {
            return AdminOperationResult.Failure("حدود الحي تتجاوز حدود الخريطة.")
        }
        updateScene(CityLayoutEditor.addDistrict(cur, district))
        return AdminOperationResult.Success("تم حفظ الحي (${district.nameAr}).", affectedDistrictId = id)
    }

    fun deleteDistrict(districtId: String): AdminOperationResult {
        updateScene(CityLayoutEditor.removeDistrict(getScene(), districtId))
        return AdminOperationResult.Success("تم حذف الحي وإزالة ربطه بالعناصر.", affectedDistrictId = districtId)
    }

    fun upsertPublicBuilding(draft: PublicBuildingDraft): AdminOperationResult {
        if (draft.nameAr.isBlank()) return AdminOperationResult.Failure("يرجى إدخال اسم المبنى العام.")
        val id = draft.id?.takeIf { it.isNotBlank() } ?: nextId("bldg")
        val building = PublicBuilding(
            id = id,
            nameAr = draft.nameAr.trim(),
            type = draft.type,
            coord = draft.coord,
            widthCells = draft.widthCells.coerceAtLeast(1),
            heightCells = draft.heightCells.coerceAtLeast(1),
            rotation = draft.rotation,
            upgradeLevel = draft.upgradeLevel.coerceIn(1, 5),
            primaryColorHex = draft.primaryColorHex,
            accentColorHex = draft.accentColorHex,
            assetKey = draft.assetKey,
            districtId = draft.districtId,
            isLitAtNight = draft.isLitAtNight,
            descriptionAr = draft.descriptionAr?.trim()?.takeIf { it.isNotEmpty() },
            publicInfoAr = draft.publicInfoAr?.trim()?.takeIf { it.isNotEmpty() }
        )
        val (res, updated) = CityLayoutEditor.placePublicBuilding(getScene(), building)
        return when (res) {
            is ValidationResult.Invalid -> AdminOperationResult.Failure(res.reason)
            is ValidationResult.Valid -> {
                updateScene(updated)
                AdminOperationResult.Success("تم حفظ المبنى العام (${building.nameAr}).", affectedBuildingId = id)
            }
        }
    }

    fun deletePublicBuilding(buildingId: String): AdminOperationResult {
        updateScene(CityLayoutEditor.removePublicBuilding(getScene(), buildingId))
        return AdminOperationResult.Success("تم حذف المبنى العام.", affectedBuildingId = buildingId)
    }

    fun upsertEnvironmentProp(draft: EnvironmentPropDraft): AdminOperationResult {
        val id = draft.id?.takeIf { it.isNotBlank() } ?: nextId("prop")
        val prop = EnvironmentProp(
            id = id,
            type = draft.type,
            coord = draft.coord,
            widthCells = draft.widthCells.coerceAtLeast(1),
            heightCells = draft.heightCells.coerceAtLeast(1),
            rotation = draft.rotation,
            scale = draft.scale.coerceIn(0.4f, 3.0f),
            variant = draft.variant,
            labelAr = draft.labelAr?.trim()?.takeIf { it.isNotEmpty() },
            layer = draft.layer,
            primaryColorHex = draft.primaryColorHex,
            assetKey = draft.assetKey
        )
        val (res, updated) = CityLayoutEditor.placeEnvironmentProp(getScene(), prop)
        return when (res) {
            is ValidationResult.Invalid -> AdminOperationResult.Failure(res.reason)
            is ValidationResult.Valid -> {
                updateScene(updated)
                AdminOperationResult.Success("تم حفظ عنصر البيئة.", affectedPropId = id)
            }
        }
    }

    fun deleteEnvironmentProp(propId: String): AdminOperationResult {
        updateScene(CityLayoutEditor.removeEnvironmentProp(getScene(), propId))
        return AdminOperationResult.Success("تم حذف عنصر البيئة.", affectedPropId = propId)
    }

    fun upsertLightSource(draft: LightSourceDraft): AdminOperationResult {
        val cur = getScene()
        if (!validator(cur).isWithinBounds(draft.coord)) {
            return AdminOperationResult.Failure("إحداثي الإنارة خارج حدود الخريطة.")
        }
        val id = draft.id?.takeIf { it.isNotBlank() } ?: nextId("light")
        val light = CityLightSource(
            id = id,
            type = draft.type,
            coord = draft.coord,
            offsetX = draft.offsetX,
            offsetY = draft.offsetY,
            colorHex = draft.colorHex,
            intensity = draft.intensity.coerceIn(0f, 3f),
            radiusWorld = draft.radiusWorld.coerceIn(16f, 600f),
            directionDeg = draft.directionDeg,
            coneAngleDeg = draft.coneAngleDeg.coerceIn(1f, 360f),
            isEnabled = draft.isEnabled,
            linkedEntityId = draft.linkedEntityId
        )
        updateScene(CityLayoutEditor.upsertLightSource(cur, light))
        return AdminOperationResult.Success("تم حفظ مصدر الإضاءة.", affectedLightId = id)
    }

    fun deleteLightSource(lightId: String): AdminOperationResult {
        updateScene(CityLayoutEditor.removeLightSource(getScene(), lightId))
        return AdminOperationResult.Success("تم حذف مصدر الإضاءة.", affectedLightId = lightId)
    }

    fun setLightingState(lightingState: CityLightingState): AdminOperationResult {
        updateScene(getScene().copy(lightingState = lightingState))
        return AdminOperationResult.Success("تم تحديث إضاءة المدينة (${lightingState.phase.name}).")
    }
}
