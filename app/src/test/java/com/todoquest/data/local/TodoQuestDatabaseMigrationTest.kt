package com.todoquest.data.local

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TodoQuestDatabaseMigrationTest {
    @Test
    fun migrationFromVersion14ToVersion15ClearsOnlyAppearanceFallbackAndPreservesGameplaySources() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val databaseName = "m14_${System.nanoTime().toString(36)}.db"
            context.deleteDatabase(databaseName)
            createVersion14Database(context, databaseName, totalXp = 250)
            insertVersion14FallbackMigrationSources(context, databaseName)

            val preservedTables = listOf(
                "todo_tasks",
                "completion_logs",
                "failure_logs",
                "reward_ledger",
                "character_profile",
                "character_current_state",
                "character_appearance",
                "monster_instances",
                "combat_progress",
                "player_attack_events",
                "monster_attack_events",
                "equipment",
                "equipment_modifiers",
                "owned_equipment",
                "character_equipment",
                "task_reminders",
                "character_status_effects",
                "status_effect_recovery_occurrences",
            )
            val legacyDatabase = context.openOrCreateDatabase(
                databaseName,
                Context.MODE_PRIVATE,
                null,
            )
            val beforeCounts: Map<String, Int>
            val beforeIndexes: Map<String, Set<String>>
            try {
                assertEquals(14, legacyDatabase.version)
                beforeCounts = preservedTables.associateWith { legacyDatabase.rowCount(it) }
                beforeIndexes = preservedTables.associateWith { legacyDatabase.indexNames(it) }
                assertEquals(
                    1,
                    legacyDatabase.rawQuery(
                        "SELECT COUNT(*) FROM character_equipped_items " +
                            "WHERE headId = 'headgear_adventure' " +
                            "AND topId = 'top_adventure' " +
                            "AND bottomId = 'bottom_adventure' " +
                            "AND shoesId = 'shoes_adventure' " +
                            "AND accessoryId = 'accessory_adventure' " +
                            "AND weaponId = 'weapon_default_sword' " +
                            "AND glovesId = 'gloves_adventure'",
                        null,
                    ).use { cursor ->
                        check(cursor.moveToFirst())
                        cursor.getInt(0)
                    },
                )
            } finally {
                legacyDatabase.close()
            }

            var database: TodoQuestDatabase? = null
            try {
                database = Room.databaseBuilder(
                    context,
                    TodoQuestDatabase::class.java,
                    databaseName,
                )
                    .addMigrations(TodoQuestDatabase.MIGRATION_14_15)
                    .allowMainThreadQueries()
                    .build()

                val sqliteDatabase = database.openHelper.writableDatabase
                assertEquals(15, sqliteDatabase.version)
                assertEquals(
                    beforeCounts,
                    preservedTables.associateWith { sqliteDatabase.rowCount(it) },
                )
                assertEquals(
                    beforeIndexes,
                    preservedTables.associateWith { sqliteDatabase.indexNames(it) },
                )
                assertEquals(
                    CharacterEquippedItemsEntity(
                        characterId = 1L,
                        headId = null,
                        topId = "top_default",
                        bottomId = "bottom_default",
                        shoesId = "shoes_default",
                        accessoryId = null,
                        weaponId = null,
                        glovesId = null,
                    ),
                    database.characterProfileDao().getEquippedItems(1L),
                )
                assertEquals(
                    listOf(
                        OwnedEquipmentEntity(6001L, 1L, 5001L, 4_700L),
                        OwnedEquipmentEntity(6002L, 1L, 5002L, 4_800L),
                    ),
                    database.equipmentDao().getOwnedEquipment(1L),
                )
                assertEquals(
                    listOf(
                        CharacterEquipmentEntity(1L, "GLOVES", 6001L),
                        CharacterEquipmentEntity(1L, "WEAPON", 6002L),
                    ),
                    database.equipmentDao().getCharacterEquipment(1L),
                )
                assertEquals(456L, database.characterProfileDao().getProfile(1L)?.currentGold)
                assertEquals(122, database.characterProfileDao().getCurrentState(1L)?.currentHp)
                assertEquals(
                    CharacterStatusEffectEntity(
                        characterId = 1L,
                        effectType = "SEVERE_INJURY",
                        definitionVersion = 1,
                        appliedAtEpochMillis = 7_000L,
                        expiresAtEpochMillis = 86_407_000L,
                        remainingRecoveryCompletions = 2,
                        active = true,
                        revision = 1L,
                        lastMutationId = "apply:7",
                    ),
                    database.statusEffectDao().getStatusEffect(1L, "SEVERE_INJURY"),
                )
                assertNotNull(database.todoTaskDao().getActiveById(7L))
                assertNotNull(database.completionLogDao().find(7L, OCCURRENCE_EPOCH_DAY))
                assertNotNull(database.rewardLedgerDao().find(7L, OCCURRENCE_EPOCH_DAY))
                assertNotNull(
                    database.combatDao()
                        .getPlayerAttackEvent(7L, OCCURRENCE_EPOCH_DAY + 6),
                )
            } finally {
                database?.close()
                context.deleteDatabase(databaseName)
            }
        }

    @Test
    fun migrationFromVersion13ToVersion14AddsNullableDifficultySnapshotsWithoutBackfill() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val databaseName = "m13_${System.nanoTime().toString(36)}.db"
            context.deleteDatabase(databaseName)
            createVersion13Database(context, databaseName, totalXp = 250)
            insertVersion13DifficultyMigrationSources(context, databaseName)

            val preservedTables = listOf(
                "todo_tasks",
                "completion_logs",
                "failure_logs",
                "reward_ledger",
                "character_profile",
                "character_current_state",
                "character_appearance",
                "character_equipped_items",
                "monster_instances",
                "combat_progress",
                "player_attack_events",
                "monster_attack_events",
                "equipment",
                "equipment_modifiers",
                "owned_equipment",
                "character_equipment",
                "task_reminders",
                "character_status_effects",
                "status_effect_recovery_occurrences",
            )
            val legacyDatabase = context.openOrCreateDatabase(
                databaseName,
                Context.MODE_PRIVATE,
                null,
            )
            val beforeCounts: Map<String, Int>
            val beforeIndexes: Map<String, Set<String>>
            try {
                assertEquals(13, legacyDatabase.version)
                beforeCounts = preservedTables.associateWith { tableName ->
                    legacyDatabase.rowCount(tableName)
                }
                beforeIndexes = preservedTables.associateWith { tableName ->
                    legacyDatabase.indexNames(tableName)
                }
            } finally {
                legacyDatabase.close()
            }

            var database: TodoQuestDatabase? = null
            try {
                database = Room.databaseBuilder(
                    context,
                    TodoQuestDatabase::class.java,
                    databaseName,
                )
                    .addMigrations(
                        TodoQuestDatabase.MIGRATION_13_14,
                        TodoQuestDatabase.MIGRATION_14_15,
                    )
                    .allowMainThreadQueries()
                    .build()

                val sqliteDatabase = database.openHelper.writableDatabase
                assertEquals(15, sqliteDatabase.version)
                assertEquals(
                    beforeCounts,
                    preservedTables.associateWith { tableName ->
                        sqliteDatabase.rowCount(tableName)
                    },
                )
                assertEquals(
                    beforeIndexes,
                    preservedTables.associateWith { tableName ->
                        sqliteDatabase.indexNames(tableName)
                    },
                )
                assertEquals("HARD", database.todoTaskDao().getActiveById(7L)?.difficulty)

                val pending = database.combatDao()
                    .getPlayerAttackEvent(7L, OCCURRENCE_EPOCH_DAY + 1)
                assertNotNull(pending)
                assertEquals("PENDING", pending?.status)
                assertEquals(25, pending?.sourceAttack)
                assertEquals(0, pending?.combatRewardVersion)
                assertNull(pending?.sourceTaskDifficulty)
                assertEquals(0, pending?.taskDifficultyBalanceVersion)

                val applied = database.combatDao()
                    .getPlayerAttackEvent(7L, OCCURRENCE_EPOCH_DAY + 6)
                assertNotNull(applied)
                assertEquals("APPLIED", applied?.status)
                assertEquals(41, applied?.sourceAttack)
                assertEquals(2, applied?.combatRewardVersion)
                assertEquals(3L, applied?.hitXpAward)
                assertEquals(20L, applied?.killBonusXpAward)
                assertEquals(15L, applied?.killGoldAward)
                assertEquals(42, applied?.finalDamage)
                assertNull(applied?.sourceTaskDifficulty)
                assertEquals(0, applied?.taskDifficultyBalanceVersion)

                assertTrue("sourceTaskDifficulty" in sqliteDatabase.columnNames("player_attack_events"))
                assertFalse(
                    sqliteDatabase.columnIsNotNull(
                        "player_attack_events",
                        "sourceTaskDifficulty",
                    ),
                )
                assertNull(
                    sqliteDatabase.columnDefaultValue(
                        "player_attack_events",
                        "sourceTaskDifficulty",
                    ),
                )
                assertTrue(
                    sqliteDatabase.columnIsNotNull(
                        "player_attack_events",
                        "taskDifficultyBalanceVersion",
                    ),
                )
                assertEquals(
                    "0",
                    sqliteDatabase.columnDefaultValue(
                        "player_attack_events",
                        "taskDifficultyBalanceVersion",
                    ),
                )
                assertEquals(1, sqliteDatabase.rowCount("character_status_effects"))
                assertEquals(1, sqliteDatabase.rowCount("status_effect_recovery_occurrences"))
            } finally {
                database?.close()
                context.deleteDatabase(databaseName)
            }
        }

    @Test
    fun freshVersion14SchemaContainsDifficultySnapshotColumnsWithLegacyDefaults() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(
            context,
            TodoQuestDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val sqliteDatabase = database.openHelper.writableDatabase
            assertEquals(15, sqliteDatabase.version)
            assertTrue("sourceTaskDifficulty" in sqliteDatabase.columnNames("player_attack_events"))
            assertTrue(
                "taskDifficultyBalanceVersion" in
                    sqliteDatabase.columnNames("player_attack_events"),
            )
            assertFalse(
                sqliteDatabase.columnIsNotNull("player_attack_events", "sourceTaskDifficulty"),
            )
            assertNull(
                sqliteDatabase.columnDefaultValue("player_attack_events", "sourceTaskDifficulty"),
            )
            assertTrue(
                sqliteDatabase.columnIsNotNull(
                    "player_attack_events",
                    "taskDifficultyBalanceVersion",
                ),
            )
            assertEquals(
                "0",
                sqliteDatabase.columnDefaultValue(
                    "player_attack_events",
                    "taskDifficultyBalanceVersion",
                ),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun migrationFromVersion12ThroughVersion14PreservesSourcesAndCreatesEmptyStatusEffectTables() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val databaseName = "m12_${System.nanoTime().toString(36)}.db"
            context.deleteDatabase(databaseName)
            createVersion12Database(context, databaseName, totalXp = 250)

            val preservedTables = listOf(
                "todo_tasks",
                "completion_logs",
                "failure_logs",
                "reward_ledger",
                "character_profile",
                "character_current_state",
                "character_appearance",
                "character_equipped_items",
                "monster_instances",
                "combat_progress",
                "player_attack_events",
                "monster_attack_events",
                "equipment",
                "equipment_modifiers",
                "owned_equipment",
                "character_equipment",
                "task_reminders",
            )
            val legacyDatabase = context.openOrCreateDatabase(
                databaseName,
                Context.MODE_PRIVATE,
                null,
            )
            val beforeCounts: Map<String, Int>
            val beforeIndexes: Map<String, Set<String>>
            try {
                assertEquals(12, legacyDatabase.version)
                beforeCounts = preservedTables.associateWith { tableName ->
                    legacyDatabase.rowCount(tableName)
                }
                beforeIndexes = preservedTables.associateWith { tableName ->
                    legacyDatabase.indexNames(tableName)
                }
            } finally {
                legacyDatabase.close()
            }

            var database: TodoQuestDatabase? = null
            try {
                database = Room.databaseBuilder(
                    context,
                    TodoQuestDatabase::class.java,
                    databaseName,
                )
                    .addMigrations(
                        TodoQuestDatabase.MIGRATION_12_13,
                        TodoQuestDatabase.MIGRATION_13_14,
                        TodoQuestDatabase.MIGRATION_14_15,
                    )
                    .allowMainThreadQueries()
                    .build()

                val sqliteDatabase = database.openHelper.writableDatabase
                assertEquals(15, sqliteDatabase.version)
                assertEquals(
                    beforeCounts,
                    preservedTables.associateWith { tableName ->
                        sqliteDatabase.rowCount(tableName)
                    },
                )
                assertEquals(
                    beforeIndexes,
                    preservedTables.associateWith { tableName ->
                        sqliteDatabase.indexNames(tableName)
                    },
                )
                assertNotNull(database.todoTaskDao().getActiveById(7L))
                assertNotNull(database.completionLogDao().find(7L, OCCURRENCE_EPOCH_DAY))
                assertEquals(250L, database.characterProfileDao().getProfile()?.totalXp)
                assertNotNull(database.combatDao().getMonsterAttackEvent(7L, OCCURRENCE_EPOCH_DAY + 2))
                assertEquals(0, sqliteDatabase.rowCount("character_status_effects"))
                assertEquals(0, sqliteDatabase.rowCount("status_effect_recovery_occurrences"))
                assertEquals(
                    setOf(
                        "character_status_effects",
                        "status_effect_recovery_occurrences",
                    ),
                    sqliteDatabase.query(
                        """
                        SELECT name
                        FROM sqlite_master
                        WHERE type = 'table'
                          AND name IN (
                              'character_status_effects',
                              'status_effect_recovery_occurrences'
                          )
                        """.trimIndent(),
                    ).use { cursor ->
                        buildSet {
                            while (cursor.moveToNext()) add(cursor.getString(0))
                        }
                    },
                )
                assertEquals(
                    setOf(
                        "characterId",
                        "effectType",
                        "definitionVersion",
                        "appliedAtEpochMillis",
                        "expiresAtEpochMillis",
                        "remainingRecoveryCompletions",
                        "active",
                        "revision",
                        "lastMutationId",
                    ),
                    sqliteDatabase.columnNames("character_status_effects"),
                )
                assertEquals(
                    setOf(
                        "characterId",
                        "effectType",
                        "revision",
                        "taskId",
                        "occurrenceDateEpochDay",
                    ),
                    sqliteDatabase.columnNames("status_effect_recovery_occurrences"),
                )
                listOf(
                    "characterId",
                    "effectType",
                    "definitionVersion",
                    "appliedAtEpochMillis",
                    "expiresAtEpochMillis",
                    "remainingRecoveryCompletions",
                    "active",
                    "revision",
                    "lastMutationId",
                ).forEach { columnName ->
                    assertTrue(
                        sqliteDatabase.columnIsNotNull("character_status_effects", columnName),
                    )
                    assertNull(
                        sqliteDatabase.columnDefaultValue(
                            "character_status_effects",
                            columnName,
                        ),
                    )
                }
                listOf(
                    "characterId",
                    "effectType",
                    "revision",
                    "taskId",
                    "occurrenceDateEpochDay",
                ).forEach { columnName ->
                    assertTrue(
                        sqliteDatabase.columnIsNotNull(
                            "status_effect_recovery_occurrences",
                            columnName,
                        ),
                    )
                    assertNull(
                        sqliteDatabase.columnDefaultValue(
                            "status_effect_recovery_occurrences",
                            columnName,
                        ),
                    )
                }
                assertEquals(
                    mapOf("characterId" to 1, "effectType" to 2),
                    sqliteDatabase.primaryKeyPositions("character_status_effects"),
                )
                assertEquals(
                    mapOf(
                        "characterId" to 1,
                        "effectType" to 2,
                        "revision" to 3,
                        "taskId" to 4,
                        "occurrenceDateEpochDay" to 5,
                    ),
                    sqliteDatabase.primaryKeyPositions("status_effect_recovery_occurrences"),
                )
                assertTrue(
                    "index_character_status_effects_characterId_active" in
                        sqliteDatabase.indexNames("character_status_effects"),
                )
                assertTrue(
                    sqliteDatabase.indexNames("status_effect_recovery_occurrences")
                        .any { indexName ->
                            indexName.startsWith(
                                "sqlite_autoindex_status_effect_recovery_occurrences_",
                            )
                        },
                )

                sqliteDatabase.execSQL(
                    """
                    INSERT INTO character_status_effects VALUES (
                        1, 'SEVERE_INJURY', 1, 1000, 86401000, 3, 1, 1, 'apply:1'
                    )
                    """.trimIndent(),
                )
                sqliteDatabase.execSQL(
                    """
                    INSERT INTO status_effect_recovery_occurrences VALUES (
                        1, 'SEVERE_INJURY', 1, 7, $OCCURRENCE_EPOCH_DAY
                    )
                    """.trimIndent(),
                )
                assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                    sqliteDatabase.execSQL(
                        """
                        INSERT INTO status_effect_recovery_occurrences VALUES (
                            1, 'SEVERE_INJURY', 1, 7, $OCCURRENCE_EPOCH_DAY
                        )
                        """.trimIndent(),
                    )
                }
            } finally {
                database?.close()
                context.deleteDatabase(databaseName)
            }
        }

    @Test
    fun migrationFromVersion1ThroughVersion14PreservesUserData() = runTest {
        assertMigrationPreservesUserData(fromVersion = 1, totalXp = 250, expectedLevel = 3)
    }

    @Test
    fun migrationFromVersion2ThroughVersion14PreservesUserDataAndCapsDerivedInitializationLevel() = runTest {
        assertMigrationPreservesUserData(fromVersion = 2, totalXp = 9_999, expectedLevel = 50)
    }

    @Test
    fun migrationFromVersion3ThroughVersion14PreservesUserDataWithoutBackfillingCombat() = runTest {
        assertMigrationPreservesUserData(fromVersion = 3, totalXp = 250, expectedLevel = 3)
    }

    @Test
    fun migrationFromVersion4ThroughVersion14AddsDefaultLoadoutWithoutReplacingCharacterState() = runTest {
        assertMigrationPreservesUserData(fromVersion = 4, totalXp = 250, expectedLevel = 3)
    }

    @Test
    fun migrationFromVersion5ToVersion14PreservesAllSourceStateAndAddsFailureContract() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "m5_${System.nanoTime().toString(36)}.db"
        context.deleteDatabase(databaseName)
        createLegacyDatabase(context, databaseName, version = 5, totalXp = 250)

        var database: TodoQuestDatabase? = null
        try {
            database = Room.databaseBuilder(context, TodoQuestDatabase::class.java, databaseName)
                .addMigrations(
                    TodoQuestDatabase.MIGRATION_5_6,
                    TodoQuestDatabase.MIGRATION_6_7,
                    TodoQuestDatabase.MIGRATION_7_8,
                    TodoQuestDatabase.MIGRATION_8_9,
                    TodoQuestDatabase.MIGRATION_9_10,
                    TodoQuestDatabase.MIGRATION_10_11,
                    TodoQuestDatabase.MIGRATION_11_12,
                    TodoQuestDatabase.MIGRATION_12_13,
                    TodoQuestDatabase.MIGRATION_13_14,
                    TodoQuestDatabase.MIGRATION_14_15,
                )
                .allowMainThreadQueries()
                .build()

            assertNotNull(database.todoTaskDao().getActiveById(7L))
            assertNotNull(database.completionLogDao().find(7L, OCCURRENCE_EPOCH_DAY))
            assertNotNull(database.rewardLedgerDao().find(7L, OCCURRENCE_EPOCH_DAY))
            assertEquals(250L, database.characterProfileDao().getProfile()?.totalXp)
            assertEquals(122, database.characterProfileDao().getCurrentState()?.currentHp)
            assertEquals("hair_default", database.characterProfileDao().getAppearance()?.hairId)
            assertEquals(
                "top_default",
                database.characterProfileDao().getEquippedItems()?.topId,
            )
            assertEquals(73, database.combatDao().getMonsterInstance(21L)?.currentHp)
            assertEquals(21L, database.combatDao().getCombatProgress()?.activeMonsterInstanceId)
            assertEquals(
                "PENDING",
                database.combatDao().getPlayerAttackEvent(7L, OCCURRENCE_EPOCH_DAY + 1)?.status,
            )
            val monsterEvent = database.combatDao()
                .getMonsterAttackEvent(7L, OCCURRENCE_EPOCH_DAY + 2)
            assertEquals("APPLIED", monsterEvent?.status)
            assertEquals("MISSED_DEADLINE", monsterEvent?.trigger)
            val backfilledFailure = database.failureLogDao()
                .find(7L, OCCURRENCE_EPOCH_DAY + 2)
            assertNotNull(backfilledFailure)
            assertEquals(7L, backfilledFailure!!.recurrenceSeriesId)
            assertEquals(4_000L, backfilledFailure.failedAtEpochMillis)
            assertNull(database.failureLogDao().find(7L, OCCURRENCE_EPOCH_DAY + 3))

            val sqliteDatabase = database.openHelper.writableDatabase
            assertEquals(
                setOf("index_failure_logs_taskId_occurrenceDateEpochDay"),
                sqliteDatabase.indexNames("failure_logs"),
            )
            assertEquals(
                "'MISSED_DEADLINE'",
                sqliteDatabase.columnDefaultValue("monster_attack_events", "trigger"),
            )
        } finally {
            database?.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migrationFromVersion6ToVersion14PreservesSourceStateAndAddsEmptyEquipmentTables() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val databaseName = "m6_${System.nanoTime().toString(36)}.db"
            context.deleteDatabase(databaseName)
            createVersion6Database(context, databaseName, totalXp = 250)

            val legacyDatabase = context.openOrCreateDatabase(
                databaseName,
                Context.MODE_PRIVATE,
                null,
            )
            try {
                assertEquals(6, legacyDatabase.version)
                assertEquals(
                    0,
                    legacyDatabase.rawQuery(
                        "SELECT COUNT(*) FROM sqlite_master " +
                            "WHERE type = 'table' AND name = 'equipment'",
                        null,
                    ).use { cursor ->
                        check(cursor.moveToFirst())
                        cursor.getInt(0)
                    },
                )
                assertEquals(
                    0,
                    legacyDatabase.rawQuery(
                        """
                        SELECT COUNT(*)
                        FROM character_equipped_items
                        WHERE headId = 'ARMOR'
                           OR topId = 'ARMOR'
                           OR bottomId = 'ARMOR'
                           OR shoesId = 'ARMOR'
                           OR accessoryId = 'ARMOR'
                           OR weaponId = 'ARMOR'
                        """.trimIndent(),
                        null,
                    ).use { cursor ->
                        check(cursor.moveToFirst())
                        cursor.getInt(0)
                    },
                )
            } finally {
                legacyDatabase.close()
            }

            var database: TodoQuestDatabase? = null
            try {
                database = Room.databaseBuilder(
                    context,
                    TodoQuestDatabase::class.java,
                    databaseName,
                )
                    .addMigrations(
                        TodoQuestDatabase.MIGRATION_6_7,
                        TodoQuestDatabase.MIGRATION_7_8,
                        TodoQuestDatabase.MIGRATION_8_9,
                        TodoQuestDatabase.MIGRATION_9_10,
                        TodoQuestDatabase.MIGRATION_10_11,
                        TodoQuestDatabase.MIGRATION_11_12,
                        TodoQuestDatabase.MIGRATION_12_13,
                        TodoQuestDatabase.MIGRATION_13_14,
                        TodoQuestDatabase.MIGRATION_14_15,
                    )
                    .allowMainThreadQueries()
                    .build()

                assertNotNull(database.todoTaskDao().getActiveById(7L))
                assertNotNull(database.completionLogDao().find(7L, OCCURRENCE_EPOCH_DAY))
                assertNotNull(database.rewardLedgerDao().find(7L, OCCURRENCE_EPOCH_DAY))
                assertEquals(250L, database.characterProfileDao().getProfile()?.totalXp)
                assertEquals(456L, database.characterProfileDao().getProfile()?.currentGold)
                assertEquals(122, database.characterProfileDao().getCurrentState()?.currentHp)
                assertEquals("hair_default", database.characterProfileDao().getAppearance()?.hairId)
                assertEquals(
                    "top_default",
                    database.characterProfileDao().getEquippedItems()?.topId,
                )
                assertEquals(
                    "bottom_default",
                    database.characterProfileDao().getEquippedItems()?.bottomId,
                )
                assertEquals(73, database.combatDao().getMonsterInstance(21L)?.currentHp)
                assertEquals(21L, database.combatDao().getCombatProgress()?.activeMonsterInstanceId)
                assertNotNull(
                    database.combatDao()
                        .getPlayerAttackEvent(7L, OCCURRENCE_EPOCH_DAY + 1),
                )
                assertNotNull(
                    database.combatDao()
                        .getMonsterAttackEvent(7L, OCCURRENCE_EPOCH_DAY + 2),
                )
                assertNotNull(
                    database.failureLogDao().find(7L, OCCURRENCE_EPOCH_DAY + 2),
                )

                val sqliteDatabase = database.openHelper.writableDatabase
                assertEquals(0, sqliteDatabase.rowCount("equipment"))
                assertEquals(0, sqliteDatabase.rowCount("equipment_modifiers"))
                assertEquals(0, sqliteDatabase.rowCount("owned_equipment"))
                assertEquals(0, sqliteDatabase.rowCount("character_equipment"))
                assertEquals(
                    setOf(
                        "id",
                        "characterId",
                        "equipmentId",
                        "acquiredAtEpochMillis",
                    ),
                    sqliteDatabase.columnNames("owned_equipment"),
                )
                assertEquals(
                    setOf("characterId", "slot", "ownedEquipmentId"),
                    sqliteDatabase.columnNames("character_equipment"),
                )
                assertEquals(
                    0,
                    sqliteDatabase.query(
                        "SELECT COUNT(*) FROM equipment WHERE slot = 'ARMOR'",
                    ).use { cursor ->
                        check(cursor.moveToFirst())
                        cursor.getInt(0)
                    },
                )
            } finally {
                database?.close()
                context.deleteDatabase(databaseName)
            }
        }

    @Test
    fun migrationFromVersion7ToVersion8BackfillsOnlyMissingAutomaticDeadlineFailures() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "m7_${System.nanoTime().toString(36)}.db"
        context.deleteDatabase(databaseName)
        createVersion7Database(context, databaseName, totalXp = 250)

        var database: TodoQuestDatabase? = null
        try {
            database = Room.databaseBuilder(
                context,
                TodoQuestDatabase::class.java,
                databaseName,
            )
                .addMigrations(
                    TodoQuestDatabase.MIGRATION_7_8,
                    TodoQuestDatabase.MIGRATION_8_9,
                    TodoQuestDatabase.MIGRATION_9_10,
                    TodoQuestDatabase.MIGRATION_10_11,
                    TodoQuestDatabase.MIGRATION_11_12,
                    TodoQuestDatabase.MIGRATION_12_13,
                    TodoQuestDatabase.MIGRATION_13_14,
                    TodoQuestDatabase.MIGRATION_14_15,
                )
                .allowMainThreadQueries()
                .build()

            val appliedFailure = database.failureLogDao()
                .find(7L, OCCURRENCE_EPOCH_DAY + 2)
            assertNotNull(appliedFailure)
            assertEquals(7L, appliedFailure!!.recurrenceSeriesId)
            assertEquals(4_000L, appliedFailure.failedAtEpochMillis)

            val skippedFailure = database.failureLogDao()
                .find(7L, OCCURRENCE_EPOCH_DAY + 3)
            assertNotNull(skippedFailure)
            assertEquals(7L, skippedFailure!!.recurrenceSeriesId)
            assertEquals(4_100L, skippedFailure.failedAtEpochMillis)

            assertNull(
                database.failureLogDao().find(7L, OCCURRENCE_EPOCH_DAY + 4),
            )
            assertNull(
                database.failureLogDao().find(7L, OCCURRENCE_EPOCH_DAY),
            )

            val existingFailure = database.failureLogDao()
                .find(7L, OCCURRENCE_EPOCH_DAY + 5)
            assertNotNull(existingFailure)
            assertEquals(700L, existingFailure!!.recurrenceSeriesId)
            assertEquals(4_500L, existingFailure.failedAtEpochMillis)
            val sqliteDatabase = database.openHelper.writableDatabase
            assertEquals(3, sqliteDatabase.rowCount("failure_logs"))

            TodoQuestDatabase.MIGRATION_7_8.migrate(sqliteDatabase)
            assertEquals(3, sqliteDatabase.rowCount("failure_logs"))
        } finally {
            database?.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migrationFromVersion8ToVersion14KeepsLegacyRewardsAndAttacksUnrewarded() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "m8_${System.nanoTime().toString(36)}.db"
        context.deleteDatabase(databaseName)
        createVersion8Database(context, databaseName, totalXp = 250)

        var database: TodoQuestDatabase? = null
        try {
            database = Room.databaseBuilder(
                context,
                TodoQuestDatabase::class.java,
                databaseName,
            )
                .addMigrations(
                    TodoQuestDatabase.MIGRATION_8_9,
                    TodoQuestDatabase.MIGRATION_9_10,
                    TodoQuestDatabase.MIGRATION_10_11,
                    TodoQuestDatabase.MIGRATION_11_12,
                    TodoQuestDatabase.MIGRATION_12_13,
                    TodoQuestDatabase.MIGRATION_13_14,
                    TodoQuestDatabase.MIGRATION_14_15,
                )
                .allowMainThreadQueries()
                .build()

            val reward = database.rewardLedgerDao().find(7L, OCCURRENCE_EPOCH_DAY)
            val attack = database.combatDao()
                .getPlayerAttackEvent(7L, OCCURRENCE_EPOCH_DAY + 1)

            assertEquals("TODO_COMPLETION", reward?.rewardMode)
            assertEquals(35L, reward?.xpAward)
            assertEquals(20L, reward?.goldAward)
            assertEquals(0, attack?.combatRewardVersion)
            assertEquals(0L, attack?.hitXpAward)
            assertEquals(0L, attack?.killBonusXpAward)
            assertEquals(0L, attack?.killGoldAward)
            assertEquals(250L, database.characterProfileDao().getProfile()?.totalXp)
            assertEquals(456L, database.characterProfileDao().getProfile()?.currentGold)
        } finally {
            database?.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migrationFromVersion9ToVersion14AddsEmptyReminderTableAndReopens() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "m9_${System.nanoTime().toString(36)}.db"
        context.deleteDatabase(databaseName)
        createVersion9Database(context, databaseName, totalXp = 250)

        val preservedTables = listOf(
            "todo_tasks",
            "completion_logs",
            "failure_logs",
            "reward_ledger",
            "character_profile",
            "character_current_state",
            "character_appearance",
            "character_equipped_items",
            "monster_instances",
            "combat_progress",
            "player_attack_events",
            "monster_attack_events",
            "equipment",
            "equipment_modifiers",
            "owned_equipment",
            "character_equipment",
        )
        val beforeCounts = context.openOrCreateDatabase(
            databaseName,
            Context.MODE_PRIVATE,
            null,
        ).use { sqliteDatabase ->
            preservedTables.associateWith { tableName -> sqliteDatabase.rowCount(tableName) }
        }

        var database: TodoQuestDatabase? = null
        try {
            database = Room.databaseBuilder(
                context,
                TodoQuestDatabase::class.java,
                databaseName,
            )
                .addMigrations(
                    TodoQuestDatabase.MIGRATION_9_10,
                    TodoQuestDatabase.MIGRATION_10_11,
                    TodoQuestDatabase.MIGRATION_11_12,
                    TodoQuestDatabase.MIGRATION_12_13,
                    TodoQuestDatabase.MIGRATION_13_14,
                    TodoQuestDatabase.MIGRATION_14_15,
                )
                .allowMainThreadQueries()
                .build()

            val sqliteDatabase = database.openHelper.writableDatabase
            assertEquals(15, sqliteDatabase.version)
            assertEquals(
                beforeCounts,
                preservedTables.associateWith { tableName -> sqliteDatabase.rowCount(tableName) },
            )
            assertEquals(0, sqliteDatabase.rowCount("task_reminders"))
            assertEquals(
                setOf(
                    "taskId",
                    "mode",
                    "customTimeMinuteOfDay",
                    "scheduleStatus",
                    "scheduledOccurrenceEpochDay",
                    "scheduledTriggerAtEpochMillis",
                    "updatedAtEpochMillis",
                ),
                sqliteDatabase.columnNames("task_reminders"),
            )
            assertEquals(
                setOf(
                    "index_task_reminders_mode",
                    "index_task_reminders_scheduleStatus",
                ),
                sqliteDatabase.indexNames("task_reminders"),
            )
            sqliteDatabase.query("PRAGMA foreign_key_list(`task_reminders`)").use { cursor ->
                assertEquals(1, cursor.count)
                check(cursor.moveToFirst())
                assertEquals("todo_tasks", cursor.getString(cursor.getColumnIndexOrThrow("table")))
                assertEquals("taskId", cursor.getString(cursor.getColumnIndexOrThrow("from")))
                assertEquals("id", cursor.getString(cursor.getColumnIndexOrThrow("to")))
                assertEquals("NO ACTION", cursor.getString(cursor.getColumnIndexOrThrow("on_update")))
                assertEquals("CASCADE", cursor.getString(cursor.getColumnIndexOrThrow("on_delete")))
            }
            assertNotNull(database.todoTaskDao().getActiveById(7L))
            assertNotNull(database.completionLogDao().find(7L, OCCURRENCE_EPOCH_DAY))
            assertNotNull(database.rewardLedgerDao().find(7L, OCCURRENCE_EPOCH_DAY))
            assertEquals(250L, database.characterProfileDao().getProfile()?.totalXp)
            assertNotNull(database.combatDao().getMonsterInstance(21L))
        } finally {
            database?.close()
        }

        try {
            database = Room.databaseBuilder(
                context,
                TodoQuestDatabase::class.java,
                databaseName,
            )
                .addMigrations(
                    TodoQuestDatabase.MIGRATION_9_10,
                    TodoQuestDatabase.MIGRATION_10_11,
                    TodoQuestDatabase.MIGRATION_11_12,
                    TodoQuestDatabase.MIGRATION_12_13,
                    TodoQuestDatabase.MIGRATION_13_14,
                    TodoQuestDatabase.MIGRATION_14_15,
                )
                .allowMainThreadQueries()
                .build()

            assertEquals(15, database.openHelper.writableDatabase.version)
            assertEquals(0, database.taskReminderDao().getConfiguredReminders().size)
            assertNotNull(database.todoTaskDao().getActiveById(7L))
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migrationFromVersion10ToVersion14AddsNullableGlovesAndPreservesEverySourceTable() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val databaseName = "m10_${System.nanoTime().toString(36)}.db"
            context.deleteDatabase(databaseName)
            createVersion10Database(context, databaseName, totalXp = 250)

            val preservedTables = listOf(
                "todo_tasks",
                "completion_logs",
                "failure_logs",
                "reward_ledger",
                "character_profile",
                "character_current_state",
                "character_appearance",
                "character_equipped_items",
                "monster_instances",
                "combat_progress",
                "player_attack_events",
                "monster_attack_events",
                "equipment",
                "equipment_modifiers",
                "owned_equipment",
                "character_equipment",
                "task_reminders",
            )
            val legacyDatabase = context.openOrCreateDatabase(
                databaseName,
                Context.MODE_PRIVATE,
                null,
            )
            val beforeCounts: Map<String, Int>
            val beforeIndexes: Map<String, Set<String>>
            try {
                assertEquals(10, legacyDatabase.version)
                assertFalse("glovesId" in legacyDatabase.columnNames("character_equipped_items"))
                beforeCounts = preservedTables.associateWith { tableName ->
                    legacyDatabase.rowCount(tableName)
                }
                beforeIndexes = preservedTables.associateWith { tableName ->
                    legacyDatabase.indexNames(tableName)
                }
            } finally {
                legacyDatabase.close()
            }

            var database: TodoQuestDatabase? = null
            try {
                database = Room.databaseBuilder(
                    context,
                    TodoQuestDatabase::class.java,
                    databaseName,
                )
                    .addMigrations(
                        TodoQuestDatabase.MIGRATION_10_11,
                        TodoQuestDatabase.MIGRATION_11_12,
                        TodoQuestDatabase.MIGRATION_12_13,
                        TodoQuestDatabase.MIGRATION_13_14,
                        TodoQuestDatabase.MIGRATION_14_15,
                    )
                    .allowMainThreadQueries()
                    .build()

                val sqliteDatabase = database.openHelper.writableDatabase
                assertEquals(15, sqliteDatabase.version)
                assertEquals(
                    beforeCounts,
                    preservedTables.associateWith { tableName ->
                        sqliteDatabase.rowCount(tableName)
                    },
                )
                assertEquals(
                    beforeIndexes,
                    preservedTables.associateWith { tableName ->
                        sqliteDatabase.indexNames(tableName)
                    },
                )
                assertEquals(
                    setOf(
                        "characterId",
                        "headId",
                        "topId",
                        "bottomId",
                        "shoesId",
                        "accessoryId",
                        "weaponId",
                        "glovesId",
                    ),
                    sqliteDatabase.columnNames("character_equipped_items"),
                )
                assertFalse(sqliteDatabase.columnIsNotNull("character_equipped_items", "glovesId"))
                assertNull(
                    sqliteDatabase.columnDefaultValue("character_equipped_items", "glovesId"),
                )

                val equippedItems = database.characterProfileDao().getEquippedItems()
                assertNotNull(equippedItems)
                assertNull(equippedItems?.headId)
                assertEquals("top_default", equippedItems?.topId)
                assertEquals("bottom_default", equippedItems?.bottomId)
                assertEquals("shoes_default", equippedItems?.shoesId)
                assertNull(equippedItems?.accessoryId)
                assertNull(equippedItems?.weaponId)
                assertNull(equippedItems?.glovesId)

                assertNotNull(database.todoTaskDao().getActiveById(7L))
                assertNotNull(database.completionLogDao().find(7L, OCCURRENCE_EPOCH_DAY))
                assertNotNull(database.rewardLedgerDao().find(7L, OCCURRENCE_EPOCH_DAY))
                assertNotNull(database.combatDao().getMonsterInstance(21L))
                assertEquals(1, sqliteDatabase.rowCount("equipment"))
                assertEquals(1, sqliteDatabase.rowCount("equipment_modifiers"))
                assertEquals(1, sqliteDatabase.rowCount("owned_equipment"))
                assertEquals(1, sqliteDatabase.rowCount("character_equipment"))
                assertEquals(1, sqliteDatabase.rowCount("task_reminders"))
                sqliteDatabase.query(
                    "SELECT mode, scheduleStatus FROM task_reminders WHERE taskId = 7",
                ).use { cursor ->
                    assertEquals(1, cursor.count)
                    check(cursor.moveToFirst())
                    assertEquals("CUSTOM_TIME", cursor.getString(0))
                    assertEquals("SCHEDULED", cursor.getString(1))
                }
            } finally {
                database?.close()
                context.deleteDatabase(databaseName)
            }
        }

    @Test
    fun migrationFromVersion11ToVersion14BackfillsWeaponsAndPreservesAllExistingSources() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val databaseName = "m11_${System.nanoTime().toString(36)}.db"
            context.deleteDatabase(databaseName)
            createVersion11Database(context, databaseName, totalXp = 250)

            val preservedTables = listOf(
                "todo_tasks",
                "completion_logs",
                "failure_logs",
                "reward_ledger",
                "character_profile",
                "character_current_state",
                "character_appearance",
                "character_equipped_items",
                "monster_instances",
                "combat_progress",
                "player_attack_events",
                "monster_attack_events",
                "equipment",
                "equipment_modifiers",
                "owned_equipment",
                "character_equipment",
                "task_reminders",
            )
            val legacyDatabase = context.openOrCreateDatabase(
                databaseName,
                Context.MODE_PRIVATE,
                null,
            )
            val beforeCounts: Map<String, Int>
            val beforeIndexes: Map<String, Set<String>>
            try {
                assertEquals(11, legacyDatabase.version)
                assertFalse("weaponType" in legacyDatabase.columnNames("equipment"))
                beforeCounts = preservedTables.associateWith { tableName ->
                    legacyDatabase.rowCount(tableName)
                }
                beforeIndexes = preservedTables.associateWith { tableName ->
                    legacyDatabase.indexNames(tableName)
                }
            } finally {
                legacyDatabase.close()
            }

            var database: TodoQuestDatabase? = null
            try {
                database = Room.databaseBuilder(
                    context,
                    TodoQuestDatabase::class.java,
                    databaseName,
                )
                    .addMigrations(
                        TodoQuestDatabase.MIGRATION_11_12,
                        TodoQuestDatabase.MIGRATION_12_13,
                        TodoQuestDatabase.MIGRATION_13_14,
                        TodoQuestDatabase.MIGRATION_14_15,
                    )
                    .allowMainThreadQueries()
                    .build()

                val sqliteDatabase = database.openHelper.writableDatabase
                assertEquals(15, sqliteDatabase.version)
                assertEquals(
                    beforeCounts,
                    preservedTables.associateWith { tableName ->
                        sqliteDatabase.rowCount(tableName)
                    },
                )
                assertEquals(
                    beforeIndexes,
                    preservedTables.associateWith { tableName ->
                        sqliteDatabase.indexNames(tableName)
                    },
                )
                assertEquals(
                    setOf(
                        "id",
                        "nameKey",
                        "descriptionKey",
                        "type",
                        "slot",
                        "rarity",
                        "price",
                        "requiredLevel",
                        "imageKey",
                        "layerKey",
                        "isForSale",
                        "weaponType",
                    ),
                    sqliteDatabase.columnNames("equipment"),
                )
                assertFalse(sqliteDatabase.columnIsNotNull("equipment", "weaponType"))
                assertNull(sqliteDatabase.columnDefaultValue("equipment", "weaponType"))

                val weapon = database.equipmentDao().getEquipment(5_002L)
                assertNotNull(weapon)
                assertEquals("LONGSWORD", weapon?.weaponType)
                assertEquals("legacy_weapon_image", weapon?.imageKey)
                assertEquals("legacy_weapon_layer", weapon?.layerKey)
                assertFalse(weapon?.isForSale ?: true)
                assertNull(database.equipmentDao().getEquipment(5_001L)?.weaponType)
                assertEquals(2, database.equipmentDao().getAllEquipmentModifiers().size)
                assertEquals(
                    setOf(6_001L, 6_002L),
                    database.equipmentDao().getOwnedEquipment(1L).map { it.id }.toSet(),
                )
                assertEquals(
                    mapOf(
                        "GLOVES" to 6_001L,
                        "WEAPON" to 6_002L,
                    ),
                    database.equipmentDao().getCharacterEquipment(1L)
                        .associate { it.slot to it.ownedEquipmentId },
                )

                val fallback = database.characterProfileDao().getEquippedItems()
                assertNotNull(fallback)
                assertNull(fallback?.weaponId)
                assertNull(fallback?.glovesId)
                assertNotNull(database.todoTaskDao().getActiveById(7L))
                assertNotNull(database.rewardLedgerDao().find(7L, OCCURRENCE_EPOCH_DAY))
                assertNotNull(database.combatDao().getMonsterInstance(21L))
                assertEquals(1, sqliteDatabase.rowCount("task_reminders"))
            } finally {
                database?.close()
                context.deleteDatabase(databaseName)
            }
        }

    private suspend fun assertMigrationPreservesUserData(
        fromVersion: Int,
        totalXp: Int,
        expectedLevel: Int,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "m${fromVersion}_${System.nanoTime().toString(36)}.db"
        context.deleteDatabase(databaseName)
        createLegacyDatabase(context, databaseName, fromVersion, totalXp)

        var database: TodoQuestDatabase? = null
        try {
            database = Room.databaseBuilder(
                context,
                TodoQuestDatabase::class.java,
                databaseName,
            )
                .addMigrations(
                    TodoQuestDatabase.MIGRATION_1_2,
                    TodoQuestDatabase.MIGRATION_2_3,
                    TodoQuestDatabase.MIGRATION_3_4,
                    TodoQuestDatabase.MIGRATION_4_5,
                    TodoQuestDatabase.MIGRATION_5_6,
                    TodoQuestDatabase.MIGRATION_6_7,
                    TodoQuestDatabase.MIGRATION_7_8,
                    TodoQuestDatabase.MIGRATION_8_9,
                    TodoQuestDatabase.MIGRATION_9_10,
                    TodoQuestDatabase.MIGRATION_10_11,
                    TodoQuestDatabase.MIGRATION_11_12,
                    TodoQuestDatabase.MIGRATION_12_13,
                    TodoQuestDatabase.MIGRATION_13_14,
                    TodoQuestDatabase.MIGRATION_14_15,
                )
                .allowMainThreadQueries()
                .build()

            val task = database.todoTaskDao().getActiveById(7L)
            val completion = database.completionLogDao().find(7L, OCCURRENCE_EPOCH_DAY)
            val reward = database.rewardLedgerDao().find(7L, OCCURRENCE_EPOCH_DAY)
            val orphanReward = database.rewardLedgerDao().find(99L, OCCURRENCE_EPOCH_DAY + 1)
            val profile = database.characterProfileDao().getProfile()
            val currentState = database.characterProfileDao().getCurrentState()
            val appearance = database.characterProfileDao().getAppearance()
            val equippedItems = database.characterProfileDao().getEquippedItems()

            assertNotNull(task)
            assertEquals(7L, task!!.recurrenceSeriesId)
            assertNotNull(completion)
            assertEquals(COMPLETED_AT_MILLIS, completion!!.completedAtEpochMillis)

            assertNotNull(reward)
            assertEquals(7L, reward!!.recurrenceSeriesId)
            assertEquals(35L, reward.xpAward)
            assertEquals(20L, reward.goldAward)
            assertEquals(OCCURRENCE_EPOCH_DAY, reward.rewardLocalDateEpochDay)
            assertFalse(reward.onTime)
            assertEquals(10_000, reward.onTimeMultiplierBp)
            assertEquals(10_000, reward.rewardEfficiencyBp)
            assertEquals(0, reward.repeatOrdinal)
            assertEquals(0, reward.dailyOrdinal)
            assertEquals(0, reward.goldGainBonusBp)
            assertFalse(reward.combatEligible)
            assertEquals(0, reward.balanceVersion)
            assertEquals("TODO_COMPLETION", reward.rewardMode)
            assertEquals(AWARDED_AT_MILLIS, reward.awardedAtEpochMillis)

            assertNotNull(orphanReward)
            assertEquals(99L, orphanReward!!.recurrenceSeriesId)

            assertNotNull(profile)
            assertEquals(totalXp.toLong(), profile!!.totalXp)
            assertEquals(456L, profile.currentGold)
            assertEquals(5, profile.strength)
            assertEquals(5, profile.vitality)
            assertEquals(5, profile.focus)
            assertEquals(5, profile.willpower)
            assertEquals(2 * (expectedLevel - 1), profile.unspentStatPoints)
            assertFalse(profile.hasUsedFreeStatReset)

            assertNotNull(currentState)
            assertEquals(1L, currentState!!.characterId)
            assertEquals(110 + 6 * (expectedLevel - 1), currentState.currentHp)
            assertEquals(1, currentState.balanceVersion)
            assertEquals(0L, currentState.updatedAtEpochMillis)

            assertEquals(
                CharacterAppearanceEntity(characterId = 1L, hairId = "hair_default"),
                appearance,
            )
            assertEquals(
                CharacterEquippedItemsEntity(
                    characterId = 1L,
                    headId = null,
                    topId = "top_default",
                    bottomId = "bottom_default",
                    shoesId = "shoes_default",
                    accessoryId = null,
                    weaponId = null,
                    glovesId = null,
                ),
                equippedItems,
            )

            val profileColumns = database.openHelper.writableDatabase
                .query("PRAGMA table_info(`character_profile`)")
                .use { cursor ->
                    buildSet {
                        val nameIndex = cursor.getColumnIndexOrThrow("name")
                        while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                    }
                }
            assertFalse("level" in profileColumns)
            assertFalse("maxHp" in profileColumns)
            assertFalse("attack" in profileColumns)
            assertFalse("defense" in profileColumns)

            val sqliteDatabase = database.openHelper.writableDatabase
            val expectedCombatRows = if (fromVersion >= 4) 1 else 0
            assertEquals(expectedCombatRows, sqliteDatabase.rowCount("monster_instances"))
            assertEquals(expectedCombatRows, sqliteDatabase.rowCount("combat_progress"))
            assertEquals(expectedCombatRows, sqliteDatabase.rowCount("player_attack_events"))
            assertEquals(expectedCombatRows, sqliteDatabase.rowCount("monster_attack_events"))
            assertEquals(expectedCombatRows, sqliteDatabase.rowCount("failure_logs"))
            if (fromVersion >= 4) {
                assertEquals(73, database.combatDao().getMonsterInstance(21L)?.currentHp)
                val migratedPlayerAttack = database.combatDao()
                    .getPlayerAttackEvent(7L, OCCURRENCE_EPOCH_DAY + 1)
                assertEquals(0, migratedPlayerAttack?.combatRewardVersion)
                assertEquals(0L, migratedPlayerAttack?.hitXpAward)
                assertEquals(0L, migratedPlayerAttack?.killBonusXpAward)
                assertEquals(0L, migratedPlayerAttack?.killGoldAward)
                assertNull(migratedPlayerAttack?.sourceTaskDifficulty)
                assertEquals(0, migratedPlayerAttack?.taskDifficultyBalanceVersion)
                assertEquals(
                    "MISSED_DEADLINE",
                    database.combatDao()
                        .getMonsterAttackEvent(7L, OCCURRENCE_EPOCH_DAY + 2)
                        ?.trigger,
                )
                val failure = database.failureLogDao()
                    .find(7L, OCCURRENCE_EPOCH_DAY + 2)
                assertNotNull(failure)
                assertEquals(7L, failure!!.recurrenceSeriesId)
                assertEquals(4_000L, failure.failedAtEpochMillis)
            }

            val monsterColumns = sqliteDatabase.columnNames("monster_instances")
            assertFalse("maxHp" in monsterColumns)
            assertFalse("damage" in monsterColumns)
            assertFalse("defense" in monsterColumns)
            assertFalse("isDefeated" in monsterColumns)
            assertEquals(0, sqliteDatabase.rowCount("task_reminders"))
        } finally {
            database?.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun createLegacyDatabase(
        context: Context,
        databaseName: String,
        version: Int,
        totalXp: Int,
    ) {
        require(version in 1..5)
        val sqliteDatabase = context.openOrCreateDatabase(
            databaseName,
            Context.MODE_PRIVATE,
            null,
        )
        if (version >= 3) {
            createVersion3Database(sqliteDatabase, totalXp)
            if (version >= 4) {
                sqliteDatabase.close()
                upgradeVersion3Database(context, databaseName, version)
                insertVersion4CombatSource(context, databaseName)
                return
            }
            sqliteDatabase.close()
            return
        }
        val endDateColumn = if (version == 2) "`endDateEpochDay` INTEGER," else ""
        val endDateInsertColumn = if (version == 2) "`endDateEpochDay`," else ""
        val endDateInsertValue = if (version == 2) "NULL," else ""
        try {
            sqliteDatabase.execSQL(
                """
                CREATE TABLE `todo_tasks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `memo` TEXT NOT NULL,
                    `startDateEpochDay` INTEGER NOT NULL,
                    $endDateColumn
                    `timeMinuteOfDay` INTEGER,
                    `difficulty` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `recurrenceRule` TEXT NOT NULL,
                    `createdAtEpochMillis` INTEGER NOT NULL,
                    `updatedAtEpochMillis` INTEGER NOT NULL,
                    `deletedAtEpochMillis` INTEGER
                )
                """.trimIndent(),
            )
            sqliteDatabase.execSQL(
                "CREATE INDEX `index_todo_tasks_startDateEpochDay` " +
                    "ON `todo_tasks` (`startDateEpochDay`)",
            )
            sqliteDatabase.execSQL(
                "CREATE INDEX `index_todo_tasks_deletedAtEpochMillis` " +
                    "ON `todo_tasks` (`deletedAtEpochMillis`)",
            )
            sqliteDatabase.execSQL(
                """
                CREATE TABLE `completion_logs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `taskId` INTEGER NOT NULL,
                    `occurrenceDateEpochDay` INTEGER NOT NULL,
                    `completedAtEpochMillis` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            sqliteDatabase.execSQL(
                "CREATE UNIQUE INDEX `index_completion_logs_taskId_occurrenceDateEpochDay` " +
                    "ON `completion_logs` (`taskId`, `occurrenceDateEpochDay`)",
            )
            sqliteDatabase.execSQL(
                """
                CREATE TABLE `reward_ledger` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `taskId` INTEGER NOT NULL,
                    `occurrenceDateEpochDay` INTEGER NOT NULL,
                    `xp` INTEGER NOT NULL,
                    `gold` INTEGER NOT NULL,
                    `awardedAtEpochMillis` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            sqliteDatabase.execSQL(
                "CREATE UNIQUE INDEX `index_reward_ledger_taskId_occurrenceDateEpochDay` " +
                    "ON `reward_ledger` (`taskId`, `occurrenceDateEpochDay`)",
            )
            sqliteDatabase.execSQL(
                """
                CREATE TABLE `character_profile` (
                    `id` INTEGER NOT NULL,
                    `level` INTEGER NOT NULL,
                    `totalXp` INTEGER NOT NULL,
                    `currentGold` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            sqliteDatabase.execSQL(
                """
                INSERT INTO `todo_tasks` (
                    `id`, `title`, `memo`, `startDateEpochDay`, $endDateInsertColumn
                    `timeMinuteOfDay`, `difficulty`, `category`, `recurrenceRule`,
                    `createdAtEpochMillis`, `updatedAtEpochMillis`, `deletedAtEpochMillis`
                ) VALUES (
                    7, 'Legacy recurring quest', '', $OCCURRENCE_EPOCH_DAY, $endDateInsertValue
                    NULL, 'HARD', 'General', 'DAILY', 10, 20, NULL
                )
                """.trimIndent(),
            )
            sqliteDatabase.execSQL(
                "INSERT INTO `completion_logs` VALUES " +
                    "(3, 7, $OCCURRENCE_EPOCH_DAY, $COMPLETED_AT_MILLIS)",
            )
            sqliteDatabase.execSQL(
                "INSERT INTO `reward_ledger` VALUES " +
                    "(11, 7, $OCCURRENCE_EPOCH_DAY, 35, 20, $AWARDED_AT_MILLIS)",
            )
            sqliteDatabase.execSQL(
                "INSERT INTO `reward_ledger` VALUES " +
                    "(12, 99, ${OCCURRENCE_EPOCH_DAY + 1}, 10, 5, ${AWARDED_AT_MILLIS + 1})",
            )
            sqliteDatabase.execSQL(
                "INSERT INTO `character_profile` VALUES (1, 42, $totalXp, 456)",
            )
            sqliteDatabase.execSQL("PRAGMA user_version = $version")
        } finally {
            sqliteDatabase.close()
        }
    }

    private fun upgradeVersion3Database(
        context: Context,
        databaseName: String,
        targetVersion: Int,
    ) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(targetVersion) {
                        override fun onCreate(db: SupportSQLiteDatabase) =
                            error("version 3 database must already exist")

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) {
                            check(oldVersion == 3 && newVersion == targetVersion)
                            TodoQuestDatabase.MIGRATION_3_4.migrate(db)
                            if (targetVersion >= 5) TodoQuestDatabase.MIGRATION_4_5.migrate(db)
                        }
                    },
                )
                .build(),
        )
        try {
            helper.writableDatabase
        } finally {
            helper.close()
        }
    }

    private fun createVersion6Database(
        context: Context,
        databaseName: String,
        totalXp: Int,
    ) {
        createLegacyDatabase(context, databaseName, version = 5, totalXp = totalXp)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(6) {
                        override fun onCreate(db: SupportSQLiteDatabase) =
                            error("version 5 database must already exist")

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) {
                            check(oldVersion == 5 && newVersion == 6)
                            TodoQuestDatabase.MIGRATION_5_6.migrate(db)
                        }
                    },
                )
                .build(),
        )
        try {
            helper.writableDatabase
        } finally {
            helper.close()
        }
    }

    private fun createVersion7Database(
        context: Context,
        databaseName: String,
        totalXp: Int,
    ) {
        createVersion6Database(context, databaseName, totalXp)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(7) {
                        override fun onCreate(db: SupportSQLiteDatabase) =
                            error("version 6 database must already exist")

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) {
                            check(oldVersion == 6 && newVersion == 7)
                            TodoQuestDatabase.MIGRATION_6_7.migrate(db)
                        }
                    },
                )
                .build(),
        )
        try {
            helper.writableDatabase
        } finally {
            helper.close()
        }
        insertVersion7FailureBackfillSource(context, databaseName)
    }

    private fun createVersion8Database(
        context: Context,
        databaseName: String,
        totalXp: Int,
    ) {
        createVersion7Database(context, databaseName, totalXp)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(8) {
                        override fun onCreate(db: SupportSQLiteDatabase) =
                            error("version 7 database must already exist")

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) {
                            check(oldVersion == 7 && newVersion == 8)
                            TodoQuestDatabase.MIGRATION_7_8.migrate(db)
                        }
                    },
                )
                .build(),
        )
        try {
            helper.writableDatabase
        } finally {
            helper.close()
        }
    }

    private fun createVersion9Database(
        context: Context,
        databaseName: String,
        totalXp: Int,
    ) {
        createVersion8Database(context, databaseName, totalXp)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(9) {
                        override fun onCreate(db: SupportSQLiteDatabase) =
                            error("version 8 database must already exist")

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) {
                            check(oldVersion == 8 && newVersion == 9)
                            TodoQuestDatabase.MIGRATION_8_9.migrate(db)
                        }
                    },
                )
                .build(),
        )
        try {
            helper.writableDatabase
        } finally {
            helper.close()
        }
    }

    private fun createVersion10Database(
        context: Context,
        databaseName: String,
        totalXp: Int,
    ) {
        createVersion9Database(context, databaseName, totalXp)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(10) {
                        override fun onCreate(db: SupportSQLiteDatabase) =
                            error("version 9 database must already exist")

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) {
                            check(oldVersion == 9 && newVersion == 10)
                            TodoQuestDatabase.MIGRATION_9_10.migrate(db)
                        }
                    },
                )
                .build(),
        )
        try {
            helper.writableDatabase
        } finally {
            helper.close()
        }

        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { database ->
            database.execSQL(
                """
                UPDATE character_equipped_items
                SET headId = NULL,
                    accessoryId = NULL,
                    weaponId = NULL
                WHERE characterId = 1
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO equipment (
                    id, nameKey, descriptionKey, type, slot, rarity, price,
                    requiredLevel, imageKey, layerKey, isForSale
                ) VALUES (
                    5001, 'legacy_gloves', 'legacy_gloves_description',
                    'GLOVES', 'GLOVES', 'COMMON', 25, 1, NULL, NULL, 1
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO equipment_modifiers (
                    equipmentId, sortOrder, targetKind, targetStat, modifierType, amount
                ) VALUES (5001, 0, 'DERIVED_STAT', 'DEFENSE', 'FLAT', 1)
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO owned_equipment (
                    id, characterId, equipmentId, acquiredAtEpochMillis
                ) VALUES (6001, 1, 5001, 4700)
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO character_equipment (
                    characterId, slot, ownedEquipmentId
                ) VALUES (1, 'GLOVES', 6001)
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO task_reminders (
                    taskId,
                    mode,
                    customTimeMinuteOfDay,
                    scheduleStatus,
                    scheduledOccurrenceEpochDay,
                    scheduledTriggerAtEpochMillis,
                    updatedAtEpochMillis
                ) VALUES (
                    7,
                    'CUSTOM_TIME',
                    480,
                    'SCHEDULED',
                    ${OCCURRENCE_EPOCH_DAY + 10},
                    5000,
                    4900
                )
                """.trimIndent(),
            )
        }
    }

    private fun createVersion11Database(
        context: Context,
        databaseName: String,
        totalXp: Int,
    ) {
        createVersion10Database(context, databaseName, totalXp)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(11) {
                        override fun onCreate(db: SupportSQLiteDatabase) =
                            error("version 10 database must already exist")

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) {
                            check(oldVersion == 10 && newVersion == 11)
                            TodoQuestDatabase.MIGRATION_10_11.migrate(db)
                        }
                    },
                )
                .build(),
        )
        try {
            helper.writableDatabase
        } finally {
            helper.close()
        }

        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { database ->
            database.execSQL(
                """
                INSERT INTO equipment (
                    id, nameKey, descriptionKey, type, slot, rarity, price,
                    requiredLevel, imageKey, layerKey, isForSale
                ) VALUES (
                    5002, 'legacy_weapon', 'legacy_weapon_description',
                    'WEAPON', 'WEAPON', 'RARE', 720, 12,
                    'legacy_weapon_image', 'legacy_weapon_layer', 0
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO equipment_modifiers (
                    equipmentId, sortOrder, targetKind, targetStat, modifierType, amount
                ) VALUES (5002, 0, 'DERIVED', 'ATTACK', 'FLAT', 10)
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO owned_equipment (
                    id, characterId, equipmentId, acquiredAtEpochMillis
                ) VALUES (6002, 1, 5002, 4800)
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO character_equipment (
                    characterId, slot, ownedEquipmentId
                ) VALUES (1, 'WEAPON', 6002)
                """.trimIndent(),
            )
        }
    }

    private fun createVersion12Database(
        context: Context,
        databaseName: String,
        totalXp: Int,
    ) {
        createVersion11Database(context, databaseName, totalXp)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(12) {
                        override fun onCreate(db: SupportSQLiteDatabase) =
                            error("version 11 database must already exist")

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) {
                            check(oldVersion == 11 && newVersion == 12)
                            TodoQuestDatabase.MIGRATION_11_12.migrate(db)
                        }
                    },
                )
                .build(),
        )
        try {
            helper.writableDatabase
        } finally {
            helper.close()
        }
    }

    private fun createVersion13Database(
        context: Context,
        databaseName: String,
        totalXp: Int,
    ) {
        createVersion12Database(context, databaseName, totalXp)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(13) {
                        override fun onCreate(db: SupportSQLiteDatabase) =
                            error("version 12 database must already exist")

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) {
                            check(oldVersion == 12 && newVersion == 13)
                            TodoQuestDatabase.MIGRATION_12_13.migrate(db)
                        }
                    },
                )
                .build(),
        )
        try {
            helper.writableDatabase
        } finally {
            helper.close()
        }
    }

    private fun createVersion14Database(
        context: Context,
        databaseName: String,
        totalXp: Int,
    ) {
        createVersion13Database(context, databaseName, totalXp)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(14) {
                        override fun onCreate(db: SupportSQLiteDatabase) =
                            error("version 13 database must already exist")

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) {
                            check(oldVersion == 13 && newVersion == 14)
                            TodoQuestDatabase.MIGRATION_13_14.migrate(db)
                        }
                    },
                )
                .build(),
        )
        try {
            helper.writableDatabase
        } finally {
            helper.close()
        }
    }

    private fun insertVersion14FallbackMigrationSources(
        context: Context,
        databaseName: String,
    ) {
        insertVersion13DifficultyMigrationSources(context, databaseName)
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { database ->
            database.execSQL(
                """
                UPDATE character_equipped_items
                SET headId = 'headgear_adventure',
                    topId = 'top_adventure',
                    bottomId = 'bottom_adventure',
                    shoesId = 'shoes_adventure',
                    accessoryId = 'accessory_adventure',
                    weaponId = 'weapon_default_sword',
                    glovesId = 'gloves_adventure'
                WHERE characterId = 1
                """.trimIndent(),
            )
        }
    }

    private fun insertVersion13DifficultyMigrationSources(
        context: Context,
        databaseName: String,
    ) {
        val database = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        try {
            database.execSQL(
                """
                INSERT INTO player_attack_events (
                    taskId,
                    occurrenceDateEpochDay,
                    recurrenceSeriesId,
                    status,
                    sourcePlayerLevel,
                    sourceAttack,
                    sourceCriticalChanceBp,
                    sourceCriticalDamageBp,
                    sourceMomentumBp,
                    characterBalanceVersion,
                    monsterBalanceVersion,
                    createdAtEpochMillis,
                    targetMonsterInstanceId,
                    seed,
                    roll,
                    wasCritical,
                    rawDamage,
                    targetDefense,
                    finalDamage,
                    targetHpBefore,
                    targetHpAfter,
                    processedAtEpochMillis,
                    combatRewardVersion,
                    hitXpAward,
                    killBonusXpAward,
                    killGoldAward,
                    rewardGradeMultiplierBp,
                    rewardGoldGainBonusBp
                ) VALUES (
                    7, ${OCCURRENCE_EPOCH_DAY + 6}, 7, 'APPLIED',
                    4, 41, 900, 16000, 400, 1, 1, 5000,
                    21, 99, 100, 1, 45, 7, 42, 73, 31, 6000,
                    2, 3, 20, 15, 10000, 0
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO character_status_effects (
                    characterId,
                    effectType,
                    definitionVersion,
                    appliedAtEpochMillis,
                    expiresAtEpochMillis,
                    remainingRecoveryCompletions,
                    active,
                    revision,
                    lastMutationId
                ) VALUES (1, 'SEVERE_INJURY', 1, 7000, 86407000, 2, 1, 1, 'apply:7')
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO status_effect_recovery_occurrences (
                    characterId,
                    effectType,
                    revision,
                    taskId,
                    occurrenceDateEpochDay
                ) VALUES (1, 'SEVERE_INJURY', 1, 7, ${OCCURRENCE_EPOCH_DAY + 1})
                """.trimIndent(),
            )
        } finally {
            database.close()
        }
    }

    private fun insertVersion7FailureBackfillSource(context: Context, databaseName: String) {
        val sqliteDatabase = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        try {
            sqliteDatabase.execSQL(
                """
                INSERT INTO monster_attack_events (
                    taskId,
                    occurrenceDateEpochDay,
                    recurrenceSeriesId,
                    trigger,
                    status,
                    skipReason,
                    sourceMonsterInstanceId,
                    sourceMonsterLevel,
                    sourceRawDamage,
                    playerDefense,
                    playerMaxHp,
                    finalDamage,
                    playerHpBefore,
                    playerHpAfter,
                    wasLethal,
                    revivedHp,
                    characterBalanceVersion,
                    monsterBalanceVersion,
                    processedAtEpochMillis
                ) VALUES
                    (
                        7, ${OCCURRENCE_EPOCH_DAY + 3}, 7,
                        'MISSED_DEADLINE', 'SKIPPED', 'SKIPPED_RECONCILIATION_CAP',
                        21, 3, 16, 10, 122, 0, 116, 116, 0, NULL, 1, 1, 4100
                    ),
                    (
                        7, ${OCCURRENCE_EPOCH_DAY + 4}, 7,
                        'MANUAL_FAILURE', 'APPLIED', NULL,
                        21, 3, 16, 10, 122, 6, 116, 110, 0, NULL, 1, 1, 4200
                    ),
                    (
                        7, $OCCURRENCE_EPOCH_DAY, 7,
                        'MISSED_DEADLINE', 'APPLIED', NULL,
                        21, 3, 16, 10, 122, 6, 110, 104, 0, NULL, 1, 1, 4300
                    ),
                    (
                        7, ${OCCURRENCE_EPOCH_DAY + 5}, 7,
                        'MISSED_DEADLINE', 'APPLIED', NULL,
                        21, 3, 16, 10, 122, 6, 104, 98, 0, NULL, 1, 1, 4400
                    )
                """.trimIndent(),
            )
            sqliteDatabase.execSQL(
                """
                INSERT INTO failure_logs (
                    id,
                    taskId,
                    occurrenceDateEpochDay,
                    recurrenceSeriesId,
                    failedAtEpochMillis
                ) VALUES (31, 7, ${OCCURRENCE_EPOCH_DAY + 5}, 700, 4500)
                """.trimIndent(),
            )
        } finally {
            sqliteDatabase.close()
        }
    }

    private fun insertVersion4CombatSource(context: Context, databaseName: String) {
        val sqliteDatabase = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        try {
            sqliteDatabase.execSQL(
                "INSERT INTO monster_instances VALUES " +
                    "(21, 'goblin_scout', 'NORMAL', 1, 1, 3, 73, 1)",
            )
            sqliteDatabase.execSQL(
                "INSERT INTO combat_progress VALUES (1, 1, 3, 21, 12345, 1)",
            )
            sqliteDatabase.execSQL(
                """
                INSERT INTO player_attack_events VALUES (
                    7, ${OCCURRENCE_EPOCH_DAY + 1}, 7, 'PENDING',
                    3, 25, 800, 15250, 300, 1, 1, 3000,
                    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
                )
                """.trimIndent(),
            )
            sqliteDatabase.execSQL(
                """
                INSERT INTO monster_attack_events VALUES (
                    7, ${OCCURRENCE_EPOCH_DAY + 2}, 7, 'APPLIED', NULL,
                    21, 3, 16, 10, 122, 6, 122, 116, 0, NULL, 1, 1, 4000
                )
                """.trimIndent(),
            )
        } finally {
            sqliteDatabase.close()
        }
    }

    private fun createVersion3Database(
        sqliteDatabase: android.database.sqlite.SQLiteDatabase,
        totalXp: Int,
    ) {
        try {
            sqliteDatabase.execSQL(
                """
                CREATE TABLE `todo_tasks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `recurrenceSeriesId` INTEGER NOT NULL DEFAULT 0,
                    `title` TEXT NOT NULL,
                    `memo` TEXT NOT NULL,
                    `startDateEpochDay` INTEGER NOT NULL,
                    `endDateEpochDay` INTEGER,
                    `timeMinuteOfDay` INTEGER,
                    `difficulty` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `recurrenceRule` TEXT NOT NULL,
                    `createdAtEpochMillis` INTEGER NOT NULL,
                    `updatedAtEpochMillis` INTEGER NOT NULL,
                    `deletedAtEpochMillis` INTEGER
                )
                """.trimIndent(),
            )
            sqliteDatabase.execSQL(
                "CREATE INDEX `index_todo_tasks_startDateEpochDay` " +
                    "ON `todo_tasks` (`startDateEpochDay`)",
            )
            sqliteDatabase.execSQL(
                "CREATE INDEX `index_todo_tasks_deletedAtEpochMillis` " +
                    "ON `todo_tasks` (`deletedAtEpochMillis`)",
            )
            sqliteDatabase.execSQL(
                """
                CREATE TABLE `completion_logs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `taskId` INTEGER NOT NULL,
                    `occurrenceDateEpochDay` INTEGER NOT NULL,
                    `completedAtEpochMillis` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            sqliteDatabase.execSQL(
                "CREATE UNIQUE INDEX `index_completion_logs_taskId_occurrenceDateEpochDay` " +
                    "ON `completion_logs` (`taskId`, `occurrenceDateEpochDay`)",
            )
            sqliteDatabase.execSQL(
                """
                CREATE TABLE `reward_ledger` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `taskId` INTEGER NOT NULL,
                    `occurrenceDateEpochDay` INTEGER NOT NULL,
                    `recurrenceSeriesId` INTEGER NOT NULL,
                    `xpAward` INTEGER NOT NULL,
                    `goldAward` INTEGER NOT NULL,
                    `rewardLocalDateEpochDay` INTEGER NOT NULL,
                    `onTime` INTEGER NOT NULL,
                    `onTimeMultiplierBp` INTEGER NOT NULL,
                    `rewardEfficiencyBp` INTEGER NOT NULL,
                    `repeatOrdinal` INTEGER NOT NULL,
                    `dailyOrdinal` INTEGER NOT NULL,
                    `goldGainBonusBp` INTEGER NOT NULL,
                    `combatEligible` INTEGER NOT NULL,
                    `balanceVersion` INTEGER NOT NULL,
                    `awardedAtEpochMillis` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            sqliteDatabase.execSQL(
                "CREATE UNIQUE INDEX `index_reward_ledger_taskId_occurrenceDateEpochDay` " +
                    "ON `reward_ledger` (`taskId`, `occurrenceDateEpochDay`)",
            )
            sqliteDatabase.execSQL(
                "CREATE INDEX `index_reward_ledger_recurrenceSeriesId` " +
                    "ON `reward_ledger` (`recurrenceSeriesId`)",
            )
            sqliteDatabase.execSQL(
                "CREATE INDEX `index_reward_ledger_rewardLocalDateEpochDay` " +
                    "ON `reward_ledger` (`rewardLocalDateEpochDay`)",
            )
            sqliteDatabase.execSQL(
                "CREATE INDEX `index_reward_ledger_onTime_occurrenceDateEpochDay` " +
                    "ON `reward_ledger` (`onTime`, `occurrenceDateEpochDay`)",
            )
            sqliteDatabase.execSQL(
                """
                CREATE TABLE `character_profile` (
                    `id` INTEGER NOT NULL,
                    `totalXp` INTEGER NOT NULL,
                    `currentGold` INTEGER NOT NULL,
                    `strength` INTEGER NOT NULL,
                    `vitality` INTEGER NOT NULL,
                    `focus` INTEGER NOT NULL,
                    `willpower` INTEGER NOT NULL,
                    `unspentStatPoints` INTEGER NOT NULL,
                    `hasUsedFreeStatReset` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            sqliteDatabase.execSQL(
                """
                CREATE TABLE `character_current_state` (
                    `characterId` INTEGER NOT NULL,
                    `currentHp` INTEGER NOT NULL,
                    `balanceVersion` INTEGER NOT NULL,
                    `updatedAtEpochMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`characterId`)
                )
                """.trimIndent(),
            )
            sqliteDatabase.execSQL(
                """
                INSERT INTO `todo_tasks` VALUES (
                    7, 7, 'Legacy recurring quest', '', $OCCURRENCE_EPOCH_DAY, NULL,
                    NULL, 'HARD', 'General', 'DAILY', 10, 20, NULL
                )
                """.trimIndent(),
            )
            sqliteDatabase.execSQL(
                "INSERT INTO `completion_logs` VALUES " +
                    "(3, 7, $OCCURRENCE_EPOCH_DAY, $COMPLETED_AT_MILLIS)",
            )
            sqliteDatabase.execSQL(
                """
                INSERT INTO `reward_ledger` VALUES (
                    11, 7, $OCCURRENCE_EPOCH_DAY, 7, 35, 20,
                    $OCCURRENCE_EPOCH_DAY, 0, 10000, 10000, 0, 0, 0, 0, 0,
                    $AWARDED_AT_MILLIS
                )
                """.trimIndent(),
            )
            sqliteDatabase.execSQL(
                """
                INSERT INTO `reward_ledger` VALUES (
                    12, 99, ${OCCURRENCE_EPOCH_DAY + 1}, 99, 10, 5,
                    ${OCCURRENCE_EPOCH_DAY + 1}, 0, 10000, 10000, 0, 0, 0, 0, 0,
                    ${AWARDED_AT_MILLIS + 1}
                )
                """.trimIndent(),
            )
            sqliteDatabase.execSQL(
                "INSERT INTO `character_profile` VALUES " +
                    "(1, $totalXp, 456, 5, 5, 5, 5, ${2 * (1 + totalXp / 100 - 1)}, 0)",
            )
            sqliteDatabase.execSQL(
                "INSERT INTO `character_current_state` VALUES " +
                    "(1, ${110 + 6 * (1 + totalXp / 100 - 1)}, 1, 0)",
            )
            sqliteDatabase.execSQL("PRAGMA user_version = 3")
        } catch (error: Throwable) {
            sqliteDatabase.close()
            throw error
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.rowCount(tableName: String): Int =
        query("SELECT COUNT(*) FROM `$tableName`").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun android.database.sqlite.SQLiteDatabase.rowCount(tableName: String): Int =
        rawQuery("SELECT COUNT(*) FROM `$tableName`", null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun android.database.sqlite.SQLiteDatabase.columnNames(
        tableName: String,
    ): Set<String> = rawQuery("PRAGMA table_info(`$tableName`)", null).use { cursor ->
        buildSet {
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) add(cursor.getString(nameIndex))
        }
    }

    private fun android.database.sqlite.SQLiteDatabase.indexNames(
        tableName: String,
    ): Set<String> = rawQuery("PRAGMA index_list(`$tableName`)", null).use { cursor ->
        buildSet {
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) add(cursor.getString(nameIndex))
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.columnNames(tableName: String): Set<String> =
        query("PRAGMA table_info(`$tableName`)").use { cursor ->
            buildSet {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.indexNames(tableName: String): Set<String> =
        query("PRAGMA index_list(`$tableName`)").use { cursor ->
            buildSet {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.columnDefaultValue(
        tableName: String,
        columnName: String,
    ): String? = query("PRAGMA table_info(`$tableName`)").use { cursor ->
        val nameIndex = cursor.getColumnIndexOrThrow("name")
        val defaultIndex = cursor.getColumnIndexOrThrow("dflt_value")
        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) == columnName) return@use cursor.getString(defaultIndex)
        }
        null
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.columnIsNotNull(
        tableName: String,
        columnName: String,
    ): Boolean = query("PRAGMA table_info(`$tableName`)").use { cursor ->
        val nameIndex = cursor.getColumnIndexOrThrow("name")
        val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) == columnName) {
                return@use cursor.getInt(notNullIndex) != 0
            }
        }
        error("Missing column $tableName.$columnName")
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.primaryKeyPositions(
        tableName: String,
    ): Map<String, Int> = query("PRAGMA table_info(`$tableName`)").use { cursor ->
        buildMap {
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val primaryKeyIndex = cursor.getColumnIndexOrThrow("pk")
            while (cursor.moveToNext()) {
                val position = cursor.getInt(primaryKeyIndex)
                if (position > 0) put(cursor.getString(nameIndex), position)
            }
        }
    }

    private companion object {
        const val OCCURRENCE_EPOCH_DAY = 20_000L
        const val COMPLETED_AT_MILLIS = 1_000L
        const val AWARDED_AT_MILLIS = 2_000L
    }
}
