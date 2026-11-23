package com.example.core.data.local

import com.example.cityengine.model.BuildingRotation
import com.example.cityengine.model.CityLightingState
import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.House
import com.example.cityengine.model.HousePalette
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.world.PlotManager
import com.example.cityengine.world.SpatialValidator
import com.example.cityengine.world.StarterCityMap
import com.example.cityengine.world.ValidationResult
import com.example.core.data.model.AdminCitySnapshot
import com.example.core.data.model.AdminHouseDraft
import com.example.core.data.model.AdminOperationResult
import com.example.core.data.model.CityBuilderServerCapabilities
import com.example.core.data.model.CityDataState
import com.example.core.data.model.DistrictDraft
import com.example.core.data.model.EnvironmentPropDraft
import com.example.core.data.model.HouseResidentSourceRecord
import com.example.core.data.model.LightSourceDraft
import com.example.core.data.model.PlotCreateDraft
import com.example.core.data.model.PublicBuildingDraft
import com.example.core.data.model.RoadSegmentDraft
import com.example.core.data.model.TilePaintChange
import com.example.core.data.repository.CityAdminRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local in-memory administration repository for development and deterministic unit tests.
 */
class LocalCityAdminRepository(
    initiallyVacantPlotIds: Set<String> = setOf("plot_10", "plot_11", "plot_12")
) : CityAdminRepository {

    private var currentScene: CitySceneState = StarterCityMap.createScene()
    private var layoutVersion: Int = 1
    private val records = mutableMapOf<String, HouseResidentSourceRecord>()
    private var nextHouseSequence: Int = 13
    private val houseRequests = mutableListOf(
        com.example.core.data.model.HouseRequest(
            id = "req_local_01",
            applicantName = "سلطان القحطاني",
            phone = "+966551112233",
            email = "sultan@example.com",
            notes = "أرغب في بيت كلاسيكي قريب من الحديقة",
            status = com.example.core.data.model.HouseRequestStatus.NEW
        ),
        com.example.core.data.model.HouseRequest(
            id = "req_local_02",
            applicantName = "مريم الشامسي",
            phone = null,
            email = "mariam@example.com",
            notes = "يفضل تصميم فيلا حديثة في الحي الهادئ",
            status = com.example.core.data.model.HouseRequestStatus.UNDER_REVIEW
        )
    )

    private val builderDelegate = LocalCityBuilderDelegate(
        getScene = { currentScene },
        updateScene = { updated ->
            currentScene = updated
            layoutVersion++
            emitUpdatedSnapshot()
        }
    )

    private val snapshotFlow: MutableStateFlow<CityDataState<AdminCitySnapshot>>

    init {
        val defaultRecords = LocalCityExplorerRepository.defaultTestRecords(currentScene)
        defaultRecords.forEach { records[it.house.id] = it }

        val pm = createPlotManager()
        for (plotId in initiallyVacantPlotIds) {
            val plot = pm.getPlotById(plotId)
            val houseId = plot?.houseId
            if (houseId != null && pm.removeHouseFromPlot(plotId)) {
                records.remove(houseId)
            }
        }
        currentScene = currentScene.copy(plots = pm.plots, houses = pm.houses)
        snapshotFlow = MutableStateFlow(CityDataState.Success(buildSnapshot()))
    }

    private fun validator() = SpatialValidator(currentScene.gridWidth, currentScene.gridHeight, currentScene.tiles)
    private fun createPlotManager() = PlotManager(currentScene.plots, currentScene.houses, validator())

    override fun observeAdminSnapshot(): Flow<CityDataState<AdminCitySnapshot>> = snapshotFlow.asStateFlow()

    override fun validatePlotForBuild(plotId: String): ValidationResult {
        val pm = createPlotManager()
        val plot = pm.getPlotById(plotId)
            ?: return ValidationResult.Invalid("القطعة $plotId غير موجودة.")
        return validator().validateCanPlaceHouse(plot, pm.houses, pm.plots)
    }

    override fun validateCoordinateForBuild(coord: GridCoord): ValidationResult {
        val v = validator()
        if (!v.isWithinBounds(coord)) {
            return ValidationResult.Invalid("الإحداثي $coord خارج حدود الخريطة.")
        }
        val tile = currentScene.tiles[coord]
        if (tile != CityTileType.PLOT_LOT) {
            return ValidationResult.Invalid("لا يمكن البناء على الشوارع أو المساحات العامة ($tile).")
        }
        val pm = createPlotManager()
        val plot = pm.getPlotAt(coord)
            ?: return ValidationResult.Invalid("لا توجد قطعة أرض مخصصة في $coord.")
        return v.validateCanPlaceHouse(plot, pm.houses, pm.plots)
    }

    override suspend fun addHouse(draft: AdminHouseDraft): AdminOperationResult {
        val trimmedOwner = draft.ownerName.trim()
        if (trimmedOwner.isEmpty()) return AdminOperationResult.Failure("يرجى إدخال اسم صاحب البيت.")
        if (draft.familyMembersCount <= 0) return AdminOperationResult.Failure("عدد أفراد الأسرة يجب أن يكون 1 على الأقل.")

        val pm = createPlotManager()
        val targetPlot = pm.getPlotById(draft.targetPlotId)
            ?: return AdminOperationResult.Failure("قطعة الأرض المحددة غير موجودة.")

        val newId = "house_${nextHouseSequence++.toString().padStart(2, '0')}"
        val newHouse = House(
            id = newId,
            plotId = targetPlot.id,
            coord = targetPlot.coord,
            ownerName = trimmedOwner,
            familyMembersCount = draft.familyMembersCount,
            flag = draft.flag,
            palette = draft.palette,
            widthCells = targetPlot.widthCells,
            heightCells = targetPlot.heightCells,
            rotation = targetPlot.rotation,
            houseTitle = draft.houseTitle?.trim()?.takeIf { it.isNotEmpty() },
            publicStatus = draft.publicStatus?.trim()?.takeIf { it.isNotEmpty() },
            allowOwnerNamePublic = draft.allowOwnerNamePublic
        )

        return when (val validation = pm.assignHouseToPlot(newHouse)) {
            is ValidationResult.Invalid -> AdminOperationResult.Failure(validation.reason)
            is ValidationResult.Valid -> {
                records[newId] = HouseResidentSourceRecord(
                    house = newHouse,
                    rawPhoneNumber = draft.phoneNumber?.trim()?.takeIf { it.isNotEmpty() },
                    rawEmail = draft.email?.trim()?.takeIf { it.isNotEmpty() },
                    rawAdditionalPhone = draft.additionalPhone?.trim()?.takeIf { it.isNotEmpty() },
                    privateNotes = draft.privateNotes?.trim()?.takeIf { it.isNotEmpty() },
                    allowPhonePublic = draft.allowPhonePublic,
                    allowEmailPublic = draft.allowEmailPublic,
                    publicNeighborhoodRole = draft.publicNeighborhoodRole?.trim()?.takeIf { it.isNotEmpty() },
                    publicAbout = draft.publicAbout?.trim()?.takeIf { it.isNotEmpty() },
                    allowPublicInfo = draft.allowPublicInfo,
                    showFamilyCountPublicly = draft.showFamilyCountPublicly,
                    isTestData = true,
                    allowOwnerNamePublic = draft.allowOwnerNamePublic,
                    houseTitle = newHouse.houseTitle,
                    publicStatus = newHouse.publicStatus
                )
                currentScene = currentScene.copy(plots = pm.plots, houses = pm.houses)
                layoutVersion++
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
        val pm = createPlotManager()
        val existing = pm.getHouseById(houseId) ?: return AdminOperationResult.Failure("البيت غير موجود.")
        val trimmedOwner = draft.ownerName.trim()
        if (trimmedOwner.isEmpty()) return AdminOperationResult.Failure("يرجى إدخال اسم صاحب البيت.")
        if (draft.familyMembersCount <= 0) return AdminOperationResult.Failure("عدد أفراد الأسرة يجب أن يكون 1 على الأقل.")

        val updated = existing.copy(
            ownerName = trimmedOwner,
            familyMembersCount = draft.familyMembersCount,
            flag = draft.flag,
            palette = draft.palette,
            houseTitle = draft.houseTitle?.trim()?.takeIf { it.isNotEmpty() },
            publicStatus = draft.publicStatus?.trim()?.takeIf { it.isNotEmpty() },
            allowOwnerNamePublic = draft.allowOwnerNamePublic
        )

        return when (val result = pm.updateHouse(updated)) {
            is ValidationResult.Invalid -> AdminOperationResult.Failure(result.reason)
            is ValidationResult.Valid -> {
                val prevRecord = records[houseId]
                records[houseId] = (prevRecord ?: HouseResidentSourceRecord(house = updated)).copy(
                    house = updated,
                    rawPhoneNumber = draft.phoneNumber?.trim()?.takeIf { it.isNotEmpty() },
                    rawEmail = draft.email?.trim()?.takeIf { it.isNotEmpty() },
                    rawAdditionalPhone = draft.additionalPhone?.trim()?.takeIf { it.isNotEmpty() },
                    privateNotes = draft.privateNotes?.trim()?.takeIf { it.isNotEmpty() },
                    allowPhonePublic = draft.allowPhonePublic,
                    allowEmailPublic = draft.allowEmailPublic,
                    publicNeighborhoodRole = draft.publicNeighborhoodRole?.trim()?.takeIf { it.isNotEmpty() },
                    publicAbout = draft.publicAbout?.trim()?.takeIf { it.isNotEmpty() },
                    allowPublicInfo = draft.allowPublicInfo,
                    showFamilyCountPublicly = draft.showFamilyCountPublicly,
                    isTestData = true,
                    allowOwnerNamePublic = draft.allowOwnerNamePublic,
                    houseTitle = updated.houseTitle,
                    publicStatus = updated.publicStatus
                )
                currentScene = currentScene.copy(plots = pm.plots, houses = pm.houses)
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
        val pm = createPlotManager()
        val existing = pm.getHouseById(houseId) ?: return AdminOperationResult.Failure("البيت غير موجود.")
        val updated = existing.copy(palette = palette)
        return when (val result = pm.updateHouse(updated)) {
            is ValidationResult.Invalid -> AdminOperationResult.Failure(result.reason)
            is ValidationResult.Valid -> {
                val prevRecord = records[houseId]
                records[houseId] = (prevRecord ?: HouseResidentSourceRecord(house = updated)).copy(house = updated)
                currentScene = currentScene.copy(plots = pm.plots, houses = pm.houses)
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
        val pm = createPlotManager()
        val currentHouse = pm.getHouseById(houseId) ?: return AdminOperationResult.Failure("البيت غير موجود.")
        if (currentHouse.plotId == targetPlotId) return AdminOperationResult.Failure("البيت موجود بالفعل على هذه القطعة.")
        return when (val result = pm.moveHouse(houseId, targetPlotId)) {
            is ValidationResult.Invalid -> AdminOperationResult.Failure(result.reason)
            is ValidationResult.Valid -> {
                val moved = pm.getHouseById(houseId)!!
                val prevRecord = records[houseId]
                records[houseId] = (prevRecord ?: HouseResidentSourceRecord(house = moved)).copy(house = moved)
                currentScene = currentScene.copy(plots = pm.plots, houses = pm.houses)
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
        val pm = createPlotManager()
        val house = pm.getHouseById(houseId) ?: return AdminOperationResult.Failure("البيت غير موجود.")
        val plotId = house.plotId
        if (!pm.removeHouseFromPlot(plotId)) {
            return AdminOperationResult.Failure("تعذر حذف البيت من القطعة $plotId.")
        }
        records.remove(houseId)
        currentScene = currentScene.copy(plots = pm.plots, houses = pm.houses)
        emitUpdatedSnapshot()
        return AdminOperationResult.Success(
            message = "تم حذف البيت وتحرير القطعة $plotId محليًا.",
            affectedHouseId = houseId,
            affectedPlotId = plotId
        )
    }

    override suspend fun expandCityGrid(newWidth: Int, newHeight: Int) = builderDelegate.expandCityGrid(newWidth, newHeight)
    override suspend fun createPlot(draft: PlotCreateDraft) = builderDelegate.createPlot(draft)
    override suspend fun resizePlot(plotId: String, widthCells: Int, heightCells: Int) = builderDelegate.resizePlot(plotId, widthCells, heightCells)
    override suspend fun rotatePlot(plotId: String, rotation: BuildingRotation) = builderDelegate.rotatePlot(plotId, rotation)
    override suspend fun movePlot(plotId: String, newCoord: GridCoord) = builderDelegate.movePlot(plotId, newCoord)
    override suspend fun splitPlot(plotId: String, splitHorizontally: Boolean) = builderDelegate.splitPlot(plotId, splitHorizontally)
    override suspend fun mergePlots(firstPlotId: String, secondPlotId: String) = builderDelegate.mergePlots(firstPlotId, secondPlotId)
    override suspend fun setPlotAvailability(plotId: String, available: Boolean) = builderDelegate.setPlotAvailability(plotId, available)
    override suspend fun assignPlotDistrict(plotId: String, districtId: String?) = builderDelegate.assignPlotDistrict(plotId, districtId)
    override suspend fun deletePlot(plotId: String) = builderDelegate.deletePlot(plotId)
    override suspend fun batchUpdatePlotAvailability(plotIds: List<String>, available: Boolean) = builderDelegate.batchUpdatePlotAvailability(plotIds, available)
    override suspend fun batchAssignPlotsDistrict(plotIds: List<String>, districtId: String?) = builderDelegate.batchAssignPlotsDistrict(plotIds, districtId)
    override suspend fun batchDeletePlots(plotIds: List<String>) = builderDelegate.batchDeletePlots(plotIds)
    override suspend fun paintTiles(changes: List<TilePaintChange>) = builderDelegate.paintTiles(changes)
    override suspend fun upsertRoadSegment(draft: RoadSegmentDraft) = builderDelegate.upsertRoadSegment(draft)
    override suspend fun deleteRoadSegment(roadId: String, eraseTilesToGrass: Boolean) = builderDelegate.deleteRoadSegment(roadId, eraseTilesToGrass)
    override suspend fun upsertDistrict(draft: DistrictDraft) = builderDelegate.upsertDistrict(draft)
    override suspend fun deleteDistrict(districtId: String) = builderDelegate.deleteDistrict(districtId)
    override suspend fun upsertPublicBuilding(draft: PublicBuildingDraft) = builderDelegate.upsertPublicBuilding(draft)
    override suspend fun deletePublicBuilding(buildingId: String) = builderDelegate.deletePublicBuilding(buildingId)
    override suspend fun upsertEnvironmentProp(draft: EnvironmentPropDraft) = builderDelegate.upsertEnvironmentProp(draft)
    override suspend fun deleteEnvironmentProp(propId: String) = builderDelegate.deleteEnvironmentProp(propId)
    override suspend fun upsertLightSource(draft: LightSourceDraft) = builderDelegate.upsertLightSource(draft)
    override suspend fun deleteLightSource(lightId: String) = builderDelegate.deleteLightSource(lightId)
    override suspend fun setLightingState(lightingState: CityLightingState) = builderDelegate.setLightingState(lightingState)

    override suspend fun updateHouseRequestStatus(
        requestId: String,
        status: com.example.core.data.model.HouseRequestStatus,
        adminNotes: String?,
        targetPlotId: String?
    ): AdminOperationResult {
        val idx = houseRequests.indexOfFirst { it.id == requestId }
        if (idx < 0) return AdminOperationResult.Failure("الطلب غير موجود.")
        val current = houseRequests[idx]
        if (status != current.status && status !in current.status.allowedNextStatuses()) {
            return AdminOperationResult.Failure("لا يمكن الانتقال من (${current.status.labelAr}) إلى (${status.labelAr}).")
        }
        houseRequests[idx] = current.copy(
            status = status,
            adminNotes = adminNotes?.trim()?.takeIf { it.isNotEmpty() } ?: current.adminNotes,
            targetPlotId = targetPlotId ?: current.targetPlotId
        )
        emitUpdatedSnapshot()
        return AdminOperationResult.Success(
            message = "تم تحديث حالة الطلب إلى ${status.labelAr}.",
            affectedRequestId = requestId,
            affectedPlotId = targetPlotId
        )
    }

    override suspend fun assignRequestPlotAndBuildHouse(
        requestId: String,
        houseDraft: AdminHouseDraft
    ): AdminOperationResult {
        val idx = houseRequests.indexOfFirst { it.id == requestId }
        if (idx < 0) return AdminOperationResult.Failure("الطلب غير موجود.")
        val addRes = addHouse(houseDraft)
        if (addRes is AdminOperationResult.Failure) return addRes
        val success = addRes as AdminOperationResult.Success
        val current = houseRequests[idx]
        houseRequests[idx] = current.copy(
            status = com.example.core.data.model.HouseRequestStatus.COMPLETED,
            targetPlotId = houseDraft.targetPlotId,
            assignedHouseId = success.affectedHouseId
        )
        emitUpdatedSnapshot()
        return AdminOperationResult.Success(
            message = "تم ربط الطلب بالقطعة وبناء المنزل بنجاح.",
            affectedHouseId = success.affectedHouseId,
            affectedPlotId = houseDraft.targetPlotId,
            affectedRequestId = requestId
        )
    }

    private fun emitUpdatedSnapshot() {
        snapshotFlow.value = CityDataState.Success(buildSnapshot())
    }

    private fun buildSnapshot(): AdminCitySnapshot {
        val currentPlots = currentScene.plots
        val currentHouses = currentScene.houses
        val availableCount = currentPlots.count { it.status == PlotStatus.AVAILABLE }
        val occupiedCount = currentPlots.count { it.status == PlotStatus.OCCUPIED }
        val totalResidents = currentHouses.sumOf { house ->
            val rec = records[house.id]
            if (rec == null || rec.showFamilyCountPublicly) house.familyMembersCount else 0
        }
        return AdminCitySnapshot(
            scene = currentScene,
            plots = currentPlots,
            recordsByHouseId = records.toMap(),
            totalHouses = currentHouses.size,
            availablePlotsCount = availableCount,
            occupiedPlotsCount = occupiedCount,
            totalKnownResidents = totalResidents,
            isLocalTestData = true,
            layoutVersion = layoutVersion,
            builderCapabilities = CityBuilderServerCapabilities(
                builderTablesAvailable = true,
                customLayoutColumnsAvailable = true
            ),
            houseRequests = houseRequests.toList()
        )
    }
}
