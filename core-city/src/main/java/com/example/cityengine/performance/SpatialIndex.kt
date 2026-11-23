package com.example.cityengine.performance

import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.GridFootprint

/**
 * Spatial hash bucket grid partitioning the logical city coordinates into chunks of [bucketSize]x[bucketSize].
 * Enables O(1) region queries for thousands of buildings, plots, props, and light sources.
 */
class SpatialGridIndex<T>(
    private val bucketSize: Int = DEFAULT_BUCKET_SIZE
) {
    init {
        require(bucketSize > 0) { "bucketSize must be positive." }
    }

    private val buckets = HashMap<Long, MutableList<T>>()

    fun clear() {
        buckets.clear()
    }

    fun insert(coord: GridCoord, item: T) {
        val key = bucketKey(Math.floorDiv(coord.x, bucketSize), Math.floorDiv(coord.y, bucketSize))
        buckets.getOrPut(key) { ArrayList(4) }.add(item)
    }

    fun insertFootprint(footprint: GridFootprint, item: T) {
        val minBx = Math.floorDiv(footprint.origin.x, bucketSize)
        val maxBx = Math.floorDiv(footprint.maxXExclusive - 1, bucketSize)
        val minBy = Math.floorDiv(footprint.origin.y, bucketSize)
        val maxBy = Math.floorDiv(footprint.maxYExclusive - 1, bucketSize)

        for (by in minBy..maxBy) {
            for (bx in minBx..maxBx) {
                val key = bucketKey(bx, by)
                buckets.getOrPut(key) { ArrayList(4) }.add(item)
            }
        }
    }

    fun queryRange(
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int
    ): List<T> {
        if (buckets.isEmpty() || minX > maxX || minY > maxY) return emptyList()
        val minBx = Math.floorDiv(minX, bucketSize)
        val maxBx = Math.floorDiv(maxX, bucketSize)
        val minBy = Math.floorDiv(minY, bucketSize)
        val maxBy = Math.floorDiv(maxY, bucketSize)

        val result = LinkedHashSet<T>()
        for (by in minBy..maxBy) {
            for (bx in minBx..maxBx) {
                val list = buckets[bucketKey(bx, by)] ?: continue
                for (i in list.indices) {
                    result.add(list[i])
                }
            }
        }
        return result.toList()
    }

    fun bucketCount(): Int = buckets.size

    private fun bucketKey(bx: Int, by: Int): Long {
        return (bx.toLong() shl 32) or (by.toLong() and 0xFFFF_FFFFL)
    }

    companion object {
        const val DEFAULT_BUCKET_SIZE = 4
    }
}
