package com.todoquest.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterProfileDao {
    @Query("SELECT * FROM character_profile WHERE id = 1")
    fun observeProfile(): Flow<CharacterProfileEntity?>

    @Query("SELECT * FROM character_profile WHERE id = :characterId")
    fun observeProfile(characterId: Long): Flow<CharacterProfileEntity?>

    @Query("SELECT * FROM character_profile WHERE id = 1")
    suspend fun getProfile(): CharacterProfileEntity?

    @Query("SELECT * FROM character_profile WHERE id = :characterId")
    suspend fun getProfile(characterId: Long): CharacterProfileEntity?

    @Query("SELECT * FROM character_current_state WHERE characterId = 1")
    fun observeCurrentState(): Flow<CharacterCurrentStateEntity?>

    @Query("SELECT * FROM character_current_state WHERE characterId = 1")
    suspend fun getCurrentState(): CharacterCurrentStateEntity?

    @Query("SELECT * FROM character_current_state WHERE characterId = :characterId")
    suspend fun getCurrentState(characterId: Long): CharacterCurrentStateEntity?

    @Query("SELECT * FROM character_appearance WHERE characterId = 1")
    fun observeAppearance(): Flow<CharacterAppearanceEntity?>

    @Query("SELECT * FROM character_appearance WHERE characterId = 1")
    suspend fun getAppearance(): CharacterAppearanceEntity?

    @Query("SELECT * FROM character_appearance WHERE characterId = :characterId")
    suspend fun getAppearance(characterId: Long): CharacterAppearanceEntity?

    @Query("SELECT * FROM character_equipped_items WHERE characterId = 1")
    fun observeEquippedItems(): Flow<CharacterEquippedItemsEntity?>

    @Query("SELECT * FROM character_equipped_items WHERE characterId = 1")
    suspend fun getEquippedItems(): CharacterEquippedItemsEntity?

    @Query("SELECT * FROM character_equipped_items WHERE characterId = :characterId")
    suspend fun getEquippedItems(characterId: Long): CharacterEquippedItemsEntity?

    @Upsert
    suspend fun upsert(entity: CharacterProfileEntity)

    @Query("UPDATE character_profile SET currentGold = :currentGold WHERE id = :characterId")
    suspend fun updateCurrentGold(characterId: Long, currentGold: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCurrentState(entity: CharacterCurrentStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppearance(entity: CharacterAppearanceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEquippedItems(entity: CharacterEquippedItemsEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProfile(entity: CharacterProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCurrentState(entity: CharacterCurrentStateEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAppearance(entity: CharacterAppearanceEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEquippedItems(entity: CharacterEquippedItemsEntity): Long

    @Transaction
    suspend fun insertCharacterIfAbsent(
        profile: CharacterProfileEntity,
        currentState: CharacterCurrentStateEntity,
        appearance: CharacterAppearanceEntity,
        equippedItems: CharacterEquippedItemsEntity,
    ): Boolean {
        require(
            setOf(
                profile.id,
                currentState.characterId,
                appearance.characterId,
                equippedItems.characterId,
            ).size == 1,
        ) {
            "character source rows must belong to the same character"
        }
        val insertedProfile = insertProfile(profile) != -1L
        val insertedCurrentState = insertCurrentState(currentState) != -1L
        val insertedAppearance = insertAppearance(appearance) != -1L
        val insertedEquippedItems = insertEquippedItems(equippedItems) != -1L
        check(
            listOf(
                insertedProfile,
                insertedCurrentState,
                insertedAppearance,
                insertedEquippedItems,
            ).distinct().size == 1,
        ) {
            "character source rows must be created together"
        }
        return insertedProfile
    }
}
