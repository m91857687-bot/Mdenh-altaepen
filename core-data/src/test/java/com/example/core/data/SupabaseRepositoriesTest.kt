package com.example.core.data

import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.model.TimeOfDayPhase
import com.example.cityengine.world.StarterCityMap
import com.example.core.data.model.CityDataState
import com.example.core.data.supabase.AlwaysOnlineChecker
import com.example.core.data.supabase.ConnectivityChecker
import com.example.core.data.supabase.HousePublicDto
import com.example.core.data.supabase.PlotDto
import com.example.core.data.supabase.SupabaseCityExplorerRepository
import com.example.core.data.supabase.SupabaseClientProvider
import com.example.core.data.supabase.SupabaseConfig
import com.example.core.data.supabase.SupabaseMapper
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SupabaseRepositoriesTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val validConfig = SupabaseConfig(
        url = "https://example.supabase.co",
        anonKey = "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiYW5vbiJ9.sig"
    )

    private fun sampleLayoutJson(): String = """
        [
          {
            "id": "main_city",
            "version": 1,
            "grid_width": 12,
            "grid_height": 12,
            "roads": [{"x":3,"y":0}],
            "sidewalks": [{"x":4,"y":4}],
            "parks": [{"x":5,"y":5}],
            "trees": [{"id":"tree_01","x":0,"y":0,"scale":1.0,"variant":0}],
            "lamp_posts": [{"id":"lamp_01","x":2,"y":2}]
          }
        ]
    """.trimIndent()

    private fun samplePlotsJson(plot10Status: String = "AVAILABLE"): String = """
        [
          {"id":"plot_01","layout_id":"main_city","plot_code":"P-01","grid_x":1,"grid_y":1,"status":"OCCUPIED"},
          {"id":"plot_10","layout_id":"main_city","plot_code":"P-10","grid_x":5,"grid_y":10,"status":"$plot10Status"}
        ]
    """.trimIndent()

    private fun samplePublicHousesJson(): String = """
        [
          {
            "house_id": "house_01",
            "plot_id": "plot_01",
            "resident_name": "أبو عبدالله الحربي",
            "country_code": "SA",
            "country": "المملكة العربية السعودية",
            "flag_stripes": [4279664454, 4294967295, 4279664454],
            "flag_vertical": false,
            "family_size": 5,
            "house_color": "terracotta",
            "palette_json": {"wallLeftHex":4294373346,"wallRightHex":4293121987,"roofLeftHex":4291320378,"roofRightHex":4288626725,"roofTrimHex":4286788124,"doorHex":4284299817,"windowHex":4293129466},
            "grid_x": 1,
            "grid_y": 1,
            "public_role": "منسق مجلس الحي",
            "public_about": "أهلاً بكم",
            "approved_phone": null,
            "approved_email": null
          }
        ]
    """.trimIndent()

    @Test
    fun `explorer loads public houses without admin credentials or exposing private contacts`() = runBlocking {
        var sawAdminUserBearer = false
        val mockEngine = MockEngine { request ->
            val authHeader = request.headers[HttpHeaders.Authorization]
            if (authHeader != null && authHeader != "Bearer ${validConfig.normalizedAnonKey}") {
                sawAdminUserBearer = true
            }
            val path = request.url.encodedPath
            val body = when {
                path.endsWith("/city_layout") -> sampleLayoutJson()
                path.endsWith("/plots") -> samplePlotsJson()
                path.endsWith("/house_public_details") -> samplePublicHousesJson()
                else -> "[]"
            }
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = SupabaseClientProvider.createClient(
            config = validConfig,
            httpClientEngine = mockEngine,
            autoLoadFromStorage = false
        )

        val repo = SupabaseCityExplorerRepository(
            config = validConfig,
            clientProvider = { client },
            connectivityChecker = AlwaysOnlineChecker,
            ioDispatcher = Dispatchers.IO,
            enableRealtime = false
        )

        val state = withTimeout(10_000) {
            repo.observeCitySnapshot().first { it !is CityDataState.Loading }
        }
        assertTrue("Expected Success, got $state", state is CityDataState.Success)
        assertFalse("Explorer must remain anonymous!", sawAdminUserBearer)
        val snap = (state as CityDataState.Success).data
        assertFalse(snap.isTestData)
        assertEquals(2, snap.scene.plots.size)
        assertEquals(1, snap.scene.houses.size)
        val details = snap.houseDetailsById["house_01"]!!
        assertEquals("أبو عبدالله الحربي", details.ownerName)
        assertNull(details.approvedPhone)
        assertNull(details.approvedEmail)
        assertFalse(details.isTestData)
    }

    @Test
    fun `explorer handles offline state and empty state cleanly`() = runBlocking {
        val offlineChecker = object : ConnectivityChecker {
            override fun isOnline(): Boolean = false
        }
        val offlineRepo = SupabaseCityExplorerRepository(
            config = validConfig,
            clientProvider = { null },
            connectivityChecker = offlineChecker,
            ioDispatcher = Dispatchers.IO,
            enableRealtime = false
        )
        val offlineState = withTimeout(10_000) {
            offlineRepo.observeCitySnapshot().first { it !is CityDataState.Loading }
        }
        assertTrue("Expected Offline, got $offlineState", offlineState is CityDataState.Offline)

        val emptyEngine = MockEngine {
            respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = SupabaseClientProvider.createClient(validConfig, emptyEngine, autoLoadFromStorage = false)
        val emptyRepo = SupabaseCityExplorerRepository(
            config = validConfig,
            clientProvider = { client },
            connectivityChecker = AlwaysOnlineChecker,
            ioDispatcher = Dispatchers.IO,
            enableRealtime = false
        )
        val emptyState = withTimeout(10_000) {
            emptyRepo.observeCitySnapshot().first { it !is CityDataState.Loading }
        }
        assertTrue("Expected Empty, got $emptyState", emptyState is CityDataState.Empty)
    }

    @Test
    fun `explorer applies incremental realtime insert update and delete without full reload`() = runBlocking {
        var httpCalls = 0
        val mockEngine = MockEngine { request ->
            httpCalls++
            val path = request.url.encodedPath
            val body = when {
                path.endsWith("/city_layout") -> sampleLayoutJson()
                path.endsWith("/plots") -> samplePlotsJson()
                path.endsWith("/house_public_details") -> samplePublicHousesJson()
                else -> "[]"
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = SupabaseClientProvider.createClient(validConfig, mockEngine, autoLoadFromStorage = false)
        val repo = SupabaseCityExplorerRepository(
            config = validConfig,
            clientProvider = { client },
            connectivityChecker = AlwaysOnlineChecker,
            ioDispatcher = Dispatchers.IO,
            enableRealtime = false
        )
        val initialState = withTimeout(10_000) {
            repo.observeCitySnapshot().first { it !is CityDataState.Loading }
        }
        assertTrue("Expected Success, got $initialState", initialState is CityDataState.Success)
        val initialHttpCalls = httpCalls

        val newHouseDto = HousePublicDto(
            houseId = "house_10",
            plotId = "plot_10",
            residentName = "سالم الدوسري",
            countryCode = "SA",
            country = "المملكة العربية السعودية",
            flagStripes = listOf(0xFF167D46, 0xFFFFFFFF, 0xFF167D46),
            flagVertical = false,
            familySize = 4,
            houseColor = "cobalt",
            paletteJson = SupabaseMapper.toPaletteDto(StarterCityMap.palettePresets[1].palette),
            gridX = 5,
            gridY = 10,
            publicRole = "جار جديد"
        )
        repo.applyIncrementalPlotUpsert(PlotDto("plot_10", "main_city", "P-10", 5, 10, "OCCUPIED"))
        repo.applyIncrementalHouseUpsert(newHouseDto)

        var snap = (repo.observeCitySnapshot().first() as CityDataState.Success).data
        assertEquals(2, snap.scene.houses.size)
        assertEquals("سالم الدوسري", snap.houseDetailsById["house_10"]?.ownerName)
        assertEquals(PlotStatus.OCCUPIED, snap.scene.plots.first { it.id == "plot_10" }.status)

        repo.applyIncrementalHouseUpsert(newHouseDto.copy(residentName = "سالم الدوسري (محدث)"))
        snap = (repo.observeCitySnapshot().first() as CityDataState.Success).data
        assertEquals("سالم الدوسري (محدث)", snap.houseDetailsById["house_10"]?.ownerName)

        repo.applyIncrementalHouseDelete("house_10")
        repo.applyIncrementalPlotUpsert(PlotDto("plot_10", "main_city", "P-10", 5, 10, "AVAILABLE"))
        snap = (repo.observeCitySnapshot().first() as CityDataState.Success).data
        assertEquals(1, snap.scene.houses.size)
        assertNull(snap.houseDetailsById["house_10"])
        assertEquals(PlotStatus.AVAILABLE, snap.scene.plots.first { it.id == "plot_10" }.status)

        assertEquals(initialHttpCalls, httpCalls)
    }

    @Test
    fun `explorer stays backward-compatible when builder tables return 404 and maps expanded builder payloads`() = runBlocking {
        val phase4OnlyEngine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/city_layout") -> respond(sampleLayoutJson(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                path.endsWith("/plots") -> respond(samplePlotsJson(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                path.endsWith("/house_public_details") -> respond(samplePublicHousesJson(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                else -> respond("""{"code":"PGRST205","message":"table not found"}""", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val clientLegacy = SupabaseClientProvider.createClient(validConfig, phase4OnlyEngine, autoLoadFromStorage = false)
        val repoLegacy = SupabaseCityExplorerRepository(validConfig, { clientLegacy }, AlwaysOnlineChecker, Dispatchers.IO, enableRealtime = false)
        val legacyState = withTimeout(10_000) { repoLegacy.observeCitySnapshot().first { it !is CityDataState.Loading } }
        assertTrue("Expected graceful Success even when optional builder tables 404, got $legacyState", legacyState is CityDataState.Success)
        assertEquals(1, (legacyState as CityDataState.Success).data.scene.houses.size)

        val expandedEngine = MockEngine { request ->
            val path = request.url.encodedPath
            val body = when {
                path.endsWith("/city_layout") -> """
                    [{
                      "id": "main_city", "version": 2, "grid_width": 20, "grid_height": 20,
                      "roads": [{"x":3,"y":0}], "sidewalks": [], "parks": [], "trees": [], "lamp_posts": [],
                      "custom_tiles": [{"x":15,"y":15,"type":"WATER","texture":"DEFAULT"}],
                      "lighting_config": {"phase":"NIGHT","sunAzimuthDeg":40,"ambientDimming":0.2,"nightLampAutoOn":true}
                    }]
                """.trimIndent()
                path.endsWith("/plots") -> """
                    [
                      {"id":"plot_01","layout_id":"main_city","plot_code":"P-01","grid_x":1,"grid_y":1,"status":"OCCUPIED","width_cells":2,"height_cells":1,"rotation_deg":90},
                      {"id":"plot_99","layout_id":"main_city","plot_code":"P-99","grid_x":14,"grid_y":2,"status":"UNAVAILABLE","width_cells":2,"height_cells":2}
                    ]
                """.trimIndent()
                path.endsWith("/house_public_details") -> samplePublicHousesJson()
                path.endsWith("/city_districts") -> """[{"id":"d1","name_ar":"الحي المركزي","code":"CIVIC","grid_x":12,"grid_y":0,"width_cells":8,"height_cells":8}]"""
                path.endsWith("/city_buildings") -> """[{"id":"b1","name_ar":"القصر الرئاسي","building_type":"PRESIDENTIAL_PALACE","grid_x":14,"grid_y":5,"width_cells":3,"height_cells":3}]"""
                path.endsWith("/city_environment_props") -> """[{"id":"p1","prop_type":"FOUNTAIN","grid_x":13,"grid_y":2}]"""
                path.endsWith("/city_lights") -> """[{"id":"l1","light_type":"FOUNTAIN_GLOW","grid_x":13,"grid_y":2,"is_enabled":true}]"""
                else -> "[]"
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val clientExpanded = SupabaseClientProvider.createClient(validConfig, expandedEngine, autoLoadFromStorage = false)
        val repoExpanded = SupabaseCityExplorerRepository(validConfig, { clientExpanded }, AlwaysOnlineChecker, Dispatchers.IO, enableRealtime = false)
        val expandedState = withTimeout(10_000) { repoExpanded.observeCitySnapshot().first { it !is CityDataState.Loading } }
        assertTrue("Expected Success for expanded payload, got $expandedState", expandedState is CityDataState.Success)
        val scene = (expandedState as CityDataState.Success).data.scene
        assertEquals(20, scene.gridWidth)
        assertEquals(TimeOfDayPhase.NIGHT, scene.lightingState.phase)
        assertEquals(CityTileType.WATER, scene.tiles[GridCoord(15, 15)])
        assertEquals(PlotStatus.UNAVAILABLE, scene.plots.first { it.id == "plot_99" }.status)
        assertEquals(1, scene.districts.size)
        assertEquals(1, scene.publicBuildings.size)
        assertEquals(1, scene.environmentProps.size)
        assertEquals(1, scene.lights.size)
    }
}
