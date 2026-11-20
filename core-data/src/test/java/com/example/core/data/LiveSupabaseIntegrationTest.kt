package com.example.core.data

import com.example.cityengine.world.StarterCityMap
import com.example.core.data.model.AdminHouseDraft
import com.example.core.data.model.AdminOperationResult
import com.example.core.data.model.CityDataState
import com.example.core.data.supabase.AdminDeviceAuthState
import com.example.core.data.supabase.HouseAdminDto
import com.example.core.data.supabase.HousePrivateDetailsDto
import com.example.core.data.supabase.InMemoryAdminDeviceCredentialStore
import com.example.core.data.supabase.SupabaseCityAdminRepository
import com.example.core.data.supabase.SupabaseCityExplorerRepository
import com.example.core.data.supabase.SupabaseClientProvider
import com.example.core.data.supabase.SupabaseConfig
import com.example.core.data.supabase.SupabaseDeviceAuthRepository
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Live end-to-end integration tests against a real Supabase project.
 * Executed automatically when SUPABASE_URL and SUPABASE_ANON_KEY (plus optional one-time
 * SUPABASE_ADMIN_ACTIVATION_CODE) are provided in the environment; skipped cleanly otherwise.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveSupabaseIntegrationTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun liveConfigOrSkip(): SupabaseConfig {
        val url = System.getenv("SUPABASE_URL").orEmpty()
        val anonKey = System.getenv("SUPABASE_ANON_KEY").orEmpty()
        val config = SupabaseConfig(url, anonKey)
        assumeTrue(
            "Skipping live Supabase test: SUPABASE_URL / SUPABASE_ANON_KEY not set in environment.",
            config.isConfigured
        )
        return config
    }

    @Test
    fun `live visitor reads public houses and cannot read house_private_details or houses`() = runTest(dispatcher) {
        val config = liveConfigOrSkip()
        val client = SupabaseClientProvider.createClient(config, autoLoadFromStorage = false)

        val explorerRepo = SupabaseCityExplorerRepository(
            config = config,
            clientProvider = { client },
            ioDispatcher = dispatcher,
            enableRealtime = false
        )

        val state = withTimeout(15_000) {
            explorerRepo.observeCitySnapshot().first { it !is CityDataState.Loading }
        }
        assertTrue("Expected Success from live Supabase, got $state", state is CityDataState.Success)
        val snap = (state as CityDataState.Success).data
        assertFalse(snap.isTestData)

        // Direct read on house_private_details as anon MUST return empty or fail RLS
        val privRows = runCatching {
            client.from("house_private_details").select().decodeList<HousePrivateDetailsDto>()
        }.getOrNull()
        assertTrue(
            "Anon visitor must NEVER read private details",
            privRows == null || privRows.isEmpty()
        )

        // Direct read on admin houses table as anon MUST return empty or fail RLS
        val adminHouses = runCatching {
            client.from("houses").select().decodeList<HouseAdminDto>()
        }.getOrNull()
        assertTrue(
            "Anon visitor must NEVER read raw houses table directly",
            adminHouses == null || adminHouses.isEmpty()
        )
    }

    @Test
    fun `live unenrolled device cannot invoke admin mutations and activated device can read snapshot`() = runTest(dispatcher) {
        val config = liveConfigOrSkip()
        val activationCode = System.getenv("SUPABASE_ADMIN_ACTIVATION_CODE").orEmpty()

        assumeTrue(
            "Skipping live device activation test: SUPABASE_ADMIN_ACTIVATION_CODE env var not set.",
            activationCode.isNotBlank()
        )

        val store = InMemoryAdminDeviceCredentialStore()
        lateinit var authRepo: SupabaseDeviceAuthRepository
        val client = SupabaseClientProvider.createClient(
            config = config,
            autoLoadFromStorage = false,
            deviceHeaderProvider = { authRepo.currentDeviceHeadersOrNull() }
        )
        authRepo = SupabaseDeviceAuthRepository(
            config = config,
            credentialStore = store,
            clientProvider = { client }
        )
        val adminRepo = SupabaseCityAdminRepository(
            config = config,
            authRepository = authRepo,
            clientProvider = { client },
            ioDispatcher = dispatcher,
            enableRealtime = false
        )

        // 1. Unenrolled device is blocked from mutations
        authRepo.verifyDeviceOnStartup()
        assertTrue(authRepo.authState.value is AdminDeviceAuthState.Unenrolled)
        val deniedAdd = adminRepo.addHouse(
            AdminHouseDraft(
                targetPlotId = "plot_12",
                ownerName = "Unauthorized Test",
                familyMembersCount = 2,
                flag = StarterCityMap.flagPresets.first(),
                palette = StarterCityMap.palettePresets.first().palette
            )
        )
        assertTrue(deniedAdd is AdminOperationResult.Failure)

        // 2. Activating with server-issued one-time code authorizes device & loads live snapshot
        authRepo.activateDevice(activationCode, "CI Live Test Device")
        assertTrue(authRepo.authState.value is AdminDeviceAuthState.Authorized)
        val snapState = withTimeout(15_000) {
            adminRepo.observeAdminSnapshot().first { it !is CityDataState.Loading }
        }
        assertTrue(snapState is CityDataState.Success)
        assertFalse((snapState as CityDataState.Success).data.isLocalTestData)
    }
}
