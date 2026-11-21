package com.example.core.data

import com.example.cityengine.world.StarterCityMap
import com.example.core.data.model.AdminHouseDraft
import com.example.core.data.model.AdminOperationResult
import com.example.core.data.model.CityDataState
import com.example.core.data.supabase.AdminAuthState
import com.example.core.data.supabase.HouseAdminDto
import com.example.core.data.supabase.HousePrivateDetailsDto
import com.example.core.data.supabase.SupabaseAdminAuthRepository
import com.example.core.data.supabase.SupabaseCityAdminRepository
import com.example.core.data.supabase.SupabaseCityExplorerRepository
import com.example.core.data.supabase.SupabaseClientProvider
import com.example.core.data.supabase.SupabaseConfig
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
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
 * Executed automatically when SUPABASE_URL and SUPABASE_ANON_KEY (plus optional
 * SUPABASE_ADMIN_EMAIL / SUPABASE_ADMIN_PASSWORD) are provided in the environment;
 * skipped cleanly otherwise.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveSupabaseIntegrationTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
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
    fun `live visitor reads public houses and cannot read house_private_details or houses`() = runBlocking {
        val config = liveConfigOrSkip()
        val client = SupabaseClientProvider.createClient(config, autoLoadFromStorage = false)

        val explorerRepo = SupabaseCityExplorerRepository(
            config = config,
            clientProvider = { client },
            ioDispatcher = Dispatchers.IO,
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
    fun `live unauthenticated caller cannot invoke admin mutations and signed in ADMIN can read snapshot`() = runBlocking {
        val config = liveConfigOrSkip()
        val adminEmail = System.getenv("SUPABASE_ADMIN_EMAIL").orEmpty()
        val adminPassword = System.getenv("SUPABASE_ADMIN_PASSWORD").orEmpty()

        assumeTrue(
            "Skipping live Admin Auth test: SUPABASE_ADMIN_EMAIL / SUPABASE_ADMIN_PASSWORD env vars not set.",
            adminEmail.isNotBlank() && adminPassword.isNotBlank()
        )

        val client = SupabaseClientProvider.createClient(config, autoLoadFromStorage = false)
        val authRepo = SupabaseAdminAuthRepository(
            config = config,
            clientProvider = { client }
        )
        val adminRepo = SupabaseCityAdminRepository(
            config = config,
            authRepository = authRepo,
            clientProvider = { client },
            ioDispatcher = Dispatchers.IO,
            enableRealtime = false
        )

        // 1. Unauthenticated caller is blocked from mutations
        authRepo.restoreAndVerifySession()
        assertTrue(authRepo.authState.value is AdminAuthState.Unauthenticated)
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

        // 2. Signing in with ADMIN email/password verifies profiles.role='ADMIN' and loads live snapshot
        authRepo.signIn(adminEmail, adminPassword)
        assertTrue(authRepo.authState.value is AdminAuthState.AuthenticatedAdmin)
        val snapState = withTimeout(15_000) {
            adminRepo.observeAdminSnapshot().first { it !is CityDataState.Loading }
        }
        assertTrue(snapState is CityDataState.Success)
        assertFalse((snapState as CityDataState.Success).data.isLocalTestData)

        authRepo.signOut()
        assertTrue(authRepo.authState.value is AdminAuthState.Unauthenticated)
    }
}
