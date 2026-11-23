package com.example.admin

import com.example.cityengine.model.ArabCountriesRegistry
import com.example.cityengine.model.BuildingRotation
import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.EditorPreviewKind
import com.example.cityengine.model.EditorPreviewOverlay
import com.example.cityengine.model.EnvironmentProp
import com.example.cityengine.model.EnvironmentPropType
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.GridFootprint
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.model.PublicBuilding
import com.example.cityengine.world.CityLayoutEditor
import com.example.cityengine.world.SpatialValidator
import com.example.cityengine.world.ValidationResult

internal object AdminEditorPreviewBuilder {

    fun buildPreview(
        coord: GridCoord,
        scene: CitySceneState,
        editorState: AdminEditorUiState,
        extraBrushCoords: Set<GridCoord> = emptySet()
    ): PendingPlacementDraft {
        val w = editorState.draftWidth.coerceAtLeast(1)
        val h = editorState.draftHeight.coerceAtLeast(1)
        val rot = editorState.draftRotation

        return when (editorState.subTool) {
            EditorSubTool.PLOT_CREATE -> {
                val fp = GridFootprint(coord, w, h, rot)
                val check = SpatialValidator(scene.gridWidth, scene.gridHeight, scene.tiles).validateNewPlotArea(
                    Plot(id = "preview_plot", coord = coord, status = PlotStatus.AVAILABLE, widthCells = w, heightCells = h, rotation = rot),
                    scene.plots, scene.publicBuildings, scene.houses
                )
                draftFromCheck(editorState.subTool, coord, w, h, rot, fp.allCoords(), EditorPreviewKind.FOOTPRINT, check, "قطعة ${w}×${h}")
            }

            EditorSubTool.PLOT_MOVE -> {
                val moving = scene.plots.firstOrNull { it.id == editorState.movingPlotId }
                if (moving == null) {
                    invalidDraft(editorState.subTool, coord, "اختر قطعة فارغة لنقلها أولًا.")
                } else {
                    val moved = moving.copy(coord = coord)
                    val cleared = scene.tiles.toMutableMap().apply { moving.occupiedCoords().forEach { put(it, CityTileType.GRASS) } }
                    val check = SpatialValidator(scene.gridWidth, scene.gridHeight, cleared).validateNewPlotArea(
                        moved, scene.plots.filterNot { it.id == moving.id }, scene.publicBuildings, scene.houses
                    )
                    val fp = GridFootprint(coord, moving.widthCells, moving.heightCells, moving.rotation)
                    draftFromCheck(editorState.subTool, coord, moving.widthCells, moving.heightCells, moving.rotation, fp.allCoords(), EditorPreviewKind.FOOTPRINT, check, "نقل ${moving.plotCode}", targetPlotId = moving.id)
                }
            }

            EditorSubTool.ROAD_SEGMENT_PLACE -> {
                val fp = GridFootprint(coord, w, h, BuildingRotation.DEG_0)
                val check = SpatialValidator(scene.gridWidth, scene.gridHeight, scene.tiles).validateCanPaintRoadOrSidewalk(
                    fp.allCoords(), scene.plots, scene.houses, scene.publicBuildings
                )
                draftFromCheck(editorState.subTool, coord, w, h, BuildingRotation.DEG_0, fp.allCoords(), EditorPreviewKind.ROAD_LINE, check, "شارع ${w}×${h}")
            }

            EditorSubTool.DISTRICT_PLACE -> {
                val dw = w.coerceAtLeast(3)
                val dh = h.coerceAtLeast(3)
                val fp = GridFootprint(coord, dw, dh, BuildingRotation.DEG_0)
                val ok = SpatialValidator(scene.gridWidth, scene.gridHeight, scene.tiles).isFootprintWithinBounds(fp)
                val check = if (ok) ValidationResult.Valid else ValidationResult.Invalid("نطاق الحي يتجاوز حدود المدينة.")
                draftFromCheck(editorState.subTool, coord, dw, dh, BuildingRotation.DEG_0, fp.allCoords(), EditorPreviewKind.DISTRICT, check, "حي ${dw}×${dh}", accentColorHex = 0xFF2E8B7D)
            }

            EditorSubTool.PUBLIC_BUILDING_PLACE -> {
                val bType = editorState.selectedBuildingType
                val bw = if (w == 1 && h == 1) bType.defaultWidth else w
                val bh = if (w == 1 && h == 1) bType.defaultHeight else h
                val candidate = PublicBuilding(id = "preview_bld", nameAr = bType.labelAr, type = bType, coord = coord, widthCells = bw, heightCells = bh, rotation = rot)
                val check = SpatialValidator(scene.gridWidth, scene.gridHeight, scene.tiles).validateCanPlaceBuilding(
                    candidate, scene.publicBuildings, scene.plots, scene.houses
                )
                draftFromCheck(editorState.subTool, coord, bw, bh, rot, candidate.footprint().allCoords(), EditorPreviewKind.FOOTPRINT, check, bType.labelAr)
            }

            EditorSubTool.PUBLIC_BUILDING_MOVE -> {
                val moving = scene.publicBuildings.firstOrNull { it.id == editorState.movingBuildingId }
                if (moving == null) {
                    invalidDraft(editorState.subTool, coord, "اختر مبنى عامًا لنقله أولًا.")
                } else {
                    val moved = moving.copy(coord = coord)
                    val check = SpatialValidator(scene.gridWidth, scene.gridHeight, scene.tiles).validateCanPlaceBuilding(
                        moved, scene.publicBuildings.filterNot { it.id == moving.id }, scene.plots, scene.houses
                    )
                    draftFromCheck(editorState.subTool, coord, moved.widthCells, moved.heightCells, moved.rotation, moved.footprint().allCoords(), EditorPreviewKind.FOOTPRINT, check, moving.nameAr, targetBuildingId = moving.id)
                }
            }

            EditorSubTool.PROP_PLACE -> {
                val pType = editorState.selectedPropType
                val layer = defaultPropLayer(pType)
                val previewLabel = if (pType == EnvironmentPropType.CITY_FLAG) {
                    val country = ArabCountriesRegistry.findByCode(editorState.selectedFlagIso)
                        ?: ArabCountriesRegistry.allCountries.first()
                    "علم ${country.nameAr}"
                } else {
                    pType.labelAr
                }
                val candidate = EnvironmentProp(id = "preview_prop", type = pType, coord = coord, rotation = rot, layer = layer)
                val check = SpatialValidator(scene.gridWidth, scene.gridHeight, scene.tiles).validateCanPlaceProp(
                    candidate, scene.houses, scene.publicBuildings
                )
                draftFromCheck(editorState.subTool, coord, 1, 1, rot, listOf(coord), EditorPreviewKind.FOOTPRINT, check, previewLabel)
            }

            EditorSubTool.LIGHT_PLACE -> {
                val validator = SpatialValidator(scene.gridWidth, scene.gridHeight, scene.tiles)
                val check = if (validator.isWithinBounds(coord)) ValidationResult.Valid else ValidationResult.Invalid("الإحداثي خارج حدود الخريطة.")
                draftFromCheck(editorState.subTool, coord, 1, 1, BuildingRotation.DEG_0, listOf(coord), EditorPreviewKind.FOOTPRINT, check, "مصدر إضاءة")
            }

            else -> {
                val coords = (extraBrushCoords + coord).toList()
                val validator = SpatialValidator(scene.gridWidth, scene.gridHeight, scene.tiles)
                val check = if (editorState.subTool == EditorSubTool.ERASE_TO_GRASS) {
                    CityLayoutEditor.eraseTilesToGrass(scene, coords.toSet()).first
                } else {
                    validator.validateCanPaintRoadOrSidewalk(coords, scene.plots, scene.houses, scene.publicBuildings)
                }
                draftFromCheck(editorState.subTool, coord, 1, 1, BuildingRotation.DEG_0, coords, EditorPreviewKind.TILE_BRUSH, check, null, brushCoords = coords.toSet())
            }
        }
    }

