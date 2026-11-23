package com.example

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.DistrictZone
import com.example.cityengine.model.EnvironmentProp
import com.example.cityengine.model.EnvironmentPropType
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.model.PublicBuilding
import com.example.cityengine.model.PublicBuildingType
import com.example.cityengine.model.RoadSegment
import com.example.cityengine.world.CityLayoutEditor
import com.example.cityengine.world.StarterCityMap
import com.example.core.data.local.LocalCityExplorerRepository
import com.example.core.data.model.CityDataState
import com.example.core.data.model.CityExplorerSnapshot
import com.example.core.data.model.HouseRequestDraft
import com.example.core.data.model.HouseRequestSubmissionResult
import com.example.core.data.repository.CityExplorerRepository
import com.example.explorer.CityExplorerScreen
import com.example.explorer.CityExplorerViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = "ar")
    fun `read localized app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("مدينة الطيبين", appName)
    }

    @Test
    fun `english localization resources resolve cleanly`() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        val enCtx = base.createConfigurationContext(config)
        assertEquals("Madinat Al-Tayibin", enCtx.getString(R.string.app_name))
        assertEquals("Available Plot", enCtx.getString(R.string.explorer_plot_available))
    }

    @Test
    fun `app opens directly into city explorer without login and shows canvas`() {
        val viewModel = CityExplorerViewModel(LocalCityExplorerRepository())

        composeTestRule.setContent {
            MyApplicationTheme {
                CityExplorerScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("city_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("explorer_header_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recenter_camera_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("explorer_hint_pill").assertIsDisplayed()
    }

    @Test
    fun `tapping house shows correct public info and test badge and hides unapproved private contacts`() {
        val viewModel = CityExplorerViewModel(LocalCityExplorerRepository())
        val firstHouse = viewModel.uiState.value.scene!!.houses.first()

        composeTestRule.setContent {
            MyApplicationTheme {
                CityExplorerScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        viewModel.onHouseTapped(firstHouse)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("selected_house_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("test_data_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("house_flag_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("house_owner_name").assertTextContains(firstHouse.ownerName, substring = true)
        composeTestRule.onNodeWithTag("house_country_value").assertTextContains(firstHouse.flag.countryName, substring = true)
        composeTestRule.onNodeWithTag("house_design_style_value").assertIsDisplayed()
        composeTestRule.onNodeWithTag("house_family_value").assertDoesNotExist()
        composeTestRule.onNodeWithTag("house_public_info_section").assertIsDisplayed()

        // Ensure unapproved private phone/email are NEVER displayed
        composeTestRule.onNodeWithTag("house_approved_contact_section").assertDoesNotExist()
        composeTestRule.onNodeWithText("+966500000100", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("resident1@private-test.local", substring = true).assertDoesNotExist()

        // Close card and return to exploration hint
        composeTestRule.onNodeWithTag("close_house_card_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("selected_house_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("explorer_hint_pill").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ar")
    fun `house with unavailable optional fields displays graceful fallback and omits family count`() {
        val viewModel = CityExplorerViewModel(LocalCityExplorerRepository())

        composeTestRule.setContent {
            MyApplicationTheme {
                CityExplorerScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // house_08 has no publishable extra info
        viewModel.selectHouseById("house_08")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("house_no_public_info").assertIsDisplayed()
        composeTestRule.onNodeWithTag("house_family_value").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "ar")
    fun `citizen explorer inspects districts buildings props streets and plots read only without edit controls`() {
        val baseSnap = (LocalCityExplorerRepository().observeCitySnapshot() as StateFlow).value
            as CityDataState.Success<CityExplorerSnapshot>
        val district = DistrictZone("dist_civic", "الحي الحكومي", "CIV", 0xFF2D6A8E, GridCoord(0, 0), 16, 16)
        val building = PublicBuilding(
            "bld_hosp",
            "مستشفى الطيبين",
            PublicBuildingType.HOSPITAL,
            GridCoord(13, 1),
            2,
            2,
            districtId = "dist_civic",
            descriptionAr = "رعاية صحية شاملة لأهالي المدينة",
            publicInfoAr = "يعمل على مدار الساعة"
        )
        val prop = EnvironmentProp("prop_fnt", EnvironmentPropType.FOUNTAIN, GridCoord(13, 4), labelAr = "نافورة الساحة")
        val flagProp = EnvironmentProp("prop_flag", EnvironmentPropType.CITY_FLAG, GridCoord(13, 5), labelAr = "مصر", assetKey = "flag_eg")
        val road = RoadSegment("road_main", GridCoord(12, 0), widthCells = 1, heightCells = 6, streetNameAr = "شارع الملك عبدالعزيز", hasCrosswalk = true)
        val availablePlot = Plot("plot_free", GridCoord(13, 6), PlotStatus.AVAILABLE, widthCells = 2, heightCells = 1, districtId = "dist_civic", plotCode = "P-FREE")
        val blockedPlot = Plot("plot_blocked", GridCoord(13, 8), PlotStatus.UNAVAILABLE, widthCells = 1, heightCells = 1, districtId = "dist_civic", plotCode = "P-HOLD")

        var expanded = CityLayoutEditor.expandGrid(baseSnap.data.scene, 16, 16)
        expanded = CityLayoutEditor.addDistrict(expanded, district)
        expanded = CityLayoutEditor.paintTiles(expanded, listOf(availablePlot.coord, GridCoord(14, 6), blockedPlot.coord), CityTileType.PLOT_LOT)
        expanded = expanded.copy(
            plots = expanded.plots + availablePlot + blockedPlot,
            publicBuildings = listOf(building),
            environmentProps = listOf(prop, flagProp),
            roadSegments = listOf(road)
        )
        val repo = object : CityExplorerRepository {
            override fun observeCitySnapshot() = MutableStateFlow(
                CityDataState.Success(baseSnap.data.copy(scene = expanded))
            )
            override suspend fun refresh() {}
            override suspend fun submitHouseRequest(draft: HouseRequestDraft) =
                HouseRequestSubmissionResult.Success("req_stub", "ok")
        }
        val viewModel = CityExplorerViewModel(repo)

        composeTestRule.setContent {
            MyApplicationTheme {
                CityExplorerScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // District chip bar & read-only district card
        composeTestRule.onNodeWithTag("explorer_districts_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("explorer_district_dist_civic").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("selected_district_card").assertIsDisplayed()

        // Civic building card + district badge + metadata
        viewModel.onBuildingTapped(building)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("selected_building_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("element_district_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("element_extra_public_info").assertIsDisplayed()

        // Districts browser dialog
        composeTestRule.onNodeWithTag("open_districts_browser_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("districts_browser_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("district_browser_item_dist_civic").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("districts_browser_dialog").assertDoesNotExist()
        composeTestRule.onNodeWithTag("selected_district_card").assertIsDisplayed()

        // Prop & standalone Arab flag cards
        viewModel.onPropTapped(prop)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("selected_prop_card").assertIsDisplayed()

        viewModel.onPropTapped(flagProp)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("selected_prop_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("مصر", substring = true).assertIsDisplayed()

        // Named street card
        viewModel.onRoadTapped(road, road.coord)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("selected_road_card").assertIsDisplayed()

        // Available & unavailable plot cards
        viewModel.onPlotTapped(availablePlot)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("selected_plot_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("أرض متاحة للبناء", substring = true).assertIsDisplayed()

        viewModel.onPlotTapped(blockedPlot)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("أرض غير متاحة حاليًا", substring = true).assertIsDisplayed()

        // Citizen explorer must NEVER expose editor or mutation controls
        composeTestRule.onNodeWithTag("editor_tool_dock").assertDoesNotExist()
        composeTestRule.onNodeWithTag("inspector_delete_plot_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("inspector_toggle_plot_button").assertDoesNotExist()
    }

    @Test
    fun `renders empty state and offline banner cleanly`() {
        val stateFlow = MutableStateFlow<CityDataState<CityExplorerSnapshot>>(CityDataState.Empty)
        val repo = object : CityExplorerRepository {
            override fun observeCitySnapshot() = stateFlow
            override suspend fun refresh() {}
            override suspend fun submitHouseRequest(draft: HouseRequestDraft) =
                HouseRequestSubmissionResult.Success("req_stub", "ok")
        }
        val viewModel = CityExplorerViewModel(repo)

        composeTestRule.setContent {
            MyApplicationTheme {
                CityExplorerScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("explorer_empty_state").assertIsDisplayed()

        stateFlow.value = CityDataState.Offline("لا يوجد اتصال")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("explorer_offline_state").assertIsDisplayed()

        val localSnap = (LocalCityExplorerRepository().observeCitySnapshot() as StateFlow).value
            as CityDataState.Success
        stateFlow.value = CityDataState.Offline(
            message = "عرض نسخة سابقة دون اتصال",
            staleData = localSnap.data
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("city_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("explorer_offline_banner").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ar")
    fun `visitor opens house request dialog without family field and submits request`() {
        val viewModel = CityExplorerViewModel(LocalCityExplorerRepository())

        composeTestRule.setContent {
            MyApplicationTheme {
                CityExplorerScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("open_house_request_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("house_request_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("عدد أفراد", substring = true).assertDoesNotExist()

        composeTestRule.onNodeWithTag("request_name_input").performTextInput("بندر العتيبي")
        composeTestRule.onNodeWithTag("request_phone_input").performTextInput("0551122334")
        composeTestRule.onNodeWithTag("request_notes_input").performTextInput("قرب الواجهة البحرية")
        composeTestRule.onNodeWithTag("request_submit_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("request_success_banner").assertIsDisplayed()
    }
}
