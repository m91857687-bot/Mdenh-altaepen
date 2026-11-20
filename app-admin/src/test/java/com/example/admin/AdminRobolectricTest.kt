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
import com.example.admin.ui.AdminDeviceActivationScreen
import com.example.admin.ui.theme.AdminTheme
import com.example.core.data.local.LocalCityAdminRepository
import com.example.core.data.supabase.AdminDeviceAuthManager
import com.example.core.data.supabase.AdminDeviceAuthState
import com.example.core.data.supabase.AlwaysOnlineChecker
import com.example.core.data.supabase.ConnectivityChecker
import com.example.core.data.supabase.InMemoryAdminDeviceCredentialStore
import com.example.core.data.supabase.StoredDeviceCredential
import com.example.core.data.supabase.SupabaseConfig
import com.example.core.data.supabase.SupabaseDeviceAuthRepository
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

    private class FakeAdminDeviceAuthManager(
        initialState: AdminDeviceAuthState
    ) : AdminDeviceAuthManager {
        private val _state = MutableStateFlow(initialState)
        override val authState: StateFlow<AdminDeviceAuthState> = _state.asStateFlow()
        var lastActivatedCode: String? = null
        var lastActivatedDeviceName: String? = null

        override fun isAuthorizedDevice(): Boolean = _state.value is AdminDeviceAuthState.Authorized

        override suspend fun verifyDeviceOnStartup() = Unit

        override suspend fun activateDevice(activationCode: String, deviceName: String) {
            lastActivatedCode = activationCode
            lastActivatedDeviceName = deviceName
            _state.value = AdminDeviceAuthState.Authorized(
                deviceId = "dev_fake_01",
                deviceName = deviceName.ifBlank { "جهاز الإدارة" }
            )
        }

        override fun beginReEnrollmentFromRevoked() {
            if (_state.value is AdminDeviceAuthState.Revoked) {
                _state.value = AdminDeviceAuthState.Unenrolled()
            }
        }
    }

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

        // 1. Dashboard opens directly without any sign-out or login controls
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

    @Test
    fun `unconfigured device displays notice and never exposes dashboard`() {
        val unconfiguredRepo = SupabaseDeviceAuthRepository.create(
            config = SupabaseConfig("", ""),
            credentialStore = InMemoryAdminDeviceCredentialStore()
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
    fun `unenrolled device shows one-time activation code form without email or password fields`() {
        val store = InMemoryAdminDeviceCredentialStore(initialCredential = null)
        val authRepo = SupabaseDeviceAuthRepository.create(
            config = testConfig,
            credentialStore = store,
            connectivityChecker = AlwaysOnlineChecker
        )
        val viewModel = AdminViewModel(
            repository = LocalCityAdminRepository(),
            authRepository = authRepo
        )

        composeTestRule.setContent {
            AdminTheme {
                AdminAppScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("admin_activation_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_activation_code_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_device_name_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_activate_submit").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_login_email").assertDoesNotExist()
        composeTestRule.onNodeWithTag("admin_login_password").assertDoesNotExist()
        composeTestRule.onNodeWithTag("nav_tab_houses").assertDoesNotExist()
    }

    @Test
    fun `trusted active device opens dashboard directly on startup`() {
        val authManager = FakeAdminDeviceAuthManager(
            AdminDeviceAuthState.Authorized(
                deviceId = "dev_trusted_01",
                deviceName = "جهاز المالك الأساسي"
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
        composeTestRule.onNodeWithTag("admin_activation_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("admin_sign_out_button").assertDoesNotExist()
    }

    @Test
    fun `revoked state blocks dashboard completely until re-enrolled with server code`() {
        val authManager = FakeAdminDeviceAuthManager(
            AdminDeviceAuthState.Revoked("تم إلغاء الجهاز من الخادم")
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

        composeTestRule.onNodeWithTag("admin_revoked_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_revoked_reason").assertTextContains("تم إلغاء الجهاز من الخادم", substring = true)
        composeTestRule.onNodeWithTag("stat_total_houses").assertDoesNotExist()

        // Tapping re-enroll moves ONLY to the empty activation screen (still no dashboard access)
        composeTestRule.onNodeWithTag("admin_reenroll_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("admin_activation_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stat_total_houses").assertDoesNotExist()
    }

    @Test
    fun `offline startup verification blocks dashboard even when device credential is stored`() {
        val store = InMemoryAdminDeviceCredentialStore(
            initialCredential = StoredDeviceCredential(
                deviceId = "dev_offline_01",
                deviceToken = "tok_offline_01",
                deviceName = "Owner Phone"
            )
        )
        val offlineChecker = object : ConnectivityChecker {
            override fun isOnline(): Boolean = false
        }
        val authRepo = SupabaseDeviceAuthRepository.create(
            config = testConfig,
            credentialStore = store,
            connectivityChecker = offlineChecker
        )
        val viewModel = AdminViewModel(
            repository = LocalCityAdminRepository(),
            authRepository = authRepo
        )

        composeTestRule.setContent {
            AdminTheme {
                AdminAppScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("admin_offline_auth_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_retry_auth_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stat_total_houses").assertDoesNotExist()
        composeTestRule.onNodeWithTag("nav_tab_houses").assertDoesNotExist()
    }

    @Test
    fun `activation screen submits entered code and device name`() {
        var submittedCode: String? = null
        var submittedName: String? = null
        composeTestRule.setContent {
            AdminTheme {
                AdminDeviceActivationScreen(
                    authState = AdminDeviceAuthState.Unenrolled("رمز التفعيل غير صالح"),
                    onActivateDevice = { code, name ->
                        submittedCode = code
                        submittedName = name
                    },
                    onRetryVerification = {},
                    onBeginReEnrollment = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("admin_activation_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_auth_feedback").assertTextContains("رمز التفعيل غير صالح", substring = true)
        composeTestRule.onNodeWithTag("admin_activation_code_input").performScrollTo().performTextInput("MADINA-1111-2222-3333-4444")
        composeTestRule.onNodeWithTag("admin_activate_submit").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals("MADINA-1111-2222-3333-4444", submittedCode)
        assertEquals(true, !submittedName.isNullOrBlank())
    }
}
