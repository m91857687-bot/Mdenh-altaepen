package com.example.cityengine.world

import com.example.cityengine.model.BuildingRotation
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.GridFootprint
import com.example.cityengine.model.House
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.model.TileTextureVariant

/**
 * Manages the city's plots and houses with strict multi-cell spatial validation,
 * plus split, merge, resize, rotate, and availability controls.
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

    fun getPlotAt(coord: GridCoord): Plot? = _plots.firstOrNull { plot ->
        GridFootprint(plot.coord, plot.widthCells, plot.heightCells, plot.rotation).contains(coord)
    }

    fun getHouseById(houseId: String): House? = _houses.firstOrNull { it.id == houseId }

    fun createPlot(plot: Plot): ValidationResult {
        if (_plots.any { it.id == plot.id }) {
            return ValidationResult.Invalid("Plot ${plot.id} already exists.")
        }
        val check = validator.validatePlotFootprint(plot, _plots)
        if (check is ValidationResult.Invalid) return check
        _plots.add(plot)
        return ValidationResult.Valid
    }

    fun movePlot(plotId: String, newCoord: GridCoord): ValidationResult {
        val index = _plots.indexOfFirst { it.id == plotId }
        if (index == -1) return ValidationResult.Invalid("Plot $plotId does not exist.")
        val current = _plots[index]
        if (current.status == PlotStatus.OCCUPIED) {
            return ValidationResult.Invalid("Cannot move occupied plot $plotId directly without moving its house.")
        }
        val candidate = current.copy(coord = newCoord)
        val check = validator.validatePlotFootprint(candidate, _plots)
        if (check is ValidationResult.Invalid) return check
        _plots[index] = candidate
        return ValidationResult.Valid
    }

    fun resizePlot(plotId: String, newWidthCells: Int, newHeightCells: Int): ValidationResult {
        if (newWidthCells <= 0 || newHeightCells <= 0) {
            return ValidationResult.Invalid("Plot dimensions must be at least 1x1.")
        }
        val index = _plots.indexOfFirst { it.id == plotId }
        if (index == -1) return ValidationResult.Invalid("Plot $plotId does not exist.")
        val current = _plots[index]
        val candidate = current.copy(widthCells = newWidthCells, heightCells = newHeightCells)
        val check = validator.validatePlotFootprint(candidate, _plots)
        if (check is ValidationResult.Invalid) return check
        _plots[index] = candidate
        current.houseId?.let { hid ->
            val hIdx = _houses.indexOfFirst { it.id == hid }
            if (hIdx != -1) {
                _houses[hIdx] = _houses[hIdx].copy(widthCells = newWidthCells, heightCells = newHeightCells)
            }
        }
        return ValidationResult.Valid
    }

    fun rotatePlot(plotId: String, rotation: BuildingRotation): ValidationResult {
        val index = _plots.indexOfFirst { it.id == plotId }
        if (index == -1) return ValidationResult.Invalid("Plot $plotId does not exist.")
        val current = _plots[index]
        val candidate = current.copy(rotation = rotation)
        val check = validator.validatePlotFootprint(candidate, _plots)
        if (check is ValidationResult.Invalid) return check
        _plots[index] = candidate
        current.houseId?.let { hid ->
            val hIdx = _houses.indexOfFirst { it.id == hid }
            if (hIdx != -1) {
                _houses[hIdx] = _houses[hIdx].copy(rotation = rotation)
            }
        }
        return ValidationResult.Valid
    }

    fun setPlotAvailability(plotId: String, available: Boolean, textureVariant: TileTextureVariant? = null): ValidationResult {
        val index = _plots.indexOfFirst { it.id == plotId }
        if (index == -1) return ValidationResult.Invalid("Plot $plotId does not exist.")
        val current = _plots[index]
        if (current.status == PlotStatus.OCCUPIED) {
            return ValidationResult.Invalid("Occupied plot $plotId cannot change availability until house is removed.")
        }
        _plots[index] = current.copy(
            status = if (available) PlotStatus.AVAILABLE else PlotStatus.UNAVAILABLE,
            textureVariant = textureVariant ?: current.textureVariant
        )
        return ValidationResult.Valid
    }

    /**
     * Splits an AVAILABLE multi-cell plot along its width ([splitHorizontally] = true) or height
     * into two valid adjacent sub-plots.
     */
    fun splitPlot(
        plotId: String,
        splitHorizontally: Boolean,
        firstId: String = "${plotId}_a",
        secondId: String = "${plotId}_b"
    ): Pair<ValidationResult, List<Plot>> {
        val index = _plots.indexOfFirst { it.id == plotId }
        if (index == -1) return ValidationResult.Invalid("Plot $plotId does not exist.") to emptyList()
        val plot = _plots[index]
        if (plot.status != PlotStatus.AVAILABLE) {
            return ValidationResult.Invalid("Only AVAILABLE plots can be split.") to emptyList()
        }
        val effW = plot.effectiveWidth()
        val effH = plot.effectiveHeight()

        val created = if (splitHorizontally) {
            if (effW < 2) return ValidationResult.Invalid("Plot width must be >= 2 to split horizontally.") to emptyList()
            val w1 = effW / 2
            val w2 = effW - w1
            listOf(
                plot.copy(id = firstId, plotCode = firstId, widthCells = w1, heightCells = effH, rotation = BuildingRotation.DEG_0),
                plot.copy(
                    id = secondId,
                    plotCode = secondId,
                    coord = GridCoord(plot.coord.x + w1, plot.coord.y),
                    widthCells = w2,
                    heightCells = effH,
                    rotation = BuildingRotation.DEG_0
                )
            )
        } else {
            if (effH < 2) return ValidationResult.Invalid("Plot height must be >= 2 to split vertically.") to emptyList()
            val h1 = effH / 2
            val h2 = effH - h1
            listOf(
                plot.copy(id = firstId, plotCode = firstId, widthCells = effW, heightCells = h1, rotation = BuildingRotation.DEG_0),
                plot.copy(
                    id = secondId,
                    plotCode = secondId,
                    coord = GridCoord(plot.coord.x, plot.coord.y + h1),
                    widthCells = effW,
                    heightCells = h2,
                    rotation = BuildingRotation.DEG_0
                )
            )
        }

        if (_plots.any { it.id == firstId || it.id == secondId }) {
            return ValidationResult.Invalid("Split target plot IDs already exist.") to emptyList()
        }
        _plots.removeAt(index)
        _plots.addAll(index, created)
        return ValidationResult.Valid to created
    }

    /**
     * Merges two adjacent AVAILABLE plots that share a full edge into one rectangular plot.
     */
    fun mergePlots(
        plotIdA: String,
        plotIdB: String,
        mergedId: String
    ): Pair<ValidationResult, Plot?> {
        if (plotIdA == plotIdB) return ValidationResult.Invalid("Cannot merge a plot with itself.") to null
        val idxA = _plots.indexOfFirst { it.id == plotIdA }
        val idxB = _plots.indexOfFirst { it.id == plotIdB }
        if (idxA == -1 || idxB == -1) return ValidationResult.Invalid("One or both plots not found.") to null

        val a = _plots[idxA]
        val b = _plots[idxB]
        if (a.status != PlotStatus.AVAILABLE || b.status != PlotStatus.AVAILABLE) {
            return ValidationResult.Invalid("Both plots must be AVAILABLE to merge.") to null
        }

        val aw = a.effectiveWidth()
        val ah = a.effectiveHeight()
        val bw = b.effectiveWidth()
        val bh = b.effectiveHeight()

        val merged = when {
            // Side-by-side horizontally with matching Y and height
            a.coord.y == b.coord.y && ah == bh && (a.coord.x + aw == b.coord.x || b.coord.x + bw == a.coord.x) -> {
                val minX = minOf(a.coord.x, b.coord.x)
                a.copy(
                    id = mergedId,
                    plotCode = mergedId,
                    coord = GridCoord(minX, a.coord.y),
                    widthCells = aw + bw,
                    heightCells = ah,
                    rotation = BuildingRotation.DEG_0
                )
            }
            // Stacked vertically with matching X and width
            a.coord.x == b.coord.x && aw == bw && (a.coord.y + ah == b.coord.y || b.coord.y + bh == a.coord.y) -> {
                val minY = minOf(a.coord.y, b.coord.y)
                a.copy(
                    id = mergedId,
                    plotCode = mergedId,
                    coord = GridCoord(a.coord.x, minY),
                    widthCells = aw,
                    heightCells = ah + bh,
                    rotation = BuildingRotation.DEG_0
                )
            }
            else -> return ValidationResult.Invalid("Plots $plotIdA and $plotIdB do not form a contiguous rectangle.") to null
        }

        if (_plots.any { it.id == mergedId && it.id != plotIdA && it.id != plotIdB }) {
            return ValidationResult.Invalid("Merged plot id $mergedId already exists.") to null
        }

        _plots.removeAll { it.id == plotIdA || it.id == plotIdB }
        _plots.add(merged)
        return ValidationResult.Valid to merged
    }

    fun assignHouseToPlot(house: House): ValidationResult {
        val plotIndex = _plots.indexOfFirst { it.id == house.plotId }
        if (plotIndex == -1) {
            return ValidationResult.Invalid("Plot ${house.plotId} does not exist.")
        }
        val plot = _plots[plotIndex]
        val normalizedHouse = house.copy(
            coord = plot.coord,
            widthCells = plot.widthCells,
            heightCells = plot.heightCells,
            rotation = plot.rotation
        )

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
        _houses[houseIndex] = currentHouse.copy(
            plotId = targetPlot.id,
            coord = targetPlot.coord,
            widthCells = targetPlot.widthCells,
            heightCells = targetPlot.heightCells,
            rotation = targetPlot.rotation
        )
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
        val normalized = updatedHouse.copy(
            plotId = plot.id,
            coord = plot.coord,
            widthCells = plot.widthCells,
            heightCells = plot.heightCells,
            rotation = plot.rotation
        )
        _houses[houseIndex] = normalized
        return ValidationResult.Valid
    }
}
