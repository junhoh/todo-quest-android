package com.todoquest.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.todoquest.core.SystemAppClock
import com.todoquest.data.local.TodoQuestDatabase
import com.todoquest.data.local.TodoTaskEntity
import com.todoquest.domain.model.EquipOwnedEquipmentResult
import com.todoquest.domain.model.EquipmentInventorySnapshot
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.EquipmentStoreSnapshot
import com.todoquest.domain.model.PurchaseEquipmentResult
import com.todoquest.domain.model.RecurrenceRule
import com.todoquest.domain.model.ReminderMode
import com.todoquest.domain.model.TaskCategory
import com.todoquest.domain.model.TaskDifficulty
import com.todoquest.domain.model.UnequipEquipmentResult
import com.todoquest.domain.repository.EquipmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodoQuestDatabaseIsolationTest {
    private lateinit var database: TodoQuestDatabase
    private var productionContainer: TodoQuestAppContainer? = null

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseDirectory = context.getDatabasePath(DATABASE_NAME).parentFile
        check(databaseDirectory == null || databaseDirectory.exists() || databaseDirectory.mkdirs()) {
            "Failed to create test database directory: ${databaseDirectory?.absolutePath}"
        }
        database = Room.databaseBuilder(
            context,
            TodoQuestDatabase::class.java,
            DATABASE_NAME,
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
            .build()
    }

    @After
    fun closeDatabase() {
        productionContainer?.database?.close()
        database.close()
    }

    @Test
    fun firstOrchestratorInvocationStartsWithEmptyDatabase() {
        assertEmptyThenInsert("first")
    }

    @Test
    fun secondOrchestratorInvocationStartsWithEmptyDatabase() {
        assertEmptyThenInsert("second")
    }

    @Test
    fun appContainerPreservesInjectedEquipmentRepository() {
        val fakeRepository = FakeEquipmentRepository()

        val container = TodoQuestAppContainer(
            database = database,
            clock = SystemAppClock(),
            equipmentRepository = fakeRepository,
        )

        assertSame(fakeRepository, container.equipmentRepository)
    }

    @Test
    fun productionContainerReopensCurrentFileWithLegacyNoneReminderFallback() {
        val taskId = runBlocking { database.todoTaskDao().insert(markerTask("legacy-none")) }
        database.close()

        val container = TodoQuestAppContainer.create(
            ApplicationProvider.getApplicationContext(),
        )
        productionContainer = container

        assertEquals(15, container.database.openHelper.writableDatabase.version)
        assertEquals(ReminderMode.NONE, runBlocking {
            container.taskRepository.getTask(taskId)?.reminderSetting?.mode
        })
        assertEquals(null, runBlocking {
            container.database.taskReminderDao().getByTaskId(taskId)
        })
    }

    private fun assertEmptyThenInsert(marker: String) {
        runBlocking {
            val tasksAtStart = database.todoTaskDao()
                .observeActiveTasksStartingBefore(Long.MAX_VALUE)
                .first()
            assertTrue("Expected an empty database at test invocation start", tasksAtStart.isEmpty())

            database.todoTaskDao().insert(markerTask(marker))
        }
    }

    private fun markerTask(marker: String) = TodoTaskEntity(
        title = "orchestrator-marker-$marker",
        memo = "",
        startDateEpochDay = 0L,
        endDateEpochDay = null,
        timeMinuteOfDay = null,
        difficulty = TaskDifficulty.MEDIUM.name,
        category = TaskCategory.DEFAULT,
        recurrenceRule = RecurrenceRule.NONE.name,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
        deletedAtEpochMillis = null,
    )

    private companion object {
        const val DATABASE_NAME = "todo-quest.db"
    }

    private class FakeEquipmentRepository : EquipmentRepository {
        override fun observeStore(characterId: Long): Flow<EquipmentStoreSnapshot> =
            error("not used")

        override fun observeInventory(characterId: Long): Flow<EquipmentInventorySnapshot> =
            error("not used")

        override suspend fun purchaseEquipment(
            characterId: Long,
            equipmentId: Long,
        ): PurchaseEquipmentResult = error("not used")

        override suspend fun equipOwnedEquipment(
            characterId: Long,
            ownedEquipmentId: Long,
            targetSlot: EquipmentSlot,
        ): EquipOwnedEquipmentResult = error("not used")

        override suspend fun unequipEquipment(
            characterId: Long,
            targetSlot: EquipmentSlot,
        ): UnequipEquipmentResult = error("not used")
    }
}
