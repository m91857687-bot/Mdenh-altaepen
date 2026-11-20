package com.example.core.data

import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.world.StarterCityMap
import com.example.cityengine.world.ValidationResult
import com.example.core.data.local.LocalCityAdminRepository
import com.example.core.data.local.LocalCityExplorerRepository
import com.example.core.data.model.AdminHouseDraft
import com.example.core.data.model.AdminOperationResult
import com.example.core.data.model.CityDataState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCityAdminRepositoryTest {

    @Test
    fun `initializes with 9 occupied plots and 3 available plots without affecting explorer`() = runTest {
        val adminRepo = LocalCityAdminRepository()
        val explorerRepo = LocalCityExplorerRepository()

        val adminSnap = (adminRepo.observeAdminSnapshot().first() as CityDataState.Success).data
        val explorerSnap = (explorerRepo.observeCitySnapshot().first() as CityDataState.Success).data

        assertEquals(9, adminSnap.totalHouses)
        assertEquals(3, adminSnap.availablePlotsCount)
        assertEquals(9, adminSnap.occupiedPlotsCount)
        assertEquals(12, explorerSnap.scene.houses.size)
    }

    @Test
    fun `rejects building on occupied plot street or park`() = runTest {
        val adminRepo = LocalCityAdminRepository()

        assertTrue(adminRepo.validatePlotForBuild("plot_01") is ValidationResult.Invalid)
        assertTrue(adminRepo.validatePlotForBuild("plot_10") is ValidationResult.Valid)

        // Road coord (3, 3) and central park coord (5, 5)
        assertTrue(adminRepo.validateCoordinateForBuild(GridCoord(3, 3)) is ValidationResult.Invalid)
        assertTrue(adminRepo.validateCoordinateForBuild(GridCoord(5, 5)) is ValidationResult.Invalid)

        val addOnOccupied = adminRepo.addHouse(
            AdminHouseDraft(
                targetPlotId = "plot_01",
                ownerName = "تجربة متداخلة",
                familyMembersCount = 3,
                flag = StarterCityMap.flagPresets.first(),
                palette = StarterCityMap.palettePresets.first().palette
            )
        )
        assertTrue(addOnOccupied is AdminOperationResult.Failure)
    }

    @Test
    fun `supports add changeColor move and delete without affecting explorer repository`() = runTest {
        val adminRepo = LocalCityAdminRepository()
        val explorerRepo = LocalCityExplorerRepository()

        // 1. Add house to available plot_10
        val addRes = adminRepo.addHouse(
            AdminHouseDraft(
                targetPlotId = "plot_10",
                ownerName = "بدر السبيعي",
                familyMembersCount = 4,
                flag = StarterCityMap.flagPresets[0],
                palette = StarterCityMap.palettePresets[1].palette
            )
        )
        assertTrue(addRes is AdminOperationResult.Success)
        val createdId = (addRes as AdminOperationResult.Success).affectedHouseId!!

        // 2. Change house color
        val newPalette = StarterCityMap.palettePresets[4].palette
        val colorRes = adminRepo.changeHouseColor(createdId, newPalette)
        assertTrue(colorRes is AdminOperationResult.Success)
        var snap = (adminRepo.observeAdminSnapshot().first() as CityDataState.Success).data
        assertEquals(newPalette, snap.scene.houses.first { it.id == createdId }.palette)

        // 3. Move house from plot_10 to available plot_11
        val moveRes = adminRepo.moveHouse(createdId, "plot_11")
        assertTrue(moveRes is AdminOperationResult.Success)
        snap = (adminRepo.observeAdminSnapshot().first() as CityDataState.Success).data
        assertEquals(PlotStatus.AVAILABLE, snap.plots.first { it.id == "plot_10" }.status)
        assertEquals(PlotStatus.OCCUPIED, snap.plots.first { it.id == "plot_11" }.status)
        assertEquals("plot_11", snap.scene.houses.first { it.id == createdId }.plotId)

        // 4. Delete house and verify plot_11 becomes AVAILABLE
        val deleteRes = adminRepo.deleteHouse(createdId)
        assertTrue(deleteRes is AdminOperationResult.Success)
        snap = (adminRepo.observeAdminSnapshot().first() as CityDataState.Success).data
        assertEquals(PlotStatus.AVAILABLE, snap.plots.first { it.id == "plot_11" }.status)
        assertNull(snap.plots.first { it.id == "plot_11" }.houseId)
        assertEquals(9, snap.totalHouses)

        // 5. Explorer remains completely unaffected
        val explorerSnap = (explorerRepo.observeCitySnapshot().first() as CityDataState.Success).data
        assertEquals(12, explorerSnap.scene.houses.size)
    }
}
