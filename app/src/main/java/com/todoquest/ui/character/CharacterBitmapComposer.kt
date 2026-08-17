package com.todoquest.ui.character

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.LruCache
import java.io.IOException

class CharacterBitmapComposer(
    private val assetManager: AssetManager,
    compositeCacheCapacity: Int = DEFAULT_COMPOSITE_CACHE_CAPACITY,
) {
    private val lock = Any()
    private val layerCache = mutableMapOf<String, LayerCacheEntry>()
    private val compositeCache: LruCache<CharacterRenderState, CompositeCacheEntry>
    private var layerDecodeCount = 0
    private var compositeCount = 0

    init {
        require(compositeCacheCapacity > 0) { "compositeCacheCapacity must be positive" }
        compositeCache = LruCache(compositeCacheCapacity)
    }

    fun compose(state: CharacterRenderState): Bitmap? = synchronized(lock) {
        compositeCache[state]?.let { return@synchronized it.bitmapOrNull() }

        val definitions = runCatching { CharacterLayerCatalog.resolve(state) }
            .getOrElse {
                compositeCache.put(state, CompositeCacheEntry.Empty)
                return@synchronized null
            }
        val layers = definitions.map { definition ->
            layerFor(definition) ?: run {
                compositeCache.put(state, CompositeCacheEntry.Empty)
                return@synchronized null
            }
        }

        compositeCount += 1
        val composite = composeLayers(layers)
        val entry = composite?.let(CompositeCacheEntry::BitmapValue)
            ?: CompositeCacheEntry.Empty
        compositeCache.put(state, entry)
        entry.bitmapOrNull()
    }

    fun cacheStats(): CacheStats = synchronized(lock) {
        CacheStats(
            layerDecodeCount = layerDecodeCount,
            compositeCount = compositeCount,
        )
    }

    private fun layerFor(definition: CharacterLayerDefinition): Bitmap? {
        val cached = layerCache.getOrPut(definition.assetPath) {
            layerDecodeCount += 1
            decodeLayer(definition)?.let(LayerCacheEntry::BitmapValue) ?: LayerCacheEntry.Empty
        }
        return cached.bitmapOrNull()
    }

    private fun decodeLayer(definition: CharacterLayerDefinition): Bitmap? {
        val decoded = try {
            assetManager.open(definition.assetPath).use { input ->
                BitmapFactory.decodeStream(
                    input,
                    null,
                    BitmapFactory.Options().apply {
                        inScaled = false
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    },
                )
            }
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        } ?: return null

        if (decoded.width != definition.canvasWidth || decoded.height != definition.canvasHeight) {
            decoded.recycle()
            return null
        }
        if (decoded.config == Bitmap.Config.ARGB_8888 && !decoded.isMutable) {
            return decoded
        }

        val immutableArgb = decoded.copy(Bitmap.Config.ARGB_8888, false)
        decoded.recycle()
        return immutableArgb
    }

    private fun composeLayers(layers: List<Bitmap>): Bitmap? {
        val mutableComposite = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        mutableComposite.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(mutableComposite)
        val paint = Paint().apply {
            isAntiAlias = false
            isDither = false
            isFilterBitmap = false
        }
        layers.forEach { layer -> canvas.drawBitmap(layer, 0f, 0f, paint) }

        val immutableComposite = mutableComposite.copy(Bitmap.Config.ARGB_8888, false)
        mutableComposite.recycle()
        return immutableComposite
    }

    data class CacheStats(
        val layerDecodeCount: Int,
        val compositeCount: Int,
    )

    private sealed interface LayerCacheEntry {
        data class BitmapValue(val bitmap: Bitmap) : LayerCacheEntry

        data object Empty : LayerCacheEntry

        fun bitmapOrNull(): Bitmap? = when (this) {
            is BitmapValue -> bitmap
            Empty -> null
        }
    }

    private sealed interface CompositeCacheEntry {
        data class BitmapValue(val bitmap: Bitmap) : CompositeCacheEntry

        data object Empty : CompositeCacheEntry

        fun bitmapOrNull(): Bitmap? = when (this) {
            is BitmapValue -> bitmap
            Empty -> null
        }
    }

    private companion object {
        const val CANVAS_SIZE = 64
        const val DEFAULT_COMPOSITE_CACHE_CAPACITY = 16
    }
}
