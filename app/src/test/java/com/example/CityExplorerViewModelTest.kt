package com.example

import com.example.core.data.local.LocalCityExplorerRepository
import com.example.explorer.CityExplorerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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
}
