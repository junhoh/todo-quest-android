package com.todoquest.background

import android.content.Context
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.todoquest.app.TodoQuestContainerOwner
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class CombatReconciliationWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val useCase = (applicationContext as? TodoQuestContainerOwner)
            ?.todoQuestContainer
            ?.reconcileCombatUseCase
            ?: return terminalFailure(
                IllegalStateException("Application does not provide the Todo Quest container"),
            )

        return try {
            useCase()
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            if (failure.isTransientDatabaseFailure() && runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Log.w(
                    LOG_TAG,
                    "Combat reconciliation will retry after transient database failure " +
                        "(attempt=$runAttemptCount)",
                    failure,
                )
                Result.retry()
            } else {
                terminalFailure(failure)
            }
        }
    }

    private fun terminalFailure(failure: Throwable): Result {
        Log.e(
            LOG_TAG,
            "Combat reconciliation failed without another immediate retry " +
                "(attempt=$runAttemptCount)",
            failure,
        )
        return Result.failure()
    }

    private fun Throwable.isTransientDatabaseFailure(): Boolean =
        generateSequence(this) { it.cause }
            .any { it is SQLiteDatabaseLockedException || it is SQLiteDiskIOException }

    companion object {
        const val MAX_RETRY_ATTEMPTS = 3
        private const val LOG_TAG = "TodoQuestCombatWork"
    }
}

object CombatReconciliationWork {
    const val STARTUP_WORK_NAME = "combat-reconciliation-startup"
    const val PERIODIC_WORK_NAME = "combat-reconciliation-periodic"
    const val REPEAT_INTERVAL_MINUTES = 15L

    fun enqueue(workManager: WorkManager) {
        workManager.enqueueUniqueWork(
            STARTUP_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CombatReconciliationWorker>().build(),
        )
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CombatReconciliationWorker>(
                REPEAT_INTERVAL_MINUTES,
                TimeUnit.MINUTES,
            ).build(),
        )
    }
}
