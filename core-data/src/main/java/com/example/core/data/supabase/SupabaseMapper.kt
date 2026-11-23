package com.example.core.data.supabase

import com.example.cityengine.model.BuildingRotation
import com.example.cityengine.model.CityLightSource
import com.example.cityengine.model.CityLightingState
import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.CountryFlagStyle
import com.example.cityengine.model.DistrictZone
import com.example.cityengine.model.EnvironmentProp
import com.example.cityengine.model.EnvironmentPropType
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.House
import com.example.cityengine.model.HouseDesignStyle
import com.example.cityengine.model.HousePalette
import com.example.cityengine.model.LampPostItem
import com.example.cityengine.model.LightSourceType
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.model.PropLayer
import com.example.cityengine.model.PublicBuilding
import com.example.cityengine.model.PublicBuildingType
import com.example.cityengine.model.RoadSegment
import com.example.cityengine.model.TileTextureVariant
import com.example.cityengine.model.TimeOfDayPhase
import com.example.cityengine.model.TreeItem
import com.example.cityengine.world.StarterCityMap
import com.example.core.data.model.AdminCitySnapshot
import com.example.core.data.model.CityBuilderServerCapabilities
import com.example.core.data.model.CityExplorerSnapshot
import com.example.core.data.model.HouseResidentSourceRecord
import com.example.core.data.model.PublicHouseDetails

