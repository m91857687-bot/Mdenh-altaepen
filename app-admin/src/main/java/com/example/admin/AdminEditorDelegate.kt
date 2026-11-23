package com.example.admin

import com.example.cityengine.model.BuildingRotation
import com.example.cityengine.model.CityLightingState
import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.EnvironmentProp
import com.example.cityengine.model.EnvironmentPropType
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.model.PublicBuilding
import com.example.cityengine.model.TileTextureVariant
import com.example.core.data.history.CityEditorCommand
import com.example.core.data.history.CityEditorHistoryManager
import com.example.core.data.model.AdminCitySnapshot
import com.example.core.data.model.AdminOperationResult
import com.example.core.data.model.DistrictDraft
import com.example.core.data.model.EnvironmentPropDraft
import com.example.core.data.model.LightSourceDraft
import com.example.core.data.model.PlotCreateDraft
import com.example.core.data.model.PublicBuildingDraft
import com.example.core.data.model.RoadSegmentDraft
import com.example.core.data.model.TilePaintChange
import com.example.core.data.repository.CityAdminRepository
import java.util.UUID

/**
 * Coordinates interactive preview validation, confirmed builder commits,
 * and conflict-safe Undo/Redo history for the Admin City Builder.
 */
class AdminEditorDelegate(
    private val repository: CityAdminRepository,
    val historyManager: CityEditorHistoryManager = CityEditorHistoryManager()
) {
    private fun uid(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(8)}"

    fun buildPreview(
        coord: GridCoord,
        scene: CitySceneState,
        editorState: AdminEditorUiState,
        extraBrushCoords: Set<GridCoord> = emptySet()
    ): PendingPlacementDraft = AdminEditorPreviewBuilder.buildPreview(coord, scene, editorState, extraBrushCoords)

    suspend fun commitPlacement(
        pending: PendingPlacementDraft,
        editorState: AdminEditorUiState,
        snapshot: AdminCitySnapshot
    ): AdminOperationResult {
        if (!pending.overlay.isValid) {
            return AdminOperationResult.Failure(pending.validationError ?: "الموقع غير صالح للتنفيذ.")
        }
        return when (pending.tool) {
            EditorSubTool.PLOT_CREATE -> executeCreatePlot(
                PlotCreateDraft(id = uid("plot"), coord = pending.origin, widthCells = pending.widthCells, heightCells = pending.heightCells, rotation = pending.rotation, districtId = editorState.activeDistrictId),
                snapshot
            )
            EditorSubTool.PLOT_MOVE -> {
                val plot = snapshot.plots.firstOrNull { it.id == pending.targetPlotId } ?: return AdminOperationResult.Failure("القطعة غير موجودة.")
                executeMovePlot(plot, pending.origin, snapshot)
            }
            EditorSubTool.ROAD_SEGMENT_PLACE -> executeUpsertRoadSegment(
                RoadSegmentDraft(id = uid("road"), coord = pending.origin, widthCells = pending.widthCells, heightCells = pending.heightCells),
                snapshot
            )
            EditorSubTool.DISTRICT_PLACE -> {
                val count = snapshot.scene.districts.size + 1
                executeUpsertDistrict(
                    DistrictDraft(id = uid("dist"), nameAr = "حي $count", code = "D$count", origin = pending.origin, widthCells = pending.widthCells, heightCells = pending.heightCells),
                    snapshot
                )
            }
            EditorSubTool.PUBLIC_BUILDING_PLACE -> {
                val bType = editorState.selectedBuildingType
                executeUpsertPublicBuilding(
                    PublicBuildingDraft(id = uid("bld"), districtId = editorState.activeDistrictId, nameAr = bType.labelAr, type = bType, coord = pending.origin, widthCells = pending.widthCells, heightCells = pending.heightCells, rotation = pending.rotation, assetKey = "building_${bType.name.lowercase()}"),
                    snapshot
                )
            }
            EditorSubTool.PUBLIC_BUILDING_MOVE -> {
                val existing = snapshot.scene.publicBuildings.firstOrNull { it.id == pending.targetBuildingId } ?: return AdminOperationResult.Failure("المبنى غير موجود.")
                executeUpsertPublicBuilding(
                    PublicBuildingDraft(
                        id = existing.id,
                        districtId = existing.districtId,
                        nameAr = existing.nameAr,
                        type = existing.type,
                        coord = pending.origin,
                        widthCells = existing.widthCells,
                        heightCells = existing.heightCells,
                        rotation = existing.rotation,
                        upgradeLevel = existing.upgradeLevel,
                        primaryColorHex = existing.primaryColorHex,
                        accentColorHex = existing.accentColorHex,
                        assetKey = existing.assetKey,
                        isLitAtNight = existing.isLitAtNight,
                        descriptionAr = existing.descriptionAr,
                        publicInfoAr = existing.publicInfoAr
                    ),
                    snapshot
                )
            }
            EditorSubTool.PROP_PLACE -> {
                val pType = editorState.selectedPropType
                if (pType == EnvironmentPropType.CITY_FLAG) {
                    val countryIdx = com.example.cityengine.model.ArabCountriesRegistry.allCountries.indexOfFirst { it.isoCode == editorState.selectedFlagIso }.coerceAtLeast(0)
                    val country = com.example.cityengine.model.ArabCountriesRegistry.allCountries[countryIdx]
                    executeUpsertProp(
                        EnvironmentPropDraft(
                            id = uid("prop"),
                            type = pType,
                            coord = pending.origin,
                            rotation = pending.rotation,
                            variant = countryIdx,
                            labelAr = country.nameAr,
                            layer = defaultPropLayer(pType),
                            assetKey = com.example.cityengine.model.ArabCountriesRegistry.assetKeyFor(country.isoCode)
                        ),
                        snapshot
                    )
                } else {
                    executeUpsertProp(
                        EnvironmentPropDraft(id = uid("prop"), type = pType, coord = pending.origin, rotation = pending.rotation, layer = defaultPropLayer(pType), assetKey = "prop_${pType.name.lowercase()}"),
                        snapshot
                    )
                }
            }
            EditorSubTool.LIGHT_PLACE -> executeUpsertLight(
                LightSourceDraft(id = uid("light"), type = editorState.selectedLightType, coord = pending.origin),
                snapshot
            )
            else -> {
                val spec = editorState.brushTileSpec() ?: return AdminOperationResult.Failure("أداة غير مدعومة.")
                executePaintTiles(pending.brushCoords.ifEmpty { setOf(pending.origin) }.map { TilePaintChange(it, spec.first, spec.second) }, snapshot)
            }
        }
    }

    suspend fun executeCreatePlot(draft: PlotCreateDraft, snapshot: AdminCitySnapshot): AdminOperationResult {
        val plotId = draft.id ?: uid("plot")
        val resolved = draft.copy(id = plotId, plotCode = draft.plotCode ?: plotId.uppercase())
        val res = repository.createPlot(resolved)
        if (res is AdminOperationResult.Success) {
            historyManager.push(CityEditorCommand(
                id = uid("cmd"), labelAr = "إنشاء قطعة ${resolved.plotCode}", labelEn = "Create Plot ${resolved.plotCode}",
                canUndoPredicate = { cur ->
                    val p = cur.plots.firstOrNull { it.id == plotId }
                    if (p == null || p.status == PlotStatus.OCCUPIED) "تعذر التراجع: القطعة تم تعديلها أو إشغالها في جلسة أخرى." else null
                },
                undoAction = { repository.deletePlot(plotId) },
                redoAction = { repository.createPlot(resolved) }
            ))
        }
        return res
    }

    suspend fun executeMovePlot(plot: Plot, newCoord: GridCoord, snapshot: AdminCitySnapshot): AdminOperationResult {
        val oldCoord = plot.coord
        val res = repository.movePlot(plot.id, newCoord)
        if (res is AdminOperationResult.Success) {
            historyManager.push(CityEditorCommand(
                id = uid("cmd"), labelAr = "نقل قطعة ${plot.plotCode}", labelEn = "Move Plot ${plot.plotCode}",
                canUndoPredicate = { cur ->
                    val curPlot = cur.plots.firstOrNull { it.id == plot.id }
                    if (curPlot == null || curPlot.coord != newCoord || curPlot.status == PlotStatus.OCCUPIED) "تعذر التراجع: تغيّر موقع القطعة في جلسة أخرى." else null
                },
                undoAction = { repository.movePlot(plot.id, oldCoord) },
                redoAction = { repository.movePlot(plot.id, newCoord) }
            ))
        }
        return res
    }

    suspend fun executeResizePlot(plot: Plot, widthCells: Int, heightCells: Int): AdminOperationResult {
        val oldW = plot.widthCells; val oldH = plot.heightCells
        val res = repository.resizePlot(plot.id, widthCells, heightCells)
        if (res is AdminOperationResult.Success) {
            historyManager.push(CityEditorCommand(
                id = uid("cmd"), labelAr = "تغيير أبعاد ${plot.plotCode}", labelEn = "Resize ${plot.plotCode}",
                canUndoPredicate = { cur ->
                    val p = cur.plots.firstOrNull { it.id == plot.id }
                    if (p == null || p.widthCells != widthCells || p.heightCells != heightCells) "تعذر التراجع: تغيرت أبعاد القطعة من جلسة أخرى." else null
                },
                undoAction = { repository.resizePlot(plot.id, oldW, oldH) },
                redoAction = { repository.resizePlot(plot.id, widthCells, heightCells) }
            ))
        }
        return res
    }

    suspend fun executeRotatePlot(plot: Plot, rotation: BuildingRotation): AdminOperationResult {
        val oldRot = plot.rotation
        val res = repository.rotatePlot(plot.id, rotation)
        if (res is AdminOperationResult.Success) {
            historyManager.push(CityEditorCommand(
                id = uid("cmd"), labelAr = "تدوير ${plot.plotCode}", labelEn = "Rotate ${plot.plotCode}",
                canUndoPredicate = { cur ->
                    val p = cur.plots.firstOrNull { it.id == plot.id }
                    if (p == null || p.rotation != rotation) "تعذر التراجع: تغيّر تدوير القطعة من جلسة أخرى." else null
                },
                undoAction = { repository.rotatePlot(plot.id, oldRot) },
                redoAction = { repository.rotatePlot(plot.id, rotation) }
            ))
        }
        return res
    }

    suspend fun executeTogglePlotAvailability(plot: Plot, available: Boolean): AdminOperationResult {
        val res = repository.setPlotAvailability(plot.id, available)
        if (res is AdminOperationResult.Success) {
            historyManager.push(CityEditorCommand(
                id = uid("cmd"), labelAr = if (available) "إتاحة ${plot.plotCode}" else "تعطيل ${plot.plotCode}",
                labelEn = if (available) "Enable ${plot.plotCode}" else "Disable ${plot.plotCode}",
                canUndoPredicate = { cur ->
                    val p = cur.plots.firstOrNull { it.id == plot.id }
                    if (p == null || p.status == PlotStatus.OCCUPIED) "تعذر التراجع: تغيرت حالة القطعة." else null
                },
                undoAction = { repository.setPlotAvailability(plot.id, !available) },
                redoAction = { repository.setPlotAvailability(plot.id, available) }
            ))
        }
        return res
    }

    suspend fun executeDeletePlot(plot: Plot): AdminOperationResult {
        val draft = PlotCreateDraft(plot.id, plot.plotCode, plot.coord, plot.widthCells, plot.heightCells, plot.rotation, plot.status, plot.districtId, plot.groundType, plot.textureVariant)
        val res = repository.deletePlot(plot.id)
        if (res is AdminOperationResult.Success) {
            historyManager.push(CityEditorCommand(
                id = uid("cmd"), labelAr = "حذف قطعة ${plot.plotCode}", labelEn = "Delete Plot ${plot.plotCode}",
                canUndoPredicate = { cur -> if (cur.plots.any { it.id == plot.id }) "تعذر التراجع: القطعة أعيد إنشاؤها." else null },
                undoAction = { repository.createPlot(draft) },
                redoAction = { repository.deletePlot(plot.id) }
            ))
        }
        return res
    }

    suspend fun executePaintTiles(changes: List<TilePaintChange>, snapshot: AdminCitySnapshot): AdminOperationResult {
        val priorChanges = changes.map { ch ->
            TilePaintChange(ch.coord, snapshot.scene.tiles[ch.coord] ?: CityTileType.GRASS, snapshot.scene.tileTextures[ch.coord] ?: TileTextureVariant.DEFAULT)
        }
        val expectedVerAfterCommit = snapshot.layoutVersion + if (snapshot.isLocalTestData) 0 else 1
        val res = repository.paintTiles(changes)
        if (res is AdminOperationResult.Success) {
            historyManager.push(CityEditorCommand(
                id = uid("cmd"), labelAr = "تعديل أرضيات (${changes.size})", labelEn = "Paint ${changes.size} tiles",
                canUndoPredicate = { cur ->
                    if (!cur.isLocalTestData && cur.layoutVersion != expectedVerAfterCommit) "تعذر التراجع: تم تعديل تخطيط المدينة من جلسة أخرى." else null
                },
                undoAction = { repository.paintTiles(priorChanges) },
                redoAction = { repository.paintTiles(changes) }
            ))
        }
        return res
    }

    suspend fun executeUpsertRoadSegment(draft: RoadSegmentDraft, snapshot: AdminCitySnapshot): AdminOperationResult {
        val roadId = draft.id ?: uid("road")
        val resolved = draft.copy(id = roadId)
        val prior = snapshot.scene.roadSegments.firstOrNull { it.id == roadId }
        val expectedVer = snapshot.layoutVersion + if (snapshot.isLocalTestData) 0 else 1
        val res = repository.upsertRoadSegment(resolved)
        if (res is AdminOperationResult.Success) {
            historyManager.push(CityEditorCommand(
                id = uid("cmd"), labelAr = resolved.streetNameAr ?: "حفظ شارع", labelEn = "Save Road",
                canUndoPredicate = { cur -> if (!cur.isLocalTestData && cur.layoutVersion != expectedVer) "تعذر التراجع: تغير إصدار التخطيط." else null },
                undoAction = {
                    if (prior != null) repository.upsertRoadSegment(RoadSegmentDraft(prior.id, prior.coord, prior.widthCells, prior.heightCells, prior.lanes, prior.hasSidewalkBorder, prior.hasCrosswalk, prior.hasParkingBay, prior.streetNameAr, prior.surfaceType))
                    else repository.deleteRoadSegment(roadId, eraseTilesToGrass = true)
                },
                redoAction = { repository.upsertRoadSegment(resolved) }
            ))
        }
        return res
    }

    suspend fun executeUpsertDistrict(draft: DistrictDraft, snapshot: AdminCitySnapshot): AdminOperationResult {
        val distId = draft.id ?: uid("dist")
        val resolved = draft.copy(id = distId)
        val prior = snapshot.scene.districts.firstOrNull { it.id == distId }
        val res = repository.upsertDistrict(resolved)
        if (res is AdminOperationResult.Success) {
            historyManager.push(CityEditorCommand(
                id = uid("cmd"), labelAr = "حي ${resolved.nameAr}", labelEn = "District ${resolved.code}",
                undoAction = {
                    if (prior != null) repository.upsertDistrict(DistrictDraft(prior.id, prior.nameAr, prior.code, prior.themeColorHex, prior.origin, prior.widthCells, prior.heightCells, prior.ambientStyle))
                    else repository.deleteDistrict(distId)
                },
                redoAction = { repository.upsertDistrict(resolved) }
            ))
        }
        return res
    }

    suspend fun executeUpsertPublicBuilding(draft: PublicBuildingDraft, snapshot: AdminCitySnapshot): AdminOperationResult {
        val bId = draft.id ?: uid("bld")
        val resolved = draft.copy(id = bId)
        val prior = snapshot.scene.publicBuildings.firstOrNull { it.id == bId }
        val res = repository.upsertPublicBuilding(resolved)
        if (res is AdminOperationResult.Success) {
            historyManager.push(CityEditorCommand(
                id = uid("cmd"), labelAr = "مبنى ${resolved.nameAr}", labelEn = "Building ${resolved.type.name}",
                undoAction = {
                    if (prior != null) repository.upsertPublicBuilding(PublicBuildingDraft(prior.id, prior.districtId, prior.nameAr, prior.type, prior.coord, prior.widthCells, prior.heightCells, prior.rotation, prior.upgradeLevel, prior.primaryColorHex, prior.accentColorHex, prior.assetKey, prior.isLitAtNight, prior.descriptionAr, prior.publicInfoAr))
                    else repository.deletePublicBuilding(bId)
                },
                redoAction = { repository.upsertPublicBuilding(resolved) }
            ))
        }
        return res
    }

    suspend fun executeDeletePublicBuilding(building: PublicBuilding): AdminOperationResult {
        val draft = PublicBuildingDraft(building.id, building.districtId, building.nameAr, building.type, building.coord, building.widthCells, building.heightCells, building.rotation, building.upgradeLevel, building.primaryColorHex, building.accentColorHex, building.assetKey, building.isLitAtNight, building.descriptionAr, building.publicInfoAr)
        val res = repository.deletePublicBuilding(building.id)
        if (res is AdminOperationResult.Success) {
            historyManager.push(CityEditorCommand(
                id = uid("cmd"), labelAr = "حذف ${building.nameAr}", labelEn = "Delete ${building.type.name}",
                undoAction = { repository.upsertPublicBuilding(draft) },
                redoAction = { repository.deletePublicBuilding(building.id) }
            ))
        }
        return res
    }

    suspend fun executeUpsertProp(draft: EnvironmentPropDraft, snapshot: AdminCitySnapshot): AdminOperationResult {
        val propId = draft.id ?: uid("prop")
        val resolved = draft.copy(id = propId)
        val prior = snapshot.scene.environmentProps.firstOrNull { it.id == propId }
        val res = repository.upsertEnvironmentProp(resolved)
        if (res is AdminOperationResult.Success) {
            historyManager.push(CityEditorCommand(
                id = uid("cmd"), labelAr = resolved.labelAr ?: resolved.type.labelAr, labelEn = resolved.type.name,
                undoAction = {
                    if (prior != null) repository.upsertEnvironmentProp(EnvironmentPropDraft(prior.id, prior.type, prior.coord, prior.widthCells, prior.heightCells, prior.rotation, prior.scale, prior.variant, prior.labelAr, prior.layer, prior.primaryColorHex, prior.assetKey))
                    else repository.deleteEnvironmentProp(propId)
                },
                redoAction = { repository.upsertEnvironmentProp(resolved) }
            ))
        }
        return res
    }

    suspend fun executeDeleteProp(prop: EnvironmentProp): AdminOperationResult {
        val draft = EnvironmentPropDraft(prop.id, prop.type, prop.coord, prop.widthCells, prop.heightCells, prop.rotation, prop.scale, prop.variant, prop.labelAr, prop.layer, prop.primaryColorHex, prop.assetKey)
        val res = repository.deleteEnvironmentProp(prop.id)
        if (res is AdminOperationResult.Success) {
            historyManager.push(CityEditorCommand(
                id = uid("cmd"), labelAr = "حذف ${prop.type.labelAr}", labelEn = "Delete ${prop.type.name}",
                undoAction = { repository.upsertEnvironmentProp(draft) },
                redoAction = { repository.deleteEnvironmentProp(prop.id) }
            ))
        }
        return res
    }

    suspend fun executeUpsertLight(draft: LightSourceDraft, snapshot: AdminCitySnapshot): AdminOperationResult {
        val lightId = draft.id ?: uid("light")
        val resolved = draft.copy(id = lightId)
        val prior = snapshot.scene.lights.firstOrNull { it.id == lightId }
        val res = repository.upsertLightSource(resolved)
        if (res is AdminOperationResult.Success) {
            historyManager.push(CityEditorCommand(
                id = uid("cmd"), labelAr = "مصدر إضاءة", labelEn = "Light Source",
                undoAction = {
                    if (prior != null) repository.upsertLightSource(LightSourceDraft(prior.id, prior.type, prior.coord, prior.offsetX, prior.offsetY, prior.colorHex, prior.intensity, prior.radiusWorld, prior.directionDeg, prior.coneAngleDeg, prior.isEnabled, prior.linkedEntityId))
                    else repository.deleteLightSource(lightId)
                },
                redoAction = { repository.upsertLightSource(resolved) }
            ))
        }
        return res
    }

    suspend fun executeSetLighting(next: CityLightingState, prior: CityLightingState): AdminOperationResult {
        val res = repository.setLightingState(next)
        if (res is AdminOperationResult.Success) {
            historyManager.push(CityEditorCommand(
                id = uid("cmd"), labelAr = "إضاءة ${next.phase.labelAr}", labelEn = "Lighting ${next.phase.name}",
                undoAction = { repository.setLightingState(prior) },
                redoAction = { repository.setLightingState(next) }
            ))
        }
        return res
    }
}
