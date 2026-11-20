package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.local.LocalCityExplorerRepository
import com.example.explorer.CityExplorerScreen
import com.example.explorer.CityExplorerViewModel
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read localized app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("مدينة الطيبين", appName)
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
        composeTestRule.onNodeWithTag("house_owner_name").assertTextContains(firstHouse.ownerName, substring = true)
        composeTestRule.onNodeWithTag("house_country_value").assertTextContains(firstHouse.flag.countryName, substring = true)
        composeTestRule.onNodeWithTag("house_family_value").assertTextContains("${firstHouse.familyMembersCount}", substring = true)
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
    fun `house with unavailable optional fields displays graceful fallback labels`() {
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

        // house_10 has unavailable public family count
        viewModel.selectHouseById("house_10")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("house_family_value").assertTextContains("غير متوفر", substring = true)
    }

    @Test
    fun `renders empty state and offline banner cleanly`() {
        val stateFlow = kotlinx.coroutines.flow.MutableStateFlow<com.example.core.data.model.CityDataState<com.example.core.data.model.CityExplorerSnapshot>>(
            com.example.core.data.model.CityDataState.Empty
        )
        val repo = object : com.example.core.data.repository.CityExplorerRepository {
            override fun observeCitySnapshot() = stateFlow
            override suspend fun refresh() {}
        }
        val viewModel = CityExplorerViewModel(repo)

        composeTestRule.setContent {
            MyApplicationTheme {
                CityExplorerScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("explorer_empty_state").assertIsDisplayed()

        stateFlow.value = com.example.core.data.model.CityDataState.Offline("لا يوجد اتصال")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("explorer_offline_state").assertIsDisplayed()

        val localSnap = (LocalCityExplorerRepository().observeCitySnapshot() as kotlinx.coroutines.flow.StateFlow).value
            as com.example.core.data.model.CityDataState.Success
        stateFlow.value = com.example.core.data.model.CityDataState.Offline(
            message = "عرض نسخة سابقة دون اتصال",
            staleData = localSnap.data
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("city_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("explorer_offline_banner").assertIsDisplayed()
    }
}
