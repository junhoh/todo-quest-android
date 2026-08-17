package com.todoquest.feature.shop

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.todoquest.domain.model.EquipmentType
import java.io.IOException
import kotlin.math.floor

@Immutable
data class EquipmentArtworkDefinition(
    val imageKey: String,
    val assetPath: String,
)

object EquipmentArtworkCatalog {
    private val definitions = listOf(
        EquipmentArtworkDefinition(
            imageKey = "headgear_leather_hat",
            assetPath = "character/layers/headgear_leather_hat.png",
        ),
        EquipmentArtworkDefinition(
            imageKey = "headgear_iron_helmet",
            assetPath = "character/layers/headgear_iron_helmet.png",
        ),
        EquipmentArtworkDefinition(
            imageKey = "top_cloth",
            assetPath = "character/layers/top_cloth.png",
        ),
        EquipmentArtworkDefinition(
            imageKey = "top_leather_armor",
            assetPath = "character/layers/top_leather_armor.png",
        ),
        EquipmentArtworkDefinition(
            imageKey = "top_iron_breastplate",
            assetPath = "character/layers/top_iron_breastplate.png",
        ),
        EquipmentArtworkDefinition(
            imageKey = "bottom_cloth_pants",
            assetPath = "character/layers/bottom_cloth_pants.png",
        ),
        EquipmentArtworkDefinition(
            imageKey = "bottom_leather_pants",
            assetPath = "character/layers/bottom_leather_pants.png",
        ),
        EquipmentArtworkDefinition(
            imageKey = "bottom_steel_greaves",
            assetPath = "character/layers/bottom_steel_greaves.png",
        ),
        EquipmentArtworkDefinition(
            imageKey = "gloves_leather",
            assetPath = "character/layers/gloves_leather.png",
        ),
        EquipmentArtworkDefinition(
            imageKey = "gloves_steel_gauntlets",
            assetPath = "character/layers/gloves_steel_gauntlets.png",
        ),
        EquipmentArtworkDefinition(
            imageKey = "shoes_travelers_boots",
            assetPath = "character/layers/shoes_travelers_boots.png",
        ),
        EquipmentArtworkDefinition(
            imageKey = "shoes_windwalker_boots",
            assetPath = "character/layers/shoes_windwalker_boots.png",
        ),
        EquipmentArtworkDefinition(
            imageKey = "weapon_worn_sword",
            assetPath = "character/layers/weapon_worn_sword.png",
        ),
        EquipmentArtworkDefinition(
            imageKey = "weapon_iron_longsword",
            assetPath = "character/layers/weapon_iron_longsword.png",
        ),
        EquipmentArtworkDefinition(
            imageKey = "weapon_ash_spear",
            assetPath = "character/layers/weapon_ash_spear.png",
        ),
        EquipmentArtworkDefinition(
            imageKey = "weapon_steel_mace",
            assetPath = "character/layers/weapon_steel_mace.png",
        ),
    ).associateBy(EquipmentArtworkDefinition::imageKey)

    fun resolve(imageKey: String?): EquipmentArtworkDefinition? = definitions[imageKey]
}

@Immutable
data class EquipmentOpaqueBounds(
    val left: Int,
    val top: Int,
    val rightInclusive: Int,
    val bottomInclusive: Int,
) {
    val width: Int = rightInclusive - left + 1
    val height: Int = bottomInclusive - top + 1

    init {
        require(left >= 0 && top >= 0) { "opaque bounds must start inside the bitmap" }
        require(rightInclusive >= left && bottomInclusive >= top) {
            "opaque bounds must not be empty"
        }
    }
}

data class LoadedEquipmentArtwork(
    val bitmap: Bitmap,
    val opaqueBounds: EquipmentOpaqueBounds,
)

