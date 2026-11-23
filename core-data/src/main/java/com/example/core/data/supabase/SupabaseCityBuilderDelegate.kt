package com.example.core.data.supabase

import com.example.cityengine.model.CityLightingState
import com.example.cityengine.model.EnvironmentProp
import com.example.cityengine.model.GridFootprint
import com.example.cityengine.model.PublicBuilding
import com.example.cityengine.world.SpatialValidator
import com.example.cityengine.world.ValidationResult
import com.example.core.data.model.AdminCitySnapshot
import com.example.core.data.model.AdminOperationResult
import com.example.core.data.model.DistrictDraft
import com.example.core.data.model.EnvironmentPropDraft
import com.example.core.data.model.LightSourceDraft
import com.example.core.data.model.PublicBuildingDraft
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Handles Districts, Public Buildings, Environment Props, and City Lighting mutations
 * against Supabase (part_14..16), keeping file size compact (< 250 lines).
 */
internal class SupabaseCityBuilderDelegate(
    private val preflightGuard: suspend () -> String?,
    private val clientProvider: () -> SupabaseClient?,
    private val snapshotProvider: () -> AdminCitySnapshot?,
    private val layoutDtoProvider: () -> CityLayoutDto?,
    plotDtoProvider: (String) -> PlotDto?,
    private val reloadFromServer: suspend (SupabaseClient) -> Unit,
    applyLayoutLocally: suspend (CityLayoutDto) -> Unit,
    private val handleFailure: suspend (Throwable, String) -> AdminOperationResult.Failure
) {
    private val json get() = SupabaseClientProvider.json

    val plotLayoutDelegate = SupabasePlotLayoutDelegate(
        preflightGuard = preflightGuard,
        clientProvider = clientProvider,
        snapshotProvider = snapshotProvider,
        layoutDtoProvider = layoutDtoProvider,
        plotDtoProvider = plotDtoProvider,
        reloadFromServer = reloadFromServer,
        applyLayoutLocally = applyLayoutLocally,
        handleFailure = handleFailure
    )

    private fun newId(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(8)}"

    suspend fun upsertDistrict(draft: DistrictDraft): AdminOperationResult {
        preflightGuard()?.let { return AdminOperationResult.Failure(it) }
        if (draft.nameAr.isBlank() || draft.code.isBlank()) return AdminOperationResult.Failure("يرجى إدخال اسم الحي ورمزه.")
        val snap = snapshotProvider() ?: return AdminOperationResult.Failure("بيانات المدينة غير محمّلة بعد.")
        val id = draft.id?.takeIf { it.isNotBlank() } ?: newId("dist")
        val v = SpatialValidator(snap.scene.gridWidth, snap.scene.gridHeight, snap.scene.tiles)
        if (!v.isFootprintWithinBounds(GridFootprint(draft.origin, draft.widthCells.coerceAtLeast(1), draft.heightCells.coerceAtLeast(1)))) {
            return AdminOperationResult.Failure("حدود الحي تتجاوز حدود الخريطة.")
        }
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        val dto = DistrictDto(
            id = id, layoutId = layoutDtoProvider()?.id ?: "main_city",
            nameAr = draft.nameAr.trim(), code = draft.code.trim().uppercase(),
            themeColorHex = draft.themeColorHex, gridX = draft.origin.x, gridY = draft.origin.y,
            widthCells = draft.widthCells.coerceAtLeast(1), heightCells = draft.heightCells.coerceAtLeast(1),
            ambientStyle = draft.ambientStyle
        )
        return try {
            client.from("city_districts").upsert(dto)
            reloadFromServer(client)
            AdminOperationResult.Success("تم حفظ الحي (${dto.nameAr}) على الخادم.", affectedDistrictId = id)
        } catch (t: Throwable) {
            handleFailure(t, "فشل حفظ الحي على الخادم.")
        }
    }

    suspend fun deleteDistrict(districtId: String): AdminOperationResult {
        preflightGuard()?.let { return AdminOperationResult.Failure(it) }
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return try {
            client.from("city_districts").delete { filter { eq("id", districtId) } }
            reloadFromServer(client)
            AdminOperationResult.Success("تم حذف الحي من الخادم.", affectedDistrictId = districtId)
        } catch (t: Throwable) {
            handleFailure(t, "فشل حذف الحي من الخادم.")
        }
    }

    suspend fun upsertPublicBuilding(draft: PublicBuildingDraft): AdminOperationResult {
        preflightGuard()?.let { return AdminOperationResult.Failure(it) }
        if (draft.nameAr.isBlank()) return AdminOperationResult.Failure("يرجى إدخال اسم المبنى العام.")
        val snap = snapshotProvider() ?: return AdminOperationResult.Failure("بيانات المدينة غير محمّلة بعد.")
        val id = draft.id?.takeIf { it.isNotBlank() } ?: newId("bldg")
        val candidate = PublicBuilding(
            id = id, nameAr = draft.nameAr.trim(), type = draft.type, coord = draft.coord,
            widthCells = draft.widthCells.coerceAtLeast(1), heightCells = draft.heightCells.coerceAtLeast(1),
            rotation = draft.rotation, upgradeLevel = draft.upgradeLevel.coerceIn(1, 5),
            primaryColorHex = draft.primaryColorHex, accentColorHex = draft.accentColorHex,
            assetKey = draft.assetKey, districtId = draft.districtId, isLitAtNight = draft.isLitAtNight,
            descriptionAr = draft.descriptionAr?.trim()?.takeIf { it.isNotEmpty() },
            publicInfoAr = draft.publicInfoAr?.trim()?.takeIf { it.isNotEmpty() }
        )
        val check = SpatialValidator(snap.scene.gridWidth, snap.scene.gridHeight, snap.scene.tiles)
            .validateCanPlaceBuilding(candidate, snap.scene.publicBuildings.filterNot { it.id == id }, snap.plots, snap.scene.houses)
        if (check is ValidationResult.Invalid) return AdminOperationResult.Failure(check.reason)
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return try {
            val baseParams = buildJsonObject {
                put("p_building_id", id); put("p_layout_id", layoutDtoProvider()?.id ?: "main_city")
                put("p_district_id", draft.districtId); put("p_name_ar", candidate.nameAr)
                put("p_building_type", candidate.type.name); put("p_grid_x", candidate.coord.x)
                put("p_grid_y", candidate.coord.y); put("p_width_cells", candidate.widthCells)
                put("p_height_cells", candidate.heightCells); put("p_rotation_deg", candidate.rotation.degrees)
                put("p_upgrade_level", candidate.upgradeLevel); put("p_primary_color_hex", candidate.primaryColorHex)
                put("p_accent_color_hex", candidate.accentColorHex); put("p_asset_key", candidate.assetKey)
                put("p_is_lit_at_night", candidate.isLitAtNight)
            }
            if (candidate.descriptionAr != null || candidate.publicInfoAr != null) {
                val extendedParams = buildJsonObject {
                    baseParams.forEach { (k, v) -> put(k, v) }
                    put("p_description_ar", candidate.descriptionAr)
                    put("p_public_info_ar", candidate.publicInfoAr)
                }
                val rpcResult = runCatching {
                    client.postgrest.rpc("admin_upsert_public_building", extendedParams)
                }
                if (rpcResult.isFailure) {
                    client.postgrest.rpc("admin_upsert_public_building", baseParams)
                }
            } else {
                client.postgrest.rpc("admin_upsert_public_building", baseParams)
            }
            reloadFromServer(client)
            AdminOperationResult.Success("تم حفظ المبنى العام (${candidate.nameAr}) على الخادم.", affectedBuildingId = id)
        } catch (t: Throwable) {
            handleFailure(t, "فشل حفظ المبنى العام على الخادم.")
        }
    }

    suspend fun deletePublicBuilding(buildingId: String): AdminOperationResult {
        preflightGuard()?.let { return AdminOperationResult.Failure(it) }
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return try {
            client.from("city_buildings").delete { filter { eq("id", buildingId) } }
            reloadFromServer(client)
            AdminOperationResult.Success("تم حذف المبنى العام من الخادم.", affectedBuildingId = buildingId)
        } catch (t: Throwable) {
            handleFailure(t, "فشل حذف المبنى العام من الخادم.")
        }
    }

    suspend fun upsertEnvironmentProp(draft: EnvironmentPropDraft): AdminOperationResult {
        preflightGuard()?.let { return AdminOperationResult.Failure(it) }
        val snap = snapshotProvider() ?: return AdminOperationResult.Failure("بيانات المدينة غير محمّلة بعد.")
        val id = draft.id?.takeIf { it.isNotBlank() } ?: newId("prop")
        val prop = EnvironmentProp(
            id = id, type = draft.type, coord = draft.coord,
            widthCells = draft.widthCells.coerceAtLeast(1), heightCells = draft.heightCells.coerceAtLeast(1),
            rotation = draft.rotation, scale = draft.scale.coerceIn(0.4f, 3.0f), variant = draft.variant,
            labelAr = draft.labelAr?.trim()?.takeIf { it.isNotEmpty() }, layer = draft.layer,
            primaryColorHex = draft.primaryColorHex, assetKey = draft.assetKey
        )
        val check = SpatialValidator(snap.scene.gridWidth, snap.scene.gridHeight, snap.scene.tiles)
            .validateCanPlaceProp(prop, snap.scene.houses, snap.scene.publicBuildings)
        if (check is ValidationResult.Invalid) return AdminOperationResult.Failure(check.reason)
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        val dto = EnvironmentPropDto(
            id = id, layoutId = layoutDtoProvider()?.id ?: "main_city",
            propType = prop.type.name, gridX = prop.coord.x, gridY = prop.coord.y,
            widthCells = prop.widthCells, heightCells = prop.heightCells, rotationDeg = prop.rotation.degrees,
            scale = prop.scale, variant = prop.variant, labelAr = prop.labelAr, layer = prop.layer.name,
            primaryColorHex = prop.primaryColorHex, assetKey = prop.assetKey
        )
        return try {
            client.from("city_environment_props").upsert(dto)
            reloadFromServer(client)
            AdminOperationResult.Success("تم حفظ عنصر البيئة على الخادم.", affectedPropId = id)
        } catch (t: Throwable) {
            handleFailure(t, "فشل حفظ عنصر البيئة على الخادم.")
        }
    }

    suspend fun deleteEnvironmentProp(propId: String): AdminOperationResult {
        preflightGuard()?.let { return AdminOperationResult.Failure(it) }
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return try {
            client.from("city_environment_props").delete { filter { eq("id", propId) } }
            reloadFromServer(client)
            AdminOperationResult.Success("تم حذف عنصر البيئة من الخادم.", affectedPropId = propId)
        } catch (t: Throwable) {
            handleFailure(t, "فشل حذف عنصر البيئة.")
        }
    }

    suspend fun upsertLightSource(draft: LightSourceDraft): AdminOperationResult {
        preflightGuard()?.let { return AdminOperationResult.Failure(it) }
        val snap = snapshotProvider() ?: return AdminOperationResult.Failure("بيانات المدينة غير محمّلة بعد.")
        if (!SpatialValidator(snap.scene.gridWidth, snap.scene.gridHeight, snap.scene.tiles).isWithinBounds(draft.coord)) {
            return AdminOperationResult.Failure("إحداثي مصدر الإنارة خارج حدود الخريطة.")
        }
        val id = draft.id?.takeIf { it.isNotBlank() } ?: newId("light")
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        val dto = CityLightDto(
            id = id, layoutId = layoutDtoProvider()?.id ?: "main_city",
            lightType = draft.type.name, gridX = draft.coord.x, gridY = draft.coord.y,
            offsetX = draft.offsetX, offsetY = draft.offsetY, colorHex = draft.colorHex,
            intensity = draft.intensity.coerceIn(0f, 3f), radiusWorld = draft.radiusWorld.coerceIn(16f, 600f),
            directionDeg = draft.directionDeg, coneAngleDeg = draft.coneAngleDeg.coerceIn(1f, 360f),
            isEnabled = draft.isEnabled, linkedEntityId = draft.linkedEntityId
        )
        return try {
            client.from("city_lights").upsert(dto)
            reloadFromServer(client)
            AdminOperationResult.Success("تم حفظ مصدر الإضاءة على الخادم.", affectedLightId = id)
        } catch (t: Throwable) {
            handleFailure(t, "فشل حفظ مصدر الإضاءة.")
        }
    }

    suspend fun deleteLightSource(lightId: String): AdminOperationResult {
        preflightGuard()?.let { return AdminOperationResult.Failure(it) }
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return try {
            client.from("city_lights").delete { filter { eq("id", lightId) } }
            reloadFromServer(client)
            AdminOperationResult.Success("تم حذف مصدر الإضاءة من الخادم.", affectedLightId = lightId)
        } catch (t: Throwable) {
            handleFailure(t, "فشل حذف مصدر الإضاءة.")
        }
    }

    suspend fun setLightingState(lightingState: CityLightingState): AdminOperationResult {
        preflightGuard()?.let { return AdminOperationResult.Failure(it) }
        val client = clientProvider() ?: return AdminOperationResult.Failure("اتصال Supabase غير مهيأ.")
        return try {
            val lightingDto = LightingConfigDto(
                phase = lightingState.phase.name, sunAzimuthDeg = lightingState.sunAzimuthDeg,
                ambientDimming = lightingState.ambientDimming, nightLampAutoOn = lightingState.nightLampAutoOn
            )
            val params = buildJsonObject {
                put("p_layout_id", layoutDtoProvider()?.id ?: "main_city")
                put("p_lighting_config", json.encodeToJsonElement(lightingDto))
            }
            client.postgrest.rpc("admin_set_lighting_phase", params)
            reloadFromServer(client)
            AdminOperationResult.Success("تم تحديث إضاءة المدينة على الخادم.")
        } catch (t: Throwable) {
            handleFailure(t, "فشل تحديث إضاءة المدينة على الخادم.")
        }
    }
}
