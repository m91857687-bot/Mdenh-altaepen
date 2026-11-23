package com.example

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
import com.example.core.data.local.LocalCityExplorerRepository
import com.example.core.data.model.CityDataState
import com.example.core.data.model.CityExplorerSnapshot
import com.example.core.data.model.HouseRequestDraft
import com.example.core.data.model.HouseRequestSubmissionResult
import com.example.core.data.repository.CityExplorerRepository
import com.example.explorer.CityExplorerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        assertFalse(state.hasSelection)
    }

    @Test
    fun `supports read-only selection of buildings props plots roads districts and preserves across refresh`() {
        val baseSnap = (LocalCityExplorerRepository().observeCitySnapshot() as StateFlow).value
            as CityDataState.Success<CityExplorerSnapshot>
        val district = DistrictZone("d1", "حي الشرق", "E1", 0xFF2D6A8E, GridCoord(0, 0), 12, 12)
        val building = PublicBuilding("b1", "جامع المدينة", PublicBuildingType.GOVERNMENT_HALL, GridCoord(13, 2), 2, 2, districtId = "d1")
        val prop = EnvironmentProp("p1", EnvironmentPropType.FOUNTAIN, GridCoord(13, 5))
        val road = RoadSegment("r1", GridCoord(3, 0), widthCells = 1, heightCells = 4, streetNameAr = "طريق الشفاء")
        val plot = Plot("pl1", GridCoord(14, 2), PlotStatus.AVAILABLE)

        var scene = CityLayoutEditor.expandGrid(baseSnap.data.scene, 16, 16)
        scene = scene.copy(
            districts = listOf(district),
            publicBuildings = listOf(building),
            environmentProps = listOf(prop),
            roadSegments = listOf(road),
            plots = scene.plots + plot
        )
        val flow = MutableStateFlow<CityDataState<CityExplorerSnapshot>>(
            CityDataState.Success(baseSnap.data.copy(scene = scene))
        )
        val repo = object : CityExplorerRepository {
            override fun observeCitySnapshot() = flow
            override suspend fun refresh() {}
            override suspend fun submitHouseRequest(draft: HouseRequestDraft) =
                HouseRequestSubmissionResult.Success("req_stub", "ok")
        }
        val viewModel = CityExplorerViewModel(repo)

        viewModel.onBuildingTapped(building)
        assertEquals("b1", viewModel.uiState.value.selectedBuilding?.id)
        assertEquals("b1", viewModel.uiState.value.scene?.selectedBuildingId)

        // Snapshot update preserves selected building
        flow.value = CityDataState.Success(baseSnap.data.copy(scene = scene.copy(districts = listOf(district.copy(nameAr = "حي الشرق المحدث")))))
        assertEquals("b1", viewModel.uiState.value.selectedBuilding?.id)

        viewModel.onPropTapped(prop)
        assertEquals("p1", viewModel.uiState.value.selectedProp?.id)
        assertNull(viewModel.uiState.value.selectedBuilding)

        viewModel.onPlotTapped(plot)
        assertEquals("pl1", viewModel.uiState.value.selectedPlot?.id)

        viewModel.onRoadTapped(road, GridCoord(3, 1))
        assertEquals("r1", viewModel.uiState.value.selectedRoad?.id)

        // Tapping a plain grass coord clears road selection
        viewModel.onRoadTapped(null, GridCoord(0, 0))
        assertFalse(viewModel.uiState.value.hasSelection)

        viewModel.onDistrictSelected(district)
        assertEquals("d1", viewModel.uiState.value.selectedDistrict?.id)

        viewModel.clearSelection()
        assertFalse(viewModel.uiState.value.hasSelection)
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
        val stateFlow = MutableStateFlow<CityDataState<CityExplorerSnapshot>>(
            CityDataState.Loading
        )
        val fakeRepo = object : CityExplorerRepository {
            override fun observeCitySnapshot() = stateFlow
            override suspend fun refresh() {}
            override suspend fun submitHouseRequest(draft: HouseRequestDraft) =
                HouseRequestSubmissionResult.Success("req_stub", "ok")
        }
        val viewModel = CityExplorerViewModel(fakeRepo)
        assertTrue(viewModel.uiState.value.isLoading)

        stateFlow.value = CityDataState.Empty
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isEmpty)

        stateFlow.value = CityDataState.Offline("لا يوجد اتصال بالإنترنت")
        assertTrue(viewModel.uiState.value.isOffline)
        assertNull(viewModel.uiState.value.scene)

        // Offline with stale snapshot preserves map while marking isOffline = true
        val localSnap = (LocalCityExplorerRepository().observeCitySnapshot() as StateFlow).value
            as CityDataState.Success
        stateFlow.value = CityDataState.Offline("بيانات قديمة", staleData = localSnap.data)
        assertTrue(viewModel.uiState.value.isOffline)
        assertNotNull(viewModel.uiState.value.scene)
        assertEquals("بيانات قديمة", viewModel.uiState.value.offlineMessage)

        stateFlow.value = CityDataState.Error("خطأ في الخادم")
        assertFalse(viewModel.uiState.value.isOffline)
        assertEquals("خطأ في الخادم", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `house request dialog validates and submits through repository and toggles districts browser`() {
        val viewModel = CityExplorerViewModel(LocalCityExplorerRepository())

        viewModel.openHouseRequestDialog()
        assertTrue(viewModel.uiState.value.houseRequestState.isOpen)

        viewModel.submitHouseRequest("أ", null, null, null)
        assertNotNull(viewModel.uiState.value.houseRequestState.errorMessage)

        viewModel.submitHouseRequest("نواف الشهري", "0554443322", "nawaf@madina.test", "قرب الساحة")
        assertNull(viewModel.uiState.value.houseRequestState.errorMessage)
        assertNotNull(viewModel.uiState.value.houseRequestState.successMessage)

        viewModel.closeHouseRequestDialog()
        assertFalse(viewModel.uiState.value.houseRequestState.isOpen)

        viewModel.openDistrictsBrowser()
        assertTrue(viewModel.uiState.value.isDistrictsBrowserOpen)
        viewModel.closeDistrictsBrowser()
        assertFalse(viewModel.uiState.value.isDistrictsBrowserOpen)
    }
}
