package com.example.core.data

import com.example.cityengine.model.BuildingRotation
import com.example.cityengine.model.CityLightingState
import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.EnvironmentPropType
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.LightSourceType
import com.example.cityengine.model.PropLayer
import com.example.cityengine.model.PublicBuildingType
import com.example.cityengine.model.TileTextureVariant
import com.example.cityengine.model.TimeOfDayPhase
import com.example.core.data.model.AdminCitySnapshot
import com.example.core.data.model.AdminOperationResult
import com.example.core.data.model.CityDataState
import com.example.core.data.model.DistrictDraft
import com.example.core.data.model.EnvironmentPropDraft
import com.example.core.data.model.LightSourceDraft
import com.example.core.data.model.PlotCreateDraft
import com.example.core.data.model.PublicBuildingDraft
import com.example.core.data.model.RoadSegmentDraft
import com.example.core.data.model.TilePaintChange
import com.example.core.data.supabase.AdminAuthManager
import com.example.core.data.supabase.AdminAuthState
import com.example.core.data.supabase.AlwaysOnlineChecker
import com.example.core.data.supabase.ConnectivityChecker
import com.example.core.data.supabase.SupabaseCityAdminRepository
import com.example.core.data.supabase.SupabaseClientProvider
import com.example.core.data.supabase.SupabaseConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SupabaseCityBuilderTest {

    private val validConfig = SupabaseConfig(
        url = "https://example.supabase.co",
        anonKey = "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiYW5vbiJ9.sig"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeAdminAuthManager(initial: AdminAuthState) : AdminAuthManager {
        private val state = MutableStateFlow(initial)
        override val authState: StateFlow<AdminAuthState> = state
        override fun isAuthenticatedAdmin(): Boolean = state.value is AdminAuthState.AuthenticatedAdmin
        override suspend fun restoreAndVerifySession() = Unit
        override suspend fun signIn(email: String, password: String) = Unit
        override suspend fun signOut() {
            state.value = AdminAuthState.Unauthenticated()
        }
    }

    private fun layoutJson(version: Int = 1): String = """
        [
          {
            "id": "main_city",
            "version": $version,
            "grid_width": 16,
            "grid_height": 16,
            "roads": [{"x":0,"y":0}],
            "sidewalks": [],
            "parks": [],
            "trees": [],
            "lamp_posts": [],
            "custom_tiles": [],
            "road_segments": [],
            "lighting_config": {"phase":"DAY","sunAzimuthDeg":42,"ambientDimming":0,"nightLampAutoOn":true}
          }
        ]
    """.trimIndent()

    private fun plotsJson(): String = """
        [
          {"id":"plot_big","layout_id":"main_city","plot_code":"P-BIG","grid_x":2,"grid_y":2,"status":"AVAILABLE","width_cells":2,"height_cells":2,"rotation_deg":0},
          {"id":"plot_m1","layout_id":"main_city","plot_code":"P-M1","grid_x":6,"grid_y":2,"status":"AVAILABLE","width_cells":1,"height_cells":1,"rotation_deg":0},
          {"id":"plot_m2","layout_id":"main_city","plot_code":"P-M2","grid_x":7,"grid_y":2,"status":"AVAILABLE","width_cells":1,"height_cells":1,"rotation_deg":0}
        ]
    """.trimIndent()

    private suspend fun createAdminRepo(
        engine: MockEngine,
        auth: AdminAuthManager = FakeAdminAuthManager(AdminAuthState.AuthenticatedAdmin("admin-1", "admin@city.test", "المدير")),
        connectivity: ConnectivityChecker = AlwaysOnlineChecker
    ): SupabaseCityAdminRepository {
        val client = SupabaseClientProvider.createClient(validConfig, engine, autoLoadFromStorage = false)
        client.auth.importSession(
            UserSession(
                accessToken = "admin-jwt",
                refreshToken = "refresh-jwt",
                expiresIn = 3600,
                expiresAt = Instant.fromEpochSeconds((System.currentTimeMillis() / 1000L) + 3600),
                tokenType = "bearer",
                user = UserInfo(id = "admin-1", aud = "authenticated", email = "admin@city.test")
            ),
            autoRefresh = false
        )
        return SupabaseCityAdminRepository(
            config = validConfig,
            authRepository = auth,
            clientProvider = { client },
            connectivityChecker = connectivity,
            ioDispatcher = Dispatchers.IO,
            enableRealtime = false
        )
    }

    @Test
    fun `builder delegates RPCs, split-before-insert, optimistic version conflict, and RLS ops accurately`() = runBlocking {
        val calledEndpoints = mutableListOf<String>()
        var failLayoutPatchWithVersionConflict = false

        val mockEngine = MockEngine { req ->
            val path = req.url.encodedPath
            calledEndpoints += "${req.method.value}:$path"
            when {
                req.method == HttpMethod.Patch && path.endsWith("/city_layout") -> {
                    if (failLayoutPatchWithVersionConflict) {
                        respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    } else {
                        respond(layoutJson(version = 2), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
                path.endsWith("/city_layout") -> respond(layoutJson(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                path.endsWith("/plots") -> respond(plotsJson(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                path.contains("/rpc/admin_") -> respond("""{"ok":true}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                else -> respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }

        val repo = createAdminRepo(mockEngine)
        val loaded = withTimeout(10_000) {
            repo.observeAdminSnapshot().first { it is CityDataState.Success }
        } as CityDataState.Success
        assertTrue(loaded.data.builderCapabilities.builderTablesAvailable)
        assertEquals(1, loaded.data.layoutVersion)

        // Expand grid
        assertTrue(repo.expandCityGrid(24, 24) is AdminOperationResult.Success)
        assertTrue(calledEndpoints.any { it == "POST:/rest/v1/rpc/admin_expand_city_grid" })

        // Split plot deletes parent before child upserts
        calledEndpoints.clear()
        val splitRes = repo.splitPlot("plot_big", splitHorizontally = true)
        assertTrue("Expected split success, got $splitRes", splitRes is AdminOperationResult.Success)
        val deleteIdx = calledEndpoints.indexOfFirst { it == "DELETE:/rest/v1/plots" }
        val upsertIdx = calledEndpoints.indexOfFirst { it == "POST:/rest/v1/rpc/admin_upsert_plot" }
        assertTrue("Expected DELETE before RPC upsert, log=$calledEndpoints", deleteIdx in 0 until upsertIdx)

        // Merge adjacent plots
        assertTrue(repo.mergePlots("plot_m1", "plot_m2") is AdminOperationResult.Success)

        // Create plot + resize + rotate + move
        assertTrue(repo.createPlot(PlotCreateDraft(coord = GridCoord(10, 10), widthCells = 1, heightCells = 1)) is AdminOperationResult.Success)
        assertTrue(repo.resizePlot("plot_big", 3, 2) is AdminOperationResult.Success)
        assertTrue(repo.rotatePlot("plot_big", BuildingRotation.DEG_90) is AdminOperationResult.Success)
        assertTrue(repo.movePlot("plot_m1", GridCoord(12, 12)) is AdminOperationResult.Success)

        // Public building, district, prop, light, lighting
        assertTrue(
            repo.upsertDistrict(DistrictDraft(nameAr = "حي النور", code = "NUR", origin = GridCoord(8, 8), widthCells = 4, heightCells = 4)) is AdminOperationResult.Success
        )
        assertTrue(
            repo.upsertPublicBuilding(
                PublicBuildingDraft(
                    nameAr = "مستشفى الأمل",
                    type = PublicBuildingType.HOSPITAL,
                    coord = GridCoord(11, 4),
                    widthCells = 2,
                    heightCells = 2,
                    assetKey = "building_hospital"
                )
            ) is AdminOperationResult.Success
        )
        assertTrue(calledEndpoints.any { it == "POST:/rest/v1/rpc/admin_upsert_public_building" })

        assertTrue(
            repo.upsertEnvironmentProp(
                EnvironmentPropDraft(
                    type = EnvironmentPropType.FOUNTAIN,
                    coord = GridCoord(9, 9),
                    layer = PropLayer.STRUCTURE,
                    assetKey = "prop_fountain"
                )
            ) is AdminOperationResult.Success
        )
        assertTrue(
            repo.upsertLightSource(LightSourceDraft(type = LightSourceType.STREET_LAMP, coord = GridCoord(9, 10))) is AdminOperationResult.Success
        )
        assertTrue(
            repo.setLightingState(CityLightingState(phase = TimeOfDayPhase.SUNSET)) is AdminOperationResult.Success
        )
        assertTrue(calledEndpoints.any { it == "POST:/rest/v1/rpc/admin_set_lighting_phase" })

        // Road segment & tile painting + optimistic version conflict rejection
        assertTrue(
            repo.upsertRoadSegment(RoadSegmentDraft(coord = GridCoord(4, 10), widthCells = 3, heightCells = 1, streetNameAr = "شارع النخيل")) is AdminOperationResult.Success
        )
        failLayoutPatchWithVersionConflict = true
        val conflictRes = repo.paintTiles(listOf(TilePaintChange(GridCoord(1, 10), CityTileType.PARK_GREEN, TileTextureVariant.CHECKER_STONE)))
        assertTrue("Expected optimistic conflict failure", conflictRes is AdminOperationResult.Failure)
    }

    @Test
    fun `gracefully degrades builder capabilities when part_14_16 tables return 404 and blocks viewer or offline writes`() = runBlocking {
        val phase4Engine = MockEngine { req ->
            val path = req.url.encodedPath
            when {
                path.endsWith("/city_layout") -> respond(layoutJson(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                path.endsWith("/plots") -> respond(plotsJson(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                path.endsWith("/houses") || path.endsWith("/house_private_details") ->
                    respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                else -> respond("""{"code":"PGRST205","message":"Could not find the table"}""", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val repo = createAdminRepo(phase4Engine)
        val snapState = withTimeout(10_000) { repo.observeAdminSnapshot().first { it is CityDataState.Success } } as CityDataState.Success
        assertFalse(snapState.data.builderCapabilities.builderTablesAvailable)

        // Calling part_16 RPC yields friendly Arabic migration guidance
        val rpcFail = repo.expandCityGrid(24, 24)
        assertTrue(rpcFail is AdminOperationResult.Failure)
        assertTrue((rpcFail as AdminOperationResult.Failure).reason.contains("part_14..16"))

        // Offline auth / viewer rejection blocks writes
        val viewerAuth = FakeAdminAuthManager(AdminAuthState.AccessDenied("viewer@city.test", "مرفوض"))
        val viewerRepo = createAdminRepo(phase4Engine, auth = viewerAuth)
        assertTrue(viewerRepo.setLightingState(CityLightingState(TimeOfDayPhase.NIGHT)) is AdminOperationResult.Failure)

        val offlineChecker = object : ConnectivityChecker { override fun isOnline(): Boolean = false }
        val offlineRepo = createAdminRepo(phase4Engine, connectivity = offlineChecker)
        assertTrue(offlineRepo.paintTiles(listOf(TilePaintChange(GridCoord(1, 1), CityTileType.ROAD))) is AdminOperationResult.Failure)
    }
}
