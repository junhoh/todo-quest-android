package com.todoquest.feature.shop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EquipmentArtworkCatalogTest {
    @Test
    fun knownImageKeysResolveToCanonicalRuntimeCharacterLayerAssets() {
        val knownImageKeys = listOf(
            "headgear_leather_hat",
            "headgear_iron_helmet",
            "top_cloth",
            "top_leather_armor",
            "top_iron_breastplate",
            "bottom_cloth_pants",
            "bottom_leather_pants",
            "bottom_steel_greaves",
            "gloves_leather",
            "gloves_steel_gauntlets",
            "shoes_travelers_boots",
            "shoes_windwalker_boots",
            "weapon_worn_sword",
            "weapon_iron_longsword",
            "weapon_ash_spear",
            "weapon_steel_mace",
        )

        knownImageKeys.forEach { imageKey ->
            assertEquals(
                EquipmentArtworkDefinition(
                    imageKey = imageKey,
                    assetPath = "character/layers/$imageKey.png",
                ),
                EquipmentArtworkCatalog.resolve(imageKey),
            )
        }
    }

    @Test
    fun nullAndUnknownImageKeysDoNotResolve() {
        assertNull(EquipmentArtworkCatalog.resolve(null))
        assertNull(EquipmentArtworkCatalog.resolve("equipment_image_unknown"))
    }
}
