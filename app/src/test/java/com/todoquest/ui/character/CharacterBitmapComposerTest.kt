package com.todoquest.ui.character

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.todoquest.domain.model.CharacterAppearance
import com.todoquest.domain.model.CharacterLoadoutCatalog
import com.todoquest.domain.model.EquippedItems
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CharacterBitmapComposerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun appAssetsComposeEveryLoadoutAndMatchAllEquipmentRawPixelGoldens() {
        val composer = CharacterBitmapComposer(
            assetManager = context.assets,
            compositeCacheCapacity = 9_600,
        )

        val composites = allLoadouts().associateWith(composer::compose)

        assertEquals(9_600, composites.size)
        assertEquals(9_600, composites.values.count { it != null })
        composites.values.filterNotNull().forEach { composite ->
            assertEquals(64, composite.width)
            assertEquals(64, composite.height)
            assertEquals(Bitmap.Config.ARGB_8888, composite.config)
            assertFalse(composite.isMutable)
        }

        val adventureState = defaultState()
        assertArrayEquals(
            runtimeEquippedGoldenPixels(),
            requireNotNull(composites[adventureState]).argbPixels(),
        )
        val defaultState = adventureState.copy(
            equippedItems = adventureState.equippedItems.copy(glovesId = null),
        )
        assertArrayEquals(
            equipmentPreviewPixels("leather-hat-equipped.png"),
            requireNotNull(
                composites[
                    defaultState.copy(
                        equippedItems = defaultState.equippedItems.copy(
                            headId = CharacterLoadoutCatalog.HEADGEAR_LEATHER_HAT,
                        ),
                    )
                ],
            ).argbPixels(),
        )
        assertArrayEquals(
            equipmentPreviewPixels("iron-helmet-equipped.png"),
            requireNotNull(
                composites[
                    defaultState.copy(
                        equippedItems = defaultState.equippedItems.copy(
                            headId = CharacterLoadoutCatalog.HEADGEAR_IRON_HELMET,
                        ),
                    )
                ],
            ).argbPixels(),
        )
        OUTFIT_TOP_PREVIEWS.forEach { (topId, previewFileName) ->
            assertArrayEquals(
                equipmentPreviewPixels(previewFileName),
                requireNotNull(
                    composites[
                        defaultState.copy(
                            equippedItems = defaultState.equippedItems.copy(topId = topId),
                        )
                    ],
                ).argbPixels(),
            )
        }
        OUTFIT_BOTTOM_PREVIEWS.forEach { (bottomId, previewFileName) ->
            assertArrayEquals(
                equipmentPreviewPixels(previewFileName),
                requireNotNull(
                    composites[
                        defaultState.copy(
                            equippedItems = defaultState.equippedItems.copy(bottomId = bottomId),
                        )
                    ],
                ).argbPixels(),
            )
        }
        GLOVE_PREVIEWS.forEach { (glovesId, previewFileName) ->
            assertArrayEquals(
                equipmentPreviewPixels(previewFileName),
                requireNotNull(
                    composites[
                        defaultState.copy(
                            equippedItems = defaultState.equippedItems.copy(
                                glovesId = glovesId,
                            ),
                        )
                    ],
                ).argbPixels(),
            )
        }
        SHOE_PREVIEWS.forEach { (shoesId, previewFileName) ->
            assertArrayEquals(
                equipmentPreviewPixels(previewFileName),
                requireNotNull(
                    composites[
                        defaultState.copy(
                            equippedItems = defaultState.equippedItems.copy(
                                shoesId = shoesId,
                            ),
                        )
                    ],
                ).argbPixels(),
            )
        }
        WEAPON_PREVIEWS.forEach { (weaponId, previewFileName) ->
            assertArrayEquals(
                equipmentPreviewPixels(previewFileName),
                requireNotNull(
                    composites[
                        defaultState.copy(
                            equippedItems = defaultState.equippedItems.copy(
                                weaponId = weaponId,
                            ),
                        )
                    ],
                ).argbPixels(),
            )
        }
        val matrix = equipmentPreviewBitmap("top-bottom-combination-matrix.png")
        OUTFIT_TOP_PREVIEWS.forEachIndexed { row, (topId, _) ->
            OUTFIT_BOTTOM_PREVIEWS.forEachIndexed { column, (bottomId, _) ->
                assertArrayEquals(
                    matrix.regionArgbPixels(
                        left = column * 64,
                        top = row * 64,
                        width = 64,
                        height = 64,
                    ),
                    requireNotNull(
                        composites[
                            defaultState.copy(
                                equippedItems = defaultState.equippedItems.copy(
                                    topId = topId,
                                    bottomId = bottomId,
                                ),
                            )
                        ],
                    ).argbPixels(),
                )
            }
        }
        val glovesShoesMatrix = equipmentPreviewBitmap("gloves-shoes-combination-matrix.png")
        GLOVE_PREVIEWS.forEachIndexed { row, (glovesId, _) ->
            SHOE_PREVIEWS.forEachIndexed { column, (shoesId, _) ->
                assertArrayEquals(
                    glovesShoesMatrix.regionArgbPixels(
                        left = column * 64,
                        top = row * 64,
                        width = 64,
                        height = 64,
                    ),
                    requireNotNull(
                        composites[
                            defaultState.copy(
                                equippedItems = defaultState.equippedItems.copy(
                                    glovesId = glovesId,
                                    shoesId = shoesId,
                                ),
                            )
                        ],
                    ).argbPixels(),
                )
            }
        }
        val weaponMatrix = equipmentPreviewBitmap("weapon-combination-matrix.png")
        WEAPON_PREVIEWS.forEachIndexed { index, (weaponId, _) ->
            assertArrayEquals(
                weaponMatrix.regionArgbPixels(
                    left = index % 2 * 64,
                    top = index / 2 * 64,
                    width = 64,
                    height = 64,
                ),
                requireNotNull(
                    composites[
                        defaultState.copy(
                            equippedItems = defaultState.equippedItems.copy(
                                weaponId = weaponId,
                            ),
                        )
                    ],
                ).argbPixels(),
            )
        }
        assertEquals(32, composer.cacheStats().layerDecodeCount)
        assertEquals(9_600, composer.cacheStats().compositeCount)
    }

    @Test
    fun gloveRuntimeLayersReplaceAllThirtyEightHandPixelsWithoutChangingTheMask() {
        val bareHands = runtimeLayerBitmap("hands_front.png")
        val barePixels = bareHands.argbPixels()
        val bareMask = barePixels.indices.filter { barePixels[it] ushr 24 != 0 }.toIntArray()

        assertEquals(38, bareMask.size)
        listOf(
            "gloves_adventure.png",
            "gloves_leather.png",
            "gloves_steel_gauntlets.png",
        ).forEach { fileName ->
            val gloves = runtimeLayerBitmap(fileName)
            val glovePixels = gloves.argbPixels()
            val gloveMask = glovePixels.indices.filter {
                glovePixels[it] ushr 24 != 0
            }.toIntArray()

            assertArrayEquals(bareMask, gloveMask)
            assertTrue(bareMask.all { index -> barePixels[index] != glovePixels[index] })
        }
    }

    @Test
    fun defaultSwordAndGlovesUseTheSchemaV5TopmostWeaponGroup() {
        val composer = CharacterBitmapComposer(assetManager = context.assets)
        val weaponHeld = runtimeLayerBitmap("weapon_held_default_sword.png")
        val weaponFront = runtimeLayerBitmap("weapon_front_default_sword.png")

        GLOVE_PREVIEWS.forEach { (glovesId, previewFileName) ->
            val gloves = runtimeLayerBitmap("$glovesId.png")
            val withWeaponState = defaultState().copy(
                equippedItems = defaultState().equippedItems.copy(glovesId = glovesId),
            )
            val withoutWeaponState = withWeaponState.copy(
                equippedItems = withWeaponState.equippedItems.copy(weaponId = null),
            )
            val withWeapon = requireNotNull(composer.compose(withWeaponState))
            val withoutWeapon = requireNotNull(composer.compose(withoutWeaponState))

            assertArrayEquals(
                equipmentPreviewPixels(previewFileName),
                withWeapon.argbPixels(),
            )
            assertEquals(weaponHeld.getPixel(42, 42), withWeapon.getPixel(42, 42))
            assertEquals(weaponFront.getPixel(42, 39), withWeapon.getPixel(42, 39))
            assertEquals(gloves.getPixel(42, 39), withoutWeapon.getPixel(42, 39))
        }
    }

    @Test
    fun gameplayWeaponsWinEveryOverlapWithHandsTopHairAndAccessory() {
        val composer = CharacterBitmapComposer(assetManager = context.assets)

        WEAPON_PREVIEWS.forEach { (weaponId, _) ->
            val state = defaultState().copy(
                equippedItems = defaultState().equippedItems.copy(weaponId = weaponId),
            )
            val compositePixels = requireNotNull(composer.compose(state)).argbPixels()
            val weaponPixels = runtimeLayerBitmap("$weaponId.png").argbPixels()
            val comparedSlots = setOf(
                CharacterLayerSlot.HANDS_FRONT,
                CharacterLayerSlot.TOP,
                CharacterLayerSlot.HAIR_FRONT,
                CharacterLayerSlot.HEADGEAR_FRONT,
                CharacterLayerSlot.ACCESSORY_FRONT,
            )
            val overlappingIndexes = CharacterLayerCatalog.resolve(state)
                .filter { it.slot in comparedSlots }
                .flatMap { definition ->
                    val layerPixels = runtimeLayerBitmap(
                        definition.assetPath.substringAfterLast('/'),
                    ).argbPixels()
                    layerPixels.indices.filter { index ->
                        layerPixels[index] ushr 24 != 0 && weaponPixels[index] ushr 24 != 0
                    }
                }
                .toSet()

            assertTrue(overlappingIndexes.isNotEmpty())
            assertTrue(overlappingIndexes.all { index ->
                compositePixels[index] == weaponPixels[index]
            })
        }
    }

    @Test
    fun newShoesPreserveEveryBottomAnkleOverlapCenterAndSole() {
        val composer = CharacterBitmapComposer(
            assetManager = context.assets,
            compositeCacheCapacity = 10,
        )
        val ankleOverlap = (24..31).flatMap { x -> listOf(x to 53, x to 54) } +
            (33..40).flatMap { x -> listOf(x to 53, x to 54) }

        SHOE_PREVIEWS.forEach { (shoesId, _) ->
            val shoes = runtimeLayerBitmap("$shoesId.png")
            val opaquePoints = (0 until 64).flatMap { y ->
                (0 until 64).mapNotNull { x ->
                    (x to y).takeIf { shoes.getPixel(x, y) ushr 24 != 0 }
                }
            }

            assertEquals(23, opaquePoints.minOf { it.first })
            assertEquals(41, opaquePoints.maxOf { it.first })
            assertEquals(32, (opaquePoints.minOf { it.first } + opaquePoints.maxOf { it.first }) / 2)
            assertEquals(58, opaquePoints.maxOf { it.second })
            assertEquals(0, shoes.getPixel(32, 58) ushr 24)
            assertTrue(((23..31) + (33..41)).all { x -> shoes.getPixel(x, 58) ushr 24 != 0 })
            assertTrue(ankleOverlap.all { (x, y) -> shoes.getPixel(x, y) ushr 24 != 0 })

            BOTTOM_IDS.forEach { bottomId ->
                val state = defaultState().copy(
                    equippedItems = defaultState().equippedItems.copy(
                        bottomId = bottomId,
                        shoesId = shoesId,
                    ),
                )
                val bottomPath = CharacterLayerCatalog.resolve(state)
                    .single { it.slot == CharacterLayerSlot.BOTTOM }
                    .assetPath
                    .removePrefix("character/layers/")
                val bottom = runtimeLayerBitmap(bottomPath)

                assertTrue(ankleOverlap.all { (x, y) -> bottom.getPixel(x, y) ushr 24 != 0 })
                assertNotNull(composer.compose(state))
            }
        }
    }

    @Test
    fun sameStateHitsBothCachesAndStateChangeOnlyCreatesRelatedComposite() {
        val composer = CharacterBitmapComposer(
            assetManager = context.assets,
            compositeCacheCapacity = 2,
        )
        val fullyEquipped = defaultState()

        val first = composer.compose(fullyEquipped)
        assertNotNull(first)
        first as Bitmap
        val firstStats = composer.cacheStats()
        val second = composer.compose(fullyEquipped)
        assertNotNull(second)
        second as Bitmap

        assertSame(first, second)
        assertEquals(firstStats, composer.cacheStats())

        val withoutHeadgear = fullyEquipped.copy(
            equippedItems = fullyEquipped.equippedItems.copy(headId = null),
        )
        assertNotNull(composer.compose(withoutHeadgear))
        assertEquals(firstStats.layerDecodeCount, composer.cacheStats().layerDecodeCount)
        assertEquals(firstStats.compositeCount + 1, composer.cacheStats().compositeCount)

        assertSame(first, composer.compose(fullyEquipped))
        assertEquals(firstStats.compositeCount + 1, composer.cacheStats().compositeCount)
    }

    @Test
    fun leastRecentlyUsedCompositeIsRecreatedWithoutRedecodingLayers() {
        val composer = CharacterBitmapComposer(
            assetManager = context.assets,
            compositeCacheCapacity = 2,
        )
        val firstState = defaultState()
        val secondState = firstState.copy(
            equippedItems = firstState.equippedItems.copy(headId = null),
        )
        val thirdState = firstState.copy(
            equippedItems = firstState.equippedItems.copy(accessoryId = null),
        )

        val first = composer.compose(firstState)
        assertNotNull(first)
        first as Bitmap
        composer.compose(secondState)
        composer.compose(thirdState)
        val beforeRecreate = composer.cacheStats()
        val recreated = composer.compose(firstState)
        assertNotNull(recreated)
        recreated as Bitmap

        assertFalse(first === recreated)
        assertEquals(beforeRecreate.layerDecodeCount, composer.cacheStats().layerDecodeCount)
        assertEquals(beforeRecreate.compositeCount + 1, composer.cacheStats().compositeCount)
    }

    @Test
    fun missingOrWrongSizeWeaponRuntimeLayerDecodesToEmpty() {
        val composer = CharacterBitmapComposer(assetManager = context.assets)
        val missingDefinition = definition(
            "character/layers/weapon_missing.png",
            CharacterLayerSlot.WEAPON_FRONT,
        )

        assertNull(composer.decodeLayerForTest(missingDefinition))

        val wrongSizeDefinition = definition(
            "character/layers/weapon_worn_sword.png",
            CharacterLayerSlot.WEAPON_FRONT,
        )
        CharacterLayerDefinition::class.java.getDeclaredField("canvasWidth").apply {
            isAccessible = true
            setInt(wrongSizeDefinition, 63)
        }
        assertNull(composer.decodeLayerForTest(wrongSizeDefinition))
    }

    private fun runtimeEquippedGoldenPixels(): IntArray {
        val sheet = BitmapFactory.decodeFile(
            modularSheetFile().absolutePath,
            BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: error("modular sheet golden could not be decoded")
        return IntArray(64 * 64).also { pixels ->
            sheet.getPixels(pixels, 0, 64, 7 * 64, 64, 64, 64)
        }
    }

    private fun equipmentPreviewPixels(fileName: String): IntArray =
        equipmentPreviewBitmap(fileName).argbPixels()

    private fun equipmentPreviewBitmap(fileName: String): Bitmap =
        BitmapFactory.decodeFile(
            equipmentPreviewFile(fileName).absolutePath,
            BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: error("equipment preview golden could not be decoded")

    private fun runtimeLayerBitmap(fileName: String): Bitmap =
        context.assets.open("character/layers/$fileName").use { input ->
            BitmapFactory.decodeStream(
                input,
                null,
                BitmapFactory.Options().apply {
                    inScaled = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            ) ?: error("runtime character layer could not be decoded: $fileName")
        }

    private fun modularSheetFile(): File = listOf(
        File("docs/art/character/todo-quest-character-modular-sheet.png"),
        File("../docs/art/character/todo-quest-character-modular-sheet.png"),
    ).firstOrNull(File::isFile) ?: error("modular sheet test golden is missing")

    private fun equipmentPreviewFile(fileName: String): File = listOf(
        File("docs/art/equipment/previews/$fileName"),
        File("../docs/art/equipment/previews/$fileName"),
    ).firstOrNull(File::isFile) ?: error("equipment preview test golden is missing")

    private fun defaultState() = CharacterRenderState(
        appearance = CharacterLoadoutCatalog.defaultAppearance,
        equippedItems = EquippedItems(
            headId = CharacterLoadoutCatalog.HEADGEAR_ADVENTURE,
            topId = CharacterLoadoutCatalog.TOP_ADVENTURE,
            bottomId = CharacterLoadoutCatalog.BOTTOM_ADVENTURE,
            shoesId = CharacterLoadoutCatalog.SHOES_ADVENTURE,
            accessoryId = CharacterLoadoutCatalog.ACCESSORY_ADVENTURE,
            weaponId = CharacterLoadoutCatalog.WEAPON_DEFAULT_SWORD,
            glovesId = CharacterLoadoutCatalog.GLOVES_ADVENTURE,
        ),
    )

    private fun Bitmap.argbPixels(): IntArray = IntArray(width * height).also { pixels ->
        getPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun Bitmap.regionArgbPixels(
        left: Int,
        top: Int,
        width: Int,
        height: Int,
    ): IntArray = IntArray(width * height).also { pixels ->
        getPixels(pixels, 0, width, left, top, width, height)
    }

    private fun definition(
        assetPath: String,
        slot: CharacterLayerSlot = CharacterLayerSlot.BODY_BASE,
    ) = CharacterLayerDefinition(
        assetPath = assetPath,
        slot = slot,
        zIndex = slot.ordinal,
        anchorProfileId = CharacterLayerCatalog.ANCHOR_PROFILE_ID,
    )

    private fun CharacterBitmapComposer.decodeLayerForTest(
        definition: CharacterLayerDefinition,
    ): Bitmap? = CharacterBitmapComposer::class.java.getDeclaredMethod(
        "decodeLayer",
        CharacterLayerDefinition::class.java,
    ).apply {
        isAccessible = true
    }.invoke(this, definition) as Bitmap?

    private fun allLoadouts(): List<CharacterRenderState> = buildList {
        listOf(
            CharacterLoadoutCatalog.TOP_DEFAULT,
            CharacterLoadoutCatalog.TOP_ADVENTURE,
            CharacterLoadoutCatalog.TOP_CLOTH,
            CharacterLoadoutCatalog.TOP_LEATHER_ARMOR,
            CharacterLoadoutCatalog.TOP_IRON_BREASTPLATE,
        )
            .forEach { topId ->
                listOf(
                    CharacterLoadoutCatalog.BOTTOM_DEFAULT,
                    CharacterLoadoutCatalog.BOTTOM_ADVENTURE,
                    CharacterLoadoutCatalog.BOTTOM_CLOTH_PANTS,
                    CharacterLoadoutCatalog.BOTTOM_LEATHER_PANTS,
                    CharacterLoadoutCatalog.BOTTOM_STEEL_GREAVES,
                ).forEach { bottomId ->
                    listOf(
                        CharacterLoadoutCatalog.SHOES_DEFAULT,
                        CharacterLoadoutCatalog.SHOES_ADVENTURE,
                        CharacterLoadoutCatalog.SHOES_TRAVELERS_BOOTS,
                        CharacterLoadoutCatalog.SHOES_WINDWALKER_BOOTS,
                    ).forEach { shoesId ->
                        listOf(
                            null,
                            CharacterLoadoutCatalog.GLOVES_ADVENTURE,
                            CharacterLoadoutCatalog.GLOVES_LEATHER,
                            CharacterLoadoutCatalog.GLOVES_STEEL_GAUNTLETS,
                        ).forEach { glovesId ->
                            listOf(
                                null,
                                CharacterLoadoutCatalog.HEADGEAR_ADVENTURE,
                                CharacterLoadoutCatalog.HEADGEAR_LEATHER_HAT,
                                CharacterLoadoutCatalog.HEADGEAR_IRON_HELMET,
                            ).forEach { headId ->
                                val accessoryId = if (headId == null) {
                                    null
                                } else {
                                    CharacterLoadoutCatalog.ACCESSORY_ADVENTURE
                                }
                                listOf(
                                    null,
                                    CharacterLoadoutCatalog.WEAPON_DEFAULT_SWORD,
                                    CharacterLoadoutCatalog.WEAPON_WORN_SWORD,
                                    CharacterLoadoutCatalog.WEAPON_IRON_LONGSWORD,
                                    CharacterLoadoutCatalog.WEAPON_ASH_SPEAR,
                                    CharacterLoadoutCatalog.WEAPON_STEEL_MACE,
                                ).forEach { weaponId ->
                                    add(
                                        CharacterRenderState(
                                            appearance = CharacterAppearance(
                                                hairId = CharacterLoadoutCatalog.HAIR_DEFAULT,
                                            ),
                                            equippedItems = EquippedItems(
                                                headId = headId,
                                                topId = topId,
                                                bottomId = bottomId,
                                                shoesId = shoesId,
                                                accessoryId = accessoryId,
                                                weaponId = weaponId,
                                                glovesId = glovesId,
                                            ),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
    }

    private companion object {
        val OUTFIT_TOP_PREVIEWS = listOf(
            CharacterLoadoutCatalog.TOP_CLOTH to "top-cloth-equipped.png",
            CharacterLoadoutCatalog.TOP_LEATHER_ARMOR to "top-leather-armor-equipped.png",
            CharacterLoadoutCatalog.TOP_IRON_BREASTPLATE to "top-iron-breastplate-equipped.png",
        )
        val OUTFIT_BOTTOM_PREVIEWS = listOf(
            CharacterLoadoutCatalog.BOTTOM_CLOTH_PANTS to "bottom-cloth-pants-equipped.png",
            CharacterLoadoutCatalog.BOTTOM_LEATHER_PANTS to "bottom-leather-pants-equipped.png",
            CharacterLoadoutCatalog.BOTTOM_STEEL_GREAVES to "bottom-steel-greaves-equipped.png",
        )
        val GLOVE_PREVIEWS = listOf(
            CharacterLoadoutCatalog.GLOVES_LEATHER to "leather-gloves-equipped.png",
            CharacterLoadoutCatalog.GLOVES_STEEL_GAUNTLETS to "steel-gauntlets-equipped.png",
        )
        val SHOE_PREVIEWS = listOf(
            CharacterLoadoutCatalog.SHOES_TRAVELERS_BOOTS to "travelers-boots-equipped.png",
            CharacterLoadoutCatalog.SHOES_WINDWALKER_BOOTS to "windwalker-boots-equipped.png",
        )
        val WEAPON_PREVIEWS = listOf(
            CharacterLoadoutCatalog.WEAPON_WORN_SWORD to "worn-sword-equipped.png",
            CharacterLoadoutCatalog.WEAPON_IRON_LONGSWORD to "iron-longsword-equipped.png",
            CharacterLoadoutCatalog.WEAPON_ASH_SPEAR to "ash-spear-equipped.png",
            CharacterLoadoutCatalog.WEAPON_STEEL_MACE to "steel-mace-equipped.png",
        )
        val BOTTOM_IDS = listOf(
            CharacterLoadoutCatalog.BOTTOM_DEFAULT,
            CharacterLoadoutCatalog.BOTTOM_ADVENTURE,
            CharacterLoadoutCatalog.BOTTOM_CLOTH_PANTS,
            CharacterLoadoutCatalog.BOTTOM_LEATHER_PANTS,
            CharacterLoadoutCatalog.BOTTOM_STEEL_GREAVES,
        )
    }
}
