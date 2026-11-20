package com.example.cityengine.world

import com.example.cityengine.model.CityTileType
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.House
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val reason: String) : ValidationResult
}

/**
 * Validates spatial integrity of plots and houses:
 * - No house or plot outside world bounds
 * - No house on roads, sidewalks, park, or grass (only designated PLOT_LOT tiles)
 * - No overlapping plots or houses at the same coordinate
 * - Consistent Plot <-> House linkage
 */
class SpatialValidator(
    private val gridWidth: Int,
    private val gridHeight: Int,
    private val tiles: Map<GridCoord, CityTileType>
) {
    fun isWithinBounds(coord: GridCoord): Boolean {
        return coord.x in 0 until gridWidth && coord.y in 0 until gridHeight
    }

    fun validateCanPlaceHouse(
        plot: Plot,
        existingHouses: List<House>,
        allPlots: List<Plot>
    ): ValidationResult {
        if (!isWithinBounds(plot.coord)) {
            return ValidationResult.Invalid("Plot ${plot.id} is outside map bounds.")
        }
        if (tiles[plot.coord] != CityTileType.PLOT_LOT) {
            return ValidationResult.Invalid("Coordinate ${plot.coord} is not a buildable PLOT_LOT tile.")
        }
        if (plot.status == PlotStatus.OCCUPIED || plot.houseId != null) {
            return ValidationResult.Invalid("Plot ${plot.id} is already occupied.")
        }
        if (existingHouses.any { it.coord == plot.coord }) {
            return ValidationResult.Invalid("Another house already occupies ${plot.coord}.")
        }
        if (allPlots.count { it.coord == plot.coord } > 1) {
            return ValidationResult.Invalid("Overlapping plot definition at ${plot.coord}.")
        }
        return ValidationResult.Valid
    }

    fun validateCityLayout(plots: List<Plot>, houses: List<House>): ValidationResult {
        val plotIds = HashSet<String>(plots.size)
        val plotCoords = HashSet<GridCoord>(plots.size)

        for (plot in plots) {
            if (!isWithinBounds(plot.coord)) {
                return ValidationResult.Invalid("Plot ${plot.id} out of bounds at ${plot.coord}.")
            }
            if (tiles[plot.coord] != CityTileType.PLOT_LOT) {
                return ValidationResult.Invalid("Plot ${plot.id} placed on non-lot tile ${tiles[plot.coord]}.")
            }
            if (!plotIds.add(plot.id)) {
                return ValidationResult.Invalid("Duplicate plot id ${plot.id}.")
            }
            if (!plotCoords.add(plot.coord)) {
                return ValidationResult.Invalid("Overlapping plots at ${plot.coord}.")
            }
        }

        val houseIds = HashSet<String>(houses.size)
        val houseCoords = HashSet<GridCoord>(houses.size)
        val plotsById = plots.associateBy { it.id }

        for (house in houses) {
            if (!isWithinBounds(house.coord)) {
                return ValidationResult.Invalid("House ${house.id} out of bounds.")
            }
            if (tiles[house.coord] != CityTileType.PLOT_LOT) {
                return ValidationResult.Invalid("House ${house.id} overlaps street/non-plot tile.")
            }
            if (!houseIds.add(house.id)) {
                return ValidationResult.Invalid("Duplicate house id ${house.id}.")
            }
            if (!houseCoords.add(house.coord)) {
                return ValidationResult.Invalid("Overlapping houses at ${house.coord}.")
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
            if (plot.status == PlotStatus.AVAILABLE && plot.houseId != null) {
                return ValidationResult.Invalid("Available plot ${plot.id} still references ${plot.houseId}.")
            }
        }

        return ValidationResult.Valid
    }
}
