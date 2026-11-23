package com.example.cityengine.world

import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.EnvironmentProp
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.GridFootprint
import com.example.cityengine.model.House
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.model.PropLayer
import com.example.cityengine.model.PublicBuilding

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val reason: String) : ValidationResult
}

/**
 * Validates spatial integrity for multi-cell plots, houses, public buildings, and environment props:
 * - Strict map bounds enforcement for full WxH footprints
 * - Zero overlap between plots, houses, or solid public buildings
 * - Terrain compatibility (no houses/buildings on roads, sidewalks, water, or parks)
 * - Smart layering rules allowing ground overlays & park/sidewalk furniture where appropriate
 */
class SpatialValidator(
    private val gridWidth: Int,
    private val gridHeight: Int,
    private val tiles: Map<GridCoord, CityTileType>
) {
    fun isWithinBounds(coord: GridCoord): Boolean {
        return coord.x in 0 until gridWidth && coord.y in 0 until gridHeight
    }

    fun isFootprintWithinBounds(footprint: GridFootprint): Boolean {
        return footprint.origin.x >= 0 &&
            footprint.origin.y >= 0 &&
            footprint.maxXExclusive <= gridWidth &&
            footprint.maxYExclusive <= gridHeight
    }

    fun validatePlotFootprint(
        plot: Plot,
        otherPlots: List<Plot>,
        otherBuildings: List<PublicBuilding> = emptyList()
    ): ValidationResult {
        val fp = GridFootprint(plot.coord, plot.widthCells, plot.heightCells, plot.rotation)
        if (!isFootprintWithinBounds(fp)) {
            return ValidationResult.Invalid("Plot ${plot.id} footprint exceeds map bounds.")
        }
        for (cell in fp.allCoords()) {
            val tile = tiles[cell]
            if (tile != CityTileType.PLOT_LOT) {
                return ValidationResult.Invalid("Plot ${plot.id} cell $cell is not PLOT_LOT ($tile).")
            }
        }
        for (other in otherPlots) {
            if (other.id == plot.id) continue
            val otherFp = GridFootprint(other.coord, other.widthCells, other.heightCells, other.rotation)
            if (fp.overlaps(otherFp)) {
                return ValidationResult.Invalid("Plot ${plot.id} overlaps plot ${other.id}.")
            }
        }
        for (building in otherBuildings) {
            if (fp.overlaps(building.footprint())) {
                return ValidationResult.Invalid("Plot ${plot.id} overlaps building ${building.id}.")
            }
        }
        return ValidationResult.Valid
    }

    fun validateNewPlotArea(
        plot: Plot,
        otherPlots: List<Plot>,
        otherBuildings: List<PublicBuilding> = emptyList(),
        houses: List<House> = emptyList()
    ): ValidationResult {
        val fp = GridFootprint(plot.coord, plot.widthCells, plot.heightCells, plot.rotation)
        if (!isFootprintWithinBounds(fp)) {
            return ValidationResult.Invalid("أبعاد القطعة (${fp.effectiveWidth()}x${fp.effectiveHeight()}) تتجاوز حدود الخريطة.")
        }
        for (cell in fp.allCoords()) {
            val tile = tiles[cell]
            if (tile == CityTileType.ROAD || tile == CityTileType.SIDEWALK ||
                tile == CityTileType.PARK_GREEN || tile == CityTileType.WATER || tile == CityTileType.PLAZA
            ) {
                return ValidationResult.Invalid("لا يمكن وضع قطعة أرض فوق شارع أو رصيف أو حديقة أو ماء ($cell).")
            }
        }
        for (other in otherPlots) {
            if (other.id == plot.id) continue
            val otherFp = GridFootprint(other.coord, other.widthCells, other.heightCells, other.rotation)
            if (fp.overlaps(otherFp)) {
                return ValidationResult.Invalid("القطعة المقترحة تتقاطع مع القطعة ${other.id}.")
            }
        }
        for (building in otherBuildings) {
            if (fp.overlaps(building.footprint())) {
                return ValidationResult.Invalid("القطعة المقترحة تتقاطع مع المبنى العام ${building.nameAr}.")
            }
        }
        for (house in houses) {
            if (house.plotId == plot.id) continue
            val houseFp = GridFootprint(house.coord, house.widthCells, house.heightCells, house.rotation)
            if (fp.overlaps(houseFp)) {
                return ValidationResult.Invalid("القطعة المقترحة تتقاطع مع البيت ${house.ownerName}.")
            }
        }
        return ValidationResult.Valid
    }

    fun validateCanPaintRoadOrSidewalk(
        coords: Iterable<GridCoord>,
        plots: List<Plot>,
        houses: List<House>,
        publicBuildings: List<PublicBuilding>
    ): ValidationResult {
        for (coord in coords) {
            if (!isWithinBounds(coord)) {
                return ValidationResult.Invalid("الإحداثي (${coord.x}, ${coord.y}) خارج حدود الخريطة.")
            }
            for (plot in plots) {
                val plotFp = GridFootprint(plot.coord, plot.widthCells, plot.heightCells, plot.rotation)
                if (plotFp.contains(coord)) {
                    return ValidationResult.Invalid("لا يمكن رسم شارع أو رصيف فوق القطعة ${plot.id}.")
                }
            }
            for (house in houses) {
                val houseFp = GridFootprint(house.coord, house.widthCells, house.heightCells, house.rotation)
                if (houseFp.contains(coord)) {
                    return ValidationResult.Invalid("لا يمكن رسم شارع فوق البيت ${house.ownerName}.")
                }
            }
            for (building in publicBuildings) {
                if (building.footprint().contains(coord)) {
                    return ValidationResult.Invalid("لا يمكن رسم شارع فوق المبنى ${building.nameAr}.")
                }
            }
        }
        return ValidationResult.Valid
    }

    fun validateCanPlaceHouse(
        plot: Plot,
        existingHouses: List<House>,
        allPlots: List<Plot>
    ): ValidationResult {
        val plotFp = GridFootprint(plot.coord, plot.widthCells, plot.heightCells, plot.rotation)
        if (!isFootprintWithinBounds(plotFp)) {
            return ValidationResult.Invalid("Plot ${plot.id} is outside map bounds.")
        }
        for (cell in plotFp.allCoords()) {
            if (tiles[cell] != CityTileType.PLOT_LOT) {
                return ValidationResult.Invalid("Coordinate $cell is not a buildable PLOT_LOT tile.")
            }
        }
        if (plot.status != PlotStatus.AVAILABLE || plot.houseId != null) {
            return ValidationResult.Invalid("Plot ${plot.id} is not available (status=${plot.status}).")
        }
        for (house in existingHouses) {
            val houseFp = GridFootprint(house.coord, house.widthCells, house.heightCells, house.rotation)
            if (plotFp.overlaps(houseFp)) {
                return ValidationResult.Invalid("Another house (${house.id}) overlaps ${plot.id}.")
            }
        }
        val overlappingPlots = allPlots.count { other ->
            val otherFp = GridFootprint(other.coord, other.widthCells, other.heightCells, other.rotation)
            other.id != plot.id && plotFp.overlaps(otherFp)
        }
        if (overlappingPlots > 0 || allPlots.count { it.coord == plot.coord } > 1) {
            return ValidationResult.Invalid("Overlapping plot definition at ${plot.coord}.")
        }
        return ValidationResult.Valid
    }

    fun validateCanPlaceBuilding(
        building: PublicBuilding,
        existingBuildings: List<PublicBuilding>,
        plots: List<Plot>,
        houses: List<House>
    ): ValidationResult {
        val fp = building.footprint()
        if (!isFootprintWithinBounds(fp)) {
            return ValidationResult.Invalid("Building ${building.id} exceeds map bounds.")
        }
        for (cell in fp.allCoords()) {
            val tile = tiles[cell]
            if (tile == CityTileType.ROAD || tile == CityTileType.WATER || tile == CityTileType.SIDEWALK) {
                return ValidationResult.Invalid("Building ${building.id} cannot overlap $tile at $cell.")
            }
        }
        for (other in existingBuildings) {
            if (other.id == building.id) continue
            if (fp.overlaps(other.footprint())) {
                return ValidationResult.Invalid("Building ${building.id} overlaps building ${other.id}.")
            }
        }
        for (plot in plots) {
            val plotFp = GridFootprint(plot.coord, plot.widthCells, plot.heightCells, plot.rotation)
            if (fp.overlaps(plotFp)) {
                return ValidationResult.Invalid("Building ${building.id} overlaps plot ${plot.id}.")
            }
        }
        for (house in houses) {
            val houseFp = GridFootprint(house.coord, house.widthCells, house.heightCells, house.rotation)
            if (fp.overlaps(houseFp)) {
                return ValidationResult.Invalid("Building ${building.id} overlaps house ${house.id}.")
            }
        }
        return ValidationResult.Valid
    }

    fun validateCanPlaceProp(
        prop: EnvironmentProp,
        houses: List<House>,
        buildings: List<PublicBuilding>
    ): ValidationResult {
        val fp = prop.footprint()
        if (!isFootprintWithinBounds(fp)) {
            return ValidationResult.Invalid("Prop ${prop.id} is outside map bounds.")
        }
        if (prop.layer == PropLayer.GROUND_OVERLAY || prop.layer == PropLayer.LIGHT_FIXTURE) {
            return ValidationResult.Valid
        }
        for (house in houses) {
            val houseFp = GridFootprint(house.coord, house.widthCells, house.heightCells, house.rotation)
            if (fp.overlaps(houseFp)) {
                return ValidationResult.Invalid("Prop ${prop.id} overlaps house ${house.id}.")
            }
        }
        for (building in buildings) {
            if (fp.overlaps(building.footprint())) {
                return ValidationResult.Invalid("Prop ${prop.id} overlaps building ${building.id}.")
            }
        }
        return ValidationResult.Valid
    }

    fun validateCityLayout(
        plots: List<Plot>,
        houses: List<House>,
        publicBuildings: List<PublicBuilding> = emptyList()
    ): ValidationResult {
        val plotIds = HashSet<String>(plots.size)
        val occupiedPlotCells = HashMap<GridCoord, String>()

        for (plot in plots) {
            val fp = GridFootprint(plot.coord, plot.widthCells, plot.heightCells, plot.rotation)
            if (!isFootprintWithinBounds(fp)) {
                return ValidationResult.Invalid("Plot ${plot.id} out of bounds at ${plot.coord}.")
            }
            if (!plotIds.add(plot.id)) {
                return ValidationResult.Invalid("Duplicate plot id ${plot.id}.")
            }
            for (cell in fp.allCoords()) {
                if (tiles[cell] != CityTileType.PLOT_LOT) {
                    return ValidationResult.Invalid("Plot ${plot.id} placed on non-lot tile ${tiles[cell]} at $cell.")
                }
                val prevPlot = occupiedPlotCells.put(cell, plot.id)
                if (prevPlot != null) {
                    return ValidationResult.Invalid("Overlapping plots $prevPlot and ${plot.id} at $cell.")
                }
            }
        }

        val houseIds = HashSet<String>(houses.size)
        val occupiedHouseCells = HashMap<GridCoord, String>()
        val plotsById = plots.associateBy { it.id }

        for (house in houses) {
            val fp = GridFootprint(house.coord, house.widthCells, house.heightCells, house.rotation)
            if (!isFootprintWithinBounds(fp)) {
                return ValidationResult.Invalid("House ${house.id} out of bounds.")
            }
            if (!houseIds.add(house.id)) {
                return ValidationResult.Invalid("Duplicate house id ${house.id}.")
            }
            for (cell in fp.allCoords()) {
                if (tiles[cell] != CityTileType.PLOT_LOT) {
                    return ValidationResult.Invalid("House ${house.id} overlaps street/non-plot tile at $cell.")
                }
                val prevHouse = occupiedHouseCells.put(cell, house.id)
                if (prevHouse != null) {
                    return ValidationResult.Invalid("Overlapping houses $prevHouse and ${house.id} at $cell.")
                }
            }
            val plot = plotsById[house.plotId]
                ?: return ValidationResult.Invalid("House ${house.id} references missing plot ${house.plotId}.")
            if (plot.coord != house.coord) {
                return ValidationResult.Invalid("House ${house.id} coord does not match plot ${plot.id}.")
            }
            if (plot.status != PlotStatus.OCCUPIED || plot.houseId != house.id) {
                return ValidationResult.Invalid("Plot ${plot.id} status/houseId inconsistent with ${house.id}.")
            }
        }

        for (plot in plots) {
            if (plot.status == PlotStatus.OCCUPIED && houses.none { it.id == plot.houseId }) {
                return ValidationResult.Invalid("Occupied plot ${plot.id} has missing house ${plot.houseId}.")
            }
            if (plot.status != PlotStatus.OCCUPIED && plot.houseId != null) {
                return ValidationResult.Invalid("Non-occupied plot ${plot.id} still references ${plot.houseId}.")
            }
        }

        val buildingIds = HashSet<String>(publicBuildings.size)
        for (i in publicBuildings.indices) {
            val building = publicBuildings[i]
            if (!buildingIds.add(building.id)) {
                return ValidationResult.Invalid("Duplicate building id ${building.id}.")
            }
            val res = validateCanPlaceBuilding(
                building = building,
                existingBuildings = publicBuildings.subList(0, i),
                plots = plots,
                houses = houses
            )
            if (res is ValidationResult.Invalid) return res
        }

        return ValidationResult.Valid
    }
}
