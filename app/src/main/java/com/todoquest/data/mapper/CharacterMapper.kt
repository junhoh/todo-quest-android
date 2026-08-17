package com.todoquest.data.mapper

import com.todoquest.data.local.CharacterCurrentStateEntity
import com.todoquest.data.local.CharacterAppearanceEntity
import com.todoquest.data.local.CharacterEquippedItemsEntity
import com.todoquest.data.local.CharacterProfileEntity
import com.todoquest.domain.model.CharacterBaseStats
import com.todoquest.domain.model.CharacterAppearance
import com.todoquest.domain.model.CharacterCurrentState
import com.todoquest.domain.model.EquippedItems
import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.PlayerCharacter

object CharacterMapper {
    fun toDomain(entity: CharacterProfileEntity): PlayerCharacter = PlayerCharacter(
        id = entity.id,
        totalXp = entity.totalXp,
        currentGold = entity.currentGold,
        baseStats = CharacterBaseStats(
            strength = entity.strength,
            vitality = entity.vitality,
            focus = entity.focus,
            willpower = entity.willpower,
        ),
        unspentStatPoints = entity.unspentStatPoints,
        hasUsedFreeStatReset = entity.hasUsedFreeStatReset,
    )

    fun fromDomain(character: PlayerCharacter): CharacterProfileEntity = CharacterProfileEntity(
        id = character.id,
        totalXp = character.totalXp,
        currentGold = character.currentGold,
        strength = character.baseStats.strength,
        vitality = character.baseStats.vitality,
        focus = character.baseStats.focus,
        willpower = character.baseStats.willpower,
        unspentStatPoints = character.unspentStatPoints,
        hasUsedFreeStatReset = character.hasUsedFreeStatReset,
    )

    fun toDomain(entity: CharacterCurrentStateEntity): CharacterCurrentState = CharacterCurrentState(
        characterId = entity.characterId,
        currentHp = entity.currentHp,
        balanceVersion = entity.balanceVersion,
        updatedAtEpochMillis = entity.updatedAtEpochMillis,
    )

    fun fromDomain(state: CharacterCurrentState): CharacterCurrentStateEntity = CharacterCurrentStateEntity(
        characterId = state.characterId,
        currentHp = state.currentHp,
        balanceVersion = state.balanceVersion,
        updatedAtEpochMillis = state.updatedAtEpochMillis,
    )

    fun toDomain(entity: CharacterAppearanceEntity): CharacterAppearance = CharacterAppearance(
        hairId = entity.hairId,
    )

    fun fromDomain(characterId: Long, appearance: CharacterAppearance): CharacterAppearanceEntity =
        CharacterAppearanceEntity(
            characterId = characterId,
            hairId = appearance.hairId,
        )

    fun toDomain(entity: CharacterEquippedItemsEntity): EquippedItems = EquippedItems(
        headId = entity.headId,
        topId = entity.topId,
        bottomId = entity.bottomId,
        shoesId = entity.shoesId,
        accessoryId = entity.accessoryId,
        weaponId = entity.weaponId,
        glovesId = entity.glovesId,
    )

    fun fromDomain(characterId: Long, items: EquippedItems): CharacterEquippedItemsEntity =
        CharacterEquippedItemsEntity(
            characterId = characterId,
            headId = items.headId,
            topId = items.topId,
            bottomId = items.bottomId,
            shoesId = items.shoesId,
            accessoryId = items.accessoryId,
            weaponId = items.weaponId,
            glovesId = items.glovesId,
        )

    fun defaultCharacter(config: CharacterStatBalanceConfig): PlayerCharacter = PlayerCharacter(
        id = DEFAULT_CHARACTER_ID,
        totalXp = 0,
        currentGold = 0,
        baseStats = CharacterBaseStats(
            strength = config.initialBaseStat,
            vitality = config.initialBaseStat,
            focus = config.initialBaseStat,
            willpower = config.initialBaseStat,
        ),
        unspentStatPoints = 0,
        hasUsedFreeStatReset = false,
    )

    const val DEFAULT_CHARACTER_ID = 1L
}
