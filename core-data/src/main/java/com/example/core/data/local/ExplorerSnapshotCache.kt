package com.example.core.data.local

import com.example.core.data.model.CityExplorerSnapshot
import com.example.core.data.supabase.CityLayoutDto
import com.example.core.data.supabase.CityLightDto
import com.example.core.data.supabase.DistrictDto
import com.example.core.data.supabase.EnvironmentPropDto
import com.example.core.data.supabase.HousePublicDto
import com.example.core.data.supabase.PlotDto
import com.example.core.data.supabase.PublicBuildingDto
import com.example.core.data.supabase.SupabaseClientProvider
import com.example.core.data.supabase.SupabaseMapper
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File

@Serializable
data class CachedCitySnapshotBundle(
    val layout: CityLayoutDto,
    val plots: List<PlotDto>,
    val publicHouses: List<HousePublicDto>,
    val districts: List<DistrictDto> = emptyList(),
    val buildings: List<PublicBuildingDto> = emptyList(),
    val props: List<EnvironmentPropDto> = emptyList(),
    val lights: List<CityLightDto> = emptyList(),
    val savedAtEpochMs: Long = System.currentTimeMillis()
)

/**
 * Lightweight persistent JSON snapshot cache for Explorer (:app) so the last approved city design
 * stays viewable offline across app restarts and resyncs seamlessly once connectivity returns.
 */
class ExplorerSnapshotCache(
    private val cacheFile: File
) {
    private val json get() = SupabaseClientProvider.json

    fun saveBundle(bundle: CachedCitySnapshotBundle) {
        runCatching {
            cacheFile.parentFile?.mkdirs()
            val tmp = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
            tmp.writeText(json.encodeToString(bundle), Charsets.UTF_8)
            if (cacheFile.exists()) cacheFile.delete()
            tmp.renameTo(cacheFile)
        }
    }

    fun loadSnapshot(): CityExplorerSnapshot? {
        return runCatching {
            if (!cacheFile.exists()) return null
            val raw = cacheFile.readText(Charsets.UTF_8)
            val bundle = json.decodeFromString<CachedCitySnapshotBundle>(raw)
            SupabaseMapper.buildExplorerSnapshot(
                layout = bundle.layout,
                plotDtos = bundle.plots,
                publicHouses = bundle.publicHouses,
                districts = bundle.districts,
                buildings = bundle.buildings,
                props = bundle.props,
                lights = bundle.lights
            )
        }.getOrNull()
    }

    fun clear() {
        runCatching {
            if (cacheFile.exists()) cacheFile.delete()
        }
    }
}
