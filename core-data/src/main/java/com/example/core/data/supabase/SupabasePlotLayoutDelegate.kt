package com.example.core.data.supabase

import com.example.cityengine.model.BuildingRotation
import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.world.CityLayoutEditor
import com.example.cityengine.world.PlotManager
import com.example.cityengine.world.SpatialValidator
import com.example.cityengine.world.ValidationResult
import com.example.core.data.model.AdminCitySnapshot
import com.example.core.data.model.AdminOperationResult
import com.example.core.data.model.PlotCreateDraft
import com.example.core.data.model.RoadSegmentDraft
import com.example.core.data.model.TilePaintChange
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Handles plot geometry/batch mutations, grid expansion, tile painting, and road segments
 * against Supabase so each delegate stays cleanly modular (< 400 lines).
 */
internal class SupabasePlotLayoutDelegate(
    private val preflightGuard: suspend () -> String?,
    private val clientProvider: () -> SupabaseClient?,
    private val snapshotProvider: () -> AdminCitySnapshot?,
    private val layoutDtoProvider: () -> CityLayoutDto?,
    private val plotDtoProvider: (String) -> PlotDto?,
    private val reloadFromServer: suspend (SupabaseClient) -> Unit,
    private val applyLayoutLocally: suspend (CityLayoutDto) -> Unit,
    private val handleFailure: suspend (Throwable, String) -> AdminOperationResult.Failure
) {
    private val json get() = SupabaseClientProvider.json

    private fun newId(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(8)}"

    suspend fun expandCityGrid(newWidth: Int, newHeight: Int): AdminOperationResult {
        preflightGuard()?.let { return AdminOperationResult.Failure(it) }
        val snap = snapshotProvider() ?: return AdminOperationResult.Failure("بيانات المدينة غير محمّلة بعد.")
        if (newWidth < snap.scene.gridWidth || newHeight < snap.scene.gridHeight) {
            return AdminOperationResult.Failure("لا يمكن تقليص أبعاد الخريطة عن الحجم الحالي.")
        }
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return try {
            val params = buildJsonObject {
                put("p_layout_id", layoutDtoProvider()?.id ?: "main_city")
                put("p_grid_width", newWidth)
                put("p_grid_height", newHeight)
            }
            client.postgrest.rpc("admin_expand_city_grid", params)
            reloadFromServer(client)
            AdminOperationResult.Success("تم توسيع خريطة المدينة على الخادم إلى ${newWidth}×${newHeight}.")
        } catch (t: Throwable) {
            handleFailure(t, "فشل توسيع خريطة المدينة على الخادم.")
        }
    }

    suspend fun createPlot(draft: PlotCreateDraft): AdminOperationResult {
        preflightGuard()?.let { return AdminOperationResult.Failure(it) }
        val snap = snapshotProvider() ?: return AdminOperationResult.Failure("بيانات المدينة غير محمّلة بعد.")
        val id = draft.id?.takeIf { it.isNotBlank() } ?: newId("plot")
        val code = draft.plotCode?.takeIf { it.isNotBlank() } ?: id.uppercase()
        val candidate = Plot(
            id = id, coord = draft.coord, status = draft.status,
            widthCells = draft.widthCells.coerceAtLeast(1), heightCells = draft.heightCells.coerceAtLeast(1),
            rotation = draft.rotation, districtId = draft.districtId, groundType = draft.groundType,
            textureVariant = draft.textureVariant, plotCode = code
        )
        val validator = SpatialValidator(snap.scene.gridWidth, snap.scene.gridHeight, snap.scene.tiles)
        val check = validator.validateNewPlotArea(candidate, snap.scene.plots, snap.scene.publicBuildings, snap.scene.houses)
        if (check is ValidationResult.Invalid) return AdminOperationResult.Failure(check.reason)
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return upsertPlotRpc(client, candidate, "تم إنشاء القطعة $code وحفظها على الخادم.")
    }

    suspend fun resizePlot(plotId: String, widthCells: Int, heightCells: Int): AdminOperationResult {
        preflightGuard()?.let { return AdminOperationResult.Failure(it) }
        if (widthCells <= 0 || heightCells <= 0) {
            return AdminOperationResult.Failure("أبعاد القطعة يجب أن تكون 1×1 على الأقل.")
        }
        val snap = snapshotProvider() ?: return AdminOperationResult.Failure("بيانات المدينة غير محمّلة بعد.")
        val existing = snap.plots.firstOrNull { it.id == plotId } ?: return AdminOperationResult.Failure("القطعة $plotId غير موجودة.")
        val candidate = existing.copy(widthCells = widthCells, heightCells = heightCells)
        val withoutTiles = snap.scene.tiles.toMutableMap().apply {
            existing.occupiedCoords().forEach { put(it, CityTileType.GRASS) }
        }
        val check = SpatialValidator(snap.scene.gridWidth, snap.scene.gridHeight, withoutTiles).validateNewPlotArea(
            candidate,
            snap.plots.filterNot { it.id == plotId },
            snap.scene.publicBuildings,
            snap.scene.houses.filterNot { it.id == existing.houseId }
        )
        if (check is ValidationResult.Invalid) return AdminOperationResult.Failure(check.reason)
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return upsertPlotRpc(client, candidate, "تم تعديل أبعاد القطعة على الخادم.")
    }

    suspend fun rotatePlot(plotId: String, rotation: BuildingRotation): AdminOperationResult {
        preflightGuard()?.let { return AdminOperationResult.Failure(it) }
        val snap = snapshotProvider() ?: return AdminOperationResult.Failure("بيانات المدينة غير محمّلة بعد.")
        val existing = snap.plots.firstOrNull { it.id == plotId } ?: return AdminOperationResult.Failure("القطعة $plotId غير موجودة.")
        val candidate = existing.copy(rotation = rotation)
        val withoutTiles = snap.scene.tiles.toMutableMap().apply {
            existing.occupiedCoords().forEach { put(it, CityTileType.GRASS) }
        }
        val check = SpatialValidator(snap.scene.gridWidth, snap.scene.gridHeight, withoutTiles).validateNewPlotArea(
            candidate,
            snap.plots.filterNot { it.id == plotId },
            snap.scene.publicBuildings,
            snap.scene.houses.filterNot { it.id == existing.houseId }
        )
        if (check is ValidationResult.Invalid) return AdminOperationResult.Failure(check.reason)
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return upsertPlotRpc(client, candidate, "تم تدوير القطعة إلى ${rotation.degrees}° على الخادم.")
    }

    suspend fun movePlot(plotId: String, newCoord: GridCoord): AdminOperationResult {
        preflightGuard()?.let { return AdminOperationResult.Failure(it) }
        val snap = snapshotProvider() ?: return AdminOperationResult.Failure("بيانات المدينة غير محمّلة بعد.")
        val existing = snap.plots.firstOrNull { it.id == plotId } ?: return AdminOperationResult.Failure("القطعة $plotId غير موجودة.")
        if (existing.houseId != null || existing.status == PlotStatus.OCCUPIED) {
            return AdminOperationResult.Failure("لا يمكن نقل قطعة مشغولة ببيت.")
        }
        val moved = existing.copy(coord = newCoord)
        val withoutTiles = snap.scene.tiles.toMutableMap().apply {
            existing.occupiedCoords().forEach { put(it, CityTileType.GRASS) }
        }
        val check = SpatialValidator(snap.scene.gridWidth, snap.scene.gridHeight, withoutTiles).validateNewPlotArea(
            moved, snap.plots.filterNot { it.id == plotId }, snap.scene.publicBuildings, snap.scene.houses
        )
        if (check is ValidationResult.Invalid) return AdminOperationResult.Failure(check.reason)
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return upsertPlotRpc(client, moved, "تم نقل القطعة إلى (${newCoord.x}, ${newCoord.y}) على الخادم.")
    }

    suspend fun splitPlot(plotId: String, splitHorizontally: Boolean): AdminOperationResult {
        preflightGuard()?.let { return AdminOperationResult.Failure(it) }
        val snap = snapshotProvider() ?: return AdminOperationResult.Failure("بيانات المدينة غير محمّلة بعد.")
        val oldDto = plotDtoProvider(plotId) ?: return AdminOperationResult.Failure("القطعة $plotId غير موجودة.")
        val pm = PlotManager(snap.scene.plots, snap.scene.houses, SpatialValidator(snap.scene.gridWidth, snap.scene.gridHeight, snap.scene.tiles))
        val (res, created) = pm.splitPlot(plotId, splitHorizontally, "${plotId}_a", "${plotId}_b")
        if (res is ValidationResult.Invalid) return AdminOperationResult.Failure(res.reason)
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return try {
            client.from("plots").delete { filter { eq("id", plotId) } }
            try {
                created.forEach { child -> client.postgrest.rpc("admin_upsert_plot", buildPlotRpcParams(child)) }
            } catch (inner: Throwable) {
                runCatching { client.from("plots").upsert(oldDto) }
                throw inner
            }
            reloadFromServer(client)
            AdminOperationResult.Success("تم تقسيم القطعة وحفظها على الخادم.", affectedPlotId = created.firstOrNull()?.id)
        } catch (t: Throwable) {
            handleFailure(t, "فشل تقسيم القطعة على الخادم.")
        }
    }

    suspend fun mergePlots(firstPlotId: String, secondPlotId: String): AdminOperationResult {
        preflightGuard()?.let { return AdminOperationResult.Failure(it) }
        val snap = snapshotProvider() ?: return AdminOperationResult.Failure("بيانات المدينة غير محمّلة بعد.")
        val dtoA = plotDtoProvider(firstPlotId)
        val dtoB = plotDtoProvider(secondPlotId)
        val pm = PlotManager(snap.scene.plots, snap.scene.houses, SpatialValidator(snap.scene.gridWidth, snap.scene.gridHeight, snap.scene.tiles))
        val (res, merged) = pm.mergePlots(firstPlotId, secondPlotId, newId("plot_m"))
        if (res is ValidationResult.Invalid || merged == null) {
            return AdminOperationResult.Failure((res as? ValidationResult.Invalid)?.reason ?: "تعذر دمج القطعتين.")
        }
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return try {
            client.from("plots").delete { filter { eq("id", firstPlotId) } }
            client.from("plots").delete { filter { eq("id", secondPlotId) } }
            try {
                client.postgrest.rpc("admin_upsert_plot", buildPlotRpcParams(merged))
            } catch (inner: Throwable) {
                if (dtoA != null) runCatching { client.from("plots").upsert(dtoA) }
                if (dtoB != null) runCatching { client.from("plots").upsert(dtoB) }
                throw inner
            }
            reloadFromServer(client)
            AdminOperationResult.Success("تم دمج القطعتين وحفظ النتيجة على الخادم.", affectedPlotId = merged.id)
        } catch (t: Throwable) {
            handleFailure(t, "فشل دمج القطعتين على الخادم.")
        }
    }

    suspend fun setPlotAvailability(plotId: String, available: Boolean): AdminOperationResult {
        preflightGuard()?.let { return AdminOperationResult.Failure(it) }
        val snap = snapshotProvider() ?: return AdminOperationResult.Failure("بيانات المدينة غير محمّلة بعد.")
        val plot = snap.plots.firstOrNull { it.id == plotId } ?: return AdminOperationResult.Failure("القطعة $plotId غير موجودة.")
        if (plot.houseId != null || plot.status == PlotStatus.OCCUPIED) {
            return AdminOperationResult.Failure("لا يمكن تعطيل قطعة مشغولة ببيت قائم.")
        }
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return try {
            val statusStr = if (available) "AVAILABLE" else "UNAVAILABLE"
            client.from("plots").update(buildJsonObject { put("status", statusStr) }) { filter { eq("id", plotId) } }
            reloadFromServer(client)
            AdminOperationResult.Success(if (available) "تم إتاحة القطعة على الخادم." else "تم تعطيل القطعة على الخادم.", affectedPlotId = plotId)
        } catch (t: Throwable) {
            handleFailure(t, "فشل تحديث حالة القطعة.")
        }
    }

    suspend fun assignPlotDistrict(plotId: String, districtId: String?): AdminOperationResult {
        preflightGuard()?.let { return AdminOperationResult.Failure(it) }
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return try {
            client.from("plots").update(buildJsonObject { put("district_id", districtId) }) { filter { eq("id", plotId) } }
            reloadFromServer(client)
            AdminOperationResult.Success("تم ربط القطعة بالحي على الخادم.", affectedPlotId = plotId, affectedDistrictId = districtId)
        } catch (t: Throwable) {
            handleFailure(t, "فشل ربط القطعة بالحي.")
        }
    }

    suspend fun deletePlot(plotId: String): AdminOperationResult {
        preflightGuard()?.let { return AdminOperationResult.Failure(it) }
        val snap = snapshotProvider() ?: return AdminOperationResult.Failure("بيانات المدينة غير محمّلة بعد.")
        val check = CityLayoutEditor.removePlot(snap.scene, plotId).first
        if (check is ValidationResult.Invalid) return AdminOperationResult.Failure(check.reason)
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return try {
            client.from("plots").delete { filter { eq("id", plotId) } }
            reloadFromServer(client)
            AdminOperationResult.Success("تم حذف القطعة من الخادم.", affectedPlotId = plotId)
        } catch (t: Throwable) {
            handleFailure(t, "فشل حذف القطعة من الخادم.")
        }
    }

    suspend fun batchUpdatePlotAvailability(plotIds: List<String>, available: Boolean): AdminOperationResult {
        preflightGuard()?.let { return AdminOperationResult.Failure(it) }
        val snap = snapshotProvider() ?: return AdminOperationResult.Failure("بيانات المدينة غير محمّلة بعد.")
        val targets = snap.plots.filter { it.id in plotIds && it.houseId == null && it.status != PlotStatus.OCCUPIED }
        if (targets.isEmpty()) return AdminOperationResult.Failure("لا توجد قطع غير مشغولة ضمن التحديد.")
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return try {
            val statusStr = if (available) "AVAILABLE" else "UNAVAILABLE"
            client.from("plots").update(buildJsonObject { put("status", statusStr) }) { filter { isIn("id", targets.map { it.id }) } }
            reloadFromServer(client)
            AdminOperationResult.Success("تم تحديث حالة ${targets.size} قطعة على الخادم.")
        } catch (t: Throwable) {
            handleFailure(t, "فشل تحديث القطع المحددة.")
        }
    }

    suspend fun batchAssignPlotsDistrict(plotIds: List<String>, districtId: String?): AdminOperationResult {
        preflightGuard()?.let { return AdminOperationResult.Failure(it) }
        if (plotIds.isEmpty()) return AdminOperationResult.Failure("لم يتم تحديد أي قطع.")
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return try {
            client.from("plots").update(buildJsonObject { put("district_id", districtId) }) { filter { isIn("id", plotIds) } }
            reloadFromServer(client)
            AdminOperationResult.Success("تم ربط ${plotIds.size} قطعة بالحي على الخادم.", affectedDistrictId = districtId)
        } catch (t: Throwable) {
            handleFailure(t, "فشل ربط القطع المحددة بالحي.")
        }
    }

    suspend fun batchDeletePlots(plotIds: List<String>): AdminOperationResult {
        preflightGuard()?.let { return AdminOperationResult.Failure(it) }
        val snap = snapshotProvider() ?: return AdminOperationResult.Failure("بيانات المدينة غير محمّلة بعد.")
        val deletable = snap.plots.filter { it.id in plotIds && it.houseId == null && it.status != PlotStatus.OCCUPIED }
        if (deletable.isEmpty()) return AdminOperationResult.Failure("لا توجد قطع فارغة قابلة للحذف ضمن التحديد.")
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return try {
            client.from("plots").delete { filter { isIn("id", deletable.map { it.id }) } }
            reloadFromServer(client)
            AdminOperationResult.Success("تم حذف ${deletable.size} قطعة من الخادم.")
        } catch (t: Throwable) {
            handleFailure(t, "فشل حذف القطع المحددة.")
        }
    }

    suspend fun paintTiles(changes: List<TilePaintChange>): AdminOperationResult {
        preflightGuard()?.let { return AdminOperationResult.Failure(it) }
        if (changes.isEmpty()) return AdminOperationResult.Failure("لا توجد مربعات محددة.")
        val snap = snapshotProvider() ?: return AdminOperationResult.Failure("بيانات المدينة غير محمّلة بعد.")
        val layout = layoutDtoProvider() ?: return AdminOperationResult.Failure("تخطيط المدينة غير محمّل.")
        val validator = SpatialValidator(snap.scene.gridWidth, snap.scene.gridHeight, snap.scene.tiles)
        for (c in changes) {
            if (!validator.isWithinBounds(c.coord)) return AdminOperationResult.Failure("الإحداثي ${c.coord} خارج حدود الخريطة.")
            if (c.tileType == CityTileType.GRASS) {
                val eraseCheck = CityLayoutEditor.eraseTilesToGrass(snap.scene, setOf(c.coord)).first
                if (eraseCheck is ValidationResult.Invalid) return AdminOperationResult.Failure(eraseCheck.reason)
            } else {
                val paintCheck = validator.validateCanPaintRoadOrSidewalk(listOf(c.coord), snap.plots, snap.scene.houses, snap.scene.publicBuildings)
                if (paintCheck is ValidationResult.Invalid) return AdminOperationResult.Failure(paintCheck.reason)
            }
        }
        val changedSet = changes.map { it.coord }.toHashSet()
        val roads = layout.roads.filterNot { GridCoord(it.x, it.y) in changedSet }.toMutableList()
        val sidewalks = layout.sidewalks.filterNot { GridCoord(it.x, it.y) in changedSet }.toMutableList()
        val parks = layout.parks.filterNot { GridCoord(it.x, it.y) in changedSet }.toMutableList()
        val customTiles = layout.customTiles.filterNot { GridCoord(it.x, it.y) in changedSet }.toMutableList()
        changes.forEach { ch ->
            val dtoCoord = GridCoordDto(ch.coord.x, ch.coord.y)
            when (ch.tileType) {
                CityTileType.ROAD -> roads += dtoCoord
                CityTileType.SIDEWALK -> sidewalks += dtoCoord
                CityTileType.PARK_GREEN -> parks += dtoCoord
                else -> Unit
            }
            if (ch.tileType == CityTileType.PLAZA || ch.tileType == CityTileType.DECORATIVE_PAVING ||
                ch.tileType == CityTileType.STONE || ch.tileType == CityTileType.FARMLAND || ch.texture.name != "DEFAULT"
            ) {
                customTiles += CustomTileDto(ch.coord.x, ch.coord.y, ch.tileType.name, ch.texture.name)
            }
        }
        return updateLayoutWithOptimisticVersion(
            layout, roads, sidewalks, parks, customTiles, layout.roadSegments,
            snap.builderCapabilities.customLayoutColumnsAvailable,
            "تم حفظ تعديل ${changes.size} مربع على الخادم."
        )
    }

    suspend fun upsertRoadSegment(draft: RoadSegmentDraft): AdminOperationResult {
        preflightGuard()?.let { return AdminOperationResult.Failure(it) }
        val snap = snapshotProvider() ?: return AdminOperationResult.Failure("بيانات المدينة غير محمّلة بعد.")
        val layout = layoutDtoProvider() ?: return AdminOperationResult.Failure("تخطيط المدينة غير محمّل.")
        val validator = SpatialValidator(snap.scene.gridWidth, snap.scene.gridHeight, snap.scene.tiles)
        val segCoords = buildList {
            for (dy in 0 until draft.heightCells.coerceAtLeast(1)) {
                for (dx in 0 until draft.widthCells.coerceAtLeast(1)) {
                    add(GridCoord(draft.coord.x + dx, draft.coord.y + dy))
                }
            }
        }
        val check = validator.validateCanPaintRoadOrSidewalk(segCoords, snap.plots, snap.scene.houses, snap.scene.publicBuildings)
        if (check is ValidationResult.Invalid) return AdminOperationResult.Failure(check.reason)
        val id = draft.id?.takeIf { it.isNotBlank() } ?: newId("road")
        val segDto = RoadSegmentDto(
            id = id, x = draft.coord.x, y = draft.coord.y,
            widthCells = draft.widthCells.coerceAtLeast(1), heightCells = draft.heightCells.coerceAtLeast(1),
            lanes = draft.lanes.coerceIn(1, 4), hasSidewalkBorder = draft.hasSidewalkBorder,
            hasCrosswalk = draft.hasCrosswalk, hasParkingBay = draft.hasParkingBay,
            streetNameAr = draft.streetNameAr?.trim()?.takeIf { it.isNotEmpty() }, surfaceType = draft.surfaceType.name
        )
        val roadSet = layout.roads.map { GridCoord(it.x, it.y) }.toMutableSet().apply { addAll(segCoords) }
        return updateLayoutWithOptimisticVersion(
            layout, roadSet.map { GridCoordDto(it.x, it.y) }, layout.sidewalks, layout.parks,
            layout.customTiles, layout.roadSegments.filterNot { it.id == id } + segDto,
            snap.builderCapabilities.customLayoutColumnsAvailable, "تم حفظ الشارع على الخادم.", affectedRoadId = id
        )
    }

    suspend fun deleteRoadSegment(roadId: String, eraseTilesToGrass: Boolean): AdminOperationResult {
        preflightGuard()?.let { return AdminOperationResult.Failure(it) }
        val snap = snapshotProvider() ?: return AdminOperationResult.Failure("بيانات المدينة غير محمّلة بعد.")
        val layout = layoutDtoProvider() ?: return AdminOperationResult.Failure("تخطيط المدينة غير محمّل.")
        val target = layout.roadSegments.firstOrNull { it.id == roadId } ?: return AdminOperationResult.Failure("مسار الشارع غير موجود.")
        val removeCoords = if (eraseTilesToGrass) {
            buildSet {
                for (dy in 0 until target.heightCells.coerceAtLeast(1)) {
                    for (dx in 0 until target.widthCells.coerceAtLeast(1)) {
                        add(GridCoord(target.x + dx, target.y + dy))
                    }
                }
            }
        } else emptySet()
        return updateLayoutWithOptimisticVersion(
            layout, layout.roads.filterNot { GridCoord(it.x, it.y) in removeCoords }, layout.sidewalks, layout.parks,
            layout.customTiles, layout.roadSegments.filterNot { it.id == roadId },
            snap.builderCapabilities.customLayoutColumnsAvailable, "تم حذف الشارع من الخادم.", affectedRoadId = roadId
        )
    }

    private suspend fun upsertPlotRpc(client: SupabaseClient, plot: Plot, successMessage: String): AdminOperationResult {
        return try {
            client.postgrest.rpc("admin_upsert_plot", buildPlotRpcParams(plot))
            reloadFromServer(client)
            AdminOperationResult.Success(successMessage, affectedPlotId = plot.id)
        } catch (t: Throwable) {
            handleFailure(t, "فشل حفظ قطعة الأرض على الخادم.")
        }
    }

    private fun buildPlotRpcParams(plot: Plot): JsonObject = buildJsonObject {
        put("p_plot_id", plot.id)
        put("p_layout_id", layoutDtoProvider()?.id ?: "main_city")
        put("p_plot_code", plot.plotCode.ifBlank { plot.id.uppercase() })
        put("p_grid_x", plot.coord.x); put("p_grid_y", plot.coord.y)
        put("p_width_cells", plot.widthCells.coerceAtLeast(1)); put("p_height_cells", plot.heightCells.coerceAtLeast(1))
        put("p_rotation_deg", plot.rotation.degrees); put("p_status", plot.status.name)
        put("p_district_id", plot.districtId); put("p_ground_type", plot.groundType.name)
        put("p_texture_variant", plot.textureVariant.name)
    }

    private suspend fun updateLayoutWithOptimisticVersion(
        layout: CityLayoutDto, roads: List<GridCoordDto>, sidewalks: List<GridCoordDto>,
        parks: List<GridCoordDto>, customTiles: List<CustomTileDto>, roadSegments: List<RoadSegmentDto>,
        caps: Boolean, successMessage: String, affectedRoadId: String? = null
    ): AdminOperationResult {
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return try {
            val payload = buildJsonObject {
                put("version", layout.version + 1)
                put("roads", json.encodeToJsonElement(roads))
                put("sidewalks", json.encodeToJsonElement(sidewalks))
                put("parks", json.encodeToJsonElement(parks))
                if (caps) {
                    put("custom_tiles", json.encodeToJsonElement(customTiles))
                    put("road_segments", json.encodeToJsonElement(roadSegments))
                }
            }
            val updated = client.from("city_layout").update(payload) {
                filter { eq("id", layout.id); eq("version", layout.version) }; select()
            }.decodeList<CityLayoutDto>()
            val newest = updated.firstOrNull()
            if (newest == null) {
                reloadFromServer(client)
                return AdminOperationResult.Failure("تعارض تحديث: تم تعديل تخطيط المدينة من جلسة أخرى. أعد المحاولة الآن.")
            }
            applyLayoutLocally(newest)
            AdminOperationResult.Success(successMessage, affectedRoadId = affectedRoadId)
        } catch (t: Throwable) {
            handleFailure(t, "فشل حفظ تعديلات التخطيط على الخادم.")
        }
    }
}
