package com.example.core.data.supabase

import com.example.cityengine.model.BuildingRotation
import com.example.cityengine.model.CityLightingState
import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.GridFootprint
import com.example.cityengine.model.HousePalette
import com.example.cityengine.world.SpatialValidator
import com.example.cityengine.world.ValidationResult
import com.example.core.data.model.AdminCitySnapshot
import com.example.core.data.model.AdminHouseDraft
import com.example.core.data.model.AdminOperationResult
import com.example.core.data.model.CityBuilderServerCapabilities
import com.example.core.data.model.CityDataState
import com.example.core.data.model.DistrictDraft
import com.example.core.data.model.EnvironmentPropDraft
import com.example.core.data.model.HouseRequestStatus
import com.example.core.data.model.LightSourceDraft
import com.example.core.data.model.PlotCreateDraft
import com.example.core.data.model.PublicBuildingDraft
import com.example.core.data.model.RoadSegmentDraft
import com.example.core.data.model.TilePaintChange
import com.example.core.data.repository.CityAdminRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.decodeRecord
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Production Supabase administration repository for :app-admin.
 * Executes mutations via transactional PostgreSQL RPCs & RLS-guarded tables,
 * supports forward-migrated City Builder capabilities safely, and syncs via Realtime.
 */
class SupabaseCityAdminRepository(
    private val config: SupabaseConfig,
    private val authRepository: AdminAuthManager,
    private val clientProvider: () -> SupabaseClient? = {
        SupabaseClientProvider.getOrCreateAdminClient(config)
    },
    private val connectivityChecker: ConnectivityChecker = AlwaysOnlineChecker,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val enableRealtime: Boolean = true
) : CityAdminRepository {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val stateMutex = Mutex()

    private var cachedLayout: CityLayoutDto? = null
    private val plotsById = mutableMapOf<String, PlotDto>()
    private val housesById = mutableMapOf<String, HouseAdminDto>()
    private val privateByHouseId = mutableMapOf<String, HousePrivateDetailsDto>()
    private val districtsById = mutableMapOf<String, DistrictDto>()
    private val buildingsById = mutableMapOf<String, PublicBuildingDto>()
    private val propsById = mutableMapOf<String, EnvironmentPropDto>()
    private val lightsById = mutableMapOf<String, CityLightDto>()
    private val requestsById = mutableMapOf<String, HouseRequestDto>()
    private var serverCapabilities = CityBuilderServerCapabilities()
    private var lastSnapshot: AdminCitySnapshot? = null

    private val snapshotFlow = MutableStateFlow<CityDataState<AdminCitySnapshot>>(CityDataState.Loading)

    private val builderDelegate = SupabaseCityBuilderDelegate(
        preflightGuard = ::preflightSensitiveMutationGuard,
        clientProvider = clientProvider,
        snapshotProvider = { lastSnapshot },
        layoutDtoProvider = { cachedLayout },
        plotDtoProvider = { plotsById[it] },
        reloadFromServer = ::reloadFullStateFromServer,
        applyLayoutLocally = { updated ->
            stateMutex.withLock { cachedLayout = updated; emitSnapshotLocked() }
        },
        handleFailure = ::handleMutationFailure
    )

    private val realtimeSync = SupabaseAdminRealtimeSync(
        scope = scope,
        connectivityChecker = connectivityChecker,
        capabilitiesProvider = { serverCapabilities },
        onRefreshSingleHouse = ::refreshSingleHouseFromServer,
        onHouseDeleted = { houseId ->
            stateMutex.withLock { housesById.remove(houseId); privateByHouseId.remove(houseId); emitSnapshotLocked() }
        },
        onAdminHouseUpsert = { dto -> stateMutex.withLock { housesById[dto.id] = dto; emitSnapshotLocked() } },
        onAdminHouseDelete = { id ->
            stateMutex.withLock { housesById.remove(id); privateByHouseId.remove(id); emitSnapshotLocked() }
        },
        onPrivateDetailsUpsert = { dto -> stateMutex.withLock { privateByHouseId[dto.houseId] = dto; emitSnapshotLocked() } },
        onPrivateDetailsDelete = { houseId -> stateMutex.withLock { privateByHouseId.remove(houseId); emitSnapshotLocked() } },
        onPlotUpsert = { dto -> stateMutex.withLock { plotsById[dto.id] = dto; emitSnapshotLocked() } },
        onPlotDelete = { id -> stateMutex.withLock { plotsById.remove(id); emitSnapshotLocked() } },
        onLayoutUpdate = { dto -> stateMutex.withLock { cachedLayout = dto; emitSnapshotLocked() } },
        onDistrictAction = { handleBuilderMapAction(districtsById, it) },
        onBuildingAction = { handleBuilderMapAction(buildingsById, it) },
        onPropAction = { handleBuilderMapAction(propsById, it) },
        onLightAction = { handleBuilderMapAction(lightsById, it) },
        onOfflineDetected = {
            lastSnapshot?.let { stale ->
                snapshotFlow.value = CityDataState.Offline("انقطع الاتصال بالإنترنت؛ آخر بيانات معروضة قد لا تكون محدّثة.", staleData = stale)
            }
        },
        onHouseRequestAction = { handleBuilderMapAction(requestsById, it) }
    )

    init {
        scope.launch {
            authRepository.authState.collect { auth ->
                if (auth is AdminAuthState.AuthenticatedAdmin) {
                    loadInitialAndSubscribe()
                } else {
                    realtimeSync.stop()
                    clearInMemoryState()
                }
            }
        }
    }

    override fun observeAdminSnapshot(): Flow<CityDataState<AdminCitySnapshot>> = snapshotFlow.asStateFlow()

    override suspend fun refresh() {
        val guardError = preflightSensitiveMutationGuard()
        if (guardError != null) {
            snapshotFlow.value = CityDataState.Error(guardError)
            return
        }
        loadInitialAndSubscribe()
    }

    override fun validatePlotForBuild(plotId: String): ValidationResult {
        val snap = lastSnapshot ?: return ValidationResult.Invalid("بيانات المدينة غير محمّلة بعد.")
        val plot = snap.plots.firstOrNull { it.id == plotId }
            ?: return ValidationResult.Invalid("القطعة $plotId غير موجودة.")
        val validator = SpatialValidator(snap.scene.gridWidth, snap.scene.gridHeight, snap.scene.tiles)
        return validator.validateCanPlaceHouse(plot, snap.scene.houses, snap.plots)
    }

    override fun validateCoordinateForBuild(coord: GridCoord): ValidationResult {
        val snap = lastSnapshot ?: return ValidationResult.Invalid("بيانات المدينة غير محمّلة بعد.")
        val validator = SpatialValidator(snap.scene.gridWidth, snap.scene.gridHeight, snap.scene.tiles)
        if (!validator.isWithinBounds(coord)) return ValidationResult.Invalid("الإحداثي $coord خارج حدود الخريطة.")
        if (snap.scene.tiles[coord] != CityTileType.PLOT_LOT) {
            return ValidationResult.Invalid("لا يمكن البناء على الشوارع أو المساحات العامة.")
        }
        val plot = snap.plots.firstOrNull {
            GridFootprint(it.coord, it.widthCells, it.heightCells, it.rotation).contains(coord)
        } ?: return ValidationResult.Invalid("لا توجد قطعة أرض مخصصة في $coord.")
        return validator.validateCanPlaceHouse(plot, snap.scene.houses, snap.plots)
    }

    override suspend fun addHouse(draft: AdminHouseDraft): AdminOperationResult {
        preflightSensitiveMutationGuard()?.let { return AdminOperationResult.Failure(it) }
        AdminRpcPayloadBuilder.validateDraftInputs(draft)?.let { return AdminOperationResult.Failure(it) }
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return try {
            val params = AdminRpcPayloadBuilder.buildHouseRpcParams(draft, "p_target_plot_id", draft.targetPlotId)
            val res = client.postgrest.rpc("admin_add_house", params).decodeAs<RpcMutationResultDto>()
            val newHouseId = res.houseId
            if (newHouseId != null && (draft.houseTitle != null || draft.publicStatus != null || !draft.allowOwnerNamePublic)) {
                syncHousePublicVisibilityBestEffort(client, newHouseId, draft)
            }
            refreshHousesAndPlotsFromServer(client)
            AdminOperationResult.Success("تم حفظ البيت على الخادم في القطعة ${res.plotId ?: draft.targetPlotId}.", newHouseId, res.plotId ?: draft.targetPlotId)
        } catch (t: Throwable) {
            handleMutationFailure(t, "فشل إضافة البيت على الخادم.")
        }
    }

    override suspend fun updateHouse(houseId: String, draft: AdminHouseDraft): AdminOperationResult {
        preflightSensitiveMutationGuard()?.let { return AdminOperationResult.Failure(it) }
        AdminRpcPayloadBuilder.validateDraftInputs(draft)?.let { return AdminOperationResult.Failure(it) }
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return try {
            val res = client.postgrest.rpc("admin_update_house", AdminRpcPayloadBuilder.buildHouseRpcParams(draft, "p_house_id", houseId)).decodeAs<RpcMutationResultDto>()
            syncHousePublicVisibilityBestEffort(client, houseId, draft)
            refreshSingleHouseFromServer(client, houseId)
            AdminOperationResult.Success("تم تحديث بيانات البيت وحفظها على الخادم.", houseId, res.plotId)
        } catch (t: Throwable) {
            handleMutationFailure(t, "فشل تحديث بيانات البيت على الخادم.")
        }
    }

    override suspend fun changeHouseColor(houseId: String, palette: HousePalette): AdminOperationResult {
        preflightSensitiveMutationGuard()?.let { return AdminOperationResult.Failure(it) }
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return try {
            val params = buildJsonObject {
                put("p_house_id", houseId); put("p_house_color", SupabaseMapper.resolveColorKey(palette))
                put("p_palette_json", SupabaseClientProvider.json.encodeToJsonElement(SupabaseMapper.toPaletteDto(palette)))
            }
            val res = client.postgrest.rpc("admin_change_house_color", params).decodeAs<RpcMutationResultDto>()
            refreshSingleHouseFromServer(client, houseId)
            AdminOperationResult.Success("تم حفظ اللون الجديد على الخادم.", houseId, res.plotId)
        } catch (t: Throwable) { handleMutationFailure(t, "فشل تغيير لون البيت على الخادم.") }
    }

    override suspend fun moveHouse(houseId: String, targetPlotId: String): AdminOperationResult {
        preflightSensitiveMutationGuard()?.let { return AdminOperationResult.Failure(it) }
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return try {
            val params = buildJsonObject { put("p_house_id", houseId); put("p_target_plot_id", targetPlotId) }
            val res = client.postgrest.rpc("admin_move_house", params).decodeAs<RpcMutationResultDto>()
            refreshHousesAndPlotsFromServer(client)
            AdminOperationResult.Success("تم نقل البيت إلى $targetPlotId وحفظ التغيير على الخادم.", houseId, res.plotId ?: targetPlotId)
        } catch (t: Throwable) { handleMutationFailure(t, "فشل نقل البيت على الخادم.") }
    }

    override suspend fun deleteHouse(houseId: String): AdminOperationResult {
        preflightSensitiveMutationGuard()?.let { return AdminOperationResult.Failure(it) }
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return try {
            val res = client.postgrest.rpc("admin_delete_house", buildJsonObject { put("p_house_id", houseId) }).decodeAs<RpcMutationResultDto>()
            refreshHousesAndPlotsFromServer(client)
            AdminOperationResult.Success("تم حذف البيت وتحرير القطعة ${res.plotId.orEmpty()} على الخادم.", houseId, res.plotId)
        } catch (t: Throwable) { handleMutationFailure(t, "فشل حذف البيت من الخادم.") }
    }

    override suspend fun expandCityGrid(newWidth: Int, newHeight: Int) = builderDelegate.plotLayoutDelegate.expandCityGrid(newWidth, newHeight)
    override suspend fun createPlot(draft: PlotCreateDraft) = builderDelegate.plotLayoutDelegate.createPlot(draft)
    override suspend fun resizePlot(plotId: String, widthCells: Int, heightCells: Int) = builderDelegate.plotLayoutDelegate.resizePlot(plotId, widthCells, heightCells)
    override suspend fun rotatePlot(plotId: String, rotation: BuildingRotation) = builderDelegate.plotLayoutDelegate.rotatePlot(plotId, rotation)
    override suspend fun movePlot(plotId: String, newCoord: GridCoord) = builderDelegate.plotLayoutDelegate.movePlot(plotId, newCoord)
    override suspend fun splitPlot(plotId: String, splitHorizontally: Boolean) = builderDelegate.plotLayoutDelegate.splitPlot(plotId, splitHorizontally)
    override suspend fun mergePlots(firstPlotId: String, secondPlotId: String) = builderDelegate.plotLayoutDelegate.mergePlots(firstPlotId, secondPlotId)
    override suspend fun setPlotAvailability(plotId: String, available: Boolean) = builderDelegate.plotLayoutDelegate.setPlotAvailability(plotId, available)
    override suspend fun assignPlotDistrict(plotId: String, districtId: String?) = builderDelegate.plotLayoutDelegate.assignPlotDistrict(plotId, districtId)
    override suspend fun deletePlot(plotId: String) = builderDelegate.plotLayoutDelegate.deletePlot(plotId)
    override suspend fun batchUpdatePlotAvailability(plotIds: List<String>, available: Boolean) = builderDelegate.plotLayoutDelegate.batchUpdatePlotAvailability(plotIds, available)
    override suspend fun batchAssignPlotsDistrict(plotIds: List<String>, districtId: String?) = builderDelegate.plotLayoutDelegate.batchAssignPlotsDistrict(plotIds, districtId)
    override suspend fun batchDeletePlots(plotIds: List<String>) = builderDelegate.plotLayoutDelegate.batchDeletePlots(plotIds)
    override suspend fun paintTiles(changes: List<TilePaintChange>) = builderDelegate.plotLayoutDelegate.paintTiles(changes)
    override suspend fun upsertRoadSegment(draft: RoadSegmentDraft) = builderDelegate.plotLayoutDelegate.upsertRoadSegment(draft)
    override suspend fun deleteRoadSegment(roadId: String, eraseTilesToGrass: Boolean) = builderDelegate.plotLayoutDelegate.deleteRoadSegment(roadId, eraseTilesToGrass)
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
        status: HouseRequestStatus,
        adminNotes: String?,
        targetPlotId: String?
    ): AdminOperationResult {
        preflightSensitiveMutationGuard()?.let { return AdminOperationResult.Failure(it) }
        val existing = requestsById[requestId]
        if (existing != null) {
            val currentStatus = HouseRequestStatus.fromServer(existing.status)
            if (status != currentStatus && status !in currentStatus.allowedNextStatuses()) {
                return AdminOperationResult.Failure(
                    "لا يمكن الانتقال من (${currentStatus.labelAr}) إلى (${status.labelAr})."
                )
            }
        }
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return try {
            val params = AdminRpcPayloadBuilder.buildRequestStatusParams(requestId, status.name, adminNotes, targetPlotId)
            client.postgrest.rpc("admin_update_house_request_status", params).decodeAs<RpcMutationResultDto>()
            refreshRequestsFromServer(client)
            AdminOperationResult.Success(
                message = "تم تحديث حالة الطلب إلى ${status.labelAr}.",
                affectedPlotId = targetPlotId,
                affectedRequestId = requestId
            )
        } catch (t: Throwable) {
            handleMutationFailure(t, "تعذر تحديث حالة طلب البناء.")
        }
    }

    override suspend fun assignRequestPlotAndBuildHouse(
        requestId: String,
        houseDraft: AdminHouseDraft
    ): AdminOperationResult {
        preflightSensitiveMutationGuard()?.let { return AdminOperationResult.Failure(it) }
        val plotCheck = validatePlotForBuild(houseDraft.targetPlotId)
        if (plotCheck is ValidationResult.Invalid) {
            return AdminOperationResult.Failure(plotCheck.reason)
        }
        val addRes = addHouse(houseDraft)
        if (addRes is AdminOperationResult.Failure) return addRes
        val success = addRes as AdminOperationResult.Success
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return try {
            val params = AdminRpcPayloadBuilder.buildAssignRequestParams(
                requestId, houseDraft.targetPlotId, success.affectedHouseId, HouseRequestStatus.COMPLETED.name,
                "تم تخصيص الأرض ${houseDraft.targetPlotId} وبناء المنزل."
            )
            client.postgrest.rpc("admin_assign_request_plot_and_house", params).decodeAs<RpcMutationResultDto>()
            refreshRequestsFromServer(client)
            AdminOperationResult.Success(
                message = "تم ربط الطلب بالقطعة ${houseDraft.targetPlotId} وبناء المنزل بنجاح.",
                affectedHouseId = success.affectedHouseId,
                affectedPlotId = houseDraft.targetPlotId,
                affectedRequestId = requestId
            )
        } catch (t: Throwable) {
            handleMutationFailure(t, "تم بناء المنزل ولكن تعذر تحديث حالة الطلب المرتبط.")
        }
    }

    private suspend fun preflightSensitiveMutationGuard(): String? =
        authRepository.ensureAuthenticatedAdminForMutation()

    private suspend fun handleMutationFailure(throwable: Throwable, fallback: String): AdminOperationResult.Failure {
        val translated = SupabaseErrorTranslator.toArabicMessage(throwable, fallback)
        if (authRepository.isAuthorizationOrSessionError(throwable)) {
            authRepository.handleAuthorizationOrSessionFailure(throwable, translated)
        }
        return AdminOperationResult.Failure(translated)
    }

    private suspend fun loadInitialAndSubscribe() {
        config.validationErrorOrNull()?.let { snapshotFlow.value = CityDataState.Error(it); return }
        if (!authRepository.isAuthenticatedAdmin()) {
            snapshotFlow.value = CityDataState.Error("يجب تسجيل الدخول بحساب الإدارة أولًا."); return
        }
        if (!connectivityChecker.isOnline()) {
            snapshotFlow.value = CityDataState.Offline("لا يوجد اتصال بالإنترنت.", staleData = lastSnapshot); return
        }
        val client = clientProvider() ?: run {
            snapshotFlow.value = CityDataState.Error("تعذر تهيئة عميل Supabase."); return
        }
        if (lastSnapshot == null) snapshotFlow.value = CityDataState.Loading

        try {
            reloadFullStateFromServer(client)
            if (enableRealtime) realtimeSync.startIfNeeded(client)
        } catch (t: Throwable) {
            if (authRepository.isAuthorizationOrSessionError(t)) {
                val msg = SupabaseErrorTranslator.toArabicMessage(t, SupabaseAdminAuthRepository.DEFAULT_ACCESS_DENIED_MESSAGE)
                authRepository.handleAuthorizationOrSessionFailure(t, msg)
                snapshotFlow.value = CityDataState.Error(msg)
                return
            }
            val stale = lastSnapshot
            if (SupabaseErrorTranslator.isNetworkError(t) || !connectivityChecker.isOnline()) {
                snapshotFlow.value = CityDataState.Offline("تعذر الاتصال بخادم الإدارة. تحقق من الإنترنت.", staleData = stale)
            } else {
                snapshotFlow.value = CityDataState.Error(SupabaseErrorTranslator.toArabicMessage(t, "تعذر تحميل بيانات الإدارة من Supabase."))
            }
        }
    }

    private suspend fun reloadFullStateFromServer(client: SupabaseClient) {
        val layout = client.from("city_layout").select().decodeList<CityLayoutDto>().firstOrNull()
        val plots = client.from("plots").select().decodeList<PlotDto>()
        val houses = client.from("houses").select().decodeList<HouseAdminDto>()
        val privates = client.from("house_private_details").select().decodeList<HousePrivateDetailsDto>()

        if (layout == null) {
            stateMutex.withLock { cachedLayout = null; snapshotFlow.value = CityDataState.Empty }
            return
        }

        var builderTablesOk = true
        val districts = runCatching { client.from("city_districts").select().decodeList<DistrictDto>() }
            .getOrElse { builderTablesOk = false; emptyList() }
        val buildings = runCatching { client.from("city_buildings").select().decodeList<PublicBuildingDto>() }
            .getOrElse { builderTablesOk = false; emptyList() }
        val props = runCatching { client.from("city_environment_props").select().decodeList<EnvironmentPropDto>() }
            .getOrElse { builderTablesOk = false; emptyList() }
        val lights = runCatching { client.from("city_lights").select().decodeList<CityLightDto>() }
            .getOrElse { builderTablesOk = false; emptyList() }
        val requests = runCatching { client.from("house_requests").select().decodeList<HouseRequestDto>() }
            .getOrElse { emptyList() }

        val customColsOk = builderTablesOk || layout.customTiles.isNotEmpty() || layout.roadSegments.isNotEmpty()
        stateMutex.withLock {
            cachedLayout = layout
            serverCapabilities = CityBuilderServerCapabilities(builderTablesOk, customColsOk)
            plotsById.clear(); plots.forEach { plotsById[it.id] = it }
            housesById.clear(); houses.forEach { housesById[it.id] = it }
            privateByHouseId.clear(); privates.forEach { privateByHouseId[it.houseId] = it }
            districtsById.clear(); districts.forEach { districtsById[it.id] = it }
            buildingsById.clear(); buildings.forEach { buildingsById[it.id] = it }
            propsById.clear(); props.forEach { propsById[it.id] = it }
            lightsById.clear(); lights.forEach { lightsById[it.id] = it }
            requestsById.clear(); requests.forEach { requestsById[it.id] = it }
            emitSnapshotLocked()
        }
    }

    private suspend fun syncHousePublicVisibilityBestEffort(client: SupabaseClient, houseId: String, draft: AdminHouseDraft) {
        runCatching {
            client.postgrest.rpc("admin_set_house_public_visibility", AdminRpcPayloadBuilder.buildPublicVisibilityParams(houseId, draft))
        }
    }

    private suspend fun refreshRequestsFromServer(client: SupabaseClient) {
        val requests = runCatching { client.from("house_requests").select().decodeList<HouseRequestDto>() }
            .getOrElse { return }
        stateMutex.withLock {
            requestsById.clear(); requests.forEach { requestsById[it.id] = it }
            emitSnapshotLocked()
        }
    }

    private suspend fun refreshHousesAndPlotsFromServer(client: SupabaseClient) {
        val plots = client.from("plots").select().decodeList<PlotDto>()
        val houses = client.from("houses").select().decodeList<HouseAdminDto>()
        val privates = client.from("house_private_details").select().decodeList<HousePrivateDetailsDto>()
        stateMutex.withLock {
            plotsById.clear(); plots.forEach { plotsById[it.id] = it }
            housesById.clear(); houses.forEach { housesById[it.id] = it }
            privateByHouseId.clear(); privates.forEach { privateByHouseId[it.houseId] = it }
            emitSnapshotLocked()
        }
    }

    private suspend fun refreshSingleHouseFromServer(client: SupabaseClient, houseId: String) {
        val house = client.from("houses").select { filter { eq("id", houseId) } }.decodeSingleOrNull<HouseAdminDto>()
        val priv = client.from("house_private_details").select { filter { eq("house_id", houseId) } }.decodeSingleOrNull<HousePrivateDetailsDto>()
        stateMutex.withLock {
            if (house != null) housesById[house.id] = house else housesById.remove(houseId)
            if (priv != null) privateByHouseId[houseId] = priv else privateByHouseId.remove(houseId)
            emitSnapshotLocked()
        }
    }

    private suspend inline fun <reified T : Any> handleBuilderMapAction(map: MutableMap<String, T>, action: PostgresAction) {
        stateMutex.withLock {
            when (action) {
                is PostgresAction.Insert, is PostgresAction.Update -> {
                    val dto = action.decodeRecord<T>()
                    val id = action.record["id"]?.jsonPrimitive?.content ?: return
                    map[id] = dto
                }
                is PostgresAction.Delete -> action.oldRecord["id"]?.jsonPrimitive?.content?.let { map.remove(it) }
                else -> Unit
            }
            emitSnapshotLocked()
        }
    }

    internal suspend fun handleAdminHouseAction(action: PostgresAction) = realtimeSync.handleAdminHouseAction(action)
    internal suspend fun handlePlotAction(action: PostgresAction) = realtimeSync.handlePlotAction(action)
    internal suspend fun handlePrivateDetailsAction(action: PostgresAction) = realtimeSync.handlePrivateDetailsAction(action)

    private suspend fun clearInMemoryState() {
        stateMutex.withLock {
            cachedLayout = null
            plotsById.clear(); housesById.clear(); privateByHouseId.clear()
            districtsById.clear(); buildingsById.clear(); propsById.clear(); lightsById.clear()
            requestsById.clear()
            lastSnapshot = null
            snapshotFlow.value = CityDataState.Loading
        }
    }

    private fun emitSnapshotLocked() {
        val layout = cachedLayout ?: run { snapshotFlow.value = CityDataState.Empty; return }
        val snapshot = SupabaseMapper.buildAdminSnapshot(
            layout = layout,
            plotDtos = plotsById.values.toList(),
            houseDtos = housesById.values.toList(),
            privateDetailsByHouseId = privateByHouseId.toMap(),
            districts = districtsById.values.toList(),
            buildings = buildingsById.values.toList(),
            props = propsById.values.toList(),
            lights = lightsById.values.toList(),
            builderCapabilities = serverCapabilities,
            houseRequests = requestsById.values.toList()
        )
        lastSnapshot = snapshot
        snapshotFlow.value = CityDataState.Success(snapshot)
    }

    fun close() = realtimeSync.stop()
}
