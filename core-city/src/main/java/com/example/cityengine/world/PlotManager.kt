package com.example.cityengine.world

import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.House
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus

/**
 * Manages the city's plots and houses with strict spatial validation.
 */
class PlotManager(
    initialPlots: List<Plot>,
    initialHouses: List<House>,
    private val validator: SpatialValidator
) {
    private val _plots = initialPlots.toMutableList()
    private val _houses = initialHouses.toMutableList()

    init {
        val result = validator.validateCityLayout(_plots, _houses)
        require(result is ValidationResult.Valid) {
            (result as ValidationResult.Invalid).reason
        }
    }

    val plots: List<Plot> get() = _plots.toList()
    val houses: List<House> get() = _houses.toList()

    fun getPlotById(plotId: String): Plot? = _plots.firstOrNull { it.id == plotId }

    fun getPlotAt(coord: GridCoord): Plot? = _plots.firstOrNull { it.coord == coord }

    fun getHouseById(houseId: String): House? = _houses.firstOrNull { it.id == houseId }

    fun assignHouseToPlot(house: House): ValidationResult {
        val plotIndex = _plots.indexOfFirst { it.id == house.plotId }
        if (plotIndex == -1) {
            return ValidationResult.Invalid("Plot ${house.plotId} does not exist.")
        }
        val plot = _plots[plotIndex]
        val normalizedHouse = if (house.coord == plot.coord) house else house.copy(coord = plot.coord)

        val check = validator.validateCanPlaceHouse(plot, _houses, _plots)
        if (check is ValidationResult.Invalid) return check

        _houses.add(normalizedHouse)
        _plots[plotIndex] = plot.copy(status = PlotStatus.OCCUPIED, houseId = normalizedHouse.id)
        return ValidationResult.Valid
    }

    fun removeHouseFromPlot(plotId: String): Boolean {
        val plotIndex = _plots.indexOfFirst { it.id == plotId }
        if (plotIndex == -1) return false
        val plot = _plots[plotIndex]
        val houseId = plot.houseId ?: return false

        _houses.removeAll { it.id == houseId }
        _plots[plotIndex] = plot.copy(status = PlotStatus.AVAILABLE, houseId = null)
        return true
    }

    fun moveHouse(houseId: String, targetPlotId: String): ValidationResult {
        val houseIndex = _houses.indexOfFirst { it.id == houseId }
        if (houseIndex == -1) return ValidationResult.Invalid("House $houseId not found.")
        val targetPlotIndex = _plots.indexOfFirst { it.id == targetPlotId }
        if (targetPlotIndex == -1) return ValidationResult.Invalid("Target plot $targetPlotId not found.")

        val currentHouse = _houses[houseIndex]
        val targetPlot = _plots[targetPlotIndex]
        val check = validator.validateCanPlaceHouse(targetPlot, _houses, _plots)
        if (check is ValidationResult.Invalid) return check

        val oldPlotIndex = _plots.indexOfFirst { it.id == currentHouse.plotId }
        if (oldPlotIndex != -1) {
            _plots[oldPlotIndex] = _plots[oldPlotIndex].copy(status = PlotStatus.AVAILABLE, houseId = null)
        }
        _plots[targetPlotIndex] = targetPlot.copy(status = PlotStatus.OCCUPIED, houseId = currentHouse.id)
        _houses[houseIndex] = currentHouse.copy(plotId = targetPlot.id, coord = targetPlot.coord)
        return ValidationResult.Valid
    }

    fun updateHouse(updatedHouse: House): ValidationResult {
        val houseIndex = _houses.indexOfFirst { it.id == updatedHouse.id }
        if (houseIndex == -1) {
            return ValidationResult.Invalid("House ${updatedHouse.id} not found.")
        }
        val currentHouse = _houses[houseIndex]
        val plot = getPlotById(currentHouse.plotId)
            ?: return ValidationResult.Invalid("Associated plot ${currentHouse.plotId} not found.")
        if (plot.status != PlotStatus.OCCUPIED || plot.houseId != currentHouse.id) {
            return ValidationResult.Invalid("Plot ${plot.id} ownership state is invalid.")
        }
        val normalized = updatedHouse.copy(plotId = plot.id, coord = plot.coord)
        _houses[houseIndex] = normalized
        return ValidationResult.Valid
    }
}
