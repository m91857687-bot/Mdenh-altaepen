package com.example.cityengine

import com.example.cityengine.assets.CityAssetCache
import com.example.cityengine.assets.CityAssetRegistry
import com.example.cityengine.model.BuildingRotation
import com.example.cityengine.model.CityLightSource
import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.DistrictZone
import com.example.cityengine.model.EnvironmentProp
import com.example.cityengine.model.EnvironmentPropType
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.GridFootprint
import com.example.cityengine.model.HouseDesignStyle
import com.example.cityengine.model.HousePalette
import com.example.cityengine.model.LightSourceType
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.model.PropLayer
import com.example.cityengine.model.PublicBuilding
import com.example.cityengine.model.PublicBuildingType
import com.example.cityengine.model.TileTextureVariant
import com.example.cityengine.model.TimeOfDayPhase
import com.example.cityengine.performance.LodTier
import com.example.cityengine.performance.SpatialGridIndex
import com.example.cityengine.performance.ViewportCuller
import com.example.cityengine.world.CityLayoutEditor
import com.example.cityengine.world.PlotManager
import com.example.cityengine.world.SpatialValidator
import com.example.cityengine.world.StarterCityMap
import com.example.cityengine.world.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CityBuilderEngineTest {

    private val testPalette = HousePalette(0xFFFFFFFF, 0xFFE0E0E0, 0xFFC85A3A, 0xFF9F4025, 0xFF83321C, 0xFF5D3A29)

    @Test
    fun `grid footprint handles 90 degree rotation and overlap detection`() {
        val fpA = GridFootprint(GridCoord(2, 2), widthCells = 3, heightCells = 1, rotation = BuildingRotation.DEG_0)
        assertEquals(3, fpA.effectiveWidth())
        assertEquals(1, fpA.effectiveHeight())
        assertTrue(fpA.contains(GridCoord(4, 2)))
        assertFalse(fpA.contains(GridCoord(2, 3)))

        val fpRotated = fpA.copy(rotation = BuildingRotation.DEG_90)
        assertEquals(1, fpRotated.effectiveWidth())
        assertEquals(3, fpRotated.effectiveHeight())
        assertTrue(fpRotated.contains(GridCoord(2, 4)))
        assertFalse(fpRotated.contains(GridCoord(3, 2)))

        val fpDisjoint = GridFootprint(GridCoord(5, 2), widthCells = 1, heightCells = 2)
        assertFalse(fpA.overlaps(fpDisjoint))
        val fpOverlap = GridFootprint(GridCoord(4, 2), widthCells = 2, heightCells = 2)
        assertTrue(fpA.overlaps(fpOverlap))
    }

    @Test
    fun `plot manager supports multi-cell resize, rotate, unavailable status, split, and merge`() {
        val tiles = buildMap {
            for (y in 0 until 12) {
                for (x in 0 until 12) {
                    put(GridCoord(x, y), if (x == 0 || y == 0) CityTileType.ROAD else CityTileType.PLOT_LOT)
                }
            }
        }
        val validator = SpatialValidator(12, 12, tiles)
        val initialPlot = Plot("p_main", GridCoord(2, 2), PlotStatus.AVAILABLE, widthCells = 2, heightCells = 2)
        val manager = PlotManager(listOf(initialPlot), emptyList(), validator)

        // UNAVAILABLE plot rejects residential construction until re-enabled
        assertEquals(ValidationResult.Valid, manager.setPlotAvailability("p_main", available = false))
        assertEquals(PlotStatus.UNAVAILABLE, manager.getPlotById("p_main")?.status)
        val blockedHouse = StarterCityMap.createScene().houses.first().copy(id = "h_blocked", plotId = "p_main", coord = GridCoord(2, 2))
        assertTrue(manager.assignHouseToPlot(blockedHouse) is ValidationResult.Invalid)
        assertEquals(ValidationResult.Valid, manager.setPlotAvailability("p_main", available = true))

        // Split 2x2 horizontally into two 1x2 plots
        val (splitRes, splitCreated) = manager.splitPlot("p_main", splitHorizontally = true, firstId = "p_left", secondId = "p_right")
        assertEquals(ValidationResult.Valid, splitRes)
        assertEquals(2, splitCreated.size)
        assertEquals(1, splitCreated[0].widthCells)
        assertEquals(2, splitCreated[0].heightCells)
        assertEquals(GridCoord(3, 2), splitCreated[1].coord)

        // Merge back into one 2x2 plot
        val (mergeRes, merged) = manager.mergePlots("p_left", "p_right", "p_merged")
        assertEquals(ValidationResult.Valid, mergeRes)
        assertNotNull(merged)
        assertEquals(2, merged!!.widthCells)
        assertEquals(2, merged.heightCells)

        // Resize to 3x1 and rotate 90 -> 1x3 effective
        assertEquals(ValidationResult.Valid, manager.resizePlot("p_merged", 3, 1))
        assertEquals(ValidationResult.Valid, manager.rotatePlot("p_merged", BuildingRotation.DEG_90))
        assertEquals(1, manager.getPlotById("p_merged")?.effectiveWidth())
        assertEquals(3, manager.getPlotById("p_merged")?.effectiveHeight())
    }

    @Test
    fun `layout editor expands grid and validates public buildings and smart prop layering`() {
        val base = StarterCityMap.createScene()
        val expanded = CityLayoutEditor.expandGrid(base, 24, 24)
        assertEquals(24, expanded.gridWidth)
        assertEquals(24, expanded.gridHeight)
        assertEquals(24 * 24, expanded.tiles.size)

        val withDistrict = CityLayoutEditor.addDistrict(
            expanded,
            DistrictZone("d1", "الحي التجاري", "COM", 0xFF2D6A8E, GridCoord(14, 2), 8, 8)
        )
        assertEquals(1, withDistrict.districts.size)

        val hospital = PublicBuilding(
            id = "b_hospital",
            nameAr = "مستشفى الطيبين",
            type = PublicBuildingType.HOSPITAL,
            coord = GridCoord(15, 4),
            widthCells = 2,
            heightCells = 2
        )
        val (placeRes, withHospital) = CityLayoutEditor.placePublicBuilding(withDistrict, hospital)
        assertEquals(ValidationResult.Valid, placeRes)
        assertEquals(1, withHospital.publicBuildings.size)

        // Placing another building overlapping the hospital must fail
        val overlapBank = PublicBuilding(
            id = "b_bank",
            nameAr = "بنك",
            type = PublicBuildingType.CENTRAL_BANK,
            coord = GridCoord(16, 5)
        )
        val (overlapRes, _) = CityLayoutEditor.placePublicBuilding(withHospital, overlapBank)
        assertTrue(overlapRes is ValidationResult.Invalid)

        // Structural prop on top of hospital must fail, while light fixture overlay is permitted
        val badTree = EnvironmentProp("t_bad", EnvironmentPropType.PALM_TREE, GridCoord(15, 4), layer = PropLayer.STRUCTURE)
        assertTrue(CityLayoutEditor.placeEnvironmentProp(withHospital, badTree).first is ValidationResult.Invalid)

        val lightProp = EnvironmentProp("l_ok", EnvironmentPropType.LAMP_POST, GridCoord(15, 4), layer = PropLayer.LIGHT_FIXTURE)
        assertEquals(ValidationResult.Valid, CityLayoutEditor.placeEnvironmentProp(withHospital, lightProp).first)
    }

    @Test
    fun `expanded showcase city populates districts, public buildings, props, lighting, and hit-testing`() {
        val showcase = StarterCityMap.createExpandedShowcaseCity(TimeOfDayPhase.SUNSET)
        assertTrue(showcase.gridWidth > 12)
        assertTrue(showcase.gridHeight > 12)
        assertEquals(TimeOfDayPhase.SUNSET, showcase.lightingState.phase)
        assertTrue(showcase.districts.size >= 3)
        assertTrue(showcase.publicBuildings.any { it.type == PublicBuildingType.PRESIDENTIAL_PALACE })
        assertTrue(showcase.publicBuildings.any { it.type == PublicBuildingType.HOSPITAL })
        assertTrue(showcase.publicBuildings.any { it.type == PublicBuildingType.MINE_INDUSTRIAL })
        assertTrue(showcase.plots.any { it.status == PlotStatus.UNAVAILABLE })
        assertTrue(showcase.environmentProps.isNotEmpty())
        assertTrue(showcase.lights.isNotEmpty())

        val engine = CityEngine(initialGridWidth = showcase.gridWidth, initialGridHeight = showcase.gridHeight)
        engine.syncSceneBounds(showcase)

        val palace = showcase.publicBuildings.first { it.type == PublicBuildingType.PRESIDENTIAL_PALACE }
        val fp = palace.footprint()
        val palaceCenter = engine.projection.footprintCenterWorld(palace.coord, fp.effectiveWidth(), fp.effectiveHeight())
        val hitBuilding = engine.findPublicBuildingAtWorldPoint(palaceCenter.x, palaceCenter.y - 20f, showcase)
        assertEquals(palace.id, hitBuilding?.id)
    }

    @Test
    fun `asset registry covers all styles, buildings, and props, and cache stays safe with null context`() {
        HouseDesignStyle.entries.forEach { style ->
            val spec = CityAssetRegistry.get(CityAssetRegistry.keyFor(style))
            assertNotNull("Missing asset spec for $style", spec)
        }
        PublicBuildingType.entries.forEach { type ->
            val spec = CityAssetRegistry.get(CityAssetRegistry.keyFor(type))
            assertNotNull("Missing asset spec for $type", spec)
            assertNotNull("Missing VectorDrawable resId for $type", spec?.drawableResId)
        }
        EnvironmentPropType.entries.forEach { propType ->
            val spec = CityAssetRegistry.get(CityAssetRegistry.keyFor(propType))
            assertNotNull("Missing asset spec for $propType", spec)
        }

        val cache = CityAssetCache(context = null)
        assertNull(cache.getOrLoadByAssetKey("building_hospital"))
        assertEquals(0, cache.cachedSize())
    }

    @Test
    fun `spatial index and viewport culler scale cleanly beyond 2500 elements under 500ms`() {
        val engine = CityEngine(initialGridWidth = 100, initialGridHeight = 100)
        val center50 = engine.projection.gridToWorld(50f, 50f)
        engine.cameraController.updateViewport(1080f, 1920f, center50)
        engine.cameraController.centerOnWorld(center50.x, center50.y, targetZoom = 1.55f)

        val visible = ViewportCuller.computeVisibleGridBounds(
            projection = engine.projection,
            camera = engine.cameraController,
            gridWidth = 100,
            gridHeight = 100
        )
        assertEquals(LodTier.HIGH, visible.lodTier)
        assertTrue(visible.maxX - visible.minX < 40)
        assertTrue(visible.maxY - visible.minY < 40)

        assertEquals(LodTier.LOW, ViewportCuller.resolveLod(0.55f))
        assertEquals(LodTier.MEDIUM, ViewportCuller.resolveLod(0.90f))

        val index = SpatialGridIndex<Int>(bucketSize = 4)
        val startNs = System.nanoTime()
        var count = 0
        for (y in 0 until 50) {
            for (x in 0 until 50) {
                index.insert(GridCoord(x * 2, y * 2), count++)
            }
        }
        assertEquals(2500, count)

        val queried = index.queryRange(visible.minX, visible.maxX, visible.minY, visible.maxY)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
        assertTrue("Expected culled query < 2500 items, got ${queried.size}", queried.size < 600)
        assertTrue("Spatial indexing + query of 2500 elements took ${elapsedMs}ms (>500ms)", elapsedMs < 500L)
    }

    @Test
    fun `phase2 engine supports prop road district hit-testing, plot creation, deletion and road erase rules`() {
        val showcase = StarterCityMap.createExpandedShowcaseCity()
        val engine = CityEngine(initialGridWidth = showcase.gridWidth, initialGridHeight = showcase.gridHeight)
        engine.syncSceneBounds(showcase)

        val fountain = showcase.environmentProps.first { it.type == EnvironmentPropType.FOUNTAIN }
        val fountainWorld = engine.projection.tileCenterWorld(fountain.coord)
        assertEquals(fountain.id, engine.findEnvironmentPropAtWorldPoint(fountainWorld.x, fountainWorld.y, showcase)?.id)

        val roadHit = engine.findRoadSegmentAtCoord(GridCoord(12, 4), showcase)
        assertEquals("road_main_ns", roadHit?.id)
        assertEquals("dist_civic", engine.findDistrictAtCoord(GridCoord(14, 2), showcase)?.id)

        // New plot creation on empty GRASS passes, while overlapping road or building fails
        val validator = SpatialValidator(showcase.gridWidth, showcase.gridHeight, showcase.tiles)
        val validPlot = Plot("plot_new_1", GridCoord(21, 10), PlotStatus.AVAILABLE, widthCells = 2, heightCells = 1)
        assertEquals(
            ValidationResult.Valid,
            validator.validateNewPlotArea(validPlot, showcase.plots, showcase.publicBuildings, showcase.houses)
        )
        val roadOverlapPlot = Plot("plot_bad_road", GridCoord(12, 10), PlotStatus.AVAILABLE)
        assertTrue(
            validator.validateNewPlotArea(roadOverlapPlot, showcase.plots, showcase.publicBuildings, showcase.houses) is ValidationResult.Invalid
        )

        // Cannot delete occupied plot, CAN delete available plot
        val (placedRes, withNewPlot) = CityLayoutEditor.placePlot(showcase, validPlot)
        assertEquals(ValidationResult.Valid, placedRes)
        assertTrue(CityLayoutEditor.removePlot(withNewPlot, "plot_01").first is ValidationResult.Invalid)
        val (removeAvailRes, afterRemovePlot) = CityLayoutEditor.removePlot(withNewPlot, "plot_new_1")
        assertEquals(ValidationResult.Valid, removeAvailRes)
        assertTrue(afterRemovePlot.plots.none { it.id == "plot_new_1" })

        // Cannot erase cells under houses/buildings, CAN erase road cells cleanly
        assertTrue(CityLayoutEditor.eraseTilesToGrass(showcase, setOf(GridCoord(1, 1))).first is ValidationResult.Invalid)
        val (eraseRoadRes, afterEraseRoad) = CityLayoutEditor.eraseTilesToGrass(showcase, setOf(GridCoord(12, 4)))
        assertEquals(ValidationResult.Valid, eraseRoadRes)
        assertEquals(CityTileType.GRASS, afterEraseRoad.tiles[GridCoord(12, 4)])

        // District removal clears references cleanly
        val afterDistrictRemove = CityLayoutEditor.removeDistrict(showcase, "dist_commercial")
        assertTrue(afterDistrictRemove.districts.none { it.id == "dist_commercial" })
        assertTrue(afterDistrictRemove.plots.none { it.districtId == "dist_commercial" })
    }
}
