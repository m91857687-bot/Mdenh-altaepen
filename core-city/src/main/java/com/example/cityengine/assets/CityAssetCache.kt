package com.example.cityengine.assets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.cityengine.model.ArabCountriesRegistry

/**
 * On-demand lazy asset loader & bounded LRU bitmap cache for VectorDrawable emblems/badges
 * and official Arab country flags drawn over isometric buildings & props.
 */
class CityAssetCache(
    private val context: Context?,
    maxCacheEntries: Int = DEFAULT_MAX_ENTRIES,
    private val rasterSizePx: Int = DEFAULT_RASTER_SIZE_PX
) {
    private val capacity = maxCacheEntries.coerceAtLeast(16)
    private val cache = object : LinkedHashMap<String, ImageBitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
            return size > capacity
        }
    }

    var hitCount: Int = 0
        private set
    var missCount: Int = 0
        private set

    fun getOrLoadBitmap(resId: Int?): ImageBitmap? {
        return getOrLoadSizedBitmap(resId, rasterSizePx, rasterSizePx)
    }

    fun getOrLoadSizedBitmap(resId: Int?, widthPx: Int, heightPx: Int): ImageBitmap? {
        val safeContext = context ?: return null
        if (resId == null || widthPx <= 0 || heightPx <= 0) return null
        val key = "${resId}_${widthPx}x$heightPx"
        synchronized(cache) {
            val cached = cache[key]
            if (cached != null) {
                hitCount++
                return cached
            }
        }
        missCount++
        val drawable = runCatching { safeContext.getDrawable(resId) }.getOrNull() ?: return null
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, widthPx, heightPx)
        drawable.draw(canvas)
        val imageBitmap = bitmap.asImageBitmap()
        synchronized(cache) {
            cache[key] = imageBitmap
        }
        return imageBitmap
    }

    fun getOrLoadFlagBitmap(countryCode: String): ImageBitmap? {
        val resId = ArabCountriesRegistry.drawableResFor(countryCode) ?: return null
        return getOrLoadSizedBitmap(resId, FLAG_RASTER_WIDTH_PX, FLAG_RASTER_HEIGHT_PX)
    }

    fun getOrLoadByAssetKey(assetKey: String): ImageBitmap? {
        val flagIso = ArabCountriesRegistry.parseCountryCodeFromAssetKey(assetKey)
        if (flagIso != null) {
            return getOrLoadFlagBitmap(flagIso)
        }
        val spec = CityAssetRegistry.get(assetKey) ?: return null
        return getOrLoadBitmap(spec.drawableResId)
    }

    fun cachedSize(): Int = synchronized(cache) { cache.size }

    fun clear() {
        synchronized(cache) {
            cache.clear()
        }
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 96
        const val DEFAULT_RASTER_SIZE_PX = 96
        const val FLAG_RASTER_WIDTH_PX = 120
        const val FLAG_RASTER_HEIGHT_PX = 80
    }
}