/**
 * Pure domain mappers between Supabase DTOs and :core-city / :core-data models.
 * Safely defaults unknown enum values so Phase-4 rows and forward-migrated rows both map cleanly.
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
        val status = when {
            dto.status.equals("OCCUPIED", ignoreCase = true) || occupyingHouseId != null -> PlotStatus.OCCUPIED
            dto.status.equals("UNAVAILABLE", ignoreCase = true) -> PlotStatus.UNAVAILABLE
            else -> PlotStatus.AVAILABLE
        }
        return Plot(
            id = dto.id,
            coord = GridCoord(dto.gridX, dto.gridY),
            status = status,
            houseId = occupyingHouseId,
            widthCells = dto.widthCells.coerceAtLeast(1),
            heightCells = dto.heightCells.coerceAtLeast(1),
            rotation = BuildingRotation.fromDegrees(dto.rotationDeg),
            districtId = dto.districtId,
            groundType = parseTileType(dto.groundType, CityTileType.PLOT_LOT),
            textureVariant = parseTextureVariant(dto.textureVariant, TileTextureVariant.DEFAULT),
            plotCode = dto.plotCode
        )
    }

    fun toFlagStyle(
        countryCode: String,
        countryName: String,
        stripeColors: List<Long>,
        isVerticalStripes: Boolean
    ): CountryFlagStyle {
        return com.example.cityengine.model.ArabCountriesRegistry.resolveFlagStyle(
            countryCode = countryCode,
            countryName = countryName,
            fallbackColors = stripeColors,
            isVerticalStripes = isVerticalStripes
        )
    }

    fun toPublicHouseAndDetails(dto: HousePublicDto): Pair<House, PublicHouseDetails> {
        val flag = toFlagStyle(dto.countryCode, dto.country, dto.flagStripes, dto.flagVertical)
        val palette = toPalette(dto.paletteJson)
        val trimmedOwner = dto.residentName.trim()
        val ownerVisible = trimmedOwner.isNotEmpty() && trimmedOwner != "غير معروض"
        val house = House(
            id = dto.houseId,
            plotId = dto.plotId,
            coord = GridCoord(dto.gridX, dto.gridY),
            ownerName = dto.residentName,
            familyMembersCount = dto.familySize ?: 0,
            flag = flag,
            palette = palette,
            widthCells = dto.widthCells.coerceAtLeast(1),
            heightCells = dto.heightCells.coerceAtLeast(1),
            rotation = BuildingRotation.fromDegrees(dto.rotationDeg),
            designStyle = parseDesignStyle(dto.designStyle),
            upgradeLevel = dto.upgradeLevel.coerceIn(1, 5),
            hasLitWindows = dto.hasLitWindows,
            assetKey = dto.assetKey.ifBlank { "house_classic" },
            houseTitle = dto.houseTitle?.trim()?.takeIf { it.isNotEmpty() },
            publicStatus = dto.publicStatus?.trim()?.takeIf { it.isNotEmpty() },
            allowOwnerNamePublic = ownerVisible
        )
        val details = PublicHouseDetails(
            houseId = dto.houseId,
            plotId = dto.plotId,
            ownerName = if (ownerVisible) dto.residentName else "غير معروض",
            countryCode = dto.countryCode,
            countryName = dto.country.ifBlank { flag.countryName },
            flag = flag,
            familyMembersCount = dto.familySize?.takeIf { it > 0 },
            publicRole = dto.publicRole?.trim()?.takeIf { it.isNotEmpty() },
            publicAbout = dto.publicAbout?.trim()?.takeIf { it.isNotEmpty() },
            approvedPhone = dto.approvedPhone?.trim()?.takeIf { it.isNotEmpty() },
            approvedEmail = dto.approvedEmail?.trim()?.takeIf { it.isNotEmpty() },
            isTestData = false,
            allowOwnerNamePublic = ownerVisible,
            houseTitle = dto.houseTitle?.trim()?.takeIf { it.isNotEmpty() },
            publicStatus = dto.publicStatus?.trim()?.takeIf { it.isNotEmpty() }
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
            palette = toPalette(dto.paletteJson),
            widthCells = dto.widthCells.coerceAtLeast(1),
            heightCells = dto.heightCells.coerceAtLeast(1),
            rotation = BuildingRotation.fromDegrees(dto.rotationDeg),
            designStyle = parseDesignStyle(dto.designStyle),
            upgradeLevel = dto.upgradeLevel.coerceIn(1, 5),
            hasLitWindows = dto.hasLitWindows,
            assetKey = dto.assetKey.ifBlank { "house_classic" },
            houseTitle = dto.houseTitle,
            publicStatus = dto.publicStatus,
            allowOwnerNamePublic = dto.allowOwnerNamePublic
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
            isTestData = false,
            allowOwnerNamePublic = dto.allowOwnerNamePublic,
            houseTitle = dto.houseTitle,
            publicStatus = dto.publicStatus
        )
    }

    fun toDistrict(dto: DistrictDto): DistrictZone = DistrictZone(
        id = dto.id,
        nameAr = dto.nameAr,
        code = dto.code,
        themeColorHex = dto.themeColorHex,
        origin = GridCoord(dto.gridX, dto.gridY),
        widthCells = dto.widthCells.coerceAtLeast(1),
        heightCells = dto.heightCells.coerceAtLeast(1),
        ambientStyle = dto.ambientStyle
    )

    fun toPublicBuilding(dto: PublicBuildingDto): PublicBuilding = PublicBuilding(
        id = dto.id,
        nameAr = dto.nameAr,
        type = parseBuildingType(dto.buildingType),
        coord = GridCoord(dto.gridX, dto.gridY),
        widthCells = dto.widthCells.coerceAtLeast(1),
        heightCells = dto.heightCells.coerceAtLeast(1),
        rotation = BuildingRotation.fromDegrees(dto.rotationDeg),
        upgradeLevel = dto.upgradeLevel.coerceIn(1, 5),
        primaryColorHex = dto.primaryColorHex,
        accentColorHex = dto.accentColorHex,
        assetKey = dto.assetKey,
        districtId = dto.districtId,
        isLitAtNight = dto.isLitAtNight,
        descriptionAr = dto.descriptionAr?.trim()?.takeIf { it.isNotEmpty() },
        publicInfoAr = dto.publicInfoAr?.trim()?.takeIf { it.isNotEmpty() }
    )

    fun toHouseRequest(dto: HouseRequestDto): com.example.core.data.model.HouseRequest =
        com.example.core.data.model.HouseRequest(
            id = dto.id,
            applicantName = dto.applicantName,
            phone = dto.phone?.trim()?.takeIf { it.isNotEmpty() },
            email = dto.email?.trim()?.takeIf { it.isNotEmpty() },
            notes = dto.notes?.trim()?.takeIf { it.isNotEmpty() },
            status = com.example.core.data.model.HouseRequestStatus.fromServer(dto.status),
            targetPlotId = dto.targetPlotId,
            assignedHouseId = dto.assignedHouseId,
            adminNotes = dto.adminNotes?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt
        )

    fun toEnvironmentProp(dto: EnvironmentPropDto): EnvironmentProp = EnvironmentProp(
        id = dto.id,
        type = parsePropType(dto.propType),
        coord = GridCoord(dto.gridX, dto.gridY),
        widthCells = dto.widthCells.coerceAtLeast(1),
        heightCells = dto.heightCells.coerceAtLeast(1),
        rotation = BuildingRotation.fromDegrees(dto.rotationDeg),
        scale = dto.scale,
        variant = dto.variant,
        labelAr = dto.labelAr,
        layer = parsePropLayer(dto.layer),
        primaryColorHex = dto.primaryColorHex,
        assetKey = dto.assetKey
    )

    fun toLightSource(dto: CityLightDto): CityLightSource = CityLightSource(
        id = dto.id,
        type = parseLightType(dto.lightType),
        coord = GridCoord(dto.gridX, dto.gridY),
        offsetX = dto.offsetX,
        offsetY = dto.offsetY,
        colorHex = dto.colorHex,
        intensity = dto.intensity,
        radiusWorld = dto.radiusWorld,
        directionDeg = dto.directionDeg,
        coneAngleDeg = dto.coneAngleDeg,
        isEnabled = dto.isEnabled,
        linkedEntityId = dto.linkedEntityId
    )

    fun toRoadSegment(dto: RoadSegmentDto): RoadSegment = RoadSegment(
        id = dto.id,
        coord = GridCoord(dto.x, dto.y),
        widthCells = dto.widthCells.coerceAtLeast(1),
        heightCells = dto.heightCells.coerceAtLeast(1),
        lanes = dto.lanes,
        hasSidewalkBorder = dto.hasSidewalkBorder,
        hasCrosswalk = dto.hasCrosswalk,
        hasParkingBay = dto.hasParkingBay,
        streetNameAr = dto.streetNameAr,
        surfaceType = parseTextureVariant(dto.surfaceType, TileTextureVariant.DEFAULT)
    )

    fun toLightingState(dto: LightingConfigDto): CityLightingState = CityLightingState(
        phase = parseTimeOfDayPhase(dto.phase),
        sunAzimuthDeg = dto.sunAzimuthDeg,
        ambientDimming = dto.ambientDimming,
        nightLampAutoOn = dto.nightLampAutoOn
    )

    fun buildTiles(layout: CityLayoutDto, plots: List<PlotDto>): Map<GridCoord, CityTileType> {
        val roadSet = layout.roads.map { GridCoord(it.x, it.y) }.toHashSet()
        val sidewalkSet = layout.sidewalks.map { GridCoord(it.x, it.y) }.toHashSet()
        val parkSet = layout.parks.map { GridCoord(it.x, it.y) }.toHashSet()

        val plotCoordMap = HashMap<GridCoord, CityTileType>()
        for (p in plots) {
            val mappedPlot = toPlot(p, occupyingHouseId = null)
            val ground = mappedPlot.groundType
            for (c in mappedPlot.occupiedCoords()) {
                plotCoordMap[c] = ground
            }
        }

        val customTileMap = HashMap<GridCoord, CityTileType>()
        for (ct in layout.customTiles) {
            customTileMap[GridCoord(ct.x, ct.y)] = parseTileType(ct.type, CityTileType.GRASS)
        }
        for (seg in layout.roadSegments) {
            val w = seg.widthCells.coerceAtLeast(1)
            val h = seg.heightCells.coerceAtLeast(1)
            for (dy in 0 until h) {
                for (dx in 0 until w) {
                    roadSet.add(GridCoord(seg.x + dx, seg.y + dy))
                }
            }
        }

        return buildMap(layout.gridWidth * layout.gridHeight) {
            for (y in 0 until layout.gridHeight) {
                for (x in 0 until layout.gridWidth) {
                    val coord = GridCoord(x, y)
                    val type = customTileMap[coord] ?: when {
                        coord in roadSet -> CityTileType.ROAD
                        coord in plotCoordMap -> plotCoordMap.getValue(coord)
                        coord in parkSet -> CityTileType.PARK_GREEN
                        coord in sidewalkSet -> CityTileType.SIDEWALK
                        else -> CityTileType.GRASS
                    }
                    put(coord, type)
                }
            }
        }
    }

    fun buildTileTextures(layout: CityLayoutDto, plots: List<PlotDto>): Map<GridCoord, TileTextureVariant> {
        val textures = HashMap<GridCoord, TileTextureVariant>()
        for (p in plots) {
            val variant = parseTextureVariant(p.textureVariant, TileTextureVariant.DEFAULT)
            if (variant != TileTextureVariant.DEFAULT) {
                toPlot(p, null).occupiedCoords().forEach { textures[it] = variant }
            }
        }
        for (seg in layout.roadSegments) {
            val variant = parseTextureVariant(seg.surfaceType, TileTextureVariant.DEFAULT)
            if (variant != TileTextureVariant.DEFAULT) {
                for (dy in 0 until seg.heightCells.coerceAtLeast(1)) {
                    for (dx in 0 until seg.widthCells.coerceAtLeast(1)) {
                        textures[GridCoord(seg.x + dx, seg.y + dy)] = variant
                    }
                }
            }
        }
        for (ct in layout.customTiles) {
            val variant = parseTextureVariant(ct.texture, TileTextureVariant.DEFAULT)
            if (variant != TileTextureVariant.DEFAULT) {
                textures[GridCoord(ct.x, ct.y)] = variant
            }
        }
        return textures
    }

    fun buildExplorerSnapshot(
        layout: CityLayoutDto,
        plotDtos: List<PlotDto>,
        publicHouses: List<HousePublicDto>,
        districts: List<DistrictDto> = emptyList(),
        buildings: List<PublicBuildingDto> = emptyList(),
        props: List<EnvironmentPropDto> = emptyList(),
        lights: List<CityLightDto> = emptyList()
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
            lampPosts = layout.lampPosts.map { LampPostItem(it.id, GridCoord(it.x, it.y)) },
            districts = districts.sortedBy { it.id }.map { toDistrict(it) },
            tileTextures = buildTileTextures(layout, plotDtos),
            roadSegments = layout.roadSegments.map { toRoadSegment(it) },
            publicBuildings = buildings.sortedBy { it.id }.map { toPublicBuilding(it) },
            environmentProps = props.sortedBy { it.id }.map { toEnvironmentProp(it) },
            lights = lights.sortedBy { it.id }.map { toLightSource(it) },
            lightingState = toLightingState(layout.lightingConfig)
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
        privateDetailsByHouseId: Map<String, HousePrivateDetailsDto>,
        districts: List<DistrictDto> = emptyList(),
        buildings: List<PublicBuildingDto> = emptyList(),
        props: List<EnvironmentPropDto> = emptyList(),
        lights: List<CityLightDto> = emptyList(),
        builderCapabilities: CityBuilderServerCapabilities = CityBuilderServerCapabilities(),
        houseRequests: List<HouseRequestDto> = emptyList()
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
            lampPosts = layout.lampPosts.map { LampPostItem(it.id, GridCoord(it.x, it.y)) },
            districts = districts.sortedBy { it.id }.map { toDistrict(it) },
            tileTextures = buildTileTextures(layout, plotDtos),
            roadSegments = layout.roadSegments.map { toRoadSegment(it) },
            publicBuildings = buildings.sortedBy { it.id }.map { toPublicBuilding(it) },
            environmentProps = props.sortedBy { it.id }.map { toEnvironmentProp(it) },
            lights = lights.sortedBy { it.id }.map { toLightSource(it) },
            lightingState = toLightingState(layout.lightingConfig)
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
            isLocalTestData = false,
            layoutVersion = layout.version,
            builderCapabilities = builderCapabilities,
            houseRequests = houseRequests.sortedByDescending { it.createdAt ?: "" }.map { toHouseRequest(it) }
        )
    }

    private fun parseTileType(raw: String, fallback: CityTileType): CityTileType =
        CityTileType.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: fallback

    private fun parseTextureVariant(raw: String, fallback: TileTextureVariant): TileTextureVariant =
        TileTextureVariant.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: fallback

    private fun parseDesignStyle(raw: String): HouseDesignStyle =
        HouseDesignStyle.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: HouseDesignStyle.CLASSIC_HOME

    private fun parseBuildingType(raw: String): PublicBuildingType =
        PublicBuildingType.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PublicBuildingType.CUSTOM_LANDMARK

    private fun parsePropType(raw: String): EnvironmentPropType =
        EnvironmentPropType.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: EnvironmentPropType.TREE_BROADLEAF

    private fun parsePropLayer(raw: String): PropLayer =
        PropLayer.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PropLayer.STRUCTURE

    private fun parseLightType(raw: String): LightSourceType =
        LightSourceType.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: LightSourceType.STREET_LAMP

    private fun parseTimeOfDayPhase(raw: String): TimeOfDayPhase =
        TimeOfDayPhase.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: TimeOfDayPhase.DAY
}
