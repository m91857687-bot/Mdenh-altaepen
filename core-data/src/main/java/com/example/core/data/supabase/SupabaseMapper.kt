package com.example.core.data.supabase

import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.CountryFlagStyle
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.House
import com.example.cityengine.model.HousePalette
import com.example.cityengine.model.LampPostItem
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.model.TreeItem
import com.example.cityengine.world.StarterCityMap
import com.example.core.data.model.AdminCitySnapshot
import com.example.core.data.model.CityExplorerSnapshot
import com.example.core.data.model.HouseResidentSourceRecord
import com.example.core.data.model.PublicHouseDetails

/**
 * Pure domain mappers between Supabase DTOs and :core-city / :core-data models.
 */
object SupabaseMapper {

    fun toPalette(dto: PaletteDto): HousePalette = HousePalette(
        wallLeftHex = dto.wallLeftHex,
        wallRightHex = dto.wallRightHex,
        roofLeftHex = dto.roofLeftHex,
        roofRightHex = dto.roofRightHex,
        roofTrimHex = dto.roofTrimHex,
        doorHex = dto.doorHex,
        windowHex = dto.windowHex
    )

    fun toPaletteDto(palette: HousePalette): PaletteDto = PaletteDto(
        wallLeftHex = palette.wallLeftHex,
        wallRightHex = palette.wallRightHex,
        roofLeftHex = palette.roofLeftHex,
        roofRightHex = palette.roofRightHex,
        roofTrimHex = palette.roofTrimHex,
        doorHex = palette.doorHex,
        windowHex = palette.windowHex
    )

    fun resolveColorKey(palette: HousePalette): String {
        return StarterCityMap.palettePresets.firstOrNull { it.palette == palette }?.key ?: "custom"
    }

    fun toPlot(dto: PlotDto, occupyingHouseId: String?): Plot {
        val status = if (dto.status.equals("OCCUPIED", ignoreCase = true) || occupyingHouseId != null) {
            PlotStatus.OCCUPIED
        } else {
            PlotStatus.AVAILABLE
        }
        return Plot(
            id = dto.id,
            coord = GridCoord(dto.gridX, dto.gridY),
            status = status,
            houseId = occupyingHouseId
        )
    }

    fun toFlagStyle(
        countryCode: String,
        countryName: String,
        stripeColors: List<Long>,
        isVerticalStripes: Boolean
    ): CountryFlagStyle {
        val colors = stripeColors.ifEmpty {
            StarterCityMap.flagPresets.firstOrNull { it.countryCode == countryCode }?.stripeColors
                ?: listOf(0xFF167D46, 0xFFFFFFFF, 0xFF167D46)
        }
        return CountryFlagStyle(
            countryCode = countryCode,
            countryName = countryName,
            stripeColors = colors,
            isVerticalStripes = isVerticalStripes
        )
    }

    fun toPublicHouseAndDetails(dto: HousePublicDto): Pair<House, PublicHouseDetails> {
        val flag = toFlagStyle(dto.countryCode, dto.country, dto.flagStripes, dto.flagVertical)
        val palette = toPalette(dto.paletteJson)
        val house = House(
            id = dto.houseId,
            plotId = dto.plotId,
            coord = GridCoord(dto.gridX, dto.gridY),
            ownerName = dto.residentName,
            familyMembersCount = dto.familySize ?: 0,
            flag = flag,
            palette = palette
        )
        val details = PublicHouseDetails(
            houseId = dto.houseId,
            plotId = dto.plotId,
            ownerName = dto.residentName.ifBlank { "غير متوفر" },
            countryCode = dto.countryCode,
            countryName = dto.country.ifBlank { "غير متوفر" },
            flag = flag,
            familyMembersCount = dto.familySize?.takeIf { it > 0 },
            publicRole = dto.publicRole?.trim()?.takeIf { it.isNotEmpty() },
            publicAbout = dto.publicAbout?.trim()?.takeIf { it.isNotEmpty() },
            approvedPhone = dto.approvedPhone?.trim()?.takeIf { it.isNotEmpty() },
            approvedEmail = dto.approvedEmail?.trim()?.takeIf { it.isNotEmpty() },
            isTestData = false
        )
        return house to details
    }

