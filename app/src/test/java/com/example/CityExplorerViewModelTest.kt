package com.example

import com.example.core.data.local.LocalCityExplorerRepository
import com.example.explorer.CityExplorerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CityExplorerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initializes directly into city without login and loads 12 test houses`() {
        val viewModel = CityExplorerViewModel(LocalCityExplorerRepository())
        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertNotNull(state.scene)
        assertEquals(12, state.scene?.houses?.size)
        assertEquals(12, state.houseDetailsById.size)
        assertTrue(state.isTestData)
        assertNull(state.selectedHouse)
    }

    @Test
    fun `selecting and clearing house updates scene selection and public details`() {
        val viewModel = CityExplorerViewModel(LocalCityExplorerRepository())
        val targetHouse = viewModel.uiState.value.scene!!.houses[2]

        viewModel.onHouseTapped(targetHouse)
        var state = viewModel.uiState.value
        assertEquals(targetHouse.id, state.scene?.selectedHouseId)
        assertEquals(targetHouse.ownerName, state.selectedHouse?.ownerName)
        assertEquals(targetHouse.flag.countryName, state.selectedHouse?.countryName)
        assertNull(state.selectedHouse?.approvedPhone)
        assertNull(state.selectedHouse?.approvedEmail)

        viewModel.clearSelection()
        state = viewModel.uiState.value
        assertNull(state.scene?.selectedHouseId)
        assertNull(state.selectedHouse)
    }

    @Test
    fun `handles unavailable optional data cleanly without leaking private contact info`() {
        val viewModel = CityExplorerViewModel(LocalCityExplorerRepository())

        viewModel.selectHouseById("house_08")
        val house08 = viewModel.uiState.value.selectedHouse
        assertNotNull(house08)
        assertFalse(house08!!.hasAdditionalPublicInfo)
        assertNull(house08.approvedPhone)
        assertNull(house08.approvedEmail)

        viewModel.selectHouseById("house_10")
        val house10 = viewModel.uiState.value.selectedHouse
        assertNotNull(house10)
        assertNull(house10!!.familyMembersCount)
    }

    @Test
    fun `exposes empty offline and error states cleanly without crashing`() = runTest {
        val stateFlow = kotlinx.coroutines.flow.MutableStateFlow<com.example.core.data.model.CityDataState<com.example.core.data.model.CityExplorerSnapshot>>(
            com.example.core.data.model.CityDataState.Loading
        )
        val fakeRepo = object : com.example.core.data.repository.CityExplorerRepository {
            override fun observeCitySnapshot() = stateFlow
            override suspend fun refresh() {}
        }
        val viewModel = CityExplorerViewModel(fakeRepo)
        assertTrue(viewModel.uiState.value.isLoading)

        stateFlow.value = com.example.core.data.model.CityDataState.Empty
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isEmpty)

        stateFlow.value = com.example.core.data.model.CityDataState.Offline("لا يوجد اتصال بالإنترنت")
        assertTrue(viewModel.uiState.value.isOffline)
        assertNull(viewModel.uiState.value.scene)

        // Offline with stale snapshot preserves map while marking isOffline = true
        val localSnap = (LocalCityExplorerRepository().observeCitySnapshot() as kotlinx.coroutines.flow.StateFlow).value
            as com.example.core.data.model.CityDataState.Success
        stateFlow.value = com.example.core.data.model.CityDataState.Offline("بيانات قديمة", staleData = localSnap.data)
        assertTrue(viewModel.uiState.value.isOffline)
        assertNotNull(viewModel.uiState.value.scene)
        assertEquals("بيانات قديمة", viewModel.uiState.value.offlineMessage)

        stateFlow.value = com.example.core.data.model.CityDataState.Error("خطأ في الخادم")
        assertFalse(viewModel.uiState.value.isOffline)
        assertEquals("خطأ في الخادم", viewModel.uiState.value.errorMessage)
    }
}
