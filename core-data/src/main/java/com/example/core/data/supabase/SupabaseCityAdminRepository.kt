package com.example.core.data.supabase

import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.HousePalette
import com.example.cityengine.world.SpatialValidator
import com.example.cityengine.world.ValidationResult
import com.example.core.data.model.AdminCitySnapshot
import com.example.core.data.model.AdminHouseDraft
import com.example.core.data.model.AdminOperationResult
import com.example.core.data.model.CityDataState
import com.example.core.data.repository.CityAdminRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Production Supabase administration repository for :app-admin.
 * Executes all mutations via transactional PostgreSQL RPCs guarded by server-side is_admin() & RLS
 * (verified via Supabase Auth JWT + profiles.role = 'ADMIN'), manages session verification/refresh
 * before sensitive mutations, and keeps the admin scene synchronized via Realtime.
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
    private var lastSnapshot: AdminCitySnapshot? = null

    private var realtimeJob: Job? = null
    private var activeChannel: RealtimeChannel? = null

    private val snapshotFlow = MutableStateFlow<CityDataState<AdminCitySnapshot>>(CityDataState.Loading)

    init {
        scope.launch {
            authRepository.authState.collect { auth ->
                if (auth is AdminAuthState.AuthenticatedAdmin) {
                    loadInitialAndSubscribe()
                } else {
                    stopRealtime()
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
        if (!validator.isWithinBounds(coord)) {
            return ValidationResult.Invalid("الإحداثي $coord خارج حدود الخريطة.")
        }
        if (snap.scene.tiles[coord] != CityTileType.PLOT_LOT) {
            return ValidationResult.Invalid("لا يمكن البناء على الشوارع أو المساحات العامة.")
        }
        val plot = snap.plots.firstOrNull { it.coord == coord }
            ?: return ValidationResult.Invalid("لا توجد قطعة أرض مخصصة في $coord.")
        return validator.validateCanPlaceHouse(plot, snap.scene.houses, snap.plots)
    }

    override suspend fun addHouse(draft: AdminHouseDraft): AdminOperationResult {
        val guardError = preflightSensitiveMutationGuard()
        if (guardError != null) return AdminOperationResult.Failure(guardError)

        val trimmedOwner = draft.ownerName.trim()
        if (trimmedOwner.isEmpty()) {
            return AdminOperationResult.Failure("يرجى إدخال اسم صاحب البيت.")
        }
        if (draft.familyMembersCount <= 0) {
            return AdminOperationResult.Failure("عدد أفراد الأسرة يجب أن يكون 1 على الأقل.")
        }

        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")

        return try {
            val params = buildJsonObject {
                put("p_target_plot_id", draft.targetPlotId)
                put("p_resident_name", trimmedOwner)
                put("p_country_code", draft.flag.countryCode)
                put("p_country", draft.flag.countryName)
                put(
                    "p_flag_stripes",
                    buildJsonArray { draft.flag.stripeColors.forEach { add(it) } }
                )
                put("p_flag_vertical", draft.flag.isVerticalStripes)
                put("p_family_size", draft.familyMembersCount)
                put("p_house_color", SupabaseMapper.resolveColorKey(draft.palette))
                put(
                    "p_palette_json",
                    SupabaseClientProvider.json.encodeToJsonElement(SupabaseMapper.toPaletteDto(draft.palette))
                )
                put("p_public_role", draft.publicNeighborhoodRole?.trim()?.takeIf { it.isNotEmpty() })
                put("p_public_about", draft.publicAbout?.trim()?.takeIf { it.isNotEmpty() })
                put("p_allow_public_info", draft.allowPublicInfo)
                put("p_show_family_count_publicly", draft.showFamilyCountPublicly)
                put("p_phone", draft.phoneNumber?.trim()?.takeIf { it.isNotEmpty() })
                put("p_email", draft.email?.trim()?.takeIf { it.isNotEmpty() })
                put("p_additional_phone", draft.additionalPhone?.trim()?.takeIf { it.isNotEmpty() })
                put("p_private_notes", draft.privateNotes?.trim()?.takeIf { it.isNotEmpty() })
                put("p_allow_phone_public", draft.allowPhonePublic)
                put("p_allow_email_public", draft.allowEmailPublic)
            }
            val res = client.postgrest.rpc("admin_add_house", params).decodeAs<RpcMutationResultDto>()
            refreshHousesAndPlotsFromServer(client)
            AdminOperationResult.Success(
                message = "تم حفظ البيت على الخادم في القطعة ${res.plotId ?: draft.targetPlotId}.",
                affectedHouseId = res.houseId,
                affectedPlotId = res.plotId ?: draft.targetPlotId
            )
        } catch (t: Throwable) {
            handleMutationFailure(t, "فشل إضافة البيت على الخادم.")
        }
    }

    override suspend fun updateHouse(houseId: String, draft: AdminHouseDraft): AdminOperationResult {
        val guardError = preflightSensitiveMutationGuard()
        if (guardError != null) return AdminOperationResult.Failure(guardError)

        val trimmedOwner = draft.ownerName.trim()
        if (trimmedOwner.isEmpty()) {
            return AdminOperationResult.Failure("يرجى إدخال اسم صاحب البيت.")
        }
        if (draft.familyMembersCount <= 0) {
            return AdminOperationResult.Failure("عدد أفراد الأسرة يجب أن يكون 1 على الأقل.")
        }

        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")

        return try {
            val params = buildJsonObject {
                put("p_house_id", houseId)
                put("p_resident_name", trimmedOwner)
                put("p_country_code", draft.flag.countryCode)
                put("p_country", draft.flag.countryName)
                put(
                    "p_flag_stripes",
                    buildJsonArray { draft.flag.stripeColors.forEach { add(it) } }
                )
                put("p_flag_vertical", draft.flag.isVerticalStripes)
                put("p_family_size", draft.familyMembersCount)
                put("p_house_color", SupabaseMapper.resolveColorKey(draft.palette))
                put(
                    "p_palette_json",
                    SupabaseClientProvider.json.encodeToJsonElement(SupabaseMapper.toPaletteDto(draft.palette))
                )
                put("p_public_role", draft.publicNeighborhoodRole?.trim()?.takeIf { it.isNotEmpty() })
                put("p_public_about", draft.publicAbout?.trim()?.takeIf { it.isNotEmpty() })
                put("p_allow_public_info", draft.allowPublicInfo)
                put("p_show_family_count_publicly", draft.showFamilyCountPublicly)
                put("p_phone", draft.phoneNumber?.trim()?.takeIf { it.isNotEmpty() })
                put("p_email", draft.email?.trim()?.takeIf { it.isNotEmpty() })
                put("p_additional_phone", draft.additionalPhone?.trim()?.takeIf { it.isNotEmpty() })
                put("p_private_notes", draft.privateNotes?.trim()?.takeIf { it.isNotEmpty() })
                put("p_allow_phone_public", draft.allowPhonePublic)
                put("p_allow_email_public", draft.allowEmailPublic)
            }
            val res = client.postgrest.rpc("admin_update_house", params).decodeAs<RpcMutationResultDto>()
            refreshSingleHouseFromServer(client, houseId)
            AdminOperationResult.Success(
                message = "تم تحديث بيانات البيت وحفظها على الخادم.",
                affectedHouseId = houseId,
                affectedPlotId = res.plotId
            )
        } catch (t: Throwable) {
            handleMutationFailure(t, "فشل تحديث بيانات البيت على الخادم.")
        }
    }

    override suspend fun changeHouseColor(houseId: String, palette: HousePalette): AdminOperationResult {
        val guardError = preflightSensitiveMutationGuard()
        if (guardError != null) return AdminOperationResult.Failure(guardError)

        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")

        return try {
            val params = buildJsonObject {
                put("p_house_id", houseId)
                put("p_house_color", SupabaseMapper.resolveColorKey(palette))
                put(
                    "p_palette_json",
                    SupabaseClientProvider.json.encodeToJsonElement(SupabaseMapper.toPaletteDto(palette))
                )
            }
            val res = client.postgrest.rpc("admin_change_house_color", params).decodeAs<RpcMutationResultDto>()
            refreshSingleHouseFromServer(client, houseId)
            AdminOperationResult.Success(
                message = "تم حفظ اللون الجديد على الخادم.",
                affectedHouseId = houseId,
                affectedPlotId = res.plotId
            )
        } catch (t: Throwable) {
            handleMutationFailure(t, "فشل تغيير لون البيت على الخادم.")
        }
    }

    override suspend fun moveHouse(houseId: String, targetPlotId: String): AdminOperationResult {
        val guardError = preflightSensitiveMutationGuard()
        if (guardError != null) return AdminOperationResult.Failure(guardError)

        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")

        return try {
            val params = buildJsonObject {
                put("p_house_id", houseId)
                put("p_target_plot_id", targetPlotId)
            }
            val res = client.postgrest.rpc("admin_move_house", params).decodeAs<RpcMutationResultDto>()
            refreshHousesAndPlotsFromServer(client)
            AdminOperationResult.Success(
                message = "تم نقل البيت إلى $targetPlotId وحفظ التغيير على الخادم.",
                affectedHouseId = houseId,
                affectedPlotId = res.plotId ?: targetPlotId
            )
        } catch (t: Throwable) {
            handleMutationFailure(t, "فشل نقل البيت على الخادم.")
        }
    }

    override suspend fun deleteHouse(houseId: String): AdminOperationResult {
        val guardError = preflightSensitiveMutationGuard()
        if (guardError != null) return AdminOperationResult.Failure(guardError)

        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")

        return try {
            val params = buildJsonObject {
                put("p_house_id", houseId)
            }
            val res = client.postgrest.rpc("admin_delete_house", params).decodeAs<RpcMutationResultDto>()
            refreshHousesAndPlotsFromServer(client)
            AdminOperationResult.Success(
                message = "تم حذف البيت وتحرير القطعة ${res.plotId.orEmpty()} على الخادم.",
                affectedHouseId = houseId,
                affectedPlotId = res.plotId
            )
        } catch (t: Throwable) {
            handleMutationFailure(t, "فشل حذف البيت من الخادم.")
        }
    }

    private suspend fun preflightSensitiveMutationGuard(): String? {
        return authRepository.ensureAuthenticatedAdminForMutation()
    }

    private suspend fun handleMutationFailure(throwable: Throwable, fallback: String): AdminOperationResult.Failure {
        val translated = SupabaseErrorTranslator.toArabicMessage(throwable, fallback)
        if (authRepository.isAuthorizationOrSessionError(throwable)) {
            authRepository.handleAuthorizationOrSessionFailure(throwable, translated)
        }
        return AdminOperationResult.Failure(translated)
    }

    private suspend fun loadInitialAndSubscribe() {
        val configError = config.validationErrorOrNull()
        if (configError != null) {
            snapshotFlow.value = CityDataState.Error(configError)
            return
        }
        if (!authRepository.isAuthenticatedAdmin()) {
            snapshotFlow.value = CityDataState.Error("يجب تسجيل الدخول بحساب الإدارة أولًا.")
            return
        }
        if (!connectivityChecker.isOnline()) {
            snapshotFlow.value = CityDataState.Offline(
                message = "لا يوجد اتصال بالإنترنت.",
                staleData = lastSnapshot
            )
            return
        }
        val client = clientProvider() ?: run {
            snapshotFlow.value = CityDataState.Error("تعذر تهيئة عميل Supabase.")
            return
        }

        if (lastSnapshot == null) {
            snapshotFlow.value = CityDataState.Loading
        }

        try {
            val layout = client.from("city_layout").select().decodeList<CityLayoutDto>().firstOrNull()
            val plots = client.from("plots").select().decodeList<PlotDto>()
            val houses = client.from("houses").select().decodeList<HouseAdminDto>()
            val privates = client.from("house_private_details").select().decodeList<HousePrivateDetailsDto>()

            if (layout == null) {
                snapshotFlow.value = CityDataState.Empty
                return
            }

            stateMutex.withLock {
                cachedLayout = layout
                plotsById.clear()
                plots.forEach { plotsById[it.id] = it }
                housesById.clear()
                houses.forEach { housesById[it.id] = it }
                privateByHouseId.clear()
                privates.forEach { privateByHouseId[it.houseId] = it }
                emitSnapshotLocked()
            }

            if (enableRealtime) {
                startRealtimeIfNeeded(client)
            }
        } catch (t: Throwable) {
            if (authRepository.isAuthorizationOrSessionError(t)) {
                val msg = SupabaseErrorTranslator.toArabicMessage(
                    t,
                    SupabaseAdminAuthRepository.DEFAULT_ACCESS_DENIED_MESSAGE
                )
                authRepository.handleAuthorizationOrSessionFailure(t, msg)
                snapshotFlow.value = CityDataState.Error(msg)
                return
            }
            val stale = lastSnapshot
            if (SupabaseErrorTranslator.isNetworkError(t) || !connectivityChecker.isOnline()) {
                snapshotFlow.value = CityDataState.Offline(
                    message = "تعذر الاتصال بخادم الإدارة. تحقق من الإنترنت.",
                    staleData = stale
                )
            } else {
                snapshotFlow.value = CityDataState.Error(
                    SupabaseErrorTranslator.toArabicMessage(t, "تعذر تحميل بيانات الإدارة من Supabase.")
                )
            }
        }
    }

    private suspend fun refreshHousesAndPlotsFromServer(client: SupabaseClient) {
        val plots = client.from("plots").select().decodeList<PlotDto>()
        val houses = client.from("houses").select().decodeList<HouseAdminDto>()
        val privates = client.from("house_private_details").select().decodeList<HousePrivateDetailsDto>()
        stateMutex.withLock {
            plotsById.clear()
            plots.forEach { plotsById[it.id] = it }
            housesById.clear()
            houses.forEach { housesById[it.id] = it }
            privateByHouseId.clear()
            privates.forEach { privateByHouseId[it.houseId] = it }
            emitSnapshotLocked()
        }
    }

    private suspend fun refreshSingleHouseFromServer(client: SupabaseClient, houseId: String) {
        val house = client.from("houses").select {
            filter { eq("id", houseId) }
        }.decodeSingleOrNull<HouseAdminDto>()
        val priv = client.from("house_private_details").select {
            filter { eq("house_id", houseId) }
        }.decodeSingleOrNull<HousePrivateDetailsDto>()

        stateMutex.withLock {
            if (house != null) {
                housesById[house.id] = house
            } else {
                housesById.remove(houseId)
            }
            if (priv != null) {
                privateByHouseId[houseId] = priv
            } else {
                privateByHouseId.remove(houseId)
            }
            emitSnapshotLocked()
        }
    }

    private fun startRealtimeIfNeeded(client: SupabaseClient) {
        if (realtimeJob?.isActive == true) return
        realtimeJob = scope.launch {
            var backoffMs = 3_000L
            while (isActive) {
                try {
                    val channel = client.channel("admin_city_realtime")
                    activeChannel = channel

                    val publicHousesFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "house_public_details"
                    }
                    val plotsFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "plots"
                    }
                    val layoutFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "city_layout"
                    }

                    val jobs = listOf(
                        publicHousesFlow
                            .onEach { handlePublicHouseTriggerAction(client, it) }
                            .catch { }
                            .launchIn(this),
                        plotsFlow
                            .onEach { handlePlotAction(it) }
                            .catch { }
                            .launchIn(this),
                        layoutFlow
                            .onEach { handleLayoutAction(it) }
                            .catch { }
                            .launchIn(this)
                    )

                    channel.subscribe(blockUntilSubscribed = true)
                    backoffMs = 3_000L

                    channel.status.collect { status ->
                        if (status == RealtimeChannel.Status.UNSUBSCRIBED && isActive) {
                            throw IllegalStateException("Admin realtime channel unsubscribed")
                        }
                    }

                    jobs.forEach { it.cancel() }
                } catch (_: Throwable) {
                    runCatching { activeChannel?.unsubscribe() }
                    activeChannel = null
                    lastSnapshot?.let { stale ->
                        if (!connectivityChecker.isOnline()) {
                            snapshotFlow.value = CityDataState.Offline(
                                message = "انقطع الاتصال بالإنترنت؛ آخر بيانات معروضة قد لا تكون محدّثة.",
                                staleData = stale
                            )
                        }
                    }
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
                }
            }
        }
    }

    private suspend fun handlePublicHouseTriggerAction(client: SupabaseClient, action: PostgresAction) {
        when (action) {
            is PostgresAction.Insert, is PostgresAction.Update -> {
                val dto = action.decodeRecord<HousePublicDto>()
                runCatching { refreshSingleHouseFromServer(client, dto.houseId) }
            }
            is PostgresAction.Delete -> {
                val deletedHouseId = action.oldRecord["house_id"]?.jsonPrimitive?.content
                if (deletedHouseId != null) {
                    stateMutex.withLock {
                        housesById.remove(deletedHouseId)
                        privateByHouseId.remove(deletedHouseId)
                        emitSnapshotLocked()
                    }
                }
            }
            else -> Unit
        }
    }

    internal suspend fun handleAdminHouseAction(action: PostgresAction) {
        stateMutex.withLock {
            when (action) {
                is PostgresAction.Insert, is PostgresAction.Update -> {
                    val dto = action.decodeRecord<HouseAdminDto>()
                    housesById[dto.id] = dto
                }
                is PostgresAction.Delete -> {
                    val deletedId = action.oldRecord["id"]?.jsonPrimitive?.content
                    if (deletedId != null) {
                        housesById.remove(deletedId)
                        privateByHouseId.remove(deletedId)
                    }
                }
                else -> Unit
            }
            emitSnapshotLocked()
        }
    }

    internal suspend fun handlePlotAction(action: PostgresAction) {
        stateMutex.withLock {
            when (action) {
                is PostgresAction.Insert, is PostgresAction.Update -> {
                    val dto = action.decodeRecord<PlotDto>()
                    plotsById[dto.id] = dto
                }
                is PostgresAction.Delete -> {
                    val deletedId = action.oldRecord["id"]?.jsonPrimitive?.content
                    if (deletedId != null) plotsById.remove(deletedId)
                }
                else -> Unit
            }
            emitSnapshotLocked()
        }
    }

    internal suspend fun handlePrivateDetailsAction(action: PostgresAction) {
        stateMutex.withLock {
            when (action) {
                is PostgresAction.Insert, is PostgresAction.Update -> {
                    val dto = action.decodeRecord<HousePrivateDetailsDto>()
                    privateByHouseId[dto.houseId] = dto
                }
                is PostgresAction.Delete -> {
                    val deletedHouseId = action.oldRecord["house_id"]?.jsonPrimitive?.content
                    if (deletedHouseId != null) privateByHouseId.remove(deletedHouseId)
                }
                else -> Unit
            }
            emitSnapshotLocked()
        }
    }

    private suspend fun handleLayoutAction(action: PostgresAction) {
        if (action is PostgresAction.Insert || action is PostgresAction.Update) {
            stateMutex.withLock {
                cachedLayout = action.decodeRecord<CityLayoutDto>()
                emitSnapshotLocked()
            }
        }
    }

    private suspend fun clearInMemoryState() {
        stateMutex.withLock {
            cachedLayout = null
            plotsById.clear()
            housesById.clear()
            privateByHouseId.clear()
            lastSnapshot = null
            snapshotFlow.value = CityDataState.Loading
        }
    }

    private fun emitSnapshotLocked() {
        val layout = cachedLayout ?: run {
            snapshotFlow.value = CityDataState.Empty
            return
        }
        val snapshot = SupabaseMapper.buildAdminSnapshot(
            layout = layout,
            plotDtos = plotsById.values.toList(),
            houseDtos = housesById.values.toList(),
            privateDetailsByHouseId = privateByHouseId.toMap()
        )
        lastSnapshot = snapshot
        snapshotFlow.value = CityDataState.Success(snapshot)
    }

    private fun stopRealtime() {
        realtimeJob?.cancel()
        realtimeJob = null
        scope.launch {
            runCatching { activeChannel?.unsubscribe() }
            activeChannel = null
        }
    }

    fun close() {
        stopRealtime()
    }
}
