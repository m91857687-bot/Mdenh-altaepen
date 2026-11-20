package com.example.core.data.supabase

import com.example.core.data.model.CityDataState
import com.example.core.data.model.CityExplorerSnapshot
import com.example.core.data.repository.CityExplorerRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
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
import kotlinx.serialization.json.jsonPrimitive

/**
 * Production Supabase repository for :app (City Explorer).
 * Reads exclusively from public, visitor-safe tables (city_layout, plots, house_public_details)
 * and receives incremental Realtime updates without reloading the full database on every change.
 */
class SupabaseCityExplorerRepository(
    private val config: SupabaseConfig,
    private val clientProvider: () -> SupabaseClient? = { SupabaseClientProvider.getOrCreate(config) },
    private val connectivityChecker: ConnectivityChecker = AlwaysOnlineChecker,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val enableRealtime: Boolean = true
) : CityExplorerRepository {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val stateMutex = Mutex()

    private var cachedLayout: CityLayoutDto? = null
    private val plotsById = mutableMapOf<String, PlotDto>()
    private val publicHousesById = mutableMapOf<String, HousePublicDto>()
    private var lastSnapshot: CityExplorerSnapshot? = null

    private var realtimeJob: Job? = null
    private var activeChannel: RealtimeChannel? = null

    private val stateFlow = MutableStateFlow<CityDataState<CityExplorerSnapshot>>(CityDataState.Loading)

    init {
        scope.launch {
            loadInitialAndSubscribe()
        }
    }

    override fun observeCitySnapshot(): Flow<CityDataState<CityExplorerSnapshot>> = stateFlow.asStateFlow()

    override suspend fun refresh() {
        loadInitialAndSubscribe()
    }

    private suspend fun loadInitialAndSubscribe() {
        val configError = config.validationErrorOrNull()
        if (configError != null) {
            stateFlow.value = CityDataState.Error(configError)
            return
        }

        if (!connectivityChecker.isOnline()) {
            val stale = lastSnapshot
            stateFlow.value = CityDataState.Offline(
                message = "لا يوجد اتصال بالإنترنت.",
                staleData = stale
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

            stateMutex.withLock {
                cachedLayout = layout
                plotsById.clear()
                plots.forEach { plotsById[it.id] = it }
                publicHousesById.clear()
                publicHouses.forEach { publicHousesById[it.houseId] = it }
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

                    val houseChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "house_public_details"
                    }
                    val plotChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "plots"
                    }
                    val layoutChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "city_layout"
                    }

                    val collectorJobs = listOf(
                        houseChanges
                            .onEach { action -> handleHouseRealtimeAction(action) }
                            .catch { /* handled by outer reconnect loop */ }
                            .launchIn(this),
                        plotChanges
                            .onEach { action -> handlePlotRealtimeAction(action) }
                            .catch { /* handled by outer reconnect loop */ }
                            .launchIn(this),
                        layoutChanges
                            .onEach { action -> handleLayoutRealtimeAction(action) }
                            .catch { /* handled by outer reconnect loop */ }
                            .launchIn(this)
                    )

                    channel.subscribe(blockUntilSubscribed = true)
                    backoffMs = 3_000L

                    // If we recovered from an Offline state, re-emit fresh state
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
                    if (deletedId != null) {
                        publicHousesById.remove(deletedId)
                    }
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
                    if (deletedId != null) {
                        plotsById.remove(deletedId)
                    }
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

    /**
     * Applies an incremental house event directly in memory without querying the database again.
     */
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
        houses: List<HousePublicDto>
    ) {
        stateMutex.withLock {
            cachedLayout = layout
            plotsById.clear()
            plots.forEach { plotsById[it.id] = it }
            publicHousesById.clear()
            houses.forEach { publicHousesById[it.houseId] = it }
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
            publicHouses = publicHousesById.values.toList()
        )
        lastSnapshot = snapshot
        stateFlow.value = if (snapshot.scene.plots.isEmpty() && snapshot.scene.houses.isEmpty()) {
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
