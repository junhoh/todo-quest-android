package com.todoquest.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TodoTaskEntity::class,
        CompletionLogEntity::class,
        FailureLogEntity::class,
        RewardLedgerEntity::class,
        CharacterProfileEntity::class,
        CharacterCurrentStateEntity::class,
        CharacterAppearanceEntity::class,
        CharacterEquippedItemsEntity::class,
        MonsterInstanceEntity::class,
        CombatProgressEntity::class,
        PlayerAttackEventEntity::class,
        MonsterAttackEventEntity::class,
        EquipmentEntity::class,
        EquipmentModifierEntity::class,
        OwnedEquipmentEntity::class,
        CharacterEquipmentEntity::class,
        TaskReminderEntity::class,
        CharacterStatusEffectEntity::class,
        StatusEffectRecoveryOccurrenceEntity::class,
    ],
    version = 15,
    exportSchema = true,
)
abstract class TodoQuestDatabase : RoomDatabase() {
    abstract fun todoTaskDao(): TodoTaskDao

    abstract fun completionLogDao(): CompletionLogDao

    abstract fun failureLogDao(): FailureLogDao

    abstract fun rewardLedgerDao(): RewardLedgerDao

    abstract fun characterProfileDao(): CharacterProfileDao

    abstract fun combatDao(): CombatDao

    abstract fun equipmentDao(): EquipmentDao

    abstract fun taskReminderDao(): TaskReminderDao

