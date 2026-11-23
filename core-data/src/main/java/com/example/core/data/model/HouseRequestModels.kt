package com.example.core.data.model

enum class HouseRequestStatus(
    val labelAr: String,
    val labelEn: String
) {
    NEW("جديد", "New"),
    UNDER_REVIEW("قيد المراجعة", "Under Review"),
    APPROVED("مقبول", "Approved"),
    PREPARING_PLOT("قيد تجهيز الأرض", "Preparing Plot"),
    BUILDING_HOUSE("قيد بناء المنزل", "Building House"),
    COMPLETED("مكتمل", "Completed"),
    REJECTED("مرفوض", "Rejected");

    fun allowedNextStatuses(): List<HouseRequestStatus> = when (this) {
        NEW -> listOf(UNDER_REVIEW, APPROVED, REJECTED)
        UNDER_REVIEW -> listOf(APPROVED, PREPARING_PLOT, REJECTED)
        APPROVED -> listOf(PREPARING_PLOT, BUILDING_HOUSE, REJECTED)
        PREPARING_PLOT -> listOf(BUILDING_HOUSE, COMPLETED, REJECTED)
        BUILDING_HOUSE -> listOf(COMPLETED, REJECTED)
        COMPLETED -> emptyList()
        REJECTED -> listOf(UNDER_REVIEW)
    }

    companion object {
        fun fromServer(raw: String?): HouseRequestStatus {
            val clean = raw?.trim()?.uppercase().orEmpty()
            if (clean == "PENDING") return NEW
            return entries.firstOrNull { it.name == clean } ?: NEW
        }
    }
}

/**
 * Visitor house building request submission form.
 * Deliberately excludes family members count per Phase 3 specification.
 */
data class HouseRequestDraft(
    val applicantName: String,
    val phone: String? = null,
    val email: String? = null,
    val notes: String? = null
) {
    fun validate(): String? {
        val cleanName = applicantName.trim()
        if (cleanName.length < 2) {
            return "يرجى إدخال الاسم الكامل (حرفان على الأقل)."
        }
        if (cleanName.length > 120) {
            return "الاسم المدخل طويل جدًا (الحد الأقصى 120 حرفًا)."
        }
        val cleanPhone = phone?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanPhone != null && (cleanPhone.length < 5 || cleanPhone.length > 32)) {
            return "رقم الجوال غير صالح (يجب أن يكون بين 5 و32 خانة)."
        }
        val cleanEmail = email?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanEmail != null && !EMAIL_REGEX.matches(cleanEmail)) {
            return "صيغة البريد الإلكتروني غير صحيحة."
        }
        val cleanNotes = notes?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanNotes != null && cleanNotes.length > 1000) {
            return "الملاحظات تتجاوز الحد المسموح (1000 حرف)."
        }
        return null
    }

    companion object {
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

data class HouseRequest(
    val id: String,
    val applicantName: String,
    val phone: String? = null,
    val email: String? = null,
    val notes: String? = null,
    val status: HouseRequestStatus = HouseRequestStatus.NEW,
    val targetPlotId: String? = null,
    val assignedHouseId: String? = null,
    val adminNotes: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

sealed interface HouseRequestSubmissionResult {
    data class Success(
        val requestId: String,
        val message: String
    ) : HouseRequestSubmissionResult

    data class Failure(val reason: String) : HouseRequestSubmissionResult
}
