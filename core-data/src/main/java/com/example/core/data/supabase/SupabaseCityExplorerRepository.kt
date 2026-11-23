package com.example.core.data.supabase

import com.example.core.data.local.CachedCitySnapshotBundle
import com.example.core.data.local.ExplorerSnapshotCache
import com.example.core.data.model.CityDataState
import com.example.core.data.model.CityExplorerSnapshot
import com.example.core.data.model.HouseRequestDraft
import com.example.core.data.model.HouseRequestSubmissionResult
import com.example.core.data.repository.CityExplorerRepository
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Production Supabase repository for :app (City Explorer).
 * Reads exclusively from public, visitor-safe tables (city_layout, plots, house_public_details,
 * and optional City Builder tables when part_14+ is applied) with Realtime updates.
 */
class SupabaseCityExplorerRepository(
    private val config: SupabaseConfig,
    private val clientProvider: () -> SupabaseClient? = { SupabaseClientProvider.getOrCreate(config) },
    private val connectivityChecker: ConnectivityChecker = AlwaysOnlineChecker,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val enableRealtime: Boolean = true,
    private val snapshotCache: ExplorerSnapshotCache? = null
) : CityExplorerRepository {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val stateMutex = Mutex()

    private var cachedLayout: CityLayoutDto? = null
    private val plotsById = mutableMapOf<String, PlotDto>()
    private val publicHousesById = mutableMapOf<String, HousePublicDto>()
    private val districtsById = mutableMapOf<String, DistrictDto>()
    private val buildingsById = mutableMapOf<String, PublicBuildingDto>()
    private val propsById = mutableMapOf<String, EnvironmentPropDto>()
    private val lightsById = mutableMapOf<String, CityLightDto>()
    private var builderTablesAvailable = false
    private var lastSnapshot: CityExplorerSnapshot? = null

    private var realtimeJob: Job? = null
    private var activeChannel: RealtimeChannel? = null

    private val stateFlow = MutableStateFlow<CityDataState<CityExplorerSnapshot>>(CityDataState.Loading)

    init {
        lastSnapshot = snapshotCache?.loadSnapshot()
        scope.launch {
            loadInitialAndSubscribe()
        }
    }

    override fun observeCitySnapshot(): Flow<CityDataState<CityExplorerSnapshot>> = stateFlow.asStateFlow()

    override suspend fun refresh() {
        loadInitialAndSubscribe()
    }

    override suspend fun submitHouseRequest(draft: HouseRequestDraft): HouseRequestSubmissionResult {
        draft.validate()?.let { return HouseRequestSubmissionResult.Failure(it) }
        config.validationErrorOrNull()?.let { return HouseRequestSubmissionResult.Failure(it) }
        if (!connectivityChecker.isOnline()) {
            return HouseRequestSubmissionResult.Failure("لا يوجد اتصال بالإنترنت. يرجى المحاولة عند عودة الاتصال.")
        }
        val client = clientProvider()
            ?: return HouseRequestSubmissionResult.Failure("تعذر تهيئة اتصال Supabase.")
        return try {
            val params = buildJsonObject {
                put("p_applicant_name", draft.applicantName.trim())
                put("p_phone", draft.phone?.trim()?.takeIf { it.isNotEmpty() })
                put("p_email", draft.email?.trim()?.takeIf { it.isNotEmpty() })
                put("p_notes", draft.notes?.trim()?.takeIf { it.isNotEmpty() })
            }
            val result = client.postgrest.rpc("submit_house_request", params).decodeAs<RpcMutationResultDto>()
            HouseRequestSubmissionResult.Success(
                requestId = result.requestId ?: "submitted",
                message = "تم إرسال طلب بناء المنزل بنجاح، وسيقوم رئيس المدينة بمراجعته وتخصيص الأرض المناسبة."
            )
        } catch (t: Throwable) {
            HouseRequestSubmissionResult.Failure(
                SupabaseErrorTranslator.toArabicMessage(t, "تعذر إرسال طلب بناء المنزل. حاول مرة أخرى.")
            )
        }
    }

    private suspend fun loadInitialAndSubscribe() {
        val configError = config.validationErrorOrNull()
        if (configError != null) {
            stateFlow.value = CityDataState.Error(configError)
            return
        }

        if (!connectivityChecker.isOnline()) {
            stateFlow.value = CityDataState.Offline(
                message = "لا يوجد اتصال بالإنترنت.",
                staleData = lastSnapshot
            )
            return
        }

        val client = clientProvider() ?: run {
            stateFlow.value = CityDataState.Error("تعذر تهيئة عميل Supabase.")
            return
        }

        if (lastSnapshot == null) {
            stateFlow.value = CityDataState.Loading
        }

        try {
            val layout = client.from("city_layout").select().decodeList<CityLayoutDto>().firstOrNull()
            val plots = client.from("plots").select().decodeList<PlotDto>()
            val publicHouses = client.from("house_public_details").select().decodeList<HousePublicDto>()

            if (layout == null) {
                stateFlow.value = CityDataState.Empty
                return
            }

            // Optional City Builder tables (gracefully empty until SQL part_14..16 are applied)
            var builderOk = true
            val districts = runCatching { client.from("city_districts").select().decodeList<DistrictDto>() }
                .getOrElse { builderOk = false; emptyList() }
            val buildings = runCatching { client.from("city_buildings").select().decodeList<PublicBuildingDto>() }
                .getOrElse { builderOk = false; emptyList() }
            val props = runCatching { client.from("city_environment_props").select().decodeList<EnvironmentPropDto>() }
                .getOrElse { builderOk = false; emptyList() }
            val lights = runCatching { client.from("city_lights").select().decodeList<CityLightDto>() }
                .getOrElse { builderOk = false; emptyList() }

            stateMutex.withLock {
                builderTablesAvailable = builderOk
                cachedLayout = layout
                plotsById.clear(); plots.forEach { plotsById[it.id] = it }
                publicHousesById.clear(); publicHouses.forEach { publicHousesById[it.houseId] = it }
                districtsById.clear(); districts.forEach { districtsById[it.id] = it }
                buildingsById.clear(); buildings.forEach { buildingsById[it.id] = it }
                propsById.clear(); props.forEach { propsById[it.id] = it }
                lightsById.clear(); lights.forEach { lightsById[it.id] = it }
                emitSnapshotLocked()
            }

            if (enableRealtime) {
                startRealtimeIfNeeded(client)
            }
        } catch (t: Throwable) {
            val stale = lastSnapshot
            if (SupabaseErrorTranslator.isNetworkError(t) || !connectivityChecker.isOnline()) {
                stateFlow.value = CityDataState.Offline(
                    message = "تعذر الاتصال بخادم المدينة. تحقق من الإنترنت وأعد المحاولة.",
                    staleData = stale
                )
            } else {
                stateFlow.value = CityDataState.Error(
                    SupabaseErrorTranslator.toArabicMessage(t, "تعذر تحميل بيانات المدينة من Supabase.")
                )
            }
        }
    }

    private fun startRealtimeIfNeeded(client: SupabaseClient) {
        if (realtimeJob?.isActive == true) return

        realtimeJob = scope.launch {
            var backoffMs = 3_000L
            while (isActive) {
                try {
                    val channel = client.channel("explorer_city_realtime")
                    activeChannel = channel

                    val collectorJobs = mutableListOf(
                        channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "house_public_details" }
                            .onEach { handleHouseRealtimeAction(it) }.catch { }.launchIn(this),
                        channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "plots" }
                            .onEach { handlePlotRealtimeAction(it) }.catch { }.launchIn(this),
                        channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "city_layout" }
                            .onEach { handleLayoutRealtimeAction(it) }.catch { }.launchIn(this)
                    )

                    if (builderTablesAvailable) {
                        collectorJobs += channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "city_districts" }
                            .onEach { handleBuilderMapRealtimeAction(districtsById, it) }.catch { }.launchIn(this)
                        collectorJobs += channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "city_buildings" }
                            .onEach { handleBuilderMapRealtimeAction(buildingsById, it) }.catch { }.launchIn(this)
                        collectorJobs += channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "city_environment_props" }
                            .onEach { handleBuilderMapRealtimeAction(propsById, it) }.catch { }.launchIn(this)
                        collectorJobs += channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "city_lights" }
                            .onEach { handleBuilderMapRealtimeAction(lightsById, it) }.catch { }.launchIn(this)
                    }

                    channel.subscribe(blockUntilSubscribed = true)
                    backoffMs = 3_000L

                    stateMutex.withLock {
                        if (stateFlow.value is CityDataState.Offline && cachedLayout != null) {
                            emitSnapshotLocked()
                        }
                    }

                    channel.status.collect { status ->
                        if (status == RealtimeChannel.Status.UNSUBSCRIBED && isActive) {
                            lastSnapshot?.let { stale ->
                                stateFlow.value = CityDataState.Offline(
                                    message = "انقطع الاتصال اللحظي بالخادم. جارٍ إعادة الاتصال…",
                                    staleData = stale
                                )
                            }
                            throw IllegalStateException("Realtime channel unsubscribed")
                        }
                    }

                    collectorJobs.forEach { it.cancel() }
                } catch (_: Throwable) {
                    runCatching { activeChannel?.unsubscribe() }
                    activeChannel = null
                    lastSnapshot?.let { stale ->
                        if (!connectivityChecker.isOnline()) {
                            stateFlow.value = CityDataState.Offline(
                                message = "انقطع الاتصال بالإنترنت. العرض الحالي قد يكون غير محدّث.",
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

    private suspend inline fun <reified T : Any> handleBuilderMapRealtimeAction(
        map: MutableMap<String, T>,
        action: PostgresAction
    ) {
        stateMutex.withLock {
            when (action) {
                is PostgresAction.Insert -> {
                    val dto = action.decodeRecord<T>()
                    val id = action.record["id"]?.jsonPrimitive?.content ?: return
                    map[id] = dto
                }
                is PostgresAction.Update -> {
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

    internal suspend fun handleHouseRealtimeAction(action: PostgresAction) {
        stateMutex.withLock {
            when (action) {
                is PostgresAction.Insert -> {
                    val dto = action.decodeRecord<HousePublicDto>()
                    publicHousesById[dto.houseId] = dto
                }
                is PostgresAction.Update -> {
                    val dto = action.decodeRecord<HousePublicDto>()
                    publicHousesById[dto.houseId] = dto
                }
                is PostgresAction.Delete -> {
                    val deletedId = action.oldRecord["house_id"]?.jsonPrimitive?.content
                    if (deletedId != null) publicHousesById.remove(deletedId)
                }
                else -> Unit
            }
            emitSnapshotLocked()
        }
    }

    internal suspend fun handlePlotRealtimeAction(action: PostgresAction) {
        stateMutex.withLock {
            when (action) {
                is PostgresAction.Insert -> {
                    val dto = action.decodeRecord<PlotDto>()
                    plotsById[dto.id] = dto
                }
                is PostgresAction.Update -> {
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

    internal suspend fun handleLayoutRealtimeAction(action: PostgresAction) {
        stateMutex.withLock {
            when (action) {
                is PostgresAction.Insert, is PostgresAction.Update -> {
                    cachedLayout = action.decodeRecord<CityLayoutDto>()
                    emitSnapshotLocked()
                }
                else -> Unit
            }
        }
    }

    internal suspend fun applyIncrementalHouseUpsert(dto: HousePublicDto) {
        stateMutex.withLock {
            publicHousesById[dto.houseId] = dto
            emitSnapshotLocked()
        }
    }

    internal suspend fun applyIncrementalHouseDelete(houseId: String) {
        stateMutex.withLock {
            publicHousesById.remove(houseId)
            emitSnapshotLocked()
        }
    }

    internal suspend fun applyIncrementalPlotUpsert(dto: PlotDto) {
        stateMutex.withLock {
            plotsById[dto.id] = dto
            emitSnapshotLocked()
        }
    }

    internal suspend fun seedStateForTesting(
        layout: CityLayoutDto,
        plots: List<PlotDto>,
        houses: List<HousePublicDto>,
        districts: List<DistrictDto> = emptyList(),
        buildings: List<PublicBuildingDto> = emptyList(),
        props: List<EnvironmentPropDto> = emptyList(),
        lights: List<CityLightDto> = emptyList()
    ) {
        stateMutex.withLock {
            cachedLayout = layout
            plotsById.clear(); plots.forEach { plotsById[it.id] = it }
            publicHousesById.clear(); houses.forEach { publicHousesById[it.houseId] = it }
            districtsById.clear(); districts.forEach { districtsById[it.id] = it }
            buildingsById.clear(); buildings.forEach { buildingsById[it.id] = it }
            propsById.clear(); props.forEach { propsById[it.id] = it }
            lightsById.clear(); lights.forEach { lightsById[it.id] = it }
            emitSnapshotLocked()
        }
    }

    private fun emitSnapshotLocked() {
        val layout = cachedLayout ?: run {
            stateFlow.value = CityDataState.Empty
            return
        }
        val snapshot = SupabaseMapper.buildExplorerSnapshot(
            layout = layout,
            plotDtos = plotsById.values.toList(),
            publicHouses = publicHousesById.values.toList(),
            districts = districtsById.values.toList(),
            buildings = buildingsById.values.toList(),
            props = propsById.values.toList(),
            lights = lightsById.values.toList()
        )
        lastSnapshot = snapshot
        snapshotCache?.saveBundle(
            CachedCitySnapshotBundle(
                layout = layout,
                plots = plotsById.values.toList(),
                publicHouses = publicHousesById.values.toList(),
                districts = districtsById.values.toList(),
                buildings = buildingsById.values.toList(),
                props = propsById.values.toList(),
                lights = lightsById.values.toList()
            )
        )
        stateFlow.value = if (snapshot.scene.plots.isEmpty() && snapshot.scene.houses.isEmpty() && snapshot.scene.publicBuildings.isEmpty()) {
            CityDataState.Empty
        } else {
            CityDataState.Success(snapshot)
        }
    }

    fun close() {
        realtimeJob?.cancel()
        scope.launch {
            runCatching { activeChannel?.unsubscribe() }
        }
    }
}
