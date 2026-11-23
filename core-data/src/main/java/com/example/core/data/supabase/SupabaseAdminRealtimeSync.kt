package com.example.core.data.supabase

import com.example.core.data.model.CityBuilderServerCapabilities
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

/**
 * Modular Realtime subscription & incremental delta sync coordinator for [SupabaseCityAdminRepository].
 */
internal class SupabaseAdminRealtimeSync(
    private val scope: CoroutineScope,
    private val connectivityChecker: ConnectivityChecker,
    private val capabilitiesProvider: () -> CityBuilderServerCapabilities,
    private val onRefreshSingleHouse: suspend (SupabaseClient, String) -> Unit,
    private val onHouseDeleted: suspend (String) -> Unit,
    private val onAdminHouseUpsert: suspend (HouseAdminDto) -> Unit,
    private val onAdminHouseDelete: suspend (String) -> Unit,
    private val onPrivateDetailsUpsert: suspend (HousePrivateDetailsDto) -> Unit,
    private val onPrivateDetailsDelete: suspend (String) -> Unit,
    private val onPlotUpsert: suspend (PlotDto) -> Unit,
    private val onPlotDelete: suspend (String) -> Unit,
    private val onLayoutUpdate: suspend (CityLayoutDto) -> Unit,
    private val onDistrictAction: suspend (PostgresAction) -> Unit,
    private val onBuildingAction: suspend (PostgresAction) -> Unit,
    private val onPropAction: suspend (PostgresAction) -> Unit,
    private val onLightAction: suspend (PostgresAction) -> Unit,
    private val onOfflineDetected: () -> Unit,
    private val onHouseRequestAction: suspend (PostgresAction) -> Unit = {}
) {
    private var realtimeJob: Job? = null
    private var activeChannel: RealtimeChannel? = null

    fun startIfNeeded(client: SupabaseClient) {
        if (realtimeJob?.isActive == true) return
        realtimeJob = scope.launch {
            var backoffMs = 3_000L
            while (isActive) {
                try {
                    val channel = client.channel("admin_city_realtime")
                    activeChannel = channel
                    val jobs = mutableListOf<Job>()
                    jobs += channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "house_public_details" }
                        .onEach { handlePublicHouseTriggerAction(client, it) }.catch { }.launchIn(this)
                    jobs += channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "plots" }
                        .onEach { handlePlotAction(it) }.catch { }.launchIn(this)
                    jobs += channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "city_layout" }
                        .onEach { handleLayoutAction(it) }.catch { }.launchIn(this)

                    if (capabilitiesProvider().builderTablesAvailable) {
                        jobs += channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "city_districts" }
                            .onEach { onDistrictAction(it) }.catch { }.launchIn(this)
                        jobs += channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "city_buildings" }
                            .onEach { onBuildingAction(it) }.catch { }.launchIn(this)
                        jobs += channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "city_environment_props" }
                            .onEach { onPropAction(it) }.catch { }.launchIn(this)
                        jobs += channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "city_lights" }
                            .onEach { onLightAction(it) }.catch { }.launchIn(this)
                    }

                    runCatching {
                        jobs += channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "house_requests" }
                            .onEach { onHouseRequestAction(it) }.catch { }.launchIn(this)
                    }

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
                    if (!connectivityChecker.isOnline()) onOfflineDetected()
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
                }
            }
        }
    }

    fun stop() {
        realtimeJob?.cancel()
        realtimeJob = null
        scope.launch {
            runCatching { activeChannel?.unsubscribe() }
            activeChannel = null
        }
    }

    suspend fun handlePublicHouseTriggerAction(client: SupabaseClient, action: PostgresAction) {
        when (action) {
            is PostgresAction.Insert, is PostgresAction.Update -> {
                val dto = action.decodeRecord<HousePublicDto>()
                runCatching { onRefreshSingleHouse(client, dto.houseId) }
            }
            is PostgresAction.Delete -> {
                action.oldRecord["house_id"]?.jsonPrimitive?.content?.let { onHouseDeleted(it) }
            }
            else -> Unit
        }
    }

    suspend fun handleAdminHouseAction(action: PostgresAction) {
        when (action) {
            is PostgresAction.Insert, is PostgresAction.Update -> onAdminHouseUpsert(action.decodeRecord())
            is PostgresAction.Delete -> action.oldRecord["id"]?.jsonPrimitive?.content?.let { onAdminHouseDelete(it) }
            else -> Unit
        }
    }

    suspend fun handlePlotAction(action: PostgresAction) {
        when (action) {
            is PostgresAction.Insert, is PostgresAction.Update -> onPlotUpsert(action.decodeRecord())
            is PostgresAction.Delete -> action.oldRecord["id"]?.jsonPrimitive?.content?.let { onPlotDelete(it) }
            else -> Unit
        }
    }

    suspend fun handlePrivateDetailsAction(action: PostgresAction) {
        when (action) {
            is PostgresAction.Insert, is PostgresAction.Update -> onPrivateDetailsUpsert(action.decodeRecord())
            is PostgresAction.Delete -> action.oldRecord["house_id"]?.jsonPrimitive?.content?.let { onPrivateDetailsDelete(it) }
            else -> Unit
        }
    }

    suspend fun handleLayoutAction(action: PostgresAction) {
        if (action is PostgresAction.Insert || action is PostgresAction.Update) {
            onLayoutUpdate(action.decodeRecord())
        }
    }
}
