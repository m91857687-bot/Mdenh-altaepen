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
import com.example.admin.ui.AdminLoginScreen
import com.example.admin.ui.theme.AdminTheme
import com.example.core.data.local.LocalCityAdminRepository
import com.example.core.data.supabase.AdminAuthManager
import com.example.core.data.supabase.AdminAuthState
import com.example.core.data.supabase.AlwaysOnlineChecker
import com.example.core.data.supabase.SupabaseAdminAuthRepository
import com.example.core.data.supabase.SupabaseConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val testConfig = SupabaseConfig(
        url = "https://example.supabase.co",
        anonKey = "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiYW5vbiJ9.sig"
    )

    private class FakeAdminAuthManager(
        initialState: AdminAuthState
    ) : AdminAuthManager {
        private val _state = MutableStateFlow(initialState)
        override val authState: StateFlow<AdminAuthState> = _state.asStateFlow()
        var lastEmail: String? = null
        var lastPassword: String? = null

        override fun isAuthenticatedAdmin(): Boolean =
            _state.value is AdminAuthState.AuthenticatedAdmin

        override suspend fun restoreAndVerifySession() = Unit

        override suspend fun signIn(email: String, password: String) {
            lastEmail = email
            lastPassword = password
            if (email.trim() == "viewer@madina.test") {
                _state.value = AdminAuthState.AccessDenied(
                    email = email.trim(),
                    message = "تم رفض الدخول: هذا الحساب (VIEWER) لا يمتلك صلاحية الإدارة (ADMIN)."
                )
            } else {
                _state.value = AdminAuthState.AuthenticatedAdmin(
                    userId = "uid_admin_01",
                    email = email.trim(),
                    displayName = "مدير المدينة"
                )
            }
        }

        override suspend fun signOut() {
            _state.value = AdminAuthState.Unauthenticated()
        }
    }

    @Test
    @Config(qualifiers = "ar")
    fun `admin app has distinct localized app name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("مدينة الطيبين للإدارة", context.getString(R.string.app_name))
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

        // 1. Local test mode opens dashboard without auth controls
        composeTestRule.onNodeWithTag("admin_top_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_sign_out_button").assertDoesNotExist()
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

        // 3. Navigate to Plots tab and filter AVAILABLE plots
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

    @Test
    fun `unconfigured supabase displays notice and never exposes dashboard`() {
        val unconfiguredRepo = SupabaseAdminAuthRepository.create(
            config = SupabaseConfig("", ""),
            connectivityChecker = AlwaysOnlineChecker
        )
        val unconfiguredVm = AdminViewModel(
            repository = LocalCityAdminRepository(),
            authRepository = unconfiguredRepo
        )

        composeTestRule.setContent {
            AdminTheme {
                AdminAppScreen(viewModel = unconfiguredVm)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("admin_unconfigured_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_houses").assertDoesNotExist()
        composeTestRule.onNodeWithTag("stat_total_houses").assertDoesNotExist()
    }

    @Test
    fun `unauthenticated admin shows email and password login form without dashboard`() {
        val authManager = FakeAdminAuthManager(AdminAuthState.Unauthenticated())
        val viewModel = AdminViewModel(
            repository = LocalCityAdminRepository(),
            authRepository = authManager
        )

        composeTestRule.setContent {
            AdminTheme {
                AdminAppScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("admin_login_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_login_email").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_login_password").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_login_submit").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_houses").assertDoesNotExist()
        composeTestRule.onNodeWithTag("stat_total_houses").assertDoesNotExist()
    }

    @Test
    fun `authenticated ADMIN opens dashboard shows sign out button and returns to login on sign out`() {
        val authManager = FakeAdminAuthManager(
            AdminAuthState.AuthenticatedAdmin(
                userId = "uid_owner_1",
                email = "owner@madina.test",
                displayName = "مالك المدينة"
            )
        )
        val viewModel = AdminViewModel(
            repository = LocalCityAdminRepository(),
            authRepository = authManager
        )

        composeTestRule.setContent {
            AdminTheme {
                AdminAppScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("stat_total_houses").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_houses").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_sign_out_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_login_card").assertDoesNotExist()

        composeTestRule.onNodeWithTag("admin_sign_out_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("admin_login_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stat_total_houses").assertDoesNotExist()
    }

    @Test
    fun `viewer access denied blocks dashboard and shows clear rejection banner`() {
        val authManager = FakeAdminAuthManager(AdminAuthState.Unauthenticated())
        val viewModel = AdminViewModel(
            repository = LocalCityAdminRepository(),
            authRepository = authManager
        )

        composeTestRule.setContent {
            AdminTheme {
                AdminAppScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("admin_login_email").performScrollTo().performTextInput("viewer@madina.test")
        composeTestRule.onNodeWithTag("admin_login_password").performScrollTo().performTextInput("ViewerPass123!")
        composeTestRule.onNodeWithTag("admin_login_submit").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("admin_access_denied_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_access_denied_banner").assertTextContains("VIEWER", substring = true)
        composeTestRule.onNodeWithTag("stat_total_houses").assertDoesNotExist()
        composeTestRule.onNodeWithTag("nav_tab_houses").assertDoesNotExist()
    }

    @Test
    fun `session expired state blocks dashboard and prompts login`() {
        val authManager = FakeAdminAuthManager(
            AdminAuthState.SessionExpired("انتهت صلاحية جلسة الإدارة. يرجى تسجيل الدخول مجددًا.")
        )
        val viewModel = AdminViewModel(
            repository = LocalCityAdminRepository(),
            authRepository = authManager
        )

        composeTestRule.setContent {
            AdminTheme {
                AdminAppScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("admin_login_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_session_expired_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stat_total_houses").assertDoesNotExist()
    }

    @Test
    fun `login screen submits entered email and password`() {
        var submittedEmail: String? = null
        var submittedPassword: String? = null
        composeTestRule.setContent {
            AdminTheme {
                AdminLoginScreen(
                    authState = AdminAuthState.Unauthenticated("البريد الإلكتروني أو كلمة المرور غير صحيحة."),
                    onSignIn = { email, password ->
                        submittedEmail = email
                        submittedPassword = password
                    },
                    onRetrySession = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("admin_login_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_auth_feedback").assertTextContains("غير صحيحة", substring = true)
        composeTestRule.onNodeWithTag("admin_login_email").performScrollTo().performTextInput("admin@madina.test")
        composeTestRule.onNodeWithTag("admin_login_password").performScrollTo().performTextInput("MySecret#2026")
        composeTestRule.onNodeWithTag("admin_login_submit").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals("admin@madina.test", submittedEmail)
        assertEquals("MySecret#2026", submittedPassword)
    }

    @Test
    @Config(qualifiers = "ar")
    fun `requests tab supports status transitions and direct plot assignment to build house`() {
        val viewModel = AdminViewModel(LocalCityAdminRepository())
        val initialHouses = viewModel.uiState.value.snapshot!!.totalHouses

        composeTestRule.setContent {
            AdminTheme {
                AdminAppScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("nav_tab_requests").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("admin_requests_list").assertIsDisplayed()

        // Update first request status
        composeTestRule.onNodeWithTag("request_update_btn_req_local_01").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_status_under_review").performClick()
        composeTestRule.onNodeWithTag("confirm_request_status_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(
            com.example.core.data.model.HouseRequestStatus.UNDER_REVIEW,
            viewModel.uiState.value.snapshot!!.houseRequests.first { it.id == "req_local_01" }.status
        )

        // Approve & build house directly from req_local_01 with UAE flag
        composeTestRule.onNodeWithTag("request_build_btn_req_local_01").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pick_flag_AE").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("confirm_house_dialog_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(initialHouses + 1, viewModel.uiState.value.snapshot!!.totalHouses)
        assertEquals(
            com.example.core.data.model.HouseRequestStatus.COMPLETED,
            viewModel.uiState.value.snapshot!!.houseRequests.first { it.id == "req_local_01" }.status
        )
    }
}
