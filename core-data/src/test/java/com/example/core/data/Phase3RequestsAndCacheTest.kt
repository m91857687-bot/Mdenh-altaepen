package com.example.core.data

import com.example.cityengine.model.ArabCountriesRegistry
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.House
import com.example.cityengine.world.StarterCityMap
import com.example.core.data.local.CachedCitySnapshotBundle
import com.example.core.data.local.ExplorerSnapshotCache
import com.example.core.data.local.LocalCityAdminRepository
import com.example.core.data.model.AdminHouseDraft
import com.example.core.data.model.AdminOperationResult
import com.example.core.data.model.CityDataState
import com.example.core.data.model.HouseRequestDraft
import com.example.core.data.model.HouseRequestStatus
import com.example.core.data.model.HouseRequestSubmissionResult
import com.example.core.data.model.HouseResidentSourceRecord
import com.example.core.data.privacy.HousePrivacySanitizer
import com.example.core.data.supabase.AlwaysOnlineChecker
import com.example.core.data.supabase.CityLayoutDto
import com.example.core.data.supabase.EnvironmentPropDto
import com.example.core.data.supabase.HousePublicDto
import com.example.core.data.supabase.PlotDto
import com.example.core.data.supabase.PublicBuildingDto
import com.example.core.data.supabase.SupabaseCityExplorerRepository
import com.example.core.data.supabase.SupabaseClientProvider
import com.example.core.data.supabase.SupabaseConfig
import com.example.core.data.supabase.SupabaseMapper
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.content.TextContent
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class Phase3RequestsAndCacheTest {

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

    @Test
    fun `house request draft validation excludes family count and checks fields cleanly`() {
        assertNull(HouseRequestDraft("خالد العتيبي", "0551234567", "khalid@madina.test", "قرب الحديقة").validate())
        assertNull(HouseRequestDraft("سارة", null, null, null).validate())
        assertNotNull(HouseRequestDraft("أ").validate())
        assertNotNull(HouseRequestDraft("خالد", email = "not-an-email").validate())
        assertNotNull(HouseRequestDraft("خالد", phone = "12").validate())
    }

    @Test
    fun `request status covers all 7 lifecycle stages and backward PENDING mapping`() {
        assertEquals(7, HouseRequestStatus.entries.size)
        assertEquals(HouseRequestStatus.NEW, HouseRequestStatus.fromServer("PENDING"))
        assertEquals(HouseRequestStatus.BUILDING_HOUSE, HouseRequestStatus.fromServer("building_house"))
        assertTrue(HouseRequestStatus.NEW.allowedNextStatuses().contains(HouseRequestStatus.UNDER_REVIEW))
        assertTrue(HouseRequestStatus.BUILDING_HOUSE.allowedNextStatuses().contains(HouseRequestStatus.COMPLETED))
        assertTrue(HouseRequestStatus.COMPLETED.allowedNextStatuses().isEmpty())
    }

    @Test
    fun `privacy sanitizer hides owner name when allowOwnerNamePublic is false while preserving title`() {
        val house = House(
            id = "h_priv",
            plotId = "plot_01",
            coord = GridCoord(1, 1),
            ownerName = "الاسم السري",
            familyMembersCount = 4,
            flag = ArabCountriesRegistry.findByCode("OM")!!.flagStyle,
            palette = StarterCityMap.palettePresets.first().palette,
            houseTitle = "بيت المجد",
            publicStatus = "مفتوح للضيافة",
            allowOwnerNamePublic = false
        )
        val sanitized = HousePrivacySanitizer.toPublicDetails(
            HouseResidentSourceRecord(
                house = house,
                rawPhoneNumber = "+96890001111",
                allowPhonePublic = false,
                allowEmailPublic = false,
                allowOwnerNamePublic = false,
                houseTitle = "بيت المجد",
                publicStatus = "مفتوح للضيافة",
                isTestData = false
            )
        )
        assertNull(sanitized.visibleOwnerName)
        assertEquals("غير معروض", sanitized.ownerName)
        assertEquals("بيت المجد", sanitized.houseTitle)
        assertEquals("مفتوح للضيافة", sanitized.publicStatus)
        assertNull(sanitized.approvedPhone)
    }

    @Test
    fun `explorer snapshot cache persists and restores houses metadata buildings and flags`() {
        val tempFile = File.createTempFile("explorer_cache_test", ".json")
        try {
            val cache = ExplorerSnapshotCache(tempFile)
            val bundle = CachedCitySnapshotBundle(
                layout = CityLayoutDto(id = "main_city", version = 3, gridWidth = 14, gridHeight = 14),
                plots = listOf(PlotDto("plot_01", "main_city", "P-01", 1, 1, "OCCUPIED")),
                publicHouses = listOf(
                    HousePublicDto(
                        houseId = "house_01",
                        plotId = "plot_01",
                        residentName = "غير معروض",
                        countryCode = "SY",
                        country = "سوريا",
                        flagStripes = emptyList(),
                        familySize = 0,
                        houseColor = "terracotta",
                        paletteJson = SupabaseMapper.toPaletteDto(StarterCityMap.palettePresets.first().palette),
                        gridX = 1,
                        gridY = 1,
                        houseTitle = "بيت الشام",
                        publicStatus = "جاهز"
                    )
                ),
                buildings = listOf(
                    PublicBuildingDto(
                        id = "bld_1",
                        nameAr = "المركز الثقافي",
                        buildingType = "MUNICIPALITY",
                        gridX = 8,
                        gridY = 4,
                        descriptionAr = "وصف تعريفي",
                        publicInfoAr = "ساعات العمل 8-4"
                    )
                ),
                props = listOf(
                    EnvironmentPropDto(
                        id = "prop_flag_eg",
                        propType = "CITY_FLAG",
                        gridX = 6,
                        gridY = 6,
                        variant = 12,
                        labelAr = "مصر",
                        assetKey = "flag_eg"
                    )
                )
            )
            cache.saveBundle(bundle)
            val restored = cache.loadSnapshot()
            assertNotNull(restored)
            assertEquals("بيت الشام", restored!!.houseDetailsById["house_01"]?.houseTitle)
            assertFalse(restored.houseDetailsById["house_01"]!!.allowOwnerNamePublic)
            assertNotNull(restored.houseDetailsById["house_01"]?.flag?.drawableResId)
            assertEquals("وصف تعريفي", restored.scene.publicBuildings.first().descriptionAr)
            assertEquals("ساعات العمل 8-4", restored.scene.publicBuildings.first().publicInfoAr)
            assertEquals("flag_eg", restored.scene.environmentProps.first().assetKey)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `supabase explorer submits house request without family size via anon RPC`() = runBlocking {
        var capturedRequest: HttpRequestData? = null
        var capturedBodyText = ""
        val mockEngine = MockEngine { request ->
            capturedRequest = request
            capturedBodyText = (request.body as? TextContent)?.text.orEmpty()
            respond(
                content = """{"request_id":"req-uuid-100","status":"NEW"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = SupabaseClientProvider.createClient(validConfig, mockEngine, autoLoadFromStorage = false)
        val repo = SupabaseCityExplorerRepository(
            config = validConfig,
            clientProvider = { client },
            connectivityChecker = AlwaysOnlineChecker,
            enableRealtime = false
        )

        val result = repo.submitHouseRequest(
            HouseRequestDraft(
                applicantName = "طارق الحسن",
                phone = "0550001122",
                email = "tariq@madina.test",
                notes = "أرغب في قطعة قرب الحديقة"
            )
        )
        assertTrue(result is HouseRequestSubmissionResult.Success)
        assertEquals("req-uuid-100", (result as HouseRequestSubmissionResult.Success).requestId)
        assertTrue(capturedRequest?.url?.encodedPath?.endsWith("/rpc/submit_house_request") == true)
        assertFalse("Request body must never include family count", capturedBodyText.contains("family"))
        assertTrue(capturedBodyText.contains("طارق الحسن"))
    }

    @Test
    fun `local admin repository advances house request and builds assigned house`() = runBlocking {
        val adminRepo = LocalCityAdminRepository()
        val initialSnap = (adminRepo.observeAdminSnapshot().first() as CityDataState.Success).data
        val pendingReq = initialSnap.houseRequests.first { it.assignedHouseId == null }

        val updateRes = adminRepo.updateHouseRequestStatus(
            requestId = pendingReq.id,
            status = HouseRequestStatus.APPROVED,
            adminNotes = "تمت الموافقة المبدئية",
            targetPlotId = "plot_10"
        )
        assertTrue(updateRes is AdminOperationResult.Success)

        val buildRes = adminRepo.assignRequestPlotAndBuildHouse(
            requestId = pendingReq.id,
            houseDraft = AdminHouseDraft(
                targetPlotId = "plot_10",
                ownerName = pendingReq.applicantName,
                familyMembersCount = 4,
                flag = ArabCountriesRegistry.findByCode("KW")!!.flagStyle,
                palette = StarterCityMap.palettePresets.first().palette,
                houseTitle = "منزل ${pendingReq.applicantName}"
            )
        )
        assertTrue(buildRes is AdminOperationResult.Success)
        val builtHouseId = (buildRes as AdminOperationResult.Success).affectedHouseId
        assertNotNull(builtHouseId)

        val updatedSnap = (adminRepo.observeAdminSnapshot().first() as CityDataState.Success).data
        val completedReq = updatedSnap.houseRequests.first { it.id == pendingReq.id }
        assertEquals(HouseRequestStatus.COMPLETED, completedReq.status)
        assertEquals("plot_10", completedReq.targetPlotId)
        assertEquals(builtHouseId, completedReq.assignedHouseId)
    }
}
