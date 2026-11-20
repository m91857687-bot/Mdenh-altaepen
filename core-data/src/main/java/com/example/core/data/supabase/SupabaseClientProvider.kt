package com.example.core.data.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.api.createClientPlugin
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.Json

/**
 * Central Supabase client factory shared across :app (Explorer) and :app-admin (Admin).
 * Keeps connection details in one module (:core-data) and attaches admin device verification
 * headers strictly to admin clients — never to public Explorer clients.
 */
object SupabaseClientProvider {

    const val HEADER_ADMIN_DEVICE_ID = "x-admin-device-id"
    const val HEADER_ADMIN_DEVICE_TOKEN = "x-admin-device-token"

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Volatile
    private var cachedExplorerKey: String? = null

    @Volatile
    private var cachedExplorerClient: SupabaseClient? = null

    @Volatile
    private var cachedAdminKey: String? = null

    @Volatile
    private var cachedAdminClient: SupabaseClient? = null

    private val activeAdminHeaderProvider = AtomicReference<(() -> Pair<String, String>?)?>(null)

    fun getOrCreate(
        config: SupabaseConfig,
        sessionManager: SessionManager? = null
    ): SupabaseClient? {
        if (!config.isConfigured) return null
        val key = "${config.normalizedUrl}|${config.normalizedAnonKey}"
        cachedExplorerClient?.let { existing ->
            if (cachedExplorerKey == key) return existing
        }
        return synchronized(this) {
            cachedExplorerClient?.let { existing ->
                if (cachedExplorerKey == key) return@synchronized existing
            }
            val created = createClient(
                config = config,
                sessionManager = sessionManager,
                deviceHeaderProvider = null
            )
            cachedExplorerKey = key
            cachedExplorerClient = created
            created
        }
    }

    fun getOrCreateAdminClient(
        config: SupabaseConfig,
        deviceHeaderProvider: () -> Pair<String, String>?
    ): SupabaseClient? {
        if (!config.isConfigured) return null
        activeAdminHeaderProvider.set(deviceHeaderProvider)
        val key = "${config.normalizedUrl}|${config.normalizedAnonKey}"
        cachedAdminClient?.let { existing ->
            if (cachedAdminKey == key) return existing
        }
        return synchronized(this) {
            cachedAdminClient?.let { existing ->
                if (cachedAdminKey == key) return@synchronized existing
            }
            val created = createClient(
                config = config,
                sessionManager = MemorySessionManager(),
                autoLoadFromStorage = false,
                deviceHeaderProvider = { activeAdminHeaderProvider.get()?.invoke() }
            )
            cachedAdminKey = key
            cachedAdminClient = created
            created
        }
    }

    @OptIn(SupabaseInternal::class)
    fun createClient(
        config: SupabaseConfig,
        httpClientEngine: HttpClientEngine? = null,
        sessionManager: SessionManager? = null,
        autoLoadFromStorage: Boolean = true,
        deviceHeaderProvider: (() -> Pair<String, String>?)? = null
    ): SupabaseClient {
        val error = config.validationErrorOrNull()
        require(error == null) { error ?: "Invalid Supabase configuration." }

        return createSupabaseClient(
            supabaseUrl = config.normalizedUrl,
            supabaseKey = config.normalizedAnonKey
        ) {
            if (httpClientEngine != null) {
                httpEngine = httpClientEngine
            }
            if (deviceHeaderProvider != null) {
                val adminHeadersPlugin = createClientPlugin("AdminDeviceHeadersPlugin") {
                    onRequest { request, _ ->
                        val creds = deviceHeaderProvider.invoke()
                        if (creds != null && creds.first.isNotBlank() && creds.second.isNotBlank()) {
                            request.headers.remove(HEADER_ADMIN_DEVICE_ID)
                            request.headers.remove(HEADER_ADMIN_DEVICE_TOKEN)
                            request.headers.append(HEADER_ADMIN_DEVICE_ID, creds.first)
                            request.headers.append(HEADER_ADMIN_DEVICE_TOKEN, creds.second)
                        }
                    }
                }
                httpConfig {
                    install(adminHeadersPlugin)
                }
            }
            defaultSerializer = KotlinXSerializer(json)
            install(Postgrest)
            install(Auth) {
                this.autoLoadFromStorage = autoLoadFromStorage
                this.alwaysAutoRefresh = false
                if (sessionManager != null) {
                    this.sessionManager = sessionManager
                    this.codeVerifierCache = MemoryCodeVerifierCache()
                } else if (!autoLoadFromStorage) {
                    this.sessionManager = MemorySessionManager()
                    this.codeVerifierCache = MemoryCodeVerifierCache()
                }
            }
            install(Realtime)
        }
    }
}