    abstract fun statusEffectDao(): StatusEffectDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todo_tasks ADD COLUMN endDateEpochDay INTEGER")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addRecurrenceSeriesIdentity(db)
                migrateCharacterProfile(db)
                migrateRewardLedger(db)
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createMonsterInstances(db)
                createCombatProgress(db)
                createPlayerAttackEvents(db)
                createMonsterAttackEvents(db)
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS character_appearance (
                        characterId INTEGER NOT NULL,
                        hairId TEXT NOT NULL,
                        PRIMARY KEY(characterId)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS character_equipped_items (
                        characterId INTEGER NOT NULL,
                        headId TEXT,
                        topId TEXT NOT NULL,
                        bottomId TEXT NOT NULL,
                        shoesId TEXT NOT NULL,
                        accessoryId TEXT,
                        weaponId TEXT,
                        PRIMARY KEY(characterId)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO character_appearance (characterId, hairId)
                    SELECT id, 'hair_default'
                    FROM character_profile
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO character_equipped_items (
                        characterId,
                        headId,
                        topId,
                        bottomId,
                        shoesId,
                        accessoryId,
                        weaponId
                    )
                    SELECT
                        id,
                        'headgear_adventure',
                        'top_adventure',
                        'bottom_adventure',
                        'shoes_adventure',
                        'accessory_adventure',
                        'weapon_default_sword'
                    FROM character_profile
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS failure_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        taskId INTEGER NOT NULL,
                        occurrenceDateEpochDay INTEGER NOT NULL,
                        recurrenceSeriesId INTEGER NOT NULL,
                        failedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_failure_logs_taskId_occurrenceDateEpochDay " +
                        "ON failure_logs (taskId, occurrenceDateEpochDay)",
                )
                db.execSQL(
                    "ALTER TABLE monster_attack_events " +
                        "ADD COLUMN trigger TEXT NOT NULL DEFAULT 'MISSED_DEADLINE'",
                )
            }
        }

        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS equipment (
                        id INTEGER NOT NULL,
                        nameKey TEXT NOT NULL,
                        descriptionKey TEXT NOT NULL,
                        type TEXT NOT NULL,
                        slot TEXT NOT NULL,
                        rarity TEXT NOT NULL,
                        price INTEGER NOT NULL,
                        requiredLevel INTEGER NOT NULL,
                        imageKey TEXT,
                        layerKey TEXT,
                        isForSale INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_equipment_isForSale " +
                        "ON equipment (isForSale)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_equipment_slot ON equipment (slot)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS equipment_modifiers (
                        equipmentId INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        targetKind TEXT NOT NULL,
                        targetStat TEXT NOT NULL,
                        modifierType TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        PRIMARY KEY(equipmentId, sortOrder),
                        FOREIGN KEY(equipmentId) REFERENCES equipment(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_equipment_modifiers_equipmentId " +
                        "ON equipment_modifiers (equipmentId)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS owned_equipment (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        characterId INTEGER NOT NULL,
                        equipmentId INTEGER NOT NULL,
                        acquiredAtEpochMillis INTEGER NOT NULL,
                        FOREIGN KEY(characterId) REFERENCES character_profile(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(equipmentId) REFERENCES equipment(id)
                            ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_owned_equipment_characterId_equipmentId " +
                        "ON owned_equipment (characterId, equipmentId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_owned_equipment_equipmentId " +
                        "ON owned_equipment (equipmentId)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS character_equipment (
                        characterId INTEGER NOT NULL,
                        slot TEXT NOT NULL,
                        ownedEquipmentId INTEGER NOT NULL,
                        PRIMARY KEY(characterId, slot),
                        FOREIGN KEY(characterId) REFERENCES character_profile(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(ownedEquipmentId) REFERENCES owned_equipment(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_character_equipment_ownedEquipmentId " +
                        "ON character_equipment (ownedEquipmentId)",
                )
            }
        }

        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO failure_logs (
                        taskId,
                        occurrenceDateEpochDay,
                        recurrenceSeriesId,
                        failedAtEpochMillis
                    )
                    SELECT
                        event.taskId,
                        event.occurrenceDateEpochDay,
                        event.recurrenceSeriesId,
                        event.processedAtEpochMillis
                    FROM monster_attack_events AS event
                    WHERE event.trigger = 'MISSED_DEADLINE'
                      AND NOT EXISTS (
                          SELECT 1
                          FROM failure_logs AS failure
                          WHERE failure.taskId = event.taskId
                            AND failure.occurrenceDateEpochDay = event.occurrenceDateEpochDay
                      )
                      AND NOT EXISTS (
                          SELECT 1
                          FROM completion_logs AS completion
                          WHERE completion.taskId = event.taskId
                            AND completion.occurrenceDateEpochDay = event.occurrenceDateEpochDay
                      )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.addColumnIfMissing(
                    tableName = "reward_ledger",
                    columnName = "rewardMode",
                    definition = "TEXT NOT NULL DEFAULT 'TODO_COMPLETION'",
                )
                db.addColumnIfMissing(
                    tableName = "player_attack_events",
                    columnName = "combatRewardVersion",
                    definition = "INTEGER NOT NULL DEFAULT 0",
                )
                db.addColumnIfMissing(
                    tableName = "player_attack_events",
                    columnName = "hitXpAward",
                    definition = "INTEGER NOT NULL DEFAULT 0",
                )
                db.addColumnIfMissing(
                    tableName = "player_attack_events",
                    columnName = "killBonusXpAward",
                    definition = "INTEGER NOT NULL DEFAULT 0",
                )
                db.addColumnIfMissing(
                    tableName = "player_attack_events",
                    columnName = "killGoldAward",
                    definition = "INTEGER NOT NULL DEFAULT 0",
                )
                db.addColumnIfMissing(
                    tableName = "player_attack_events",
                    columnName = "rewardGradeMultiplierBp",
                    definition = "INTEGER NOT NULL DEFAULT 0",
                )
                db.addColumnIfMissing(
                    tableName = "player_attack_events",
                    columnName = "rewardGoldGainBonusBp",
                    definition = "INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS task_reminders (
                        taskId INTEGER NOT NULL,
                        mode TEXT NOT NULL,
                        customTimeMinuteOfDay INTEGER,
                        scheduleStatus TEXT NOT NULL,
                        scheduledOccurrenceEpochDay INTEGER,
                        scheduledTriggerAtEpochMillis INTEGER,
                        updatedAtEpochMillis INTEGER NOT NULL,
                        PRIMARY KEY(taskId),
                        FOREIGN KEY(taskId) REFERENCES todo_tasks(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_task_reminders_mode " +
                        "ON task_reminders (mode)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_task_reminders_scheduleStatus " +
                        "ON task_reminders (scheduleStatus)",
                )
            }
        }

        val MIGRATION_10_11: Migration = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE character_equipped_items ADD COLUMN glovesId TEXT")
            }
        }

        val MIGRATION_11_12: Migration = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE equipment ADD COLUMN weaponType TEXT")
                db.execSQL("UPDATE equipment SET weaponType = 'LONGSWORD' WHERE type = 'WEAPON'")
            }
        }

        val MIGRATION_12_13: Migration = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS character_status_effects (
                        characterId INTEGER NOT NULL,
                        effectType TEXT NOT NULL,
                        definitionVersion INTEGER NOT NULL,
                        appliedAtEpochMillis INTEGER NOT NULL,
                        expiresAtEpochMillis INTEGER NOT NULL,
                        remainingRecoveryCompletions INTEGER NOT NULL,
                        active INTEGER NOT NULL,
                        revision INTEGER NOT NULL,
                        lastMutationId TEXT NOT NULL,
                        PRIMARY KEY(characterId, effectType),
                        FOREIGN KEY(characterId) REFERENCES character_profile(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "index_character_status_effects_characterId_active " +
                        "ON character_status_effects (characterId, active)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS status_effect_recovery_occurrences (
                        characterId INTEGER NOT NULL,
                        effectType TEXT NOT NULL,
                        revision INTEGER NOT NULL,
                        taskId INTEGER NOT NULL,
                        occurrenceDateEpochDay INTEGER NOT NULL,
                        PRIMARY KEY(
                            characterId,
                            effectType,
                            revision,
                            taskId,
                            occurrenceDateEpochDay
                        ),
                        FOREIGN KEY(characterId, effectType)
                            REFERENCES character_status_effects(characterId, effectType)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_13_14: Migration = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE player_attack_events ADD COLUMN sourceTaskDifficulty TEXT")
                db.execSQL(
                    "ALTER TABLE player_attack_events " +
                        "ADD COLUMN taskDifficultyBalanceVersion INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        val MIGRATION_14_15: Migration = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE character_equipped_items
                    SET headId = NULL,
                        topId = 'top_default',
                        bottomId = 'bottom_default',
                        shoesId = 'shoes_default',
                        accessoryId = NULL,
                        weaponId = NULL,
                        glovesId = NULL
                    """.trimIndent(),
                )
            }
        }

        private fun SupportSQLiteDatabase.addColumnIfMissing(
            tableName: String,
            columnName: String,
            definition: String,
        ) {
            val columnExists = query("PRAGMA table_info(`$tableName`)").use { cursor ->
                val nameColumnIndex = cursor.getColumnIndexOrThrow("name")
                generateSequence { if (cursor.moveToNext()) cursor else null }
                    .any { it.getString(nameColumnIndex) == columnName }
            }
            if (!columnExists) {
                execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $definition")
            }
        }

        private fun createMonsterInstances(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS monster_instances (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    definitionId TEXT NOT NULL,
                    grade TEXT NOT NULL,
                    stageNumber INTEGER NOT NULL,
                    encounterNumber INTEGER NOT NULL,
                    level INTEGER NOT NULL,
                    currentHp INTEGER NOT NULL,
                    balanceVersion INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "index_monster_instances_stageNumber_encounterNumber " +
                    "ON monster_instances (stageNumber, encounterNumber)",
            )
        }

        private fun createCombatProgress(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS combat_progress (
                    id INTEGER NOT NULL,
                    stageNumber INTEGER NOT NULL,
                    stageLevel INTEGER NOT NULL,
                    activeMonsterInstanceId INTEGER NOT NULL,
                    lastReconciledAtEpochMillis INTEGER NOT NULL,
                    balanceVersion INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent(),
            )
        }

        private fun createPlayerAttackEvents(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS player_attack_events (
                    taskId INTEGER NOT NULL,
                    occurrenceDateEpochDay INTEGER NOT NULL,
                    recurrenceSeriesId INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    sourcePlayerLevel INTEGER NOT NULL,
                    sourceAttack INTEGER NOT NULL,
                    sourceCriticalChanceBp INTEGER NOT NULL,
                    sourceCriticalDamageBp INTEGER NOT NULL,
                    sourceMomentumBp INTEGER NOT NULL,
                    characterBalanceVersion INTEGER NOT NULL,
                    monsterBalanceVersion INTEGER NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL,
                    targetMonsterInstanceId INTEGER,
                    seed INTEGER,
                    roll INTEGER,
                    wasCritical INTEGER,
                    rawDamage INTEGER,
                    targetDefense INTEGER,
                    finalDamage INTEGER,
                    targetHpBefore INTEGER,
                    targetHpAfter INTEGER,
                    processedAtEpochMillis INTEGER,
                    PRIMARY KEY(taskId, occurrenceDateEpochDay)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS " +
                    "index_player_attack_events_status_createdAtEpochMillis " +
                    "ON player_attack_events (status, createdAtEpochMillis)",
            )
        }

        private fun createMonsterAttackEvents(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS monster_attack_events (
                    taskId INTEGER NOT NULL,
                    occurrenceDateEpochDay INTEGER NOT NULL,
                    recurrenceSeriesId INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    skipReason TEXT,
                    sourceMonsterInstanceId INTEGER NOT NULL,
                    sourceMonsterLevel INTEGER NOT NULL,
                    sourceRawDamage INTEGER NOT NULL,
                    playerDefense INTEGER NOT NULL,
                    playerMaxHp INTEGER NOT NULL,
                    finalDamage INTEGER NOT NULL,
                    playerHpBefore INTEGER NOT NULL,
                    playerHpAfter INTEGER NOT NULL,
                    wasLethal INTEGER NOT NULL,
                    revivedHp INTEGER,
                    characterBalanceVersion INTEGER NOT NULL,
                    monsterBalanceVersion INTEGER NOT NULL,
                    processedAtEpochMillis INTEGER NOT NULL,
                    PRIMARY KEY(taskId, occurrenceDateEpochDay)
                )
                """.trimIndent(),
            )
        }

        private fun addRecurrenceSeriesIdentity(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE todo_tasks " +
                    "ADD COLUMN recurrenceSeriesId INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL("UPDATE todo_tasks SET recurrenceSeriesId = id")
        }

        private fun migrateCharacterProfile(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS character_profile_v3 (
                    id INTEGER NOT NULL,
                    totalXp INTEGER NOT NULL,
                    currentGold INTEGER NOT NULL,
                    strength INTEGER NOT NULL,
                    vitality INTEGER NOT NULL,
                    focus INTEGER NOT NULL,
                    willpower INTEGER NOT NULL,
                    unspentStatPoints INTEGER NOT NULL,
                    hasUsedFreeStatReset INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO character_profile_v3 (
                    id,
                    totalXp,
                    currentGold,
                    strength,
                    vitality,
                    focus,
                    willpower,
                    unspentStatPoints,
                    hasUsedFreeStatReset
                )
                SELECT
                    id,
                    totalXp,
                    currentGold,
                    5,
                    5,
                    5,
                    5,
                    2 * (MIN(50, MAX(1, 1 + totalXp / 100)) - 1),
                    0
                FROM character_profile
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS character_current_state (
                    characterId INTEGER NOT NULL,
                    currentHp INTEGER NOT NULL,
                    balanceVersion INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL,
                    PRIMARY KEY(characterId)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO character_current_state (
                    characterId,
                    currentHp,
                    balanceVersion,
                    updatedAtEpochMillis
                )
                SELECT
                    id,
                    110 + 6 * (MIN(50, MAX(1, 1 + totalXp / 100)) - 1),
                    1,
                    0
                FROM character_profile
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE character_profile")
            db.execSQL("ALTER TABLE character_profile_v3 RENAME TO character_profile")
        }

        private fun migrateRewardLedger(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS reward_ledger_v3 (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    taskId INTEGER NOT NULL,
                    occurrenceDateEpochDay INTEGER NOT NULL,
                    recurrenceSeriesId INTEGER NOT NULL,
                    xpAward INTEGER NOT NULL,
                    goldAward INTEGER NOT NULL,
                    rewardLocalDateEpochDay INTEGER NOT NULL,
                    onTime INTEGER NOT NULL,
                    onTimeMultiplierBp INTEGER NOT NULL,
                    rewardEfficiencyBp INTEGER NOT NULL,
                    repeatOrdinal INTEGER NOT NULL,
                    dailyOrdinal INTEGER NOT NULL,
                    goldGainBonusBp INTEGER NOT NULL,
                    combatEligible INTEGER NOT NULL,
                    balanceVersion INTEGER NOT NULL,
                    awardedAtEpochMillis INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO reward_ledger_v3 (
                    id,
                    taskId,
                    occurrenceDateEpochDay,
                    recurrenceSeriesId,
                    xpAward,
                    goldAward,
                    rewardLocalDateEpochDay,
                    onTime,
                    onTimeMultiplierBp,
                    rewardEfficiencyBp,
                    repeatOrdinal,
                    dailyOrdinal,
                    goldGainBonusBp,
                    combatEligible,
                    balanceVersion,
                    awardedAtEpochMillis
                )
                SELECT
                    legacy.id,
                    legacy.taskId,
                    legacy.occurrenceDateEpochDay,
                    COALESCE(task.recurrenceSeriesId, legacy.taskId),
                    legacy.xp,
                    legacy.gold,
                    legacy.occurrenceDateEpochDay,
                    0,
                    10000,
                    10000,
                    0,
                    0,
                    0,
                    0,
                    0,
                    legacy.awardedAtEpochMillis
                FROM reward_ledger AS legacy
                LEFT JOIN todo_tasks AS task ON task.id = legacy.taskId
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE reward_ledger")
            db.execSQL("ALTER TABLE reward_ledger_v3 RENAME TO reward_ledger")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "index_reward_ledger_taskId_occurrenceDateEpochDay " +
                    "ON reward_ledger (taskId, occurrenceDateEpochDay)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_reward_ledger_recurrenceSeriesId " +
                    "ON reward_ledger (recurrenceSeriesId)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_reward_ledger_rewardLocalDateEpochDay " +
                    "ON reward_ledger (rewardLocalDateEpochDay)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_reward_ledger_onTime_occurrenceDateEpochDay " +
                    "ON reward_ledger (onTime, occurrenceDateEpochDay)",
            )
        }
    }
}
