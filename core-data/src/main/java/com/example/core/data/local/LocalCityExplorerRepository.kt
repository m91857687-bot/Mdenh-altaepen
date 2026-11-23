package com.example.core.data.local

import com.example.cityengine.model.CitySceneState
import com.example.cityengine.world.StarterCityMap
import com.example.core.data.model.CityDataState
import com.example.core.data.model.CityExplorerSnapshot
import com.example.core.data.model.HouseResidentSourceRecord
import com.example.core.data.privacy.HousePrivacySanitizer
import com.example.core.data.repository.CityExplorerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Structured Phase 2 local data source wrapping the 12 starter test houses.
 * Includes private contact fields with explicit consent = false to verify privacy redaction,
 * and varied availability of optional public info to verify graceful handling of missing fields.
 */
class LocalCityExplorerRepository(
    private val sceneProvider: () -> CitySceneState = { StarterCityMap.createScene() },
    private val recordEnricher: (CitySceneState) -> List<HouseResidentSourceRecord> = ::defaultTestRecords
) : CityExplorerRepository {

    private val stateFlow = MutableStateFlow<CityDataState<CityExplorerSnapshot>>(buildSnapshotState())
    private val submittedRequests = mutableListOf<com.example.core.data.model.HouseRequest>()

    override fun observeCitySnapshot(): Flow<CityDataState<CityExplorerSnapshot>> = stateFlow.asStateFlow()

    override suspend fun refresh() {
        stateFlow.value = buildSnapshotState()
    }

    override suspend fun submitHouseRequest(
        draft: com.example.core.data.model.HouseRequestDraft
    ): com.example.core.data.model.HouseRequestSubmissionResult {
        val validationError = draft.validate()
        if (validationError != null) {
            return com.example.core.data.model.HouseRequestSubmissionResult.Failure(validationError)
        }
        val reqId = "local_req_${submittedRequests.size + 1}"
        submittedRequests += com.example.core.data.model.HouseRequest(
            id = reqId,
            applicantName = draft.applicantName.trim(),
            phone = draft.phone?.trim()?.takeIf { it.isNotEmpty() },
            email = draft.email?.trim()?.takeIf { it.isNotEmpty() },
            notes = draft.notes?.trim()?.takeIf { it.isNotEmpty() },
            status = com.example.core.data.model.HouseRequestStatus.NEW
        )
        return com.example.core.data.model.HouseRequestSubmissionResult.Success(
            requestId = reqId,
            message = "تم استلام طلب بناء المنزل بنجاح (وضع التجربة المحلية)."
        )
    }

    fun getSubmittedRequestsForTesting(): List<com.example.core.data.model.HouseRequest> = submittedRequests.toList()

    private fun buildSnapshotState(): CityDataState<CityExplorerSnapshot> {
        return try {
            val scene = sceneProvider()
            val records = recordEnricher(scene)
            val publicById = records.associate { record ->
                record.house.id to HousePrivacySanitizer.toPublicDetails(record)
            }
            CityDataState.Success(
                CityExplorerSnapshot(
                    scene = scene,
                    houseDetailsById = publicById,
                    isTestData = records.any { it.isTestData }
                )
            )
        } catch (t: Throwable) {
            CityDataState.Error(t.message ?: "تعذر تحميل بيانات المدينة.")
        }
    }

    companion object {
        private data class TestPublicMetadata(
            val role: String?,
            val about: String?,
            val allowPublicInfo: Boolean = true,
            val showFamilyCount: Boolean = true
        )

        private val metadataByHouseId = mapOf(
            "house_01" to TestPublicMetadata("منسق مجلس الحي", "أهلاً بكم في دار الطيبين — القهوة العربية حاضرة دائمًا للجيران."),
            "house_02" to TestPublicMetadata("مهندس معماري", "مهتم بتجميل الواجهات والمساحات الخضراء في الحي."),
            "house_03" to TestPublicMetadata("طبيبة أطفال", "يسعدني تقديم الإرشاد الصحي لأسر الحي."),
            "house_04" to TestPublicMetadata("خطاط ومصمم", "أهلاً بأهل الحي الكرام في البيت المغربي."),
            "house_05" to TestPublicMetadata(null, "نحب الهدوء والتعاون بين الجيران."),
            "house_06" to TestPublicMetadata("معلم رياضيات", "باب بيتنا مفتوح دائمًا لمبادرات الطلبة."),
            "house_07" to TestPublicMetadata("هاوي زراعة", "نشارك شتلات النعناع والريحان مع جيران الشارع."),
            "house_08" to TestPublicMetadata(null, null, allowPublicInfo = false, showFamilyCount = true),
            "house_09" to TestPublicMetadata("باحثة تاريخية", "سعداء بالانضمام إلى حي مدينة الطيبين."),
            "house_10" to TestPublicMetadata("صانع محتوى", null, allowPublicInfo = true, showFamilyCount = false),
            "house_11" to TestPublicMetadata("مصممة حدائق", "الحديقة الوسطى هي أجمل مكان للقاء العائلات."),
            "house_12" to TestPublicMetadata(null, "تحية طيبة لكل زوار المدينة.")
        )

        fun defaultTestRecords(scene: CitySceneState): List<HouseResidentSourceRecord> {
            return scene.houses.mapIndexed { index, house ->
                val meta = metadataByHouseId[house.id]
                HouseResidentSourceRecord(
                    house = house,
                    // Unapproved internal test contacts: must NEVER appear in Explorer UI
                    rawPhoneNumber = "+9665000001${index.toString().padStart(2, '0')}",
                    rawEmail = "resident${index + 1}@private-test.local",
                    allowPhonePublic = false,
                    allowEmailPublic = false,
                    publicNeighborhoodRole = meta?.role,
                    publicAbout = meta?.about,
                    allowPublicInfo = meta?.allowPublicInfo ?: false,
                    showFamilyCountPublicly = meta?.showFamilyCount ?: true,
                    isTestData = true
                )
            }
        }
    }
}
