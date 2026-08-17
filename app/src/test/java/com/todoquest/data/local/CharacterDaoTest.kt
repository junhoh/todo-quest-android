package com.todoquest.data.local

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CharacterDaoTest {
    private lateinit var database: TodoQuestDatabase
    private lateinit var dao: CharacterProfileDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            TodoQuestDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.characterProfileDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertsDefaultProfileAndCurrentStateAtomicallyWhenMissing() = runTest {
        assertNull(dao.getProfile())
        assertNull(dao.getCurrentState())
        assertNull(dao.getAppearance())
        assertNull(dao.getEquippedItems())

        val inserted = dao.insertCharacterIfAbsent(
            defaultProfile(),
            defaultCurrentState(),
            defaultAppearance(),
            defaultEquippedItems(),
        )

        assertTrue(inserted)
        assertEquals(defaultProfile(), dao.observeProfile().first())
        assertEquals(defaultCurrentState(), dao.observeCurrentState().first())
        assertEquals(defaultAppearance(), dao.observeAppearance().first())
        assertEquals(defaultEquippedItems(), dao.observeEquippedItems().first())
    }

    @Test
    fun repeatedInitializationDoesNotOverwriteExistingCharacter() = runTest {
        assertTrue(
            dao.insertCharacterIfAbsent(
                defaultProfile(),
                defaultCurrentState(),
                defaultAppearance(),
                defaultEquippedItems(),
            ),
        )

        val inserted = dao.insertCharacterIfAbsent(
            defaultProfile().copy(totalXp = 900, currentGold = 300),
            defaultCurrentState().copy(currentHp = 1, updatedAtEpochMillis = 999),
            defaultAppearance().copy(hairId = "changed"),
            defaultEquippedItems().copy(topId = "changed"),
        )

        assertFalse(inserted)
        assertEquals(defaultProfile(), dao.getProfile())
        assertEquals(defaultCurrentState(), dao.getCurrentState())
        assertEquals(defaultAppearance(), dao.getAppearance())
        assertEquals(defaultEquippedItems(), dao.getEquippedItems())
    }

    @Test
    fun freshDatabasePersistsGlovesFallbackValue() = runTest {
        assertTrue(
            dao.insertCharacterIfAbsent(
                defaultProfile(),
                defaultCurrentState(),
                defaultAppearance(),
                defaultEquippedItems(),
            ),
        )

        val glovesFallback = defaultEquippedItems().copy(glovesId = "gloves_leather")
        dao.upsertEquippedItems(glovesFallback)

        assertEquals(glovesFallback, dao.getEquippedItems())
        assertEquals(glovesFallback, dao.observeEquippedItems().first())
    }

    private fun defaultProfile() = CharacterProfileEntity(
        id = 1L,
        totalXp = 0L,
        currentGold = 0L,
        strength = 5,
        vitality = 5,
        focus = 5,
        willpower = 5,
        unspentStatPoints = 0,
        hasUsedFreeStatReset = false,
    )

    private fun defaultCurrentState() = CharacterCurrentStateEntity(
        characterId = 1L,
        currentHp = 110,
        balanceVersion = 1,
        updatedAtEpochMillis = 0L,
    )

    private fun defaultAppearance() = CharacterAppearanceEntity(
        characterId = 1L,
        hairId = "hair_default",
    )

    private fun defaultEquippedItems() = CharacterEquippedItemsEntity(
        characterId = 1L,
        headId = "headgear_adventure",
        topId = "top_adventure",
        bottomId = "bottom_adventure",
        shoesId = "shoes_adventure",
        accessoryId = "accessory_adventure",
        weaponId = "weapon_default_sword",
        glovesId = null,
    )
}
