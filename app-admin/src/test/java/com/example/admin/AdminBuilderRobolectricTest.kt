package com.example.admin

import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.admin.ui.AdminAppScreen
import com.example.admin.ui.theme.AdminTheme
import com.example.cityengine.model.BuildingRotation
import com.example.cityengine.model.CityLightingState
import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.EnvironmentPropType
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.model.PublicBuildingType
import com.example.cityengine.model.TimeOfDayPhase
import com.example.core.data.local.LocalCityAdminRepository
import com.example.core.data.model.DistrictDraft
import com.example.core.data.model.EnvironmentPropDraft
import com.example.core.data.model.LightSourceDraft
import com.example.core.data.model.PublicBuildingDraft
import com.example.core.data.model.RoadSegmentDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdminBuilderRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `map editor previews valid and conflicting placements shows toolbar and supports undo redo`() {
        val viewModel = AdminViewModel(LocalCityAdminRepository())

        composeTestRule.setContent {
            AdminTheme {
                AdminAppScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("nav_tab_map").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("editor_tool_dock").assertIsDisplayed()
        composeTestRule.onNodeWithTag("editor_undo_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("editor_redo_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("editor_lighting_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("editor_expand_grid_button").assertIsDisplayed()

        // Switch to Plots -> Create Plot
        composeTestRule.onNodeWithTag("editor_cat_plots").performClick()
        composeTestRule.waitForIdle()

        // Tapping occupied road/house coordinate (2, 1) must produce an invalid red preview banner
        val existingHouseCoord = viewModel.uiState.value.snapshot!!.scene.houses.first().coord
        viewModel.onGridCoordTapped(existingHouseCoord)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("editor_preview_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("editor_confirm_preview_button").assertIsNotEnabled()

        // Expand grid first so we have plenty of clean GRASS tiles at (14, 14)
        viewModel.expandCityGrid(20, 20)
        composeTestRule.waitForIdle()
        assertEquals(20, viewModel.uiState.value.snapshot!!.scene.gridWidth)

        // Tap open grass tile (14, 14) -> valid emerald preview -> Confirm creates plot
        val initialPlotCount = viewModel.uiState.value.snapshot!!.plots.size
        viewModel.setDraftDimensions(2, 2)
        viewModel.onGridCoordTapped(GridCoord(14, 14))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("editor_confirm_preview_button").assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNodeWithTag("editor_confirm_preview_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(initialPlotCount + 1, viewModel.uiState.value.snapshot!!.plots.size)
        assertTrue(viewModel.uiState.value.historyState.canUndo)

        // Undo removes the newly created plot; Redo brings it back
        composeTestRule.onNodeWithTag("editor_undo_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(initialPlotCount, viewModel.uiState.value.snapshot!!.plots.size)
        assertTrue(viewModel.uiState.value.historyState.canRedo)

        composeTestRule.onNodeWithTag("editor_redo_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(initialPlotCount + 1, viewModel.uiState.value.snapshot!!.plots.size)

        // Props -> CITY_FLAG reveals all 22 Arab countries dock & places flag with assetKey
        composeTestRule.onNodeWithTag("editor_cat_props_lights").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("subtool_prop_city_flag").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dock_flag_MA").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        viewModel.onGridCoordTapped(GridCoord(15, 16))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("editor_confirm_preview_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(viewModel.uiState.value.snapshot!!.scene.environmentProps.any { it.assetKey == "flag_ma" })
    }

    @Test
    fun `plot resize rotate split merge disable delete road district building prop lighting and multi-select work`() {
        val viewModel = AdminViewModel(LocalCityAdminRepository())
        viewModel.expandCityGrid(24, 24)

        // Create 2x2 plot at (16, 16)
        viewModel.selectEditorSubTool(EditorSubTool.PLOT_CREATE)
        viewModel.setDraftDimensions(2, 2)
        viewModel.onGridCoordTapped(GridCoord(16, 16))
        viewModel.confirmPendingPlacement()

        val created = viewModel.uiState.value.snapshot!!.plots.first { it.coord == GridCoord(16, 16) }
        viewModel.resizePlot(created, 2, 1)
        viewModel.rotatePlot(created.copy(widthCells = 2, heightCells = 1), BuildingRotation.DEG_90)
        val rotated = viewModel.uiState.value.snapshot!!.plots.first { it.id == created.id }
        assertEquals(BuildingRotation.DEG_90, rotated.rotation)

        viewModel.togglePlotAvailability(rotated)
        assertEquals(PlotStatus.UNAVAILABLE, viewModel.uiState.value.snapshot!!.plots.first { it.id == created.id }.status)
        viewModel.togglePlotAvailability(viewModel.uiState.value.snapshot!!.plots.first { it.id == created.id })
        assertEquals(PlotStatus.AVAILABLE, viewModel.uiState.value.snapshot!!.plots.first { it.id == created.id }.status)

        // Create 2x1 plot and split & merge
        viewModel.selectEditorSubTool(EditorSubTool.PLOT_CREATE)
        viewModel.setDraftDimensions(2, 1)
        viewModel.onGridCoordTapped(GridCoord(18, 18))
        viewModel.confirmPendingPlacement()
        val bigPlot = viewModel.uiState.value.snapshot!!.plots.first { it.coord == GridCoord(18, 18) }
        viewModel.splitPlot(bigPlot, horizontal = true)
        val splitA = viewModel.uiState.value.snapshot!!.plots.firstOrNull { it.id == "${bigPlot.id}_a" }
        val splitB = viewModel.uiState.value.snapshot!!.plots.firstOrNull { it.id == "${bigPlot.id}_b" }
        assertNotNull(splitA)
        assertNotNull(splitB)
        viewModel.mergePlots(splitA!!.id, splitB!!.id)

        // Multi-select batch availability + district assignment + delete
        val extraPlots = viewModel.uiState.value.snapshot!!.plots.filter { it.status == PlotStatus.AVAILABLE }.take(2)
        viewModel.selectEditorSubTool(EditorSubTool.PLOT_MULTI_SELECT)
        extraPlots.forEach { viewModel.onGridCoordTapped(it.coord) }
        assertEquals(2, viewModel.uiState.value.editor.multiSelectedPlotIds.size)

        viewModel.upsertDistrict(DistrictDraft(id = "dist_test", nameAr = "حي السلام", code = "SAL", origin = GridCoord(14, 14), widthCells = 6, heightCells = 6))
        viewModel.batchAssignPlotsDistrict("dist_test")
        viewModel.batchSetPlotsAvailability(false)
        extraPlots.forEach { p ->
            val updated = viewModel.uiState.value.snapshot!!.plots.first { it.id == p.id }
            assertEquals("dist_test", updated.districtId)
            assertEquals(PlotStatus.UNAVAILABLE, updated.status)
        }
        viewModel.batchDeleteSelectedPlots()
        assertFalse(viewModel.uiState.value.snapshot!!.plots.any { it.id == extraPlots.first().id })

        // Road segment, brush paint & erase
        viewModel.upsertRoadSegment(RoadSegmentDraft(id = "road_main", coord = GridCoord(20, 2), widthCells = 3, heightCells = 1, streetNameAr = "طريق السلام", hasCrosswalk = true))
        assertEquals(CityTileType.ROAD, viewModel.uiState.value.snapshot!!.scene.tiles[GridCoord(20, 2)])
        viewModel.eraseTileToGrass(GridCoord(20, 2))
        assertEquals(CityTileType.GRASS, viewModel.uiState.value.snapshot!!.scene.tiles[GridCoord(20, 2)])

        // Public building, prop, light, lighting
        viewModel.upsertPublicBuilding(PublicBuildingDraft(id = "bld_hosp", districtId = "dist_test", nameAr = "مستشفى المدينة", type = PublicBuildingType.HOSPITAL, coord = GridCoord(20, 10), assetKey = "building_hospital"))
        assertTrue(viewModel.uiState.value.snapshot!!.scene.publicBuildings.any { it.id == "bld_hosp" })

        viewModel.upsertEnvironmentProp(EnvironmentPropDraft(id = "prop_fnt", type = EnvironmentPropType.FOUNTAIN, coord = GridCoord(20, 14), assetKey = "prop_fountain"))
        assertTrue(viewModel.uiState.value.snapshot!!.scene.environmentProps.any { it.id == "prop_fnt" })

        viewModel.upsertLightSource(LightSourceDraft(id = "lgt_1", coord = GridCoord(20, 15)))
        assertTrue(viewModel.uiState.value.snapshot!!.scene.lights.any { it.id == "lgt_1" })

        viewModel.setLightingState(CityLightingState(phase = TimeOfDayPhase.NIGHT, nightLampAutoOn = true))
        assertEquals(TimeOfDayPhase.NIGHT, viewModel.uiState.value.snapshot!!.scene.lightingState.phase)
    }

    @Test
    fun `english localization resources resolve cleanly`() {
        val base = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        val enCtx = base.createConfigurationContext(config)
        assertEquals("Madinat Al-Tayibin Admin", enCtx.getString(R.string.app_name))
        assertEquals("Undo", enCtx.getString(R.string.editor_undo_cd))
    }
}
