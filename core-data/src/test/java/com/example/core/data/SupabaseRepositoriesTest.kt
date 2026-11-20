package com.example.core.data

import com.example.cityengine.model.PlotStatus
import com.example.cityengine.world.StarterCityMap
import com.example.core.data.model.AdminHouseDraft
import com.example.core.data.model.AdminOperationResult
import com.example.core.data.model.CityDataState
import com.example.core.data.supabase.AdminDeviceAuthState
import com.example.core.data.supabase.AlwaysOnlineChecker
import com.example.core.data.supabase.ConnectivityChecker
import com.example.core.data.supabase.HousePublicDto
import com.example.core.data.supabase.InMemoryAdminDeviceCredentialStore
import com.example.core.data.supabase.PlotDto
import com.example.core.data.supabase.StoredDeviceCredential
import com.example.core.data.supabase.SupabaseCityAdminRepository
import com.example.core.data.supabase.SupabaseCityExplorerRepository
import com.example.core.data.supabase.SupabaseClientProvider
import com.example.core.data.supabase.SupabaseConfig
import com.example.core.data.supabase.SupabaseDeviceAuthRepository
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
import org.junit.Assert.assertNotNull
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

    private fun sampleAdminHousesJson(): String = """
        [
          {
            "id": "house_01",
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
            "allow_public_info": true,
            "show_family_count_publicly": true
          }
        ]
    """.trimIndent()

    @Test
    fun `explorer loads public houses without sending admin device headers or exposing private contacts`() = runBlocking {
        var sawAdminHeader = false
        val mockEngine = MockEngine { request ->
            if (request.headers.contains(SupabaseClientProvider.HEADER_ADMIN_DEVICE_ID) ||
                request.headers.contains(SupabaseClientProvider.HEADER_ADMIN_DEVICE_TOKEN)
            ) {
                sawAdminHeader = true
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
            autoLoadFromStorage = false,
            deviceHeaderProvider = null
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
        assertFalse("Explorer must never transmit Admin device headers!", sawAdminHeader)
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
    fun `one-time device enrollment stores token and opens directly on subsequent verified launches`() = runBlocking {
        var codeAccepted = false
        val store = InMemoryAdminDeviceCredentialStore(initialDeviceId = "dev_owner_001")

        val mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/rpc/activate_admin_device") -> {
                    if (!codeAccepted) {
                        codeAccepted = true
                        respond(
                            content = """{"code":"42501","message":"رمز التفعيل غير صحيح."}""",
                            status = HttpStatusCode.Forbidden,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    } else {
                        respond(
                            content = """{"device_id":"dev_owner_001","device_token":"tok_256_valid","device_name":"جهاز المالك","status":"ACTIVE","activated_at":"2026-11-20T07:00:00Z"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                }
                path.endsWith("/rpc/verify_admin_device") -> {
                    respond(
                        content = """{"authorized":true,"status":"ACTIVE","device_id":"dev_owner_001","device_name":"جهاز المالك"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }

        val client = SupabaseClientProvider.createClient(validConfig, mockEngine, autoLoadFromStorage = false)
        val authRepo = SupabaseDeviceAuthRepository(
            config = validConfig,
            credentialStore = store,
            clientProvider = { client },
            connectivityChecker = AlwaysOnlineChecker
        )

        // 1. Fresh device starts Unenrolled
        authRepo.verifyDeviceOnStartup()
        assertTrue(authRepo.authState.value is AdminDeviceAuthState.Unenrolled)
        assertNull(store.loadCredential())

        // 2. Bad activation code fails and leaves device Unenrolled
        authRepo.activateDevice("WRONG-CODE-1234", "جهاز المالك")
        assertTrue(authRepo.authState.value is AdminDeviceAuthState.Unenrolled)
        assertEquals("رمز التفعيل غير صحيح.", (authRepo.authState.value as AdminDeviceAuthState.Unenrolled).feedbackMessage)
        assertNull(store.loadCredential())

        // 3. Valid one-time code enrolls device and stores encrypted token
        authRepo.activateDevice("MADINA-1111-2222-3333-4444", "جهاز المالك")
        assertTrue("Expected Authorized, got ${authRepo.authState.value}", authRepo.authState.value is AdminDeviceAuthState.Authorized)
        assertTrue(authRepo.isAuthorizedDevice())
        assertEquals("tok_256_valid", store.loadCredential()?.deviceToken)

        // 4. Subsequent launch verifies directly with server without showing login/activation UI
        val secondLaunchRepo = SupabaseDeviceAuthRepository(
            config = validConfig,
            credentialStore = store,
            clientProvider = { client },
            connectivityChecker = AlwaysOnlineChecker
        )
        secondLaunchRepo.verifyDeviceOnStartup()
        assertTrue(secondLaunchRepo.authState.value is AdminDeviceAuthState.Authorized)
    }

    @Test
    fun `server revocation wipes stored credentials and blocks offline or revoked operations`() = runBlocking {
        var online = true
        var deviceActiveOnServer = true
        val connectivity = object : ConnectivityChecker {
            override fun isOnline(): Boolean = online
        }
        val store = InMemoryAdminDeviceCredentialStore(
            initialCredential = StoredDeviceCredential(
                deviceId = "dev_owner_002",
                deviceToken = "tok_active_002",
                deviceName = "Owner Phone"
            )
        )

        lateinit var authRepo: SupabaseDeviceAuthRepository
        val calledMutationRpcs = mutableListOf<String>()

        val mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/rpc/verify_admin_device") -> {
                    if (deviceActiveOnServer) {
                        respond(
                            content = """{"authorized":true,"status":"ACTIVE","device_id":"dev_owner_002","device_name":"Owner Phone"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    } else {
                        respond(
                            content = """{"authorized":false,"status":"REVOKED","reason":"تم إلغاء اعتماد الجهاز من لوحة مالك النظام"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                }
                path.endsWith("/city_layout") -> respond(sampleLayoutJson(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                path.endsWith("/plots") -> respond(samplePlotsJson(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                path.endsWith("/houses") -> respond(sampleAdminHousesJson(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                path.endsWith("/house_private_details") -> respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                path.contains("/rpc/admin_") -> {
                    calledMutationRpcs += path.substringAfterLast("/rpc/")
                    respond("""{"house_id":"house_99","plot_id":"plot_10"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
                else -> respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }

        val client = SupabaseClientProvider.createClient(
            config = validConfig,
            httpClientEngine = mockEngine,
            autoLoadFromStorage = false,
            deviceHeaderProvider = { authRepo.currentDeviceHeadersOrNull() }
        )
        authRepo = SupabaseDeviceAuthRepository(validConfig, store, { client }, connectivity)
        val adminRepo = SupabaseCityAdminRepository(
            config = validConfig,
            authRepository = authRepo,
            clientProvider = { client },
            connectivityChecker = connectivity,
            ioDispatcher = Dispatchers.IO,
            enableRealtime = false
        )

        // 1. Offline startup with stored credential MUST block access (cannot trust local cache alone)
        online = false
        authRepo.verifyDeviceOnStartup()
        assertTrue(authRepo.authState.value is AdminDeviceAuthState.OfflineVerificationBlocked)
        assertFalse(authRepo.isAuthorizedDevice())

        // 2. Online startup verifies and authorizes directly
        online = true
        authRepo.verifyDeviceOnStartup()
        assertTrue(authRepo.authState.value is AdminDeviceAuthState.Authorized)

        val sampleDraft = AdminHouseDraft(
            targetPlotId = "plot_10",
            ownerName = "سعد",
            familyMembersCount = 4,
            flag = StarterCityMap.flagPresets.first(),
            palette = StarterCityMap.palettePresets.first().palette
        )

        // 3. Offline sensitive operation MUST be blocked without calling admin RPC
        online = false
        val offlineMutation = adminRepo.addHouse(sampleDraft)
        assertTrue(offlineMutation is AdminOperationResult.Failure)
        assertTrue(calledMutationRpcs.isEmpty())

        // 4. Server revokes device mid-session -> pre-mutation verification catches REVOKED, clears Keystore, blocks mutation
        online = true
        deviceActiveOnServer = false
        val revokedMutation = adminRepo.addHouse(sampleDraft)
        assertTrue(revokedMutation is AdminOperationResult.Failure)
        assertTrue(calledMutationRpcs.isEmpty())
        assertTrue(authRepo.authState.value is AdminDeviceAuthState.Revoked)
        assertNull("Credential store must be wiped upon revocation", store.loadCredential())
    }

    @Test
    fun `authorized admin sends device headers and executes all 5 rpc endpoints cleanly`() = runBlocking {
        val calledRpcs = mutableListOf<String>()
        val capturedDeviceHeaders = mutableListOf<Pair<String?, String?>>()
        var failNextWithRls = false

        val store = InMemoryAdminDeviceCredentialStore(
            initialCredential = StoredDeviceCredential(
                deviceId = "dev_admin_777",
                deviceToken = "tok_admin_secret_777",
                deviceName = "Owner Tablet"
            )
        )
        lateinit var authRepo: SupabaseDeviceAuthRepository

        val mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            if (path.contains("/rpc/admin_") || path.endsWith("/houses")) {
                capturedDeviceHeaders += request.headers[SupabaseClientProvider.HEADER_ADMIN_DEVICE_ID] to
                    request.headers[SupabaseClientProvider.HEADER_ADMIN_DEVICE_TOKEN]
            }
            if (failNextWithRls && path.contains("/rpc/admin_")) {
                failNextWithRls = false
                return@MockEngine respond(
                    content = """{"code":"42501","message":"permission denied for table houses"}""",
                    status = HttpStatusCode.Forbidden,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            when {
                path.endsWith("/rpc/verify_admin_device") -> respond(
                    content = """{"authorized":true,"status":"ACTIVE","device_id":"dev_admin_777","device_name":"Owner Tablet"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                path.endsWith("/city_layout") -> respond(
                    sampleLayoutJson(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")
                )
                path.endsWith("/plots") -> respond(
                    samplePlotsJson(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")
                )
                path.endsWith("/houses") -> respond(
                    sampleAdminHousesJson(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")
                )
                path.endsWith("/house_private_details") -> respond(
                    "[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")
                )
                path.contains("/rpc/admin_") -> {
                    calledRpcs += path.substringAfterLast("/rpc/")
                    respond(
                        content = """{"house_id":"house_99","plot_id":"plot_10"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }

        val client = SupabaseClientProvider.createClient(
            config = validConfig,
            httpClientEngine = mockEngine,
            autoLoadFromStorage = false,
            deviceHeaderProvider = { authRepo.currentDeviceHeadersOrNull() }
        )
        authRepo = SupabaseDeviceAuthRepository(validConfig, store, { client }, AlwaysOnlineChecker)
        val adminRepo = SupabaseCityAdminRepository(
            config = validConfig,
            authRepository = authRepo,
            clientProvider = { client },
            connectivityChecker = AlwaysOnlineChecker,
            ioDispatcher = Dispatchers.IO,
            enableRealtime = false
        )

        authRepo.verifyDeviceOnStartup()
        assertTrue(authRepo.isAuthorizedDevice())

        val initialAdminState = withTimeout(10_000) {
            adminRepo.observeAdminSnapshot().first { it !is CityDataState.Loading }
        }
        assertTrue("Expected Admin Success, got $initialAdminState", initialAdminState is CityDataState.Success)

        val sampleDraft = AdminHouseDraft(
            targetPlotId = "plot_10",
            ownerName = "فيصل",
            familyMembersCount = 4,
            flag = StarterCityMap.flagPresets.first(),
            palette = StarterCityMap.palettePresets.first().palette
        )

        assertTrue(adminRepo.addHouse(sampleDraft) is AdminOperationResult.Success)
        assertTrue(adminRepo.updateHouse("house_01", sampleDraft) is AdminOperationResult.Success)
        assertTrue(adminRepo.changeHouseColor("house_01", StarterCityMap.palettePresets[2].palette) is AdminOperationResult.Success)
        assertTrue(adminRepo.moveHouse("house_01", "plot_10") is AdminOperationResult.Success)
        assertTrue(adminRepo.deleteHouse("house_01") is AdminOperationResult.Success)

        assertEquals(
            listOf(
                "admin_add_house",
                "admin_update_house",
                "admin_change_house_color",
                "admin_move_house",
                "admin_delete_house"
            ),
            calledRpcs
        )
        assertTrue(capturedDeviceHeaders.isNotEmpty())
        assertTrue(
            capturedDeviceHeaders.all { it.first == "dev_admin_777" && it.second == "tok_admin_secret_777" }
        )

        // Server RLS 42501 failure revokes device cleanly
        failNextWithRls = true
        val rlsFailure = adminRepo.deleteHouse("house_01")
        assertTrue(rlsFailure is AdminOperationResult.Failure)
        assertNotNull((rlsFailure as AdminOperationResult.Failure).reason)
        assertTrue(authRepo.authState.value is AdminDeviceAuthState.Revoked)
    }
}
