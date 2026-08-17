package com.todoquest.feature.battle

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.todoquest.R
import com.todoquest.domain.model.CharacterLoadoutCatalog
import com.todoquest.domain.model.CombatEventKey
import com.todoquest.domain.model.CombatEventKind
import com.todoquest.domain.model.MonsterSpecies
import com.todoquest.domain.model.StatusEffectType
import com.todoquest.ui.character.CharacterRenderState
import com.todoquest.ui.theme.TodoQuestTheme

@Preview(name = "밝은 초원 · 몬스터 1마리", widthDp = 412, showBackground = true)
@Composable
private fun BattleMapOneMonsterPreview() {
    BattleMapPreviewContent(monsterCount = 1)
}

@Preview(name = "타락한 나무 정령 1마리", widthDp = 412, showBackground = true)
@Composable
private fun BattleMapTreeSpiritPreview() {
    BattleMapPreviewContent(
        monsterCount = 1,
        monsterSpecies = MonsterSpecies.CORRUPTED_TREE_SPIRIT,
    )
}

@Preview(name = "하피 1마리", widthDp = 412, showBackground = true)
@Composable
private fun BattleMapHarpyPreview() {
    BattleMapPreviewContent(
        monsterCount = 1,
        monsterSpecies = MonsterSpecies.HARPY,
    )
}

@Preview(name = "슬라임 1마리", widthDp = 412, showBackground = true)
@Composable
private fun BattleMapSlimePreview() {
    BattleMapPreviewContent(
        monsterCount = 1,
        monsterSpecies = MonsterSpecies.SLIME,
    )
}

@Preview(name = "몬스터 3마리", widthDp = 412, showBackground = true)
@Composable
private fun BattleMapThreeMonstersPreview() {
    BattleMapPreviewContent(monsterCount = 3)
}

@Preview(name = "몬스터 없음", widthDp = 412, showBackground = true)
@Composable
private fun BattleMapNoMonsterPreview() {
    BattleMapPreviewContent(monsterCount = 0)
}

@Preview(name = "320dp 소형 폰 · 0 EXP", widthDp = 320, showBackground = true)
@Composable
private fun BattleMapSmallPhonePreview() {
    BattleMapPreviewContent(
        monsterCount = 1,
        level = 1,
        currentExp = 0,
        requiredExp = 100,
        gold = 0,
    )
}

@Preview(name = "일반 폰", widthDp = 412, heightDp = 300, showBackground = true)
@Composable
private fun BattleMapPhonePreview() {
    BattleMapPreviewContent(monsterCount = 1)
}

@Preview(name = "가로 화면", widthDp = 800, heightDp = 360, showBackground = true)
@Composable
private fun BattleMapLandscapePreview() {
    BattleMapPreviewContent(monsterCount = 3)
}

@Preview(name = "플레이어 진행 로딩", widthDp = 412, showBackground = true)
@Composable
private fun BattleMapPlayerLoadingPreview() {
    BattleMapPreviewContent(monsterCount = 0, isLoading = true)
}

