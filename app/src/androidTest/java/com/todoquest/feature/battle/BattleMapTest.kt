package com.todoquest.feature.battle

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.todoquest.R
import com.todoquest.domain.model.CharacterLoadoutCatalog
import com.todoquest.domain.model.CombatSnapshot
import com.todoquest.domain.model.CombatEventKey
import com.todoquest.domain.model.CombatEventKind
import com.todoquest.domain.model.MonsterBalanceConfig
import com.todoquest.domain.model.MonsterCatalog
import com.todoquest.domain.model.MonsterGrade
import com.todoquest.domain.model.MonsterInstance
import com.todoquest.domain.model.MonsterSpecies
import com.todoquest.domain.model.MonsterStats
import com.todoquest.domain.model.StageProgress
import com.todoquest.domain.model.StatusEffectType
import com.todoquest.domain.usecase.MonsterSpeciesPolicy
import com.todoquest.domain.usecase.MonsterStagePolicy
import com.todoquest.ui.character.CharacterRenderState
import com.todoquest.ui.theme.TodoQuestTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BattleMapTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contentRendersIndependentBattleLayersAndUnitSemantics() {
        composeRule.setContent {
            TodoQuestTheme {
                BattleMap(state = contentState(monsterCount = 1))
            }
        }

        listOf(
            "battle-map",
            "battle-background-image",
            "battle-decorations-layer",
            "battle-ground-layer",
            "battle-player-layer",
            "battle-monster-layer",
            "battle-player-health",
            "battle-monster-health",
            "battle-overlay-layer",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }
        composeRule.onNodeWithContentDescription("모험가, 체력 75/100")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("고블린 정찰병, 체력 40/50")
            .assertIsDisplayed()
    }

    @Test
    fun playerAndMonsterLayersDrawOpaquePixelSpriteOutlines() {
        composeRule.setContent {
            TodoQuestTheme {
                BattleMap(state = contentState(monsterCount = 1))
            }
        }

        listOf("battle-player-layer", "battle-monster-layer").forEach { tag ->
            val bitmap = composeRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
            val hasOutlinePixel = (0 until bitmap.height).any { y ->
                (0 until bitmap.width).any { x ->
                    bitmap.getPixel(x, y) == PixelSpriteOutlineArgb
                }
            }
            assertTrue("$tag must draw an opaque pixel sprite outline", hasOutlinePixel)
        }
    }

    @Test
    fun skeletonUnitRendersSpeciesSpriteHealthSemanticsAndOpaqueOutlinePixels() {
        val state = contentState(
            monsterCount = 1,
            monsterSpecies = MonsterSpecies.SKELETON_SOLDIER,
        )
        composeRule.setContent {
            TodoQuestTheme {
                BattleMap(state = state)
            }
        }

        val monster = state.monsters.single()
        val sprite = monster.sprite as BattleSpriteUiModel.Resource
        assertEquals(R.drawable.todo_quest_skeleton_soldier_front_idle, sprite.spriteResId)
        assertEquals(64, sprite.frame.sourceWidth)
        assertEquals(64, sprite.frame.sourceHeight)
        assertEquals(BattlePosition(0.5f, 58f / 64f), sprite.frame.groundAnchor)
        composeRule.onNodeWithContentDescription("해골 병사, 체력 40/50")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("battle-ground-layer").assertIsDisplayed()

        val bitmap = composeRule.onNodeWithTag("battle-monster-layer")
            .captureToImage()
            .asAndroidBitmap()
        val hasOutlinePixel = (0 until bitmap.height).any { y ->
            (0 until bitmap.width).any { x ->
                bitmap.getPixel(x, y) == PixelSpriteOutlineArgb
            }
        }
        assertTrue("skeleton must draw an opaque pixel sprite outline", hasOutlinePixel)
        val hasAlphaFringe = (0 until bitmap.height).any { y ->
            (0 until bitmap.width).any { x ->
                val alpha = bitmap.getPixel(x, y) ushr 24
                alpha != 0 && alpha != 255
            }
        }
        assertTrue("nearest-neighbor skeleton rendering must not add alpha fringe", !hasAlphaFringe)
    }

    @Test
    fun slimeRendersTwoTimesNearestNeighborAtSharedFootAnchorWithDocumentedBoundsAndScale() {
        val slime = contentState(
            monsterCount = 1,
            monsterSpecies = MonsterSpecies.SLIME,
        )
        val slimeMonster = slime.monsters.single()
        val slimeSprite = slimeMonster.sprite as BattleSpriteUiModel.Resource
        val invalidSpriteState = slime.copy(
            monsters = listOf(
                slimeMonster.copy(
                    sprite = slimeSprite.copy(spriteResId = Int.MAX_VALUE),
                ),
            ),
        )
        val renderedState = mutableStateOf(contentState(monsterCount = 0))
        composeRule.setContent {
            TodoQuestTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 1.5f, fontScale = 1f),
                ) {
                    Box(modifier = Modifier.width(585.143.dp)) {
                        BattleMap(state = renderedState.value)
                    }
                }
            }
        }

        val groundWithoutMonster = composeRule.onNodeWithTag("battle-ground-layer")
            .captureToImage()
            .asAndroidBitmap()
        composeRule.runOnIdle { renderedState.value = invalidSpriteState }
        composeRule.waitForIdle()
        val spriteBackground = composeRule.onNodeWithTag("battle-monster-layer")
            .captureToImage()
            .asAndroidBitmap()

        composeRule.runOnIdle { renderedState.value = slime }
        composeRule.waitForIdle()
        assertEquals(R.drawable.todo_quest_slime_front_idle, slimeSprite.spriteResId)
        assertEquals(0, slimeSprite.frame.sourceX)
        assertEquals(0, slimeSprite.frame.sourceY)
        assertEquals(64, slimeSprite.frame.sourceWidth)
        assertEquals(64, slimeSprite.frame.sourceHeight)
        assertEquals(BattlePosition(0.5f, 58f / 64f), slimeSprite.frame.groundAnchor)
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val slimeName = targetContext.getString(R.string.battle_monster_slime_name)
        composeRule.onNodeWithContentDescription(
            targetContext.getString(
                R.string.battle_unit_health_description,
                slimeName,
                40,
                50,
            ),
        )
            .assertIsDisplayed()

        val rendered = composeRule.onNodeWithTag("battle-monster-layer")
            .captureToImage()
            .asAndroidBitmap()
        val source = requireNotNull(
            BitmapFactory.decodeResource(
                targetContext.resources,
                R.drawable.todo_quest_slime_front_idle,
                BitmapFactory.Options().apply { inScaled = false },
            ),
        )
        val playerBody = targetContext.assets.open("character/layers/body_base.png").use { input ->
            requireNotNull(BitmapFactory.decodeStream(input))
        }
        assertEquals(64, source.width)
        assertEquals(64, source.height)
        assertEquals(listOf(17, 35, 47, 58), source.opaqueBoundsInclusive())
        assertEquals(listOf(20, 7, 44, 58), playerBody.opaqueBoundsInclusive())
        val slimeOpaqueHeight = 58 - 35 + 1
        val playerOpaqueHeight = 58 - 7 + 1
        assertEquals(46.2f, slimeOpaqueHeight * 100f / playerOpaqueHeight, 0.1f)
        assertTwoTimesNearestNeighbor(
            source = source,
            rendered = rendered,
            background = spriteBackground,
        )
        assertTrue(
            "slime must draw an opaque pixel sprite outline",
            rendered.containsColor(PixelSpriteOutlineArgb),
        )
        assertTrue(
            "slime source alpha must stay binary before nearest-neighbor composition",
            source.alphaValues().all { it == 0 || it == 255 },
        )
        assertTrue(
            "nearest-neighbor slime rendering must not add alpha fringe",
            rendered.alphaValues().all { it == 0 || it == 255 },
        )

        val sourceFootY = source.maximumOpaqueY()
        val renderedFootY = rendered.maximumDifferentY(spriteBackground)
        assertEquals(58, sourceFootY)
        assertEquals(sourceFootY * 2 + 1, renderedFootY)

        val monsterBounds = boundsOf("battle-monster-layer")
        val groundBounds = boundsOf("battle-ground-layer")
        val groundBitmap = composeRule.onNodeWithTag("battle-ground-layer")
            .captureToImage()
            .asAndroidBitmap()
        val anchorX = (
            monsterBounds.left - groundBounds.left +
                monsterBounds.width * slimeSprite.frame.groundAnchor.x
            ).toInt().coerceIn(0, groundBitmap.width - 1)
        val anchorY = (
            monsterBounds.top - groundBounds.top +
                monsterBounds.height * slimeSprite.frame.groundAnchor.y
            ).toInt().coerceIn(0, groundBitmap.height - 1)
        assertTrue(
            "ground shadow must stay centered on the shared slime foot anchor",
            groundBitmap.getPixel(anchorX, anchorY) !=
                groundWithoutMonster.getPixel(anchorX, anchorY),
        )
    }

    @Test
    fun harpyRendersTwoTimesNearestNeighborAtTheSharedFootAnchorWithoutAlphaFringe() {
        val harpy = contentState(
            monsterCount = 1,
            monsterSpecies = MonsterSpecies.HARPY,
        )
        val harpyMonster = harpy.monsters.single()
        val harpySprite = harpyMonster.sprite as BattleSpriteUiModel.Resource
        val invalidSpriteState = harpy.copy(
            monsters = listOf(
                harpyMonster.copy(
                    sprite = harpySprite.copy(spriteResId = Int.MAX_VALUE),
                ),
            ),
        )
        val renderedState = mutableStateOf(contentState(monsterCount = 0))
        composeRule.setContent {
            TodoQuestTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 1.5f, fontScale = 1f),
                ) {
                    Box(modifier = Modifier.width(585.143.dp)) {
                        BattleMap(state = renderedState.value)
                    }
                }
            }
        }

        val groundWithoutMonster = composeRule.onNodeWithTag("battle-ground-layer")
            .captureToImage()
            .asAndroidBitmap()
        composeRule.runOnIdle { renderedState.value = invalidSpriteState }
        composeRule.waitForIdle()
        val spriteBackground = composeRule.onNodeWithTag("battle-monster-layer")
            .captureToImage()
            .asAndroidBitmap()

        composeRule.runOnIdle { renderedState.value = harpy }
        composeRule.waitForIdle()
        assertEquals(R.drawable.todo_quest_harpy_front_idle, harpySprite.spriteResId)
        assertEquals(0, harpySprite.frame.sourceX)
        assertEquals(0, harpySprite.frame.sourceY)
        assertEquals(64, harpySprite.frame.sourceWidth)
        assertEquals(64, harpySprite.frame.sourceHeight)
        assertEquals(BattlePosition(0.5f, 58f / 64f), harpySprite.frame.groundAnchor)
        composeRule.onNodeWithContentDescription("하피, 체력 40/50")
            .assertIsDisplayed()

        val rendered = composeRule.onNodeWithTag("battle-monster-layer")
            .captureToImage()
            .asAndroidBitmap()
        val source = requireNotNull(
            BitmapFactory.decodeResource(
                InstrumentationRegistry.getInstrumentation().targetContext.resources,
                R.drawable.todo_quest_harpy_front_idle,
                BitmapFactory.Options().apply { inScaled = false },
            ),
        )
        assertEquals(64, source.width)
        assertEquals(64, source.height)
        assertEquals(128, rendered.width)
        assertEquals(128, rendered.height)
        assertTwoTimesNearestNeighbor(
            source = source,
            rendered = rendered,
            background = spriteBackground,
        )
        assertTrue(
            "harpy must draw an opaque pixel sprite outline",
            rendered.containsColor(PixelSpriteOutlineArgb),
        )
        assertTrue(
            "harpy source alpha must stay binary before nearest-neighbor composition",
            source.alphaValues().all { it == 0 || it == 255 },
        )
        assertTrue(
            "nearest-neighbor harpy rendering must not add alpha fringe",
            rendered.alphaValues().all { it == 0 || it == 255 },
        )

        val sourceFootY = source.maximumOpaqueY()
        val renderedFootY = rendered.maximumDifferentY(spriteBackground)
        assertEquals(58, sourceFootY)
        assertEquals(sourceFootY * 2 + 1, renderedFootY)

        val monsterBounds = boundsOf("battle-monster-layer")
        val groundBounds = boundsOf("battle-ground-layer")
        val groundBitmap = composeRule.onNodeWithTag("battle-ground-layer")
            .captureToImage()
            .asAndroidBitmap()
        val anchorX = (
            monsterBounds.left - groundBounds.left +
                monsterBounds.width * harpySprite.frame.groundAnchor.x
            ).toInt().coerceIn(0, groundBitmap.width - 1)
        val anchorY = (
            monsterBounds.top - groundBounds.top +
                monsterBounds.height * harpySprite.frame.groundAnchor.y
            ).toInt().coerceIn(0, groundBitmap.height - 1)
        assertTrue(
            "ground shadow must stay centered on the shared harpy foot anchor",
            groundBitmap.getPixel(anchorX, anchorY) !=
                groundWithoutMonster.getPixel(anchorX, anchorY),
        )
    }

    @Test
    fun corruptedTreeSpiritRendersTwoTimesNearestNeighborAtTheSharedFootAnchor() {
        val treeSpirit = contentState(
            monsterCount = 1,
            monsterSpecies = MonsterSpecies.CORRUPTED_TREE_SPIRIT,
        )
        val treeSpiritMonster = treeSpirit.monsters.single()
        val treeSpiritSprite = treeSpiritMonster.sprite as BattleSpriteUiModel.Resource
        val invalidSpriteState = treeSpirit.copy(
            monsters = listOf(
                treeSpiritMonster.copy(
                    sprite = treeSpiritSprite.copy(spriteResId = Int.MAX_VALUE),
                ),
            ),
        )
        val renderedState = mutableStateOf(contentState(monsterCount = 0))
        composeRule.setContent {
            TodoQuestTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 1.5f, fontScale = 1f),
                ) {
                    Box(modifier = Modifier.width(585.143.dp)) {
                        BattleMap(state = renderedState.value)
                    }
                }
            }
        }

        val groundWithoutMonster = composeRule.onNodeWithTag("battle-ground-layer")
            .captureToImage()
            .asAndroidBitmap()
        composeRule.runOnIdle { renderedState.value = invalidSpriteState }
        composeRule.waitForIdle()
        val spriteBackground = composeRule.onNodeWithTag("battle-monster-layer")
            .captureToImage()
            .asAndroidBitmap()

        composeRule.runOnIdle { renderedState.value = treeSpirit }
        composeRule.waitForIdle()
        assertEquals(
            R.drawable.todo_quest_corrupted_tree_spirit_front_idle,
            treeSpiritSprite.spriteResId,
        )
        assertEquals(0, treeSpiritSprite.frame.sourceX)
        assertEquals(0, treeSpiritSprite.frame.sourceY)
        assertEquals(64, treeSpiritSprite.frame.sourceWidth)
        assertEquals(64, treeSpiritSprite.frame.sourceHeight)
        assertEquals(BattlePosition(0.5f, 58f / 64f), treeSpiritSprite.frame.groundAnchor)
        composeRule.onNodeWithContentDescription("타락한 나무 정령, 체력 40/50")
            .assertIsDisplayed()

        val rendered = composeRule.onNodeWithTag("battle-monster-layer")
            .captureToImage()
            .asAndroidBitmap()
        val source = requireNotNull(
            BitmapFactory.decodeResource(
                InstrumentationRegistry.getInstrumentation().targetContext.resources,
                R.drawable.todo_quest_corrupted_tree_spirit_front_idle,
                BitmapFactory.Options().apply { inScaled = false },
            ),
        )
        assertEquals(64, source.width)
        assertEquals(64, source.height)
        assertEquals(128, rendered.width)
        assertEquals(128, rendered.height)
        assertTwoTimesNearestNeighbor(
            source = source,
            rendered = rendered,
            background = spriteBackground,
        )
        assertTrue(
            "tree spirit must draw an opaque pixel sprite outline",
            rendered.containsColor(PixelSpriteOutlineArgb),
        )
        assertTrue(
            "tree spirit source alpha must stay binary before nearest-neighbor composition",
            source.alphaValues().all { it == 0 || it == 255 },
        )

        val sourceFootY = source.maximumOpaqueY()
        val renderedFootY = rendered.maximumDifferentY(spriteBackground)
        assertEquals(58, sourceFootY)
        assertEquals(sourceFootY * 2 + 1, renderedFootY)

        val monsterBounds = boundsOf("battle-monster-layer")
        val groundBounds = boundsOf("battle-ground-layer")
        val groundBitmap = composeRule.onNodeWithTag("battle-ground-layer")
            .captureToImage()
            .asAndroidBitmap()
        val anchorX = (
            monsterBounds.left - groundBounds.left +
                monsterBounds.width * treeSpiritSprite.frame.groundAnchor.x
            ).toInt().coerceIn(0, groundBitmap.width - 1)
        val anchorY = (
            monsterBounds.top - groundBounds.top +
                monsterBounds.height * treeSpiritSprite.frame.groundAnchor.y
            ).toInt().coerceIn(0, groundBitmap.height - 1)
        assertTrue(
            "ground shadow must stay centered on the shared tree spirit foot anchor",
            groundBitmap.getPixel(anchorX, anchorY) !=
                groundWithoutMonster.getPixel(anchorX, anchorY),
        )
    }

    @Test
    fun corruptedTreeSpiritAttackHitAndDeathAnnouncementsUseSpeciesResources() {
        val treeSpirit = contentState(
            monsterCount = 1,
            monsterSpecies = MonsterSpecies.CORRUPTED_TREE_SPIRIT,
        )
        val phase = mutableStateOf(
            activePresentation(BattleAnimationPhase.PLAYER_ATTACKING, treeSpirit),
        )
        composeRule.setContent {
            TodoQuestTheme {
                BattleMap(
                    state = treeSpirit,
                    presentation = phase.value,
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            "공격자 모험가, 대상 타락한 나무 정령, 공격합니다.",
        ).assertIsDisplayed()

        composeRule.runOnIdle {
            phase.value = activePresentation(BattleAnimationPhase.MONSTER_ATTACKING, treeSpirit)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(
            "공격자 타락한 나무 정령, 대상 모험가, 공격합니다.",
        ).assertIsDisplayed()

        composeRule.runOnIdle {
            phase.value = activePresentation(BattleAnimationPhase.MONSTER_HIT, treeSpirit)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("타락한 나무 정령, 15 피해를 받았습니다.")
            .assertIsDisplayed()

        composeRule.runOnIdle {
            phase.value = activePresentation(BattleAnimationPhase.MONSTER_DYING, treeSpirit)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("타락한 나무 정령이 쓰러졌습니다.")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
        composeRule.onNodeWithContentDescription("고블린 정찰병이 쓰러졌습니다.")
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription("해골 병사가 쓰러졌습니다.")
            .assertDoesNotExist()
    }

    @Test
    fun skeletonAttackHitAndDeathAnnouncementsUseSkeletonNameAndDeathResource() {
        val skeleton = contentState(
            monsterCount = 1,
            monsterSpecies = MonsterSpecies.SKELETON_SOLDIER,
        )
        val phase = mutableStateOf(activePresentation(BattleAnimationPhase.PLAYER_ATTACKING, skeleton))
        composeRule.setContent {
            TodoQuestTheme {
                BattleMap(
                    state = skeleton,
                    presentation = phase.value,
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            "공격자 모험가, 대상 해골 병사, 공격합니다.",
        ).assertIsDisplayed()

        composeRule.runOnIdle {
            phase.value = activePresentation(BattleAnimationPhase.MONSTER_ATTACKING, skeleton)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(
            "공격자 해골 병사, 대상 모험가, 공격합니다.",
        ).assertIsDisplayed()

        composeRule.runOnIdle {
            phase.value = activePresentation(BattleAnimationPhase.MONSTER_HIT, skeleton)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("해골 병사, 15 피해를 받았습니다.")
            .assertIsDisplayed()

        composeRule.runOnIdle {
            phase.value = activePresentation(BattleAnimationPhase.MONSTER_DYING, skeleton)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("해골 병사가 쓰러졌습니다.")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("고블린 정찰병이 쓰러졌습니다.")
            .assertDoesNotExist()
    }

    @Test
    fun harpyAttackHitAndDeathAnnouncementsUseHarpyNameAndDeathResource() {
        val harpy = contentState(
            monsterCount = 1,
            monsterSpecies = MonsterSpecies.HARPY,
        )
        val phase = mutableStateOf(activePresentation(BattleAnimationPhase.PLAYER_ATTACKING, harpy))
        composeRule.setContent {
            TodoQuestTheme {
                BattleMap(
                    state = harpy,
                    presentation = phase.value,
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            "공격자 모험가, 대상 하피, 공격합니다.",
        ).assertIsDisplayed()

        composeRule.runOnIdle {
            phase.value = activePresentation(BattleAnimationPhase.MONSTER_ATTACKING, harpy)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(
            "공격자 하피, 대상 모험가, 공격합니다.",
        ).assertIsDisplayed()

        composeRule.runOnIdle {
            phase.value = activePresentation(BattleAnimationPhase.MONSTER_HIT, harpy)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("하피, 15 피해를 받았습니다.")
            .assertIsDisplayed()

        composeRule.runOnIdle {
            phase.value = activePresentation(BattleAnimationPhase.MONSTER_DYING, harpy)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("하피가 쓰러졌습니다.")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
        composeRule.onNodeWithContentDescription("고블린 정찰병이 쓰러졌습니다.")
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription("해골 병사가 쓰러졌습니다.")
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription("타락한 나무 정령이 쓰러졌습니다.")
            .assertDoesNotExist()
    }

    @Test
    fun slimeAttackHitAndDeathAnnouncementsUseSlimeNameAndDeathResource() {
        val slime = contentState(
            monsterCount = 1,
            monsterSpecies = MonsterSpecies.SLIME,
        )
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val playerName = targetContext.getString(R.string.battle_player_name)
        val slimeName = targetContext.getString(R.string.battle_monster_slime_name)
        val phase = mutableStateOf(activePresentation(BattleAnimationPhase.PLAYER_ATTACKING, slime))
        composeRule.setContent {
            TodoQuestTheme {
                BattleMap(
                    state = slime,
                    presentation = phase.value,
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            targetContext.getString(
                R.string.battle_attack_announcement,
                playerName,
                slimeName,
            ),
        ).assertIsDisplayed()

        composeRule.runOnIdle {
            phase.value = activePresentation(BattleAnimationPhase.MONSTER_ATTACKING, slime)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(
            targetContext.getString(
                R.string.battle_attack_announcement,
                slimeName,
                playerName,
            ),
        ).assertIsDisplayed()

        composeRule.runOnIdle {
            phase.value = activePresentation(BattleAnimationPhase.MONSTER_HIT, slime)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(
            targetContext.getString(R.string.battle_hit_announcement, slimeName, 15),
        )
            .assertIsDisplayed()

        composeRule.runOnIdle {
            phase.value = activePresentation(BattleAnimationPhase.MONSTER_DYING, slime)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(
            targetContext.getString(R.string.battle_monster_slime_death_announcement),
        )
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
        listOf(
            R.string.battle_monster_death_announcement,
            R.string.battle_monster_skeleton_soldier_death_announcement,
            R.string.battle_monster_corrupted_tree_spirit_death_announcement,
            R.string.battle_monster_harpy_death_announcement,
        ).forEach { otherSpeciesAnnouncementResId ->
            composeRule.onNodeWithContentDescription(
                targetContext.getString(otherSpeciesAnnouncementResId),
            )
                .assertDoesNotExist()
        }
    }

    @Test
    fun spawnSceneReplacesOutgoingGoblinWithSkeletonDrawableAndName() {
        val goblin = contentState(
            monsterCount = 1,
            monsterSpecies = MonsterSpecies.GOBLIN_SCOUT,
        )
        val skeleton = contentState(
            monsterCount = 1,
            monsterSpecies = MonsterSpecies.SKELETON_SOLDIER,
        )
        val phase = mutableStateOf(activePresentation(BattleAnimationPhase.MONSTER_HIT, goblin))
        composeRule.setContent {
            TodoQuestTheme {
                BattleMap(
                    state = goblin,
                    presentation = phase.value,
                )
            }
        }

        val outgoingBounds = boundsOf("battle-monster-layer")
        val outgoingBitmap = composeRule.onNodeWithTag("battle-monster-layer")
            .captureToImage()
            .asAndroidBitmap()
        composeRule.onNodeWithContentDescription("고블린 정찰병").assertIsDisplayed()

        composeRule.runOnIdle {
            phase.value = activePresentation(BattleAnimationPhase.MONSTER_SPAWNING, skeleton)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("해골 병사").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("고블린 정찰병").assertDoesNotExist()
        val incomingBounds = boundsOf("battle-monster-layer")
        val incomingBitmap = composeRule.onNodeWithTag("battle-monster-layer")
            .captureToImage()
            .asAndroidBitmap()
        assertEquals(outgoingBounds.width, incomingBounds.width, 0.5f)
        assertEquals(outgoingBounds.height, incomingBounds.height, 0.5f)
        assertEquals(outgoingBounds.center.x, incomingBounds.center.x, 0.5f)
        assertTrue(
            "spawned skeleton bitmap must replace the outgoing goblin bitmap",
            bitmapsDiffer(outgoingBitmap, incomingBitmap),
        )
    }

    @Test
    fun spawnSceneReplacesOutgoingSkeletonWithCorruptedTreeSpiritDrawableAndName() {
        val skeleton = contentState(
            monsterCount = 1,
            monsterSpecies = MonsterSpecies.SKELETON_SOLDIER,
        )
        val treeSpirit = contentState(
            monsterCount = 1,
            monsterSpecies = MonsterSpecies.CORRUPTED_TREE_SPIRIT,
        )
        val phase = mutableStateOf(activePresentation(BattleAnimationPhase.MONSTER_HIT, skeleton))
        composeRule.setContent {
            TodoQuestTheme {
                BattleMap(
                    state = skeleton,
                    presentation = phase.value,
                )
            }
        }

        val outgoingBounds = boundsOf("battle-monster-layer")
        val outgoingBitmap = composeRule.onNodeWithTag("battle-monster-layer")
            .captureToImage()
            .asAndroidBitmap()
        composeRule.onNodeWithContentDescription("해골 병사").assertIsDisplayed()

        composeRule.runOnIdle {
            phase.value = activePresentation(BattleAnimationPhase.MONSTER_SPAWNING, treeSpirit)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("타락한 나무 정령").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("해골 병사").assertDoesNotExist()
        val incomingBounds = boundsOf("battle-monster-layer")
        val incomingBitmap = composeRule.onNodeWithTag("battle-monster-layer")
            .captureToImage()
            .asAndroidBitmap()
        assertEquals(outgoingBounds.width, incomingBounds.width, 0.5f)
        assertEquals(outgoingBounds.height, incomingBounds.height, 0.5f)
        assertEquals(outgoingBounds.center.x, incomingBounds.center.x, 0.5f)
        assertTrue(
            "spawned tree spirit bitmap must replace the outgoing skeleton bitmap",
            bitmapsDiffer(outgoingBitmap, incomingBitmap),
        )
    }

    @Test
    fun spawnSceneReplacesOutgoingTreeSpiritWithHarpyDrawableAndName() {
        val treeSpirit = contentState(
            monsterCount = 1,
            monsterSpecies = MonsterSpecies.CORRUPTED_TREE_SPIRIT,
        )
        val harpy = contentState(
            monsterCount = 1,
            monsterSpecies = MonsterSpecies.HARPY,
        )
        val phase = mutableStateOf(activePresentation(BattleAnimationPhase.MONSTER_HIT, treeSpirit))
        composeRule.setContent {
            TodoQuestTheme {
                BattleMap(
                    state = treeSpirit,
                    presentation = phase.value,
                )
            }
        }

        val outgoingBounds = boundsOf("battle-monster-layer")
        val outgoingBitmap = composeRule.onNodeWithTag("battle-monster-layer")
            .captureToImage()
            .asAndroidBitmap()
        composeRule.onNodeWithContentDescription("타락한 나무 정령").assertIsDisplayed()

        composeRule.runOnIdle {
            phase.value = activePresentation(BattleAnimationPhase.MONSTER_SPAWNING, harpy)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("하피").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("타락한 나무 정령").assertDoesNotExist()
        val incomingBounds = boundsOf("battle-monster-layer")
        val incomingBitmap = composeRule.onNodeWithTag("battle-monster-layer")
            .captureToImage()
            .asAndroidBitmap()
        assertEquals(
            R.drawable.todo_quest_harpy_front_idle,
            (harpy.monsters.single().sprite as BattleSpriteUiModel.Resource).spriteResId,
        )
        assertEquals(outgoingBounds.width, incomingBounds.width, 0.5f)
        assertEquals(outgoingBounds.height, incomingBounds.height, 0.5f)
        assertEquals(outgoingBounds.center.x, incomingBounds.center.x, 0.5f)
        assertTrue(
            "spawned harpy bitmap must replace the outgoing tree spirit bitmap",
            bitmapsDiffer(outgoingBitmap, incomingBitmap),
        )
    }

    @Test
    fun spawnSceneReplacesHarpyWithSlimeAndThenSlimeWithGoblinWithoutStaleBitmapOrName() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val harpyName = targetContext.getString(R.string.battle_monster_harpy_name)
        val slimeName = targetContext.getString(R.string.battle_monster_slime_name)
        val goblinName = targetContext.getString(R.string.battle_monster_goblin_scout_name)
        val harpy = contentState(
            monsterCount = 1,
            monsterSpecies = MonsterSpecies.HARPY,
        )
        val slime = contentState(
            monsterCount = 1,
            monsterSpecies = MonsterSpecies.SLIME,
        )
        val goblin = contentState(
            monsterCount = 1,
            monsterSpecies = MonsterSpecies.GOBLIN_SCOUT,
        )
        val phase = mutableStateOf(activePresentation(BattleAnimationPhase.MONSTER_HIT, harpy))
        composeRule.setContent {
            TodoQuestTheme {
                BattleMap(
                    state = harpy,
                    presentation = phase.value,
                )
            }
        }

        val harpyBitmap = composeRule.onNodeWithTag("battle-monster-layer")
            .captureToImage()
            .asAndroidBitmap()
        composeRule.onNodeWithContentDescription(harpyName).assertIsDisplayed()

        composeRule.runOnIdle {
            phase.value = activePresentation(BattleAnimationPhase.MONSTER_SPAWNING, slime)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(slimeName).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(harpyName).assertDoesNotExist()
        assertEquals(
            R.drawable.todo_quest_slime_front_idle,
            (slime.monsters.single().sprite as BattleSpriteUiModel.Resource).spriteResId,
        )
        val slimeBitmap = composeRule.onNodeWithTag("battle-monster-layer")
            .captureToImage()
            .asAndroidBitmap()
        assertTrue(
            "spawned slime bitmap must replace the outgoing harpy bitmap",
            bitmapsDiffer(harpyBitmap, slimeBitmap),
        )

        composeRule.runOnIdle {
            phase.value = activePresentation(BattleAnimationPhase.MONSTER_SPAWNING, goblin)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(goblinName).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(slimeName).assertDoesNotExist()
        val goblinBitmap = composeRule.onNodeWithTag("battle-monster-layer")
            .captureToImage()
            .asAndroidBitmap()
        assertTrue(
            "spawned goblin bitmap must replace the outgoing slime bitmap",
            bitmapsDiffer(slimeBitmap, goblinBitmap),
        )
    }

    @Test
    fun stageOneNormalGoldenEncountersRenderScheduledSpeciesIncludingSlimeAtFiveAndEight() {
        val config = MonsterBalanceConfig()
        val state = mutableStateOf(contentStateForEncounter(1, config))
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            TodoQuestTheme {
                BattleMap(state = state.value)
            }
        }

        listOf(
            1 to MonsterSpecies.SKELETON_SOLDIER,
            2 to MonsterSpecies.HARPY,
            3 to MonsterSpecies.GOBLIN_SCOUT,
            5 to MonsterSpecies.SLIME,
            7 to MonsterSpecies.CORRUPTED_TREE_SPIRIT,
            8 to MonsterSpecies.SLIME,
        ).forEach { (encounter, expectedSpecies) ->
            val grade = MonsterStagePolicy.gradeFor(1, config)
            val encounterCount = MonsterStagePolicy.encounterCount(1, config)
            val species = MonsterSpeciesPolicy.speciesFor(
                stageNumber = 1,
                encounterNumber = encounter,
                grade = grade,
                encounterCount = encounterCount,
                balanceVersion = config.version,
            )
            assertEquals(MonsterGrade.NORMAL, grade)
            assertEquals(expectedSpecies, species)

            composeRule.runOnIdle {
                state.value = contentStateForEncounter(encounter, config)
            }
            composeRule.waitForIdle()
            val visual = BattleMonsterVisualCatalog.forSpecies(expectedSpecies)
            assertEquals(
                visual.spriteResId,
                (state.value.monsters.single().sprite as BattleSpriteUiModel.Resource).spriteResId,
            )
            composeRule.onNodeWithContentDescription(
                targetContext.getString(
                    R.string.battle_unit_health_description,
                    targetContext.getString(visual.nameResId),
                    40,
                    50,
                ),
            ).assertIsDisplayed()
        }
    }

    @Test
    fun invalidBackgroundResourceUsesLayeredCanvasFallback() {
        composeRule.setContent {
            TodoQuestTheme {
                BattleMap(
                    state = contentState(monsterCount = 1),
                    theme = BattleMapTheme(backgroundResId = Int.MAX_VALUE),
                )
            }
        }

        composeRule.onNodeWithTag("battle-background-fallback").assertIsDisplayed()
        composeRule.onNodeWithTag("battle-background-image").assertDoesNotExist()
    }

    @Test
    fun decorationsCanBeDisabledByTheme() {
        composeRule.setContent {
            TodoQuestTheme {
                BattleMap(
                    state = contentState(monsterCount = 0),
                    theme = BattleMapTheme(showDecorations = false),
                )
            }
        }

        composeRule.onNodeWithTag("battle-decorations-layer").assertDoesNotExist()
        composeRule.onNodeWithTag("battle-background-image").assertIsDisplayed()
    }

    @Test
    fun contentSupportsZeroOneThreeAndFourMonsterLayers() {
        val state = mutableStateOf<BattleMapUiState>(contentState(monsterCount = 0))
        composeRule.setContent {
            TodoQuestTheme {
                BattleMap(state = state.value)
            }
        }

        listOf(0, 1, 3, 4).forEach { monsterCount ->
            composeRule.runOnIdle {
                state.value = contentState(monsterCount = monsterCount)
            }
            composeRule.waitForIdle()
            composeRule.onAllNodesWithTag("battle-monster-layer")
                .assertCountEquals(monsterCount)
            composeRule.onAllNodesWithTag("battle-player-layer")
                .assertCountEquals(1)
        }
    }

    @Test
    fun smallWidthUsesMinimumHeightAndClampsEveryUnitInsideMapBounds() {
        val state = contentState(
            monsterCount = 4,
            playerPosition = BattlePosition(0f, 0f),
            monsterPositions = listOf(
                BattlePosition(1f, 0f),
                BattlePosition(0f, 1f),
                BattlePosition(1f, 1f),
                BattlePosition(0.5f, 1f),
            ),
        )
        composeRule.setContent {
            TodoQuestTheme {
                Box(modifier = Modifier.width(320.dp)) {
                    BattleMap(state = state)
                }
            }
        }

        composeRule.onNodeWithTag("battle-map")
            .assertWidthIsEqualTo(320.dp)
            .assertHeightIsEqualTo(190.dp)
        val mapBounds = composeRule.onNodeWithTag("battle-map")
            .fetchSemanticsNode().boundsInRoot
        val unitBounds = composeRule.onAllNodesWithTag("battle-player-layer")
            .fetchSemanticsNodes().map { it.boundsInRoot } +
            composeRule.onAllNodesWithTag("battle-monster-layer")
                .fetchSemanticsNodes().map { it.boundsInRoot }

        unitBounds.forEach { bounds ->
            assertContainedBy(bounds = bounds, container = mapBounds)
        }
    }

    @Test
    fun loadingAndUnavailableExposeKoreanStatusWithoutHidingBackground() {
        val state = mutableStateOf<BattleMapUiState>(BattleMapUiState.Loading)
        composeRule.setContent {
            TodoQuestTheme {
                BattleMap(state = state.value)
            }
        }

        composeRule.onNodeWithTag("battle-background-image").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("전투 지도를 불러오는 중")
            .assertIsDisplayed()

        composeRule.runOnIdle {
            state.value = BattleMapUiState.Unavailable
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("battle-background-fallback").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("전투 지도를 표시할 수 없습니다.")
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag("battle-player-layer").assertCountEquals(0)
        composeRule.onAllNodesWithTag("battle-monster-layer").assertCountEquals(0)
    }

    @Test
    fun playerProgressHudRendersInOverlayWithIntegratedSemantics() {
        composeRule.setContent {
            TodoQuestTheme {
                BattleMap(
                    state = contentState(monsterCount = 1),
                    overlayContent = {
                        PlayerProgressHud(
                            isLoading = false,
                            level = 1,
                            currentExp = 40,
                            requiredExp = 100,
                            gold = 120,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(8.dp)
                                .fillMaxWidth(),
                        )
                    },
                )
            }
        }

        listOf(
            "battle-background-image",
            "battle-player-layer",
            "battle-monster-layer",
            "battle-overlay-layer",
            "player-progress-hud",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
        }
        composeRule.onNodeWithContentDescription("레벨 1, 경험치 40/100, 골드 120")
            .assertIsDisplayed()

        val overlayBounds = composeRule.onNodeWithTag("battle-overlay-layer")
            .fetchSemanticsNode().boundsInRoot
        val hudBounds = composeRule.onNodeWithTag(
            testTag = "player-progress-hud",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        assertContainedBy(bounds = hudBounds, container = overlayBounds)
    }

    @Test
    fun playerProgressHudLoadingDoesNotRenderPlaceholderValues() {
        composeRule.setContent {
            TodoQuestTheme {
                PlayerProgressHud(
                    isLoading = true,
                    level = 99,
                    currentExp = 99,
                    requiredExp = 100,
                    gold = 999,
                    modifier = Modifier.width(304.dp),
                )
            }
        }

        composeRule.onNodeWithContentDescription("플레이어 정보를 불러오는 중")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Lv. 99", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("99/100", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("999", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun zeroExperienceStillRendersAVisibleBoundedTrack() {
        composeRule.setContent {
            TodoQuestTheme {
                PlayerProgressHud(
                    isLoading = false,
                    level = 1,
                    currentExp = 0,
                    requiredExp = 100,
                    gold = 0,
                    modifier = Modifier.width(304.dp),
                )
            }
        }

        composeRule.onNodeWithTag("player-progress-bar", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertHeightIsEqualTo(4.dp)
            .assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))
        composeRule.onNodeWithTag("player-progress-level", useUnmergedTree = true)
            .assertTextEquals("Lv. 1")
        composeRule.onNodeWithTag("player-progress-gold", useUnmergedTree = true)
            .assertTextEquals("0")
        composeRule.onNodeWithTag("player-progress-exp-value", useUnmergedTree = true)
            .assertTextEquals("0/100")
        composeRule.onNodeWithContentDescription("레벨 1, 경험치 0/100, 골드 0")
            .assertIsDisplayed()

        val minimumExpWidthPx = 104.dp.toPx(composeRule.density)
        val expBarWidth = boundsOf("player-progress-bar").width
        assertTrue(
            "EXP bar width=$expBarWidth minimum=$minimumExpWidthPx",
            expBarWidth >= minimumExpWidthPx,
        )
        assertExpLabelsAlignWithBar()
    }

    @Test
    fun combatRewardBadgeShowsHitRewardForSixHundredMillis() {
        composeRule.mainClock.autoAdvance = false
        val state = contentState(monsterCount = 1)
        composeRule.setContent {
            TodoQuestTheme {
                BattleMap(
                    state = state,
                    presentation = activePresentation(
                        battlePhase = BattleAnimationPhase.MONSTER_HIT,
                        scene = state,
                        rewardFeedback = BattleRewardFeedback(
                            xpAward = 1L,
                            goldAward = 0L,
                            isVictory = false,
                        ),
                    ),
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag("battle-reward-effect").assertIsDisplayed()
        composeRule.onNodeWithText("+1 EXP").assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(601L)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("battle-reward-effect").assertDoesNotExist()
    }

    @Test
    fun playerProgressHudSafelyDescribesBoundaryValues() {
        val cases = listOf(
            HudValues(
                currentExp = 0,
                requiredExp = 100,
                gold = 0,
                description = "레벨 1, 경험치 0/100, 골드 0",
            ),
            HudValues(
                currentExp = 120,
                requiredExp = 100,
                gold = Long.MAX_VALUE,
                description = "레벨 1, 경험치 120/100, 골드 9,223,372,036,854,775,807",
            ),
            HudValues(
                currentExp = 40,
                requiredExp = 0,
                gold = 15,
                description = "레벨 1, 경험치 40/0, 골드 15",
            ),
        )
        val caseIndex = mutableStateOf(0)
        composeRule.setContent {
            TodoQuestTheme {
                val values = cases[caseIndex.value]
                PlayerProgressHud(
                    isLoading = false,
                    level = 1,
                    currentExp = values.currentExp,
                    requiredExp = values.requiredExp,
                    gold = values.gold,
                    modifier = Modifier.width(304.dp),
                )
            }
        }

        cases.forEachIndexed { index, values ->
            composeRule.runOnIdle { caseIndex.value = index }
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(values.description).assertIsDisplayed()
        }
    }

    @Test
    fun playerProgressHudStaysInsideSmallMapWithoutTextOverlapAtLargeFontScale() {
        composeRule.setContent {
            TodoQuestTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = density.density, fontScale = 2f),
                ) {
                    Box(modifier = Modifier.width(320.dp)) {
                        BattleMap(
                            state = contentState(monsterCount = 3),
                            overlayContent = {
                                PlayerProgressHud(
                                    isLoading = false,
                                    level = 50,
                                    currentExp = 9_876_543_210,
                                    requiredExp = 12_345_678_901,
                                    gold = 9_876_543_210,
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(8.dp)
                                        .fillMaxWidth(),
                                )
                            },
                        )
                    }
                }
            }
        }

        val mapBounds = composeRule.onNodeWithTag("battle-map")
            .fetchSemanticsNode().boundsInRoot
        val hudBounds = composeRule.onNodeWithTag(
            testTag = "player-progress-hud",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val levelBounds = boundsOf("player-progress-level")
        val summaryBounds = boundsOf("player-progress-summary")
        val goldBounds = boundsOf("player-progress-gold-group")
        val expBounds = boundsOf("player-progress-exp-group")
        val expContentBounds = boundsOf("player-progress-exp-content")
        val expLabelBounds = boundsOf("player-progress-exp-label")
        val expValueBounds = boundsOf("player-progress-exp-value")
        val expBarBounds = boundsOf("player-progress-bar")
        val goldIconBounds = boundsOf("player-progress-gold-icon")
        val goldAmountBounds = boundsOf("player-progress-gold")

        assertContainedBy(bounds = hudBounds, container = mapBounds)
        val density = composeRule.density
        assertTrue(levelBounds.right <= summaryBounds.left)
        assertTrue(
            kotlin.math.abs(summaryBounds.right - (hudBounds.right - 12.dp.toPx(density))) < 0.6f,
        )
        assertTrue(kotlin.math.abs(expBounds.left - goldBounds.right - 12.dp.toPx(density)) < 0.6f)
        assertTrue(kotlin.math.abs(goldAmountBounds.left - goldIconBounds.right - 3.dp.toPx(density)) < 0.6f)
        assertTrue(expLabelBounds.right <= expValueBounds.left)
        assertTrue(kotlin.math.abs(expContentBounds.left - expBarBounds.left) < 0.6f)
        assertTrue(kotlin.math.abs(expContentBounds.right - expBarBounds.right) < 0.6f)
        assertTrue(kotlin.math.abs(expBounds.left - expContentBounds.left) < 0.6f)
        assertTrue(kotlin.math.abs(expBounds.right - expContentBounds.right) < 0.6f)
        assertExpLabelsAlignWithBar()
        composeRule.onNodeWithText("98.7억", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("98.7억/123.4억", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "레벨 50, 경험치 9,876,543,210/12,345,678,901, 골드 9,876,543,210",
        ).assertIsDisplayed()
    }

    @Test
    fun healthBarsClampLowHealthAndAnimateToTargetIn220Millis() {
        val state = mutableStateOf(contentState(monsterCount = 1))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            TodoQuestTheme {
                BattleMap(state = state.value)
            }
        }

        composeRule.onNodeWithTag("battle-player-health")
            .assertExists()
        composeRule.onNodeWithText("75/100", useUnmergedTree = true).assertIsDisplayed()
        val density = composeRule.density
        val minimumReadableHeightPx = 17.dp.toPx(density) - 1f
        val playerHealthHeight = boundsOf("battle-player-health").height
        val monsterHealthHeight = boundsOf("battle-monster-health").height
        assertTrue(
            "player health height=$playerHealthHeight minimum=$minimumReadableHeightPx",
            playerHealthHeight >= minimumReadableHeightPx,
        )
        assertTrue(
            "monster health height=$monsterHealthHeight minimum=$minimumReadableHeightPx",
            monsterHealthHeight >= minimumReadableHeightPx,
        )

        composeRule.runOnIdle {
            state.value = state.value.copy(
                player = state.value.player.copy(currentHp = 25),
            )
        }
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithText("25/100", useUnmergedTree = true).assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(140)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("25/100", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "모험가, 체력 25/100, 체력이 낮습니다.",
        ).assertIsDisplayed()
    }

    @Test
    fun standardAndCompactMapsKeepHudHealthAndActorsInsideAtLargeFontScale() {
        val policy = mutableStateOf(BattleMapHeightPolicy.STANDARD)
        composeRule.setContent {
            TodoQuestTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = density.density, fontScale = 2f),
                ) {
                    Box(modifier = Modifier.width(320.dp)) {
                        BattleMap(
                            state = contentState(monsterCount = 1),
                            heightPolicy = policy.value,
                            overlayContent = {
                                PlayerProgressHud(
                                    isLoading = false,
                                    level = 50,
                                    currentExp = 9_876_543_210,
                                    requiredExp = 12_345_678_901,
                                    gold = 9_876_543_210,
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(8.dp)
                                        .fillMaxWidth(),
                                )
                            },
                        )
                    }
                }
            }
        }

        listOf(
            BattleMapHeightPolicy.STANDARD to 190.dp,
            BattleMapHeightPolicy.COMPACT to 150.dp,
        ).forEach { (heightPolicy, expectedHeight) ->
            composeRule.runOnIdle { policy.value = heightPolicy }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("battle-map").assertHeightIsEqualTo(expectedHeight)
            val mapBounds = boundsOf("battle-map")
            val hudBounds = boundsOf("player-progress-hud")
            val playerHealthBounds = boundsOf("battle-player-health")
            val monsterHealthBounds = boundsOf("battle-monster-health")
            val playerBounds = boundsOf("battle-player-layer")
            val monsterBounds = boundsOf("battle-monster-layer")

            listOf(hudBounds, playerHealthBounds, monsterHealthBounds, playerBounds, monsterBounds)
                .forEach { assertContainedBy(it, mapBounds) }
            listOf(playerHealthBounds, monsterHealthBounds).forEach { healthBounds ->
                assertTrue(healthBounds.top >= hudBounds.bottom - 0.5f)
            }
            assertExpLabelsAlignWithBar()
            assertTrue(
                "$heightPolicy player health=$playerHealthBounds actor=$playerBounds hud=$hudBounds",
                playerHealthBounds.bottom <= playerBounds.top + 0.5f,
            )
            assertTrue(
                "$heightPolicy monster health=$monsterHealthBounds actor=$monsterBounds hud=$hudBounds",
                monsterHealthBounds.bottom <= monsterBounds.top + 0.5f,
            )
        }
    }

    @Test
    fun severeInjuryBadgeUsesDedicatedGeometryAndAccessibleDetailsAtLargeFontScale() {
        val injury = ActiveStatusEffectUiModel(
            type = StatusEffectType.SEVERE_INJURY,
            revision = 2L,
            remainingRecoveryCompletions = 2,
            remainingTime = StatusEffectRemainingTimeUiState.Hours(4),
        )
        val battleState = contentState(monsterCount = 1)
        val showDetails = mutableStateOf(false)
        composeRule.setContent {
            TodoQuestTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = density.density, fontScale = 2f),
                ) {
                    Box(modifier = Modifier.width(320.dp)) {
                        BattleMap(
                            state = battleState,
                            heightPolicy = BattleMapHeightPolicy.COMPACT,
                            presentation = activePresentation(
                                BattleAnimationPhase.PLAYER_HIT,
                                battleState,
                            ),
                            activeStatusEffects = listOf(injury),
                            onStatusEffectClick = { showDetails.value = true },
                            overlayContent = {
                                PlayerProgressHud(
                                    isLoading = false,
                                    level = 7,
                                    currentExp = 40,
                                    requiredExp = 100,
                                    gold = 1_280,
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(8.dp)
                                        .fillMaxWidth(),
                                )
                            },
                        )
                        if (showDetails.value) {
                            SevereInjuryDetailsDialog(
                                effect = injury,
                                onDismiss = { showDetails.value = false },
                            )
                        }
                    }
                }
            }
        }

        val mapBounds = boundsOf("battle-map")
        val hudBounds = boundsOf("player-progress-hud")
        val healthBounds = boundsOf("battle-player-health")
        val badgeBounds = boundsOf("battle-severe-injury-badge-visual")
        val playerBounds = boundsOf("battle-player-layer")
        val monsterBounds = boundsOf("battle-monster-layer")

        assertContainedBy(badgeBounds, mapBounds)
        assertTrue(badgeBounds.top >= hudBounds.bottom - 0.5f)
        assertTrue(healthBounds.bottom <= badgeBounds.top + 0.5f)
        assertContainedBy(playerBounds, mapBounds)
        assertTrue(!badgeBounds.overlaps(monsterBounds))
        composeRule.onNodeWithTag("battle-damage-effect").assertIsDisplayed()
        composeRule.onNodeWithText("중상", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "플레이어 상태 중상, 최대 체력과 공격력 20퍼센트 감소, 회복까지 할 일 2개",
        ).assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithTag("status-effect-details-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("회복 조건은 회복까지 할 일 2개 또는 4시간")
            .assertIsDisplayed()
    }

    @Test
    fun severeInjuryDoesNotMovePlayerSprite() {
        val activeStatusEffects = mutableStateOf(emptyList<ActiveStatusEffectUiModel>())
        val injury = ActiveStatusEffectUiModel(
            type = StatusEffectType.SEVERE_INJURY,
            revision = 1L,
            remainingRecoveryCompletions = 3,
            remainingTime = StatusEffectRemainingTimeUiState.Hours(24),
        )
        composeRule.setContent {
            TodoQuestTheme {
                Box(modifier = Modifier.width(320.dp)) {
                    BattleMap(
                        state = contentState(monsterCount = 1),
                        activeStatusEffects = activeStatusEffects.value,
                        overlayContent = {
                            PlayerProgressHud(
                                isLoading = false,
                                level = 7,
                                currentExp = 40,
                                requiredExp = 100,
                                gold = 1_280,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(8.dp)
                                    .fillMaxWidth(),
                            )
                        },
                    )
                }
            }
        }

        val healthyPlayerBounds = boundsOf("battle-player-layer")
        composeRule.runOnIdle { activeStatusEffects.value = listOf(injury) }
        composeRule.waitForIdle()
        val injuredPlayerBounds = boundsOf("battle-player-layer")

        assertEquals(healthyPlayerBounds.left, injuredPlayerBounds.left, 0f)
        assertEquals(healthyPlayerBounds.top, injuredPlayerBounds.top, 0f)
    }

    @Test
    fun presentationPhasesRenderAttackHitDeathSpawnAndKoAnnouncementsInsideMap() {
        val base = contentState(monsterCount = 1)
        val phase = mutableStateOf(activePresentation(BattleAnimationPhase.PLAYER_ATTACKING, base))
        composeRule.setContent {
            TodoQuestTheme {
                BattleMap(
                    state = base,
                    presentation = phase.value,
                )
            }
        }

        composeRule.onNodeWithTag("battle-attack-effect").assertIsDisplayed()
        composeRule.onNodeWithText("공격!", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "공격자 모험가, 대상 고블린 정찰병, 공격합니다.",
        ).assertIsDisplayed()
        assertContainedBy(boundsOf("battle-attack-effect"), boundsOf("battle-map"))

        composeRule.runOnIdle {
            phase.value = activePresentation(BattleAnimationPhase.MONSTER_ATTACKING, base)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("공격!", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "공격자 고블린 정찰병, 대상 모험가, 공격합니다.",
        ).assertIsDisplayed()

        composeRule.runOnIdle {
            phase.value = activePresentation(
                BattleAnimationPhase.PLAYER_HIT,
                base.copy(player = base.player.copy(currentHp = 60)),
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("-15", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("모험가, 15 피해를 받았습니다.")
            .assertIsDisplayed()
        assertContainedBy(boundsOf("battle-damage-effect"), boundsOf("battle-map"))

        composeRule.runOnIdle {
            phase.value = activePresentation(
                battlePhase = BattleAnimationPhase.MONSTER_HIT,
                scene = base.copy(
                    monsters = listOf(base.monsters.single().copy(currentHp = 25)),
                ),
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("battle-damage-effect").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "고블린 정찰병, 15 피해를 받았습니다.",
        ).assertIsDisplayed()

        composeRule.runOnIdle {
            phase.value = activePresentation(BattleAnimationPhase.MONSTER_DYING, base)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("battle-death-effect").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("고블린 정찰병이 쓰러졌습니다.")
            .assertIsDisplayed()

        composeRule.runOnIdle {
            phase.value = activePresentation(
                BattleAnimationPhase.MONSTER_SPAWN_ALERT,
                base.copy(monsters = emptyList()),
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("battle-spawn-alert-effect").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("새로운 몬스터 등장!").assertIsDisplayed()

        composeRule.runOnIdle {
            phase.value = activePresentation(BattleAnimationPhase.MONSTER_SPAWNING, base)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("battle-spawn-effect").assertIsDisplayed()

        composeRule.runOnIdle {
            phase.value = activePresentation(BattleAnimationPhase.PLAYER_DYING, base)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("모험가가 쓰러졌습니다.").assertIsDisplayed()

        composeRule.runOnIdle {
            phase.value = activePresentation(BattleAnimationPhase.PLAYER_DEFEATED, base)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("battle-player-defeated-effect").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("모험가가 전투 불능이 되었습니다.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("전투 불능", useUnmergedTree = true).assertIsDisplayed()

        composeRule.runOnIdle {
            phase.value = activePresentation(BattleAnimationPhase.STATUS_EFFECT_APPLYING, base)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("battle-status-effect").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("중상이 적용되었습니다.").assertIsDisplayed()
        composeRule.onNodeWithText("중상", useUnmergedTree = true).assertIsDisplayed()

        composeRule.runOnIdle {
            phase.value = activePresentation(
                BattleAnimationPhase.PLAYER_EMERGENCY_RECOVERING,
                base,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("battle-emergency-recovery-effect").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("응급 회복으로 체력을 회복했습니다.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("응급 회복", useUnmergedTree = true).assertIsDisplayed()

        val mapBounds = boundsOf("battle-map")
        assertContainedBy(boundsOf("battle-emergency-recovery-effect"), mapBounds)

        composeRule.runOnIdle {
            phase.value = BattlePresentationState(
                phase = BattleAnimationPhase.STATUS_EFFECT_REMOVING,
                sequenceId = 2L,
                eventId = "status-effect:removed:SEVERE_INJURY:1",
                sceneOverride = base,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("battle-status-effect-removed").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("중상에서 회복했습니다.").assertIsDisplayed()
        composeRule.onNodeWithText("중상 회복", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun contentState(
        monsterCount: Int,
        playerPosition: BattlePosition = BattleMapDefaults.PLAYER_POSITION,
        monsterPositions: List<BattlePosition> = BattleMonsterSlots.forCount(monsterCount),
        monsterSpecies: MonsterSpecies = MonsterSpecies.GOBLIN_SCOUT,
    ): BattleMapUiState.Content {
        require(monsterPositions.size == monsterCount)
        val monsterVisual = BattleMonsterVisualCatalog.forSpecies(monsterSpecies)
        return BattleMapUiState.Content(
            player = unit(
                id = "player",
                type = BattleUnitType.PLAYER,
                position = playerPosition,
                currentHp = 75,
                maxHp = 100,
                nameResId = R.string.battle_player_name,
            ),
            monsters = monsterPositions.mapIndexed { index, position ->
                unit(
                    id = "monster-$index",
                    type = BattleUnitType.MONSTER,
                    position = position,
                    currentHp = 40,
                    maxHp = 50,
                    nameResId = monsterVisual.nameResId,
                    monsterVisual = monsterVisual,
                )
            },
            stageNumber = 7,
        )
    }

    private fun unit(
        id: String,
        type: BattleUnitType,
        position: BattlePosition,
        currentHp: Int,
        maxHp: Int,
        nameResId: Int,
        monsterVisual: BattleMonsterVisual? = null,
    ) = BattleUnitUiModel(
        id = id,
        type = type,
        sprite = if (type == BattleUnitType.PLAYER) {
            BattleSpriteUiModel.LayeredCharacter(
                renderState = CharacterRenderState(
                    appearance = CharacterLoadoutCatalog.defaultAppearance,
                    equippedItems = CharacterLoadoutCatalog.defaultEquippedItems,
                ),
                frame = BattleMapDefaults.PLAYER_FRAME,
            )
        } else {
            BattleSpriteUiModel.Resource(
                spriteResId = requireNotNull(monsterVisual).spriteResId,
                frame = BattleMapDefaults.MONSTER_FRAME,
            )
        },
        position = position,
        scale = 1f,
        groundOffset = 0f,
        currentHp = currentHp,
        maxHp = maxHp,
        nameResId = nameResId,
        deathAnnouncementResId = if (type == BattleUnitType.PLAYER) {
            R.string.battle_player_death_announcement
        } else {
            requireNotNull(monsterVisual).deathAnnouncementResId
        },
    )

    private fun contentStateForEncounter(
        encounter: Int,
        config: MonsterBalanceConfig,
    ): BattleMapUiState.Content {
        val stageNumber = 1
        val grade = MonsterStagePolicy.gradeFor(stageNumber, config)
        val encounterCount = MonsterStagePolicy.encounterCount(stageNumber, config)
        val type = MonsterStagePolicy.typeFor(stageNumber, encounter, config)
        val species = MonsterSpeciesPolicy.speciesFor(
            stageNumber = stageNumber,
            encounterNumber = encounter,
            grade = grade,
            encounterCount = encounterCount,
            balanceVersion = config.version,
        )
        val definition = MonsterCatalog.definitionFor(type, config)
        return BattlePresentationMapper.mapSnapshot(
            snapshot = CombatSnapshot(
                progress = StageProgress(
                    stageNumber = stageNumber,
                    stageLevel = 1,
                    activeMonsterInstanceId = encounter.toLong(),
                    lastReconciledAt = Instant.EPOCH,
                    balanceVersion = config.version,
                ),
                activeMonster = MonsterInstance(
                    id = encounter.toLong(),
                    definitionId = definition.id,
                    grade = grade,
                    stageNumber = stageNumber,
                    encounterNumber = encounter,
                    level = 1,
                    currentHp = 40,
                    balanceVersion = config.version,
                ),
                activeMonsterStats = MonsterStats(
                    maxHp = 50,
                    damage = 10,
                    defense = 5,
                ),
                activeMonsterSpecies = species,
                playerCurrentHp = 75,
                playerMaxHp = 100,
            ),
            characterRenderState = CharacterRenderState(
                appearance = CharacterLoadoutCatalog.defaultAppearance,
                equippedItems = CharacterLoadoutCatalog.defaultEquippedItems,
            ),
        )
    }

    private fun bitmapsDiffer(
        first: android.graphics.Bitmap,
        second: android.graphics.Bitmap,
    ): Boolean {
        if (first.width != second.width || first.height != second.height) return true
        return (0 until first.height).any { y ->
            (0 until first.width).any { x -> first.getPixel(x, y) != second.getPixel(x, y) }
        }
    }

    private fun assertTwoTimesNearestNeighbor(
        source: Bitmap,
        rendered: Bitmap,
        background: Bitmap,
    ) {
        assertEquals(source.width * 2, rendered.width)
        assertEquals(source.height * 2, rendered.height)
        assertEquals(rendered.width, background.width)
        assertEquals(rendered.height, background.height)
        for (sourceY in 0 until source.height) {
            for (sourceX in 0 until source.width) {
                val expected = source.getPixel(sourceX, sourceY)
                repeat(2) { offsetY ->
                    repeat(2) { offsetX ->
                        val renderedX = sourceX * 2 + offsetX
                        val renderedY = sourceY * 2 + offsetY
                        val actual = rendered.getPixel(renderedX, renderedY)
                        if (expected.alphaValue() == 0) {
                            assertEquals(background.getPixel(renderedX, renderedY), actual)
                        } else {
                            assertEquals(expected, actual)
                        }
                    }
                }
            }
        }
    }

    private fun Bitmap.containsColor(color: Int): Boolean = (0 until height).any { y ->
        (0 until width).any { x -> getPixel(x, y) == color }
    }

    private fun Bitmap.alphaValues(): Sequence<Int> = sequence {
        for (y in 0 until height) {
            for (x in 0 until width) {
                yield(getPixel(x, y).alphaValue())
            }
        }
    }

    private fun Bitmap.maximumOpaqueY(): Int = (height - 1 downTo 0).first { y ->
        (0 until width).any { x -> getPixel(x, y).alphaValue() == 255 }
    }

    private fun Bitmap.maximumDifferentY(other: Bitmap): Int = (height - 1 downTo 0).first { y ->
        (0 until width).any { x -> getPixel(x, y) != other.getPixel(x, y) }
    }

    private fun Bitmap.opaqueBoundsInclusive(): List<Int> {
        val opaquePixels = sequence {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (getPixel(x, y).alphaValue() == 255) yield(x to y)
                }
            }
        }.toList()
        require(opaquePixels.isNotEmpty()) { "bitmap must contain at least one opaque pixel" }
        return listOf(
            opaquePixels.minOf { it.first },
            opaquePixels.minOf { it.second },
            opaquePixels.maxOf { it.first },
            opaquePixels.maxOf { it.second },
        )
    }

    private fun Int.alphaValue(): Int = ushr(24) and 0xFF

    private fun assertContainedBy(bounds: Rect, container: Rect) {
        val tolerance = 0.5f
        assertTrue(bounds.left >= container.left - tolerance)
        assertTrue(bounds.top >= container.top - tolerance)
        assertTrue(bounds.right <= container.right + tolerance)
        assertTrue(bounds.bottom <= container.bottom + tolerance)
    }

    private fun activePresentation(
        battlePhase: BattleAnimationPhase,
        scene: BattleMapUiState.Content,
        rewardFeedback: BattleRewardFeedback? = null,
    ): BattlePresentationState {
        val monsterAttack = battlePhase in setOf(
            BattleAnimationPhase.MONSTER_ATTACKING,
            BattleAnimationPhase.PLAYER_HIT,
            BattleAnimationPhase.PLAYER_DYING,
            BattleAnimationPhase.PLAYER_DEFEATED,
            BattleAnimationPhase.STATUS_EFFECT_APPLYING,
            BattleAnimationPhase.STATUS_EFFECT_REFRESHING,
            BattleAnimationPhase.PLAYER_EMERGENCY_RECOVERING,
        )
        return BattlePresentationState(
            phase = battlePhase,
            sequenceId = 1L,
            eventId = "preview-event-$battlePhase",
            eventKey = CombatEventKey(
                kind = if (monsterAttack) {
                    CombatEventKind.MONSTER_ATTACK
                } else {
                    CombatEventKind.PLAYER_ATTACK
                },
                taskId = 1L,
                occurrenceDateEpochDay = 20_000L,
            ),
            attacker = if (monsterAttack) {
                BattleUnitType.MONSTER
            } else {
                BattleUnitType.PLAYER
            },
            target = if (monsterAttack) {
                BattleUnitType.PLAYER
            } else {
                BattleUnitType.MONSTER
            },
            damage = 15,
            isLethal = battlePhase in setOf(
                BattleAnimationPhase.MONSTER_DYING,
                BattleAnimationPhase.PLAYER_DYING,
            ),
            rewardFeedback = rewardFeedback,
            sceneOverride = scene,
        )
    }

    private fun androidx.compose.ui.unit.Dp.toPx(density: Density): Float = with(density) { toPx() }

    private fun boundsOf(testTag: String): Rect = composeRule.onNodeWithTag(
        testTag = testTag,
        useUnmergedTree = true,
    ).fetchSemanticsNode().boundsInRoot

    private fun assertExpLabelsAlignWithBar() {
        val labelBounds = boundsOf("player-progress-exp-label")
        val valueBounds = boundsOf("player-progress-exp-value")
        val barBounds = boundsOf("player-progress-bar")
        assertTrue(kotlin.math.abs(labelBounds.left - barBounds.left) < 0.6f)
        assertTrue(kotlin.math.abs(valueBounds.right - barBounds.right) < 0.6f)
        assertTrue(labelBounds.right <= valueBounds.left)
    }

    private data class HudValues(
        val currentExp: Long,
        val requiredExp: Long,
        val gold: Long,
        val description: String,
    )
}

private val PixelSpriteOutlineArgb = 0xFF263B5A.toInt()
