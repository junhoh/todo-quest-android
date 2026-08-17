package com.todoquest.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RewardLedgerDaoTest {
    private lateinit var database: TodoQuestDatabase
    private lateinit var dao: RewardLedgerDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            TodoQuestDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.rewardLedgerDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun dailyAndSeriesCountsIncludeMigratedHistoryRowsWithZeroOrdinals() = runTest {
        dao.insert(ledger(id = 1, taskId = 1, seriesId = 10, occurrenceDate = 100, rewardDate = 200))
        dao.insert(ledger(id = 2, taskId = 2, seriesId = 10, occurrenceDate = 101, rewardDate = 201))
        dao.insert(ledger(id = 3, taskId = 3, seriesId = 30, occurrenceDate = 102, rewardDate = 200))

        assertEquals(2, dao.countForRewardLocalDate(200))
        assertEquals(2, dao.countForRecurrenceSeries(10))
    }

    @Test
    fun observesDistinctOnTimeOccurrenceDatesThroughReferenceDate() = runTest {
        dao.insert(ledger(id = 1, taskId = 1, occurrenceDate = 100, rewardDate = 105, onTime = true))
        dao.insert(ledger(id = 2, taskId = 2, occurrenceDate = 100, rewardDate = 106, onTime = true))
        dao.insert(ledger(id = 3, taskId = 3, occurrenceDate = 101, rewardDate = 106, onTime = false))
        dao.insert(ledger(id = 4, taskId = 4, occurrenceDate = 103, rewardDate = 103, onTime = true))

        assertEquals(
            listOf(100L),
            dao.observeOnTimeOccurrenceDatesThrough(102).first(),
        )
    }

    @Test
    fun preservesOccurrenceUniquenessAndSeriesSnapshotWhenTaskIsReassigned() = runTest {
        val original = ledger(id = 1, taskId = 1, seriesId = 10, occurrenceDate = 100, rewardDate = 100)
        assertEquals(1L, dao.insert(original))
        assertEquals(-1L, dao.insert(original.copy(id = 0, xpAward = 999)))

        dao.reassignFrom(taskId = 1, fromOccurrenceDateEpochDay = 100, newTaskId = 2)

        assertNull(dao.find(1, 100))
        val reassigned = dao.find(2, 100)
        assertNotNull(reassigned)
        assertEquals(10L, reassigned!!.recurrenceSeriesId)
        assertEquals(20L, reassigned.xpAward)
    }

    private fun ledger(
        id: Long,
        taskId: Long,
        seriesId: Long = taskId,
        occurrenceDate: Long,
        rewardDate: Long,
        onTime: Boolean = false,
    ) = RewardLedgerEntity(
        id = id,
        taskId = taskId,
        occurrenceDateEpochDay = occurrenceDate,
        recurrenceSeriesId = seriesId,
        xpAward = 20L,
        goldAward = 10L,
        rewardLocalDateEpochDay = rewardDate,
        onTime = onTime,
        onTimeMultiplierBp = 10_000,
        rewardEfficiencyBp = 10_000,
        repeatOrdinal = 0,
        dailyOrdinal = 0,
        goldGainBonusBp = 0,
        combatEligible = false,
        balanceVersion = 0,
        awardedAtEpochMillis = 1_000L,
    )
}