    fun toAdminHouseRecord(
        dto: HouseAdminDto,
        privateDto: HousePrivateDetailsDto?
    ): HouseResidentSourceRecord {
        val flag = toFlagStyle(dto.countryCode, dto.country, dto.flagStripes, dto.flagVertical)
        val house = House(
            id = dto.id,
            plotId = dto.plotId,
            coord = GridCoord(dto.gridX, dto.gridY),
            ownerName = dto.residentName,
            familyMembersCount = dto.familySize,
            flag = flag,
            palette = toPalette(dto.paletteJson)
        )
        return HouseResidentSourceRecord(
            house = house,
            rawPhoneNumber = privateDto?.phone,
            rawEmail = privateDto?.email,
            rawAdditionalPhone = privateDto?.additionalPhone,
            privateNotes = privateDto?.privateNotes,
            allowPhonePublic = privateDto?.allowPhonePublic ?: false,
            allowEmailPublic = privateDto?.allowEmailPublic ?: false,
            publicNeighborhoodRole = dto.publicRole,
            publicAbout = dto.publicAbout,
            allowPublicInfo = dto.allowPublicInfo,
            showFamilyCountPublicly = dto.showFamilyCountPublicly,
            isTestData = false
        )
    }

    fun buildTiles(layout: CityLayoutDto, plots: List<PlotDto>): Map<GridCoord, CityTileType> {
        val roadSet = layout.roads.map { GridCoord(it.x, it.y) }.toSet()
        val sidewalkSet = layout.sidewalks.map { GridCoord(it.x, it.y) }.toSet()
        val parkSet = layout.parks.map { GridCoord(it.x, it.y) }.toSet()
        val plotSet = plots.map { GridCoord(it.gridX, it.gridY) }.toSet()

        return buildMap {
            for (y in 0 until layout.gridHeight) {
                for (x in 0 until layout.gridWidth) {
                    val coord = GridCoord(x, y)
                    val type = when {
                        coord in roadSet -> CityTileType.ROAD
                        coord in plotSet -> CityTileType.PLOT_LOT
                        coord in parkSet -> CityTileType.PARK_GREEN
                        coord in sidewalkSet -> CityTileType.SIDEWALK
                        else -> CityTileType.GRASS
                    }
                    put(coord, type)
                }
            }
        }
    }

    fun buildExplorerSnapshot(
        layout: CityLayoutDto,
        plotDtos: List<PlotDto>,
        publicHouses: List<HousePublicDto>
    ): CityExplorerSnapshot {
        val houseIdByPlotId = publicHouses.associate { it.plotId to it.houseId }
        val plots = plotDtos.sortedBy { it.id }.map { toPlot(it, houseIdByPlotId[it.id]) }
        val converted = publicHouses.sortedBy { it.houseId }.map { toPublicHouseAndDetails(it) }
        val houses = converted.map { it.first }
        val detailsById = converted.associate { it.second.houseId to it.second }

        val scene = CitySceneState(
            gridWidth = layout.gridWidth,
            gridHeight = layout.gridHeight,
            tiles = buildTiles(layout, plotDtos),
            plots = plots,
            houses = houses,
            trees = layout.trees.map { TreeItem(it.id, GridCoord(it.x, it.y), it.scale, it.variant) },
            lampPosts = layout.lampPosts.map { LampPostItem(it.id, GridCoord(it.x, it.y)) }
        )

        return CityExplorerSnapshot(
            scene = scene,
            houseDetailsById = detailsById,
            isTestData = false
        )
    }

    fun buildAdminSnapshot(
        layout: CityLayoutDto,
        plotDtos: List<PlotDto>,
        houseDtos: List<HouseAdminDto>,
        privateDetailsByHouseId: Map<String, HousePrivateDetailsDto>
    ): AdminCitySnapshot {
        val houseIdByPlotId = houseDtos.associate { it.plotId to it.id }
        val plots = plotDtos.sortedBy { it.id }.map { toPlot(it, houseIdByPlotId[it.id]) }
        val records = houseDtos.sortedBy { it.id }.map { dto ->
            toAdminHouseRecord(dto, privateDetailsByHouseId[dto.id])
        }
        val houses = records.map { it.house }
        val recordsById = records.associateBy { it.house.id }

        val scene = CitySceneState(
            gridWidth = layout.gridWidth,
            gridHeight = layout.gridHeight,
            tiles = buildTiles(layout, plotDtos),
            plots = plots,
            houses = houses,
            trees = layout.trees.map { TreeItem(it.id, GridCoord(it.x, it.y), it.scale, it.variant) },
            lampPosts = layout.lampPosts.map { LampPostItem(it.id, GridCoord(it.x, it.y)) }
        )

        val availableCount = plots.count { it.status == PlotStatus.AVAILABLE }
        val occupiedCount = plots.count { it.status == PlotStatus.OCCUPIED }
        val totalResidents = houses.sumOf { it.familyMembersCount }

        return AdminCitySnapshot(
            scene = scene,
            plots = plots,
            recordsByHouseId = recordsById,
            totalHouses = houses.size,
            availablePlotsCount = availableCount,
            occupiedPlotsCount = occupiedCount,
            totalKnownResidents = totalResidents,
            isLocalTestData = false
        )
    }
}
