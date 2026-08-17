package com.todoquest.feature.battle

import androidx.compose.ui.unit.dp
import com.todoquest.R
import com.todoquest.domain.model.CharacterLoadoutCatalog
import com.todoquest.domain.model.CharacterStatusEffect
import com.todoquest.domain.model.MonsterSpecies
import com.todoquest.domain.model.StatusEffectType
import com.todoquest.ui.character.CharacterRenderState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BattleMapUiModelTest {
    @Test
    fun battlePositionAcceptsOnlyFiniteNormalizedCoordinates() {
        assertEquals(BattlePosition(0f, 1f), BattlePosition(0f, 1f))

        listOf(
            Float.NaN to 0.5f,
            Float.POSITIVE_INFINITY to 0.5f,
            Float.NEGATIVE_INFINITY to 0.5f,
            -0.01f to 0.5f,
            1.01f to 0.5f,
            0.5f to Float.NaN,
            0.5f to Float.POSITIVE_INFINITY,
            0.5f to Float.NEGATIVE_INFINITY,
            0.5f to -0.01f,
            0.5f to 1.01f,
        ).forEach { (x, y) ->
            assertThrows(IllegalArgumentException::class.java) {
                BattlePosition(x, y)
            }
        }
    }

    @Test
    fun spriteFrameKeepsFullSourceRectAndNormalizedGroundAnchor() {
        assertEquals(
            BattleSpriteFrame(
                sourceX = 0,
                sourceY = 0,
                sourceWidth = 64,
                sourceHeight = 64,
                groundAnchor = BattlePosition(0.5f, 58f / 64f),
            ),
            BattleMapDefaults.PLAYER_FRAME,
        )
        assertEquals(BattleMapDefaults.PLAYER_FRAME, BattleMapDefaults.MONSTER_FRAME)

        assertThrows(IllegalArgumentException::class.java) {
            BattleSpriteFrame(-1, 0, 64, 64, BattlePosition(0.5f, 0.5f))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BattleSpriteFrame(0, 0, 0, 64, BattlePosition(0.5f, 0.5f))
        }
    }

    @Test
    fun monsterVisualCatalogMapsSpeciesToSpriteNameAndDeathAnnouncementResources() {
        assertEquals(
            BattleMonsterVisual(
                spriteResId = R.drawable.todo_quest_goblin_scout_front_idle,
                nameResId = R.string.battle_monster_goblin_scout_name,
                deathAnnouncementResId = R.string.battle_monster_death_announcement,
            ),
            BattleMonsterVisualCatalog.forSpecies(MonsterSpecies.GOBLIN_SCOUT),
        )
        assertEquals(
            BattleMonsterVisual(
                spriteResId = R.drawable.todo_quest_skeleton_soldier_front_idle,
                nameResId = R.string.battle_monster_skeleton_soldier_name,
                deathAnnouncementResId =
                    R.string.battle_monster_skeleton_soldier_death_announcement,
            ),
            BattleMonsterVisualCatalog.forSpecies(MonsterSpecies.SKELETON_SOLDIER),
        )
        assertEquals(
            BattleMonsterVisual(
                spriteResId = R.drawable.todo_quest_corrupted_tree_spirit_front_idle,
                nameResId = R.string.battle_monster_corrupted_tree_spirit_name,
                deathAnnouncementResId =
                    R.string.battle_monster_corrupted_tree_spirit_death_announcement,
            ),
            BattleMonsterVisualCatalog.forSpecies(MonsterSpecies.CORRUPTED_TREE_SPIRIT),
        )
        assertEquals(
            BattleMonsterVisual(
                spriteResId = R.drawable.todo_quest_harpy_front_idle,
                nameResId = R.string.battle_monster_harpy_name,
                deathAnnouncementResId = R.string.battle_monster_harpy_death_announcement,
            ),
            BattleMonsterVisualCatalog.forSpecies(MonsterSpecies.HARPY),
        )
        assertEquals(
            BattleMonsterVisual(
                spriteResId = R.drawable.todo_quest_slime_front_idle,
                nameResId = R.string.battle_monster_slime_name,
                deathAnnouncementResId = R.string.battle_monster_slime_death_announcement,
            ),
            BattleMonsterVisualCatalog.forSpecies(MonsterSpecies.SLIME),
        )
    }

    @Test
    fun battleUnitValidatesIdentityScaleOffsetAndHp() {
        validUnit()

        assertThrows(IllegalArgumentException::class.java) { validUnit(id = "  ") }
        assertThrows(IllegalArgumentException::class.java) { validUnit(scale = 0f) }
        assertThrows(IllegalArgumentException::class.java) {
            validUnit(scale = Float.POSITIVE_INFINITY)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validUnit(groundOffset = Float.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) { validUnit(currentHp = -1) }
        assertThrows(IllegalArgumentException::class.java) { validUnit(maxHp = 0) }
        assertThrows(IllegalArgumentException::class.java) {
            validUnit(currentHp = 101, maxHp = 100)
        }
    }

    @Test
    fun contentRequiresPlayerAndUpToFourMonstersWithPositiveStage() {
        val player = validUnit(type = BattleUnitType.PLAYER)
        val monsters = (1..4).map { index ->
            validUnit(
                id = "monster-$index",
                type = BattleUnitType.MONSTER,
                position = BattleMonsterSlots.forCount(4)[index - 1],
            )
        }

        BattleMapUiState.Content(player = player, monsters = emptyList(), stageNumber = 1)
        BattleMapUiState.Content(player = player, monsters = monsters, stageNumber = 10)

        assertThrows(IllegalArgumentException::class.java) {
            BattleMapUiState.Content(
                player = validUnit(type = BattleUnitType.MONSTER),
                monsters = emptyList(),
                stageNumber = 1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            BattleMapUiState.Content(
                player = player,
                monsters = listOf(validUnit(id = "not-monster")),
                stageNumber = 1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            BattleMapUiState.Content(
                player = player,
                monsters = monsters + validUnit(
                    id = "monster-5",
                    type = BattleUnitType.MONSTER,
                ),
                stageNumber = 1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            BattleMapUiState.Content(player = player, monsters = emptyList(), stageNumber = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BattleMapUiState.Content(
                player = validUnit(
                    type = BattleUnitType.PLAYER,
                    sprite = monsterSprite(),
                ),
                monsters = emptyList(),
                stageNumber = 1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            BattleMapUiState.Content(
                player = player,
                monsters = listOf(
                    validUnit(
                        id = "monster-layered-character",
                        type = BattleUnitType.MONSTER,
                        sprite = playerSprite(),
                    ),
                ),
                stageNumber = 1,
            )
        }
    }

    @Test
    fun defaultThemeUsesGrasslandDrawableAndPlayerPositionIsNormalizedConstant() {
        assertEquals(R.drawable.battle_map_grassland, BattleMapTheme().backgroundResId)
        assertEquals(BattlePosition(0.20f, 0.82f), BattleMapDefaults.PLAYER_POSITION)
    }

    @Test
    fun heightPoliciesKeepExistingDefaultAndBoundCompactCalendarMode() {
        assertEquals(190.dp, BattleMapHeightPolicy.STANDARD.minimumHeight)
        assertEquals(320.dp, BattleMapHeightPolicy.STANDARD.maximumHeight)
        assertEquals(150.dp, BattleMapHeightPolicy.COMPACT.minimumHeight)
        assertEquals(190.dp, BattleMapHeightPolicy.COMPACT.maximumHeight)
    }

    @Test
    fun activeStatusEffectRoundsRemainingHoursUpAndPreservesRecoveryCount() {
        val now = Instant.parse("2026-08-05T08:00:00Z")
        val effect = severeInjury(
            expiresAt = now.plusSeconds(2 * 60 * 60 + 1),
            remainingCompletions = 2,
        )

        assertEquals(
            ActiveStatusEffectUiModel(
                type = StatusEffectType.SEVERE_INJURY,
                revision = 1L,
                remainingRecoveryCompletions = 2,
                remainingTime = StatusEffectRemainingTimeUiState.Hours(3),
            ),
            effect.toActiveStatusEffectUiModel(now),
        )
    }

    @Test
    fun activeStatusEffectUsesDedicatedLessThanOneHourStateAndRejectsExpiredTime() {
        val now = Instant.parse("2026-08-05T08:00:00Z")

        assertEquals(
            StatusEffectRemainingTimeUiState.LessThanOneHour,
            severeInjury(now.plusMillis(1)).toActiveStatusEffectUiModel(now).remainingTime,
        )
        assertThrows(IllegalArgumentException::class.java) {
            severeInjury(now).toActiveStatusEffectUiModel(now)
        }
    }

    private fun validUnit(
        id: String = "player",
        type: BattleUnitType = BattleUnitType.PLAYER,
        position: BattlePosition = BattleMapDefaults.PLAYER_POSITION,
        scale: Float = 1f,
        groundOffset: Float = 0f,
        currentHp: Int = 75,
        maxHp: Int = 100,
        sprite: BattleSpriteUiModel = if (type == BattleUnitType.PLAYER) {
            playerSprite()
        } else {
            monsterSprite()
        },
    ) = BattleUnitUiModel(
        id = id,
        type = type,
        sprite = sprite,
        position = position,
        scale = scale,
        groundOffset = groundOffset,
        currentHp = currentHp,
        maxHp = maxHp,
        nameResId = R.string.character_adventurer_description,
        deathAnnouncementResId = R.string.battle_player_death_announcement,
    )

    private fun playerSprite() = BattleSpriteUiModel.LayeredCharacter(
        renderState = CharacterRenderState(
            appearance = CharacterLoadoutCatalog.defaultAppearance,
            equippedItems = CharacterLoadoutCatalog.defaultEquippedItems,
        ),
        frame = BattleMapDefaults.PLAYER_FRAME,
    )

    private fun monsterSprite() = BattleSpriteUiModel.Resource(
        spriteResId = R.drawable.todo_quest_goblin_scout_front_idle,
        frame = BattleMapDefaults.MONSTER_FRAME,
    )

    private fun severeInjury(
        expiresAt: Instant,
        remainingCompletions: Int = 3,
    ) = CharacterStatusEffect(
        characterId = 1L,
        type = StatusEffectType.SEVERE_INJURY,
        definitionVersion = 1,
        appliedAtEpochMillis = Instant.parse("2026-08-05T07:00:00Z").toEpochMilli(),
        expiresAtEpochMillis = expiresAt.toEpochMilli(),
        remainingRecoveryCompletions = remainingCompletions,
        active = true,
        revision = 1L,
        lastMutationId = "status-effect:test",
    )
}
