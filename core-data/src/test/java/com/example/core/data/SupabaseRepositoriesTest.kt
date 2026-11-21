package com.example.core.data

import com.example.cityengine.model.PlotStatus
import com.example.cityengine.world.StarterCityMap
import com.example.core.data.model.AdminHouseDraft
import com.example.core.data.model.AdminOperationResult
import com.example.core.data.model.CityDataState
import com.example.core.data.supabase.AdminAuthState
import com.example.core.data.supabase.AlwaysOnlineChecker
import com.example.core.data.supabase.ConnectivityChecker
import com.example.core.data.supabase.HousePublicDto
import com.example.core.data.supabase.PlotDto
import com.example.core.data.supabase.SupabaseAdminAuthRepository
import com.example.core.data.supabase.SupabaseCityAdminRepository
import com.example.core.data.supabase.SupabaseCityExplorerRepository
import com.example.core.data.supabase.SupabaseClientProvider
import com.example.core.data.supabase.SupabaseConfig
import com.example.core.data.supabase.SupabaseMapper
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.time.Instant
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

    private fun sampleTokenResponseJson(
        accessToken: String,
        refreshToken: String,
        userId: String,
        email: String
    ): String = """
        {
          "access_token": "$accessToken",
          "token_type": "bearer",
          "expires_in": 3600,
          "refresh_token": "$refreshToken",
          "user": {
            "id": "$userId",
            "aud": "authenticated",
            "role": "authenticated",
            "email": "$email"
          }
        }
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
    fun `admin login with email and password verifies ADMIN role executes all 5 rpcs and supports sign out`() = runBlocking {
        val calledRpcs = mutableListOf<String>()
        val capturedAuthHeaders = mutableListOf<String?>()
        var logoutCalled = false

        val mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            if (path.contains("/rpc/admin_") || path.endsWith("/houses")) {
                capturedAuthHeaders += request.headers[HttpHeaders.Authorization]
            }
            when {
                path.endsWith("/auth/v1/token") -> respond(
                    content = sampleTokenResponseJson(
                        accessToken = "jwt_admin_valid_123",
                        refreshToken = "rt_admin_123",
                        userId = "uid_owner_1",
                        email = "owner@madina.test"
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                path.endsWith("/auth/v1/logout") -> {
                    logoutCalled = true
                    respond("", HttpStatusCode.NoContent)
                }
                path.endsWith("/profiles") -> respond(
                    content = """[{"id":"uid_owner_1","display_name":"مالك المدينة","role":"ADMIN"}]""",
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

        val sessionManager = MemorySessionManager()
        val client = SupabaseClientProvider.createClient(
            config = validConfig,
            httpClientEngine = mockEngine,
            sessionManager = sessionManager,
            autoLoadFromStorage = false
        )
        val authRepo = SupabaseAdminAuthRepository(validConfig, { client }, AlwaysOnlineChecker)
        val adminRepo = SupabaseCityAdminRepository(
            config = validConfig,
            authRepository = authRepo,
            clientProvider = { client },
            connectivityChecker = AlwaysOnlineChecker,
            ioDispatcher = Dispatchers.IO,
            enableRealtime = false
        )

        // 1. Fresh launch with no stored session starts Unauthenticated
        authRepo.restoreAndVerifySession()
        assertTrue(authRepo.authState.value is AdminAuthState.Unauthenticated)
        assertFalse(authRepo.isAuthenticatedAdmin())

        // 2. Sign in with valid ADMIN credentials
        authRepo.signIn("owner@madina.test", "StrongPass#2026")
        val authed = authRepo.authState.value
        assertTrue("Expected AuthenticatedAdmin, got $authed", authed is AdminAuthState.AuthenticatedAdmin)
        assertEquals("owner@madina.test", (authed as AdminAuthState.AuthenticatedAdmin).email)
        assertEquals("مالك المدينة", authed.displayName)
        assertTrue(authRepo.isAuthenticatedAdmin())

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
        assertTrue(capturedAuthHeaders.isNotEmpty())
        assertTrue(capturedAuthHeaders.all { it == "Bearer jwt_admin_valid_123" })

        // 3. Sign out clears session & blocks subsequent mutations
        authRepo.signOut()
        assertTrue(logoutCalled)
        assertTrue(authRepo.authState.value is AdminAuthState.Unauthenticated)
        assertNull(sessionManager.loadSession())
        val afterSignOutAdd = adminRepo.addHouse(sampleDraft)
        assertTrue(afterSignOutAdd is AdminOperationResult.Failure)
    }

    @Test
    fun `viewer role account is rejected on sign in and blocked from admin mutations`() = runBlocking {
        var logoutCalled = false
        val calledRpcs = mutableListOf<String>()

        val mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/auth/v1/token") -> respond(
                    content = sampleTokenResponseJson(
                        accessToken = "jwt_viewer_only",
                        refreshToken = "rt_viewer_only",
                        userId = "uid_viewer_99",
                        email = "viewer@madina.test"
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                path.endsWith("/auth/v1/logout") -> {
                    logoutCalled = true
                    respond("", HttpStatusCode.NoContent)
                }
                path.endsWith("/profiles") -> respond(
                    content = """[{"id":"uid_viewer_99","display_name":"مستخدم عادي","role":"VIEWER"}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                path.contains("/rpc/admin_") -> {
                    calledRpcs += path
                    respond("""{}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
                else -> respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }

        val sessionManager = MemorySessionManager()
        val client = SupabaseClientProvider.createClient(
            config = validConfig,
            httpClientEngine = mockEngine,
            sessionManager = sessionManager,
            autoLoadFromStorage = false
        )
        val authRepo = SupabaseAdminAuthRepository(validConfig, { client }, AlwaysOnlineChecker)
        val adminRepo = SupabaseCityAdminRepository(
            config = validConfig,
            authRepository = authRepo,
            clientProvider = { client },
            connectivityChecker = AlwaysOnlineChecker,
            ioDispatcher = Dispatchers.IO,
            enableRealtime = false
        )

        authRepo.signIn("viewer@madina.test", "ViewerPass123!")

        val state = authRepo.authState.value
        assertTrue("Expected AccessDenied for VIEWER, got $state", state is AdminAuthState.AccessDenied)
        assertEquals("viewer@madina.test", (state as AdminAuthState.AccessDenied).email)
        assertFalse(authRepo.isAuthenticatedAdmin())
        assertTrue("VIEWER session must be signed out immediately", logoutCalled)
        assertNull(sessionManager.loadSession())

        val attempt = adminRepo.addHouse(
            AdminHouseDraft(
                targetPlotId = "plot_10",
                ownerName = "تجربة مرفوضة",
                familyMembersCount = 3,
                flag = StarterCityMap.flagPresets.first(),
                palette = StarterCityMap.palettePresets.first().palette
            )
        )
        assertTrue(attempt is AdminOperationResult.Failure)
        assertTrue(calledRpcs.isEmpty())
    }

    @Test
    fun `bad password and offline login attempts are rejected cleanly`() = runBlocking {
        var online = true
        val connectivity = object : ConnectivityChecker {
            override fun isOnline(): Boolean = online
        }
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/auth/v1/token")) {
                respond(
                    content = """{"error":"invalid_grant","error_description":"Invalid login credentials"}""",
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val client = SupabaseClientProvider.createClient(
            config = validConfig,
            httpClientEngine = mockEngine,
            autoLoadFromStorage = false
        )
        val authRepo = SupabaseAdminAuthRepository(validConfig, { client }, connectivity)

        // 1. Offline sign-in rejected before HTTP
        online = false
        authRepo.signIn("owner@madina.test", "WrongPass")
        val offlineState = authRepo.authState.value
        assertTrue(offlineState is AdminAuthState.Unauthenticated)
        assertTrue((offlineState as AdminAuthState.Unauthenticated).feedbackMessage!!.contains("إنترنت"))

        // 2. Wrong password rejected with Arabic message
        online = true
        authRepo.signIn("owner@madina.test", "WrongPass")
        val badCredsState = authRepo.authState.value
        assertTrue(badCredsState is AdminAuthState.Unauthenticated)
        assertEquals(
            "البريد الإلكتروني أو كلمة المرور غير صحيحة.",
            (badCredsState as AdminAuthState.Unauthenticated).feedbackMessage
        )
    }

    @Test
    fun `session restore refreshes expired token safely and handles 401 or 42501 failure`() = runBlocking {
        var refreshSucceeded = false
        var failNextAdminRpcWith: String? = null
        val expiredSession = UserSession(
            accessToken = "jwt_expired_old",
            refreshToken = "rt_valid_1",
            expiresIn = 3600L,
            tokenType = "bearer",
            user = UserInfo(id = "uid_owner_1", aud = "authenticated", email = "owner@madina.test"),
            expiresAt = Instant.fromEpochSeconds(1_000_000L) // Expired in the past
        )
        val sessionManager = MemorySessionManager()
        sessionManager.saveSession(expiredSession)

        val mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            if (failNextAdminRpcWith != null && path.contains("/rpc/admin_")) {
                val mode = failNextAdminRpcWith
                failNextAdminRpcWith = null
                return@MockEngine if (mode == "401") {
                    respond(
                        content = """{"message":"JWT expired"}""",
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else {
                    respond(
                        content = """{"code":"42501","message":"permission denied for table houses"}""",
                        status = HttpStatusCode.Forbidden,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
            when {
                path.endsWith("/auth/v1/token") -> {
                    refreshSucceeded = true
                    respond(
                        content = sampleTokenResponseJson(
                            accessToken = "jwt_refreshed_fresh",
                            refreshToken = "rt_valid_2",
                            userId = "uid_owner_1",
                            email = "owner@madina.test"
                        ),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                path.endsWith("/auth/v1/logout") -> respond("", HttpStatusCode.NoContent)
                path.endsWith("/profiles") -> respond(
                    content = """[{"id":"uid_owner_1","display_name":"مالك المدينة","role":"ADMIN"}]""",
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
                else -> respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }

        val client = SupabaseClientProvider.createClient(
            config = validConfig,
            httpClientEngine = mockEngine,
            sessionManager = sessionManager,
            autoLoadFromStorage = true,
            alwaysAutoRefresh = false
        )
        val authRepo = SupabaseAdminAuthRepository(validConfig, { client }, AlwaysOnlineChecker)
        val adminRepo = SupabaseCityAdminRepository(
            config = validConfig,
            authRepository = authRepo,
            clientProvider = { client },
            connectivityChecker = AlwaysOnlineChecker,
            ioDispatcher = Dispatchers.IO,
            enableRealtime = false
        )

        // 1. Restoring near/past-expiry session refreshes token and verifies ADMIN role
        authRepo.restoreAndVerifySession()
        assertTrue("Expected refresh_token grant to run", refreshSucceeded)
        assertTrue("Expected AuthenticatedAdmin, got ${authRepo.authState.value}", authRepo.authState.value is AdminAuthState.AuthenticatedAdmin)
        assertEquals("jwt_refreshed_fresh", sessionManager.loadSession()?.accessToken)

        val snap = withTimeout(10_000) {
            adminRepo.observeAdminSnapshot().first { it !is CityDataState.Loading }
        }
        assertTrue(snap is CityDataState.Success)

        // 2. Mid-session 401 JWT expired forces SessionExpired and clears session
        failNextAdminRpcWith = "401"
        val expiredResult = adminRepo.deleteHouse("house_01")
        assertTrue(expiredResult is AdminOperationResult.Failure)
        assertTrue(authRepo.authState.value is AdminAuthState.SessionExpired)
        assertNull(sessionManager.loadSession())

        // 3. Re-authenticate, then mid-session 42501 permission denied forces AccessDenied
        client.auth.importSession(
            UserSession(
                accessToken = "jwt_valid_again",
                refreshToken = "rt_valid_3",
                expiresIn = 3600L,
                tokenType = "bearer",
                user = UserInfo(id = "uid_owner_1", aud = "authenticated", email = "owner@madina.test")
            )
        )
        authRepo.restoreAndVerifySession()
        assertTrue(authRepo.authState.value is AdminAuthState.AuthenticatedAdmin)

        failNextAdminRpcWith = "42501"
        val rlsFailure = adminRepo.deleteHouse("house_01")
        assertTrue(rlsFailure is AdminOperationResult.Failure)
        assertNotNull((rlsFailure as AdminOperationResult.Failure).reason)
        assertTrue(authRepo.authState.value is AdminAuthState.AccessDenied)
    }
}
