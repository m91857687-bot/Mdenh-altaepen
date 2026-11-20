package com.example.core.data.local

import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.House
import com.example.cityengine.model.HousePalette
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.world.PlotManager
import com.example.cityengine.world.SpatialValidator
import com.example.cityengine.world.StarterCityMap
import com.example.cityengine.world.ValidationResult
import com.example.core.data.model.AdminCitySnapshot
import com.example.core.data.model.AdminHouseDraft
import com.example.core.data.model.AdminOperationResult
import com.example.core.data.model.CityDataState
import com.example.core.data.model.HouseResidentSourceRecord
import com.example.core.data.repository.CityAdminRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local in-memory administration repository for Phase 3 testing and development.
 * All mutations run exclusively locally via [PlotManager] and [SpatialValidator] and are NOT
 * persisted to a server or synced with app-explorer.
 */
class LocalCityAdminRepository(
    initiallyVacantPlotIds: Set<String> = setOf("plot_10", "plot_11", "plot_12")
) : CityAdminRepository {

    private val baseScene: CitySceneState = StarterCityMap.createScene()
    private val validator = SpatialValidator(baseScene.gridWidth, baseScene.gridHeight, baseScene.tiles)
    private val plotManager = PlotManager(baseScene.plots, baseScene.houses, validator)
    private val records = mutableMapOf<String, HouseResidentSourceRecord>()
    private var nextHouseSequence: Int = 13

    private val snapshotFlow: MutableStateFlow<CityDataState<AdminCitySnapshot>>

    init {
        val defaultRecords = LocalCityExplorerRepository.defaultTestRecords(baseScene)
        defaultRecords.forEach { records[it.house.id] = it }

        // Seed initial AVAILABLE plots for testing build & move workflows right out of the box
        for (plotId in initiallyVacantPlotIds) {
            val plot = plotManager.getPlotById(plotId)
            val houseId = plot?.houseId
            if (houseId != null && plotManager.removeHouseFromPlot(plotId)) {
                records.remove(houseId)
            }
        }
        snapshotFlow = MutableStateFlow(CityDataState.Success(buildSnapshot()))
    }

    override fun observeAdminSnapshot(): Flow<CityDataState<AdminCitySnapshot>> = snapshotFlow.asStateFlow()

    override fun validatePlotForBuild(plotId: String): ValidationResult {
        val plot = plotManager.getPlotById(plotId)
            ?: return ValidationResult.Invalid("القطعة $plotId غير موجودة.")
        return validator.validateCanPlaceHouse(plot, plotManager.houses, plotManager.plots)
    }

    override fun validateCoordinateForBuild(coord: GridCoord): ValidationResult {
        if (!validator.isWithinBounds(coord)) {
            return ValidationResult.Invalid("الإحداثي $coord خارج حدود الخريطة.")
        }
        val tile = baseScene.tiles[coord]
        if (tile != CityTileType.PLOT_LOT) {
            return ValidationResult.Invalid("لا يمكن البناء على الشوارع أو المساحات العامة ($tile).")
        }
        val plot = plotManager.getPlotAt(coord)
            ?: return ValidationResult.Invalid("لا توجد قطعة أرض مخصصة في $coord.")
        return validator.validateCanPlaceHouse(plot, plotManager.houses, plotManager.plots)
    }

    override suspend fun addHouse(draft: AdminHouseDraft): AdminOperationResult {
        val trimmedOwner = draft.ownerName.trim()
        if (trimmedOwner.isEmpty()) {
            return AdminOperationResult.Failure("يرجى إدخال اسم صاحب البيت.")
        }
        if (draft.familyMembersCount <= 0) {
            return AdminOperationResult.Failure("عدد أفراد الأسرة يجب أن يكون 1 على الأقل.")
        }

        val targetPlot = plotManager.getPlotById(draft.targetPlotId)
            ?: return AdminOperationResult.Failure("قطعة الأرض المحددة غير موجودة.")

        val newId = "house_${nextHouseSequence++.toString().padStart(2, '0')}"
        val newHouse = House(
            id = newId,
            plotId = targetPlot.id,
            coord = targetPlot.coord,
            ownerName = trimmedOwner,
            familyMembersCount = draft.familyMembersCount,
            flag = draft.flag,
            palette = draft.palette
        )

        return when (val validation = plotManager.assignHouseToPlot(newHouse)) {
            is ValidationResult.Invalid -> AdminOperationResult.Failure(validation.reason)
            is ValidationResult.Valid -> {
                records[newId] = HouseResidentSourceRecord(
                    house = newHouse,
                    allowPhonePublic = false,
                    allowEmailPublic = false,
                    publicNeighborhoodRole = draft.publicNeighborhoodRole?.trim()?.takeIf { it.isNotEmpty() },
                    publicAbout = draft.publicAbout?.trim()?.takeIf { it.isNotEmpty() },
                    allowPublicInfo = draft.allowPublicInfo,
                    showFamilyCountPublicly = draft.showFamilyCountPublicly,
                    isTestData = true
                )
                emitUpdatedSnapshot()
                AdminOperationResult.Success(
                    message = "تم إضافة البيت محليًا على القطعة ${targetPlot.id}.",
                    affectedHouseId = newId,
                    affectedPlotId = targetPlot.id
                )
            }
        }
    }

    override suspend fun updateHouse(houseId: String, draft: AdminHouseDraft): AdminOperationResult {
        val existing = plotManager.getHouseById(houseId)
            ?: return AdminOperationResult.Failure("البيت غير موجود.")
        val trimmedOwner = draft.ownerName.trim()
        if (trimmedOwner.isEmpty()) {
            return AdminOperationResult.Failure("يرجى إدخال اسم صاحب البيت.")
        }
        if (draft.familyMembersCount <= 0) {
            return AdminOperationResult.Failure("عدد أفراد الأسرة يجب أن يكون 1 على الأقل.")
        }

        val updated = existing.copy(
            ownerName = trimmedOwner,
            familyMembersCount = draft.familyMembersCount,
            flag = draft.flag,
            palette = draft.palette
        )

        return when (val result = plotManager.updateHouse(updated)) {
            is ValidationResult.Invalid -> AdminOperationResult.Failure(result.reason)
            is ValidationResult.Valid -> {
                val prevRecord = records[houseId]
                records[houseId] = (prevRecord ?: HouseResidentSourceRecord(house = updated)).copy(
                    house = updated,
                    publicNeighborhoodRole = draft.publicNeighborhoodRole?.trim()?.takeIf { it.isNotEmpty() },
                    publicAbout = draft.publicAbout?.trim()?.takeIf { it.isNotEmpty() },
                    allowPublicInfo = draft.allowPublicInfo,
                    showFamilyCountPublicly = draft.showFamilyCountPublicly,
                    isTestData = true
                )
                emitUpdatedSnapshot()
                AdminOperationResult.Success(
                    message = "تم تحديث بيانات البيت محليًا.",
                    affectedHouseId = houseId,
                    affectedPlotId = updated.plotId
                )
            }
        }
    }

    override suspend fun changeHouseColor(houseId: String, palette: HousePalette): AdminOperationResult {
        val existing = plotManager.getHouseById(houseId)
            ?: return AdminOperationResult.Failure("البيت غير موجود.")
        val updated = existing.copy(palette = palette)
        return when (val result = plotManager.updateHouse(updated)) {
            is ValidationResult.Invalid -> AdminOperationResult.Failure(result.reason)
            is ValidationResult.Valid -> {
                val prevRecord = records[houseId]
                records[houseId] = (prevRecord ?: HouseResidentSourceRecord(house = updated)).copy(house = updated)
                emitUpdatedSnapshot()
                AdminOperationResult.Success(
                    message = "تم تغيير لون البيت محليًا.",
                    affectedHouseId = houseId,
                    affectedPlotId = updated.plotId
                )
            }
        }
    }

    override suspend fun moveHouse(houseId: String, targetPlotId: String): AdminOperationResult {
        val currentHouse = plotManager.getHouseById(houseId)
            ?: return AdminOperationResult.Failure("البيت غير موجود.")
        if (currentHouse.plotId == targetPlotId) {
            return AdminOperationResult.Failure("البيت موجود بالفعل على هذه القطعة.")
        }
        return when (val result = plotManager.moveHouse(houseId, targetPlotId)) {
            is ValidationResult.Invalid -> AdminOperationResult.Failure(result.reason)
            is ValidationResult.Valid -> {
                val moved = plotManager.getHouseById(houseId)!!
                val prevRecord = records[houseId]
                records[houseId] = (prevRecord ?: HouseResidentSourceRecord(house = moved)).copy(house = moved)
                emitUpdatedSnapshot()
                AdminOperationResult.Success(
                    message = "تم نقل البيت إلى $targetPlotId محليًا.",
                    affectedHouseId = houseId,
                    affectedPlotId = targetPlotId
                )
            }
        }
    }

    override suspend fun deleteHouse(houseId: String): AdminOperationResult {
        val house = plotManager.getHouseById(houseId)
            ?: return AdminOperationResult.Failure("البيت غير موجود.")
        val plotId = house.plotId
        val removed = plotManager.removeHouseFromPlot(plotId)
        if (!removed) {
            return AdminOperationResult.Failure("تعذر حذف البيت من القطعة $plotId.")
        }
        records.remove(houseId)
        emitUpdatedSnapshot()
        return AdminOperationResult.Success(
            message = "تم حذف البيت وتحرير القطعة $plotId محليًا.",
            affectedHouseId = houseId,
            affectedPlotId = plotId
        )
    }

    private fun emitUpdatedSnapshot() {
        snapshotFlow.value = CityDataState.Success(buildSnapshot())
    }

    private fun buildSnapshot(): AdminCitySnapshot {
        val currentPlots = plotManager.plots
        val currentHouses = plotManager.houses
        val updatedScene = baseScene.copy(
            plots = currentPlots,
            houses = currentHouses
        )
        val availableCount = currentPlots.count { it.status == PlotStatus.AVAILABLE }
        val occupiedCount = currentPlots.count { it.status == PlotStatus.OCCUPIED }
        val totalResidents = currentHouses.sumOf { house ->
            val rec = records[house.id]
            if (rec == null || rec.showFamilyCountPublicly) house.familyMembersCount else 0
        }
        return AdminCitySnapshot(
            scene = updatedScene,
            plots = currentPlots,
            recordsByHouseId = records.toMap(),
            totalHouses = currentHouses.size,
            availablePlotsCount = availableCount,
            occupiedPlotsCount = occupiedCount,
            totalKnownResidents = totalResidents,
            isLocalTestData = true
        )
    }
}
