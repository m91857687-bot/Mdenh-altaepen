package com.example.admin

import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.world.StarterCityMap
import com.example.cityengine.world.ValidationResult
import com.example.core.data.local.LocalCityAdminRepository
import com.example.core.data.local.LocalCityExplorerRepository
import com.example.core.data.model.AdminHouseDraft
import com.example.core.data.model.AdminOperationResult
import com.example.core.data.model.CityDataState
import com.example.core.data.supabase.AdminAuthManager
import com.example.core.data.supabase.AdminAuthState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeAuthManager(
        initialState: AdminAuthState = AdminAuthState.Unauthenticated()
    ) : AdminAuthManager {
        private val _authState = MutableStateFlow(initialState)
        override val authState: StateFlow<AdminAuthState> = _authState.asStateFlow()

        override fun isAuthenticatedAdmin(): Boolean =
            _authState.value is AdminAuthState.AuthenticatedAdmin

        override suspend fun restoreAndVerifySession() = Unit

        override suspend fun signIn(email: String, password: String) {
            if (email == "viewer@madina.test") {
                _authState.value = AdminAuthState.AccessDenied(
                    email = email,
                    message = "تم رفض الدخول: هذا الحساب (VIEWER) لا يمتلك صلاحية الإدارة (ADMIN)."
                )
            } else {
                _authState.value = AdminAuthState.AuthenticatedAdmin(
                    userId = "uid_admin",
                    email = email,
                    displayName = "مدير المدينة"
                )
            }
        }

        override suspend fun signOut() {
            _authState.value = AdminAuthState.Unauthenticated()
        }
    }

    @Test
    fun `dashboard initializes with 9 houses 3 available plots and 9 occupied plots`() {
        val viewModel = AdminViewModel(LocalCityAdminRepository())
        val snap = viewModel.uiState.value.snapshot!!

        assertEquals(9, snap.totalHouses)
        assertEquals(3, snap.availablePlotsCount)
        assertEquals(9, snap.occupiedPlotsCount)
        assertEquals(12, snap.plots.size)
    }

    @Test
    fun `validates available vs occupied plots and rejects street or park coordinates`() {
        val viewModel = AdminViewModel(LocalCityAdminRepository())

        assertTrue(viewModel.validatePlotForBuild("plot_10") is ValidationResult.Valid)
        assertTrue(viewModel.validatePlotForBuild("plot_01") is ValidationResult.Invalid)
        assertTrue(viewModel.validateCoordinateForBuild(GridCoord(3, 4)) is ValidationResult.Invalid)
        assertTrue(viewModel.validateCoordinateForBuild(GridCoord(5, 5)) is ValidationResult.Invalid)
    }

    @Test
    fun `add changeColor move and delete keep plots consistent and leave explorer untouched`() = runTest {
        val explorerRepo = LocalCityExplorerRepository()
        val viewModel = AdminViewModel(LocalCityAdminRepository())

        var opResult: AdminOperationResult? = null
        viewModel.addHouse(
            AdminHouseDraft(
                targetPlotId = "plot_10",
                ownerName = "ماجد القحطاني",
                familyMembersCount = 5,
                flag = StarterCityMap.flagPresets.first(),
                palette = StarterCityMap.palettePresets[0].palette
            )
        ) { opResult = it }
        assertTrue(opResult is AdminOperationResult.Success)
        val newHouseId = (opResult as AdminOperationResult.Success).affectedHouseId!!

        // Prevent overlap on now-occupied plot_10
        viewModel.addHouse(
            AdminHouseDraft(
                targetPlotId = "plot_10",
                ownerName = "تداخل مرفوض",
                familyMembersCount = 2,
                flag = StarterCityMap.flagPresets.first(),
                palette = StarterCityMap.palettePresets[0].palette
            )
        ) { opResult = it }
        assertTrue(opResult is AdminOperationResult.Failure)

        // Change color
        val updatedPalette = StarterCityMap.palettePresets[3].palette
        viewModel.changeHouseColor(newHouseId, updatedPalette) { opResult = it }
        assertTrue(opResult is AdminOperationResult.Success)
        assertEquals(
            updatedPalette,
            viewModel.uiState.value.snapshot!!.scene.houses.first { it.id == newHouseId }.palette
        )

        // Move from plot_10 to plot_12
        viewModel.moveHouse(newHouseId, "plot_12") { opResult = it }
        assertTrue(opResult is AdminOperationResult.Success)
        var plots = viewModel.uiState.value.snapshot!!.plots
        assertEquals(PlotStatus.AVAILABLE, plots.first { it.id == "plot_10" }.status)
        assertEquals(PlotStatus.OCCUPIED, plots.first { it.id == "plot_12" }.status)

        // Delete house and free plot_12
        viewModel.deleteHouse(newHouseId) { opResult = it }
        assertTrue(opResult is AdminOperationResult.Success)
        plots = viewModel.uiState.value.snapshot!!.plots
        assertEquals(PlotStatus.AVAILABLE, plots.first { it.id == "plot_12" }.status)
        assertNull(plots.first { it.id == "plot_12" }.houseId)

        // Explorer remains completely independent
        val explorerSnap = (explorerRepo.observeCitySnapshot().first() as CityDataState.Success).data
        assertEquals(12, explorerSnap.scene.houses.size)
    }

    @Test
    fun `auth state gates snapshot clears on signOut and denies viewer account`() {
        val fakeAuth = FakeAuthManager(AdminAuthState.Unauthenticated())
        val viewModel = AdminViewModel(
            repository = LocalCityAdminRepository(),
            authRepository = fakeAuth
        )

        // Unauthenticated clears snapshot
        assertTrue(viewModel.uiState.value.authState is AdminAuthState.Unauthenticated)
        assertNull(viewModel.uiState.value.snapshot)

        // VIEWER login results in AccessDenied and no snapshot exposure
        viewModel.signIn("viewer@madina.test", "secret123")
        assertTrue(viewModel.uiState.value.authState is AdminAuthState.AccessDenied)
        assertNull(viewModel.uiState.value.snapshot)

        // ADMIN login unlocks state, signOut clears again
        viewModel.signIn("owner@madina.test", "secret123")
        assertTrue(viewModel.uiState.value.authState is AdminAuthState.AuthenticatedAdmin)

        viewModel.signOut()
        assertTrue(viewModel.uiState.value.authState is AdminAuthState.Unauthenticated)
        assertNull(viewModel.uiState.value.snapshot)
    }
}
