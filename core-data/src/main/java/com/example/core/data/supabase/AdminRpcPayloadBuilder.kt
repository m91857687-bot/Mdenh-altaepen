package com.example.core.data.supabase

import com.example.core.data.model.AdminHouseDraft
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

internal object AdminRpcPayloadBuilder {

    fun validateDraftInputs(draft: AdminHouseDraft): String? {
        if (draft.ownerName.trim().isEmpty()) return "يرجى إدخال اسم صاحب البيت."
        if (draft.familyMembersCount <= 0) return "عدد أفراد الأسرة يجب أن يكون 1 على الأقل."
        return null
    }

    fun buildHouseRpcParams(
        draft: AdminHouseDraft,
        extraKey: String,
        extraVal: String
    ): JsonObject = buildJsonObject {
        put(extraKey, extraVal)
        put("p_resident_name", draft.ownerName.trim())
        put("p_country_code", draft.flag.countryCode)
        put("p_country", draft.flag.countryName)
        put("p_flag_stripes", buildJsonArray { draft.flag.stripeColors.forEach { add(it) } })
        put("p_flag_vertical", draft.flag.isVerticalStripes)
        put("p_family_size", draft.familyMembersCount)
        put("p_house_color", SupabaseMapper.resolveColorKey(draft.palette))
        put("p_palette_json", SupabaseClientProvider.json.encodeToJsonElement(SupabaseMapper.toPaletteDto(draft.palette)))
        put("p_public_role", draft.publicNeighborhoodRole?.trim()?.takeIf { it.isNotEmpty() })
        put("p_public_about", draft.publicAbout?.trim()?.takeIf { it.isNotEmpty() })
        put("p_allow_public_info", draft.allowPublicInfo)
        put("p_show_family_count_publicly", draft.showFamilyCountPublicly)
        put("p_phone", draft.phoneNumber?.trim()?.takeIf { it.isNotEmpty() })
        put("p_email", draft.email?.trim()?.takeIf { it.isNotEmpty() })
        put("p_additional_phone", draft.additionalPhone?.trim()?.takeIf { it.isNotEmpty() })
        put("p_private_notes", draft.privateNotes?.trim()?.takeIf { it.isNotEmpty() })
        put("p_allow_phone_public", draft.allowPhonePublic)
        put("p_allow_email_public", draft.allowEmailPublic)
    }

    fun buildPublicVisibilityParams(houseId: String, draft: AdminHouseDraft): JsonObject = buildJsonObject {
        put("p_house_id", houseId)
        put("p_house_title", draft.houseTitle?.trim()?.takeIf { it.isNotEmpty() })
        put("p_public_status", draft.publicStatus?.trim()?.takeIf { it.isNotEmpty() })
        put("p_allow_owner_name_public", draft.allowOwnerNamePublic)
    }

    fun buildRequestStatusParams(
        requestId: String,
        status: String,
        adminNotes: String?,
        targetPlotId: String?
    ): JsonObject = buildJsonObject {
        put("p_request_id", requestId)
        put("p_status", status)
        put("p_admin_notes", adminNotes?.trim()?.takeIf { it.isNotEmpty() })
        put("p_target_plot_id", targetPlotId)
    }

    fun buildAssignRequestParams(
        requestId: String,
        targetPlotId: String,
        assignedHouseId: String?,
        status: String,
        adminNotes: String
    ): JsonObject = buildJsonObject {
        put("p_request_id", requestId)
        put("p_target_plot_id", targetPlotId)
        put("p_assigned_house_id", assignedHouseId)
        put("p_status", status)
        put("p_admin_notes", adminNotes)
    }
}