@Preview(
    name = "다크 모드",
    widthDp = 412,
    showBackground = true,
    backgroundColor = 0xFF15181B,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun BattleMapDarkModePreview() {
    BattleMapPreviewContent(monsterCount = 1, showSevereInjury = true)
}

@Preview(name = "플레이어 공격", widthDp = 412, showBackground = true)
@Composable
private fun BattleMapPlayerAttackingPreview() {
    BattleMapPhasePreview(BattleAnimationPhase.PLAYER_ATTACKING)
}

@Preview(name = "몬스터 피격", widthDp = 412, showBackground = true)
@Composable
private fun BattleMapMonsterHitPreview() {
    BattleMapPhasePreview(BattleAnimationPhase.MONSTER_HIT)
}

@Preview(name = "몬스터 쓰러짐", widthDp = 412, showBackground = true)
@Composable
private fun BattleMapMonsterDyingPreview() {
    BattleMapPhasePreview(BattleAnimationPhase.MONSTER_DYING)
}

@Preview(name = "몬스터 등장 경고", widthDp = 412, showBackground = true)
@Composable
private fun BattleMapMonsterSpawnAlertPreview() {
    BattleMapPhasePreview(BattleAnimationPhase.MONSTER_SPAWN_ALERT)
}

@Preview(name = "몬스터 등장", widthDp = 412, showBackground = true)
@Composable
private fun BattleMapMonsterSpawningPreview() {
    BattleMapPhasePreview(BattleAnimationPhase.MONSTER_SPAWNING)
}

@Preview(name = "몬스터 공격", widthDp = 412, showBackground = true)
@Composable
private fun BattleMapMonsterAttackingPreview() {
    BattleMapPhasePreview(BattleAnimationPhase.MONSTER_ATTACKING)
}

@Preview(name = "플레이어 피격", widthDp = 412, showBackground = true)
@Composable
private fun BattleMapPlayerHitPreview() {
    BattleMapPhasePreview(BattleAnimationPhase.PLAYER_HIT)
}

@Preview(name = "플레이어 쓰러짐", widthDp = 412, showBackground = true)
@Composable
private fun BattleMapPlayerDyingPreview() {
    BattleMapPhasePreview(BattleAnimationPhase.PLAYER_DYING)
}

@Preview(name = "플레이어 전투 불능", widthDp = 412, showBackground = true)
@Composable
private fun BattleMapPlayerDefeatedPreview() {
    BattleMapPhasePreview(BattleAnimationPhase.PLAYER_DEFEATED)
}

@Preview(name = "중상 적용", widthDp = 412, showBackground = true)
@Composable
private fun BattleMapSevereInjuryApplyingPreview() {
    BattleMapPhasePreview(BattleAnimationPhase.STATUS_EFFECT_APPLYING)
}

@Preview(name = "중상 갱신", widthDp = 412, showBackground = true)
@Composable
private fun BattleMapSevereInjuryRefreshingPreview() {
    BattleMapPhasePreview(BattleAnimationPhase.STATUS_EFFECT_REFRESHING)
}

@Preview(name = "플레이어 응급 회복", widthDp = 412, showBackground = true)
@Composable
private fun BattleMapPlayerEmergencyRecoveringPreview() {
    BattleMapPhasePreview(BattleAnimationPhase.PLAYER_EMERGENCY_RECOVERING)
}

@Preview(name = "중상 회복", widthDp = 412, showBackground = true)
@Composable
private fun BattleMapSevereInjuryRemovedPreview() {
    BattleMapPhasePreview(BattleAnimationPhase.STATUS_EFFECT_REMOVING)
}

@Composable
private fun BattleMapPhasePreview(phase: BattleAnimationPhase) {
    val defaultScene = previewState(monsterCount = 1)
    val monsterAttack = phase in setOf(
        BattleAnimationPhase.MONSTER_ATTACKING,
        BattleAnimationPhase.PLAYER_HIT,
        BattleAnimationPhase.PLAYER_DYING,
        BattleAnimationPhase.PLAYER_DEFEATED,
        BattleAnimationPhase.STATUS_EFFECT_APPLYING,
        BattleAnimationPhase.STATUS_EFFECT_REFRESHING,
        BattleAnimationPhase.PLAYER_EMERGENCY_RECOVERING,
    )
    val scene = when (phase) {
        BattleAnimationPhase.MONSTER_HIT,
        BattleAnimationPhase.MONSTER_DYING,
        -> defaultScene.copy(
            monsters = listOf(defaultScene.monsters.single().copy(currentHp = 0)),
        )

        BattleAnimationPhase.MONSTER_SPAWN_ALERT -> defaultScene.copy(monsters = emptyList())
        BattleAnimationPhase.PLAYER_HIT,
        BattleAnimationPhase.PLAYER_DYING,
        BattleAnimationPhase.PLAYER_DEFEATED,
        -> defaultScene.copy(player = defaultScene.player.copy(currentHp = 0))

        BattleAnimationPhase.STATUS_EFFECT_APPLYING,
        BattleAnimationPhase.STATUS_EFFECT_REFRESHING,
        -> defaultScene.copy(
            player = defaultScene.player.copy(currentHp = 0, maxHp = 64),
        )

        BattleAnimationPhase.PLAYER_EMERGENCY_RECOVERING -> defaultScene.copy(
            player = defaultScene.player.copy(currentHp = 32, maxHp = 64),
        )

        else -> defaultScene
    }
    val presentation = if (phase == BattleAnimationPhase.STATUS_EFFECT_REMOVING) {
        BattlePresentationState(
            phase = phase,
            sequenceId = 1L,
            eventId = "preview:${phase.name}",
            sceneOverride = scene,
        )
    } else {
        BattlePresentationState(
            phase = phase,
            sequenceId = 1L,
            eventId = "preview:${phase.name}",
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
            isLethal = phase == BattleAnimationPhase.MONSTER_DYING ||
                phase in setOf(
                    BattleAnimationPhase.PLAYER_DYING,
                    BattleAnimationPhase.PLAYER_DEFEATED,
                    BattleAnimationPhase.STATUS_EFFECT_APPLYING,
                    BattleAnimationPhase.STATUS_EFFECT_REFRESHING,
                    BattleAnimationPhase.PLAYER_EMERGENCY_RECOVERING,
                ),
            sceneOverride = scene,
        )
    }
    TodoQuestTheme {
        BattleMap(
            state = defaultScene,
            presentation = presentation,
            activeStatusEffects = if (
                phase in setOf(
                    BattleAnimationPhase.STATUS_EFFECT_APPLYING,
                    BattleAnimationPhase.STATUS_EFFECT_REFRESHING,
                    BattleAnimationPhase.PLAYER_EMERGENCY_RECOVERING,
                )
            ) {
                listOf(previewSevereInjury())
            } else {
                emptyList()
            },
            modifier = Modifier.padding(12.dp),
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

@Composable
private fun BattleMapPreviewContent(
    monsterCount: Int,
    monsterSpecies: MonsterSpecies = MonsterSpecies.GOBLIN_SCOUT,
    isLoading: Boolean = false,
    level: Int = 7,
    currentExp: Long = 40,
    requiredExp: Long = 100,
    gold: Long = 1_280,
    showSevereInjury: Boolean = false,
) {
    TodoQuestTheme {
        BattleMap(
            state = previewState(monsterCount, monsterSpecies),
            activeStatusEffects = if (showSevereInjury) {
                listOf(previewSevereInjury())
            } else {
                emptyList()
            },
            modifier = Modifier.padding(12.dp),
            overlayContent = {
                PlayerProgressHud(
                    isLoading = isLoading,
                    level = level,
                    currentExp = currentExp,
                    requiredExp = requiredExp,
                    gold = gold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp)
                        .fillMaxWidth(),
                )
            },
        )
    }
}

private fun previewSevereInjury() = ActiveStatusEffectUiModel(
    type = StatusEffectType.SEVERE_INJURY,
    revision = 1L,
    remainingRecoveryCompletions = 2,
    remainingTime = StatusEffectRemainingTimeUiState.Hours(12),
)

private fun previewState(
    monsterCount: Int,
    monsterSpecies: MonsterSpecies = MonsterSpecies.GOBLIN_SCOUT,
): BattleMapUiState.Content {
    val monsterVisual = BattleMonsterVisualCatalog.forSpecies(monsterSpecies)
    return BattleMapUiState.Content(
        player = BattleUnitUiModel(
            id = "preview-player",
            type = BattleUnitType.PLAYER,
            sprite = BattleSpriteUiModel.LayeredCharacter(
                renderState = CharacterRenderState(
                    appearance = CharacterLoadoutCatalog.defaultAppearance,
                    equippedItems = CharacterLoadoutCatalog.defaultEquippedItems,
                ),
                frame = BattleMapDefaults.PLAYER_FRAME,
            ),
            position = BattleMapDefaults.PLAYER_POSITION,
            scale = 1f,
            groundOffset = 0f,
            currentHp = 75,
            maxHp = 100,
            nameResId = R.string.battle_player_name,
            deathAnnouncementResId = R.string.battle_player_death_announcement,
        ),
        monsters = BattleMonsterSlots.forCount(monsterCount).mapIndexed { index, position ->
            BattleUnitUiModel(
                id = "preview-monster-$index",
                type = BattleUnitType.MONSTER,
                sprite = BattleSpriteUiModel.Resource(
                    spriteResId = monsterVisual.spriteResId,
                    frame = BattleMapDefaults.MONSTER_FRAME,
                ),
                position = position,
                scale = 1f,
                groundOffset = 0f,
                currentHp = 40,
                maxHp = 50,
                nameResId = monsterVisual.nameResId,
                deathAnnouncementResId = monsterVisual.deathAnnouncementResId,
            )
        },
        stageNumber = 7,
    )
}
