package com.todoquest.feature.shop

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EquipmentArtworkLoaderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun knownAssetsDecodeAsImmutableArgb64BitmapsWithInclusiveOpaqueBounds() {
        val loader = EquipmentArtworkLoader(context.assets)
        val expectedBoundsByImageKey = linkedMapOf(
            "headgear_leather_hat" to EquipmentOpaqueBounds(19, 4, 45, 22),
            "headgear_iron_helmet" to EquipmentOpaqueBounds(18, 4, 46, 29),
            "top_cloth" to EquipmentOpaqueBounds(20, 29, 44, 45),
            "top_leather_armor" to EquipmentOpaqueBounds(20, 29, 44, 45),
            "top_iron_breastplate" to EquipmentOpaqueBounds(20, 29, 44, 45),
            "bottom_cloth_pants" to EquipmentOpaqueBounds(24, 41, 40, 54),
            "bottom_leather_pants" to EquipmentOpaqueBounds(24, 41, 40, 54),
            "bottom_steel_greaves" to EquipmentOpaqueBounds(24, 41, 40, 54),
            "gloves_leather" to EquipmentOpaqueBounds(21, 39, 43, 45),
            "gloves_steel_gauntlets" to EquipmentOpaqueBounds(21, 39, 43, 45),
            "shoes_travelers_boots" to EquipmentOpaqueBounds(23, 53, 41, 58),
            "shoes_windwalker_boots" to EquipmentOpaqueBounds(23, 53, 41, 58),
            "weapon_worn_sword" to EquipmentOpaqueBounds(40, 4, 58, 58),
            "weapon_iron_longsword" to EquipmentOpaqueBounds(40, 4, 58, 58),
            "weapon_ash_spear" to EquipmentOpaqueBounds(40, 4, 58, 58),
            "weapon_steel_mace" to EquipmentOpaqueBounds(40, 4, 58, 58),
        )

        expectedBoundsByImageKey.forEach { (imageKey, expectedBounds) ->
            val loaded = loader.load(
                requireNotNull(EquipmentArtworkCatalog.resolve(imageKey)),
            )
            assertNotNull(loaded)
            loaded as LoadedEquipmentArtwork
            assertEquals(64, loaded.bitmap.width)
            assertEquals(64, loaded.bitmap.height)
            assertEquals(Bitmap.Config.ARGB_8888, loaded.bitmap.config)
            assertFalse(loaded.bitmap.isMutable)
            assertEquals(expectedBounds, loaded.opaqueBounds)
        }
        assertEquals(expectedBoundsByImageKey.size, loader.cacheStats().decodeCount)
    }

    @Test
    fun positiveAndNegativeResultsAreCachedByAssetPath() {
        val loader = EquipmentArtworkLoader(context.assets)
        val outfitDefinition = requireNotNull(
            EquipmentArtworkCatalog.resolve("top_cloth"),
        )

        val first = loader.load(outfitDefinition)
        assertSame(first, loader.load(outfitDefinition.copy(imageKey = "alias")))
        assertEquals(1, loader.cacheStats().decodeCount)

        val missing = EquipmentArtworkDefinition(
            imageKey = "missing",
            assetPath = "character/layers/missing-equipment-artwork.png",
        )
        assertNull(loader.load(missing))
        assertNull(loader.load(missing.copy(imageKey = "missing-alias")))
        assertEquals(2, loader.cacheStats().decodeCount)
    }
}