    private fun draftFromCheck(
        tool: EditorSubTool,
        coord: GridCoord,
        w: Int,
        h: Int,
        rot: BuildingRotation,
        coords: List<GridCoord>,
        kind: EditorPreviewKind,
        check: ValidationResult,
        label: String?,
        targetPlotId: String? = null,
        targetBuildingId: String? = null,
        accentColorHex: Long? = null,
        brushCoords: Set<GridCoord> = emptySet()
    ): PendingPlacementDraft {
        val err = (check as? ValidationResult.Invalid)?.reason
        return PendingPlacementDraft(
            tool = tool, origin = coord, widthCells = w, heightCells = h, rotation = rot,
            overlay = EditorPreviewOverlay(coords, coord, w, h, rot, check is ValidationResult.Valid, kind, accentColorHex, label),
            validationError = err, targetPlotId = targetPlotId, targetBuildingId = targetBuildingId, brushCoords = brushCoords
        )
    }

    private fun invalidDraft(tool: EditorSubTool, coord: GridCoord, reason: String) = PendingPlacementDraft(
        tool = tool, origin = coord,
        overlay = EditorPreviewOverlay(listOf(coord), coord, 1, 1, BuildingRotation.DEG_0, false, EditorPreviewKind.FOOTPRINT),
        validationError = reason
    )
}