class EquipmentArtworkLoader(
    private val assetManager: AssetManager,
) {
    private val lock = Any()
    private val cache = mutableMapOf<String, CacheEntry>()
    private var decodeCount = 0

    fun load(definition: EquipmentArtworkDefinition): LoadedEquipmentArtwork? = synchronized(lock) {
        cache.getOrPut(definition.assetPath) {
            decodeCount += 1
            decode(definition.assetPath)?.let(CacheEntry::Loaded) ?: CacheEntry.Empty
        }.artworkOrNull()
    }

    fun cacheStats(): CacheStats = synchronized(lock) {
        CacheStats(decodeCount = decodeCount)
    }

    private fun decode(assetPath: String): LoadedEquipmentArtwork? {
        val decoded = try {
            assetManager.open(assetPath).use { input ->
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

        if (decoded.width != CanvasSize || decoded.height != CanvasSize) {
            decoded.recycle()
            return null
        }

        val immutableArgb = if (decoded.config == Bitmap.Config.ARGB_8888 && !decoded.isMutable) {
            decoded
        } else {
            decoded.copy(Bitmap.Config.ARGB_8888, false).also { decoded.recycle() }
        } ?: return null
        val opaqueBounds = immutableArgb.opaqueBounds() ?: run {
            immutableArgb.recycle()
            return null
        }
        return LoadedEquipmentArtwork(
            bitmap = immutableArgb,
            opaqueBounds = opaqueBounds,
        )
    }

    private fun Bitmap.opaqueBounds(): EquipmentOpaqueBounds? {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        pixels.forEachIndexed { index, color ->
            if ((color ushr AlphaShift) != 0) {
                val x = index % width
                val y = index / width
                if (x < left) left = x
                if (y < top) top = y
                if (x > right) right = x
                if (y > bottom) bottom = y
            }
        }
        return if (right < 0) {
            null
        } else {
            EquipmentOpaqueBounds(left, top, right, bottom)
        }
    }

    data class CacheStats(val decodeCount: Int)

    private sealed interface CacheEntry {
        data class Loaded(val artwork: LoadedEquipmentArtwork) : CacheEntry

        data object Empty : CacheEntry

        fun artworkOrNull(): LoadedEquipmentArtwork? = when (this) {
            is Loaded -> artwork
            Empty -> null
        }
    }

    private companion object {
        const val CanvasSize = 64
        const val AlphaShift = 24
    }
}

@Composable
internal fun EquipmentArtwork(
    imageKey: String?,
    type: EquipmentType,
    contentDescription: String?,
    modifier: Modifier = Modifier.size(64.dp),
) {
    val definition = EquipmentArtworkCatalog.resolve(imageKey)
    val assetManager = LocalContext.current.assets
    val loader = remember(assetManager) { EquipmentArtworkLoader(assetManager) }
    val loaded = remember(loader, definition) {
        definition?.let(loader::load)
    }
    if (definition == null || loaded == null) {
        EquipmentPlaceholder(
            type = type,
            modifier = modifier,
            decorative = contentDescription == null,
        )
        return
    }

    val image = remember(loaded) { loaded.bitmap.asImageBitmap() }
    val semanticModifier = if (contentDescription == null) {
        Modifier
    } else {
        Modifier.semantics(mergeDescendants = true) {
            this.contentDescription = contentDescription
        }
    }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline,
        ),
        shape = MaterialTheme.shapes.small,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(ArtworkPadding)
                .then(semanticModifier)
                .testTag("equipment_artwork_${definition.imageKey}"),
        ) {
            val bounds = loaded.opaqueBounds
            if (size.width <= 0f || size.height <= 0f) return@Canvas
            val integerScale = floor(
                minOf(size.width / bounds.width, size.height / bounds.height),
            ).toInt()
            val destinationSize = if (integerScale > 0) {
                IntSize(bounds.width * integerScale, bounds.height * integerScale)
            } else {
                val fitScale = minOf(size.width / bounds.width, size.height / bounds.height)
                IntSize(
                    width = floor(bounds.width * fitScale).toInt().coerceAtLeast(1),
                    height = floor(bounds.height * fitScale).toInt().coerceAtLeast(1),
                )
            }
            drawImage(
                image = image,
                srcOffset = IntOffset(bounds.left, bounds.top),
                srcSize = IntSize(bounds.width, bounds.height),
                dstOffset = IntOffset(
                    x = ((size.width - destinationSize.width) / 2f).toInt(),
                    y = ((size.height - destinationSize.height) / 2f).toInt(),
                ),
                dstSize = destinationSize,
                filterQuality = FilterQuality.None,
            )
        }
    }
}

private val ArtworkPadding = 6.dp
