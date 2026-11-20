package com.example.admin

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.admin.ui.AdminAppScreen
import com.example.admin.ui.theme.AdminTheme
import com.example.core.data.local.LocalCityAdminRepository
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdminRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `admin app has distinct localized app name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("إدارة الطيبين", context.getString(R.string.app_name))
    }

    @Test
    fun `dashboard houses plots and map tabs work end to end`() {
        val viewModel = AdminViewModel(LocalCityAdminRepository())

        composeTestRule.setContent {
            AdminTheme {
                AdminAppScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // 1. Dashboard displays stats and local mode banner
        composeTestRule.onNodeWithTag("admin_top_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_local_mode_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stat_total_houses").assertTextContains("9", substring = true)
        composeTestRule.onNodeWithTag("stat_available_plots").assertTextContains("3", substring = true)
        composeTestRule.onNodeWithTag("stat_occupied_plots").assertTextContains("9", substring = true)
        composeTestRule.onNodeWithTag("dashboard_city_preview_card")
            .performScrollTo()
            .assertIsDisplayed()

        // 2. Navigate to Houses tab and open add house dialog
        composeTestRule.onNodeWithTag("nav_tab_houses").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("admin_houses_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_house_fab").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("pick_plot_plot_10").performClick()
        composeTestRule.onNodeWithTag("input_owner_name").performTextInput("فيصل العلي")
        composeTestRule.onNodeWithTag("confirm_house_dialog_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(10, viewModel.uiState.value.snapshot!!.totalHouses)

        // 3. Navigate to Plots tab and filter AVAILABLE plots (plot_11, plot_12 remain)
        composeTestRule.onNodeWithTag("nav_tab_plots").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("admin_plots_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("filter_plots_available").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("plot_card_plot_11").assertIsDisplayed()

        // 4. Select available plot on map
        composeTestRule.onNodeWithTag("show_plot_map_plot_11").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("city_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_map_selection_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_build_on_selected_plot_button").assertIsDisplayed()
    }
}
