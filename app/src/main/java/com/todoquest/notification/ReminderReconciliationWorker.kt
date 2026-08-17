package com.todoquest.notification

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

class ReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!isRestoreAction(intent.action)) return
        try {
            ReminderReconciliationWork.enqueueStartup(
                WorkManager.getInstance(context.applicationContext),
            )
        } catch (failure: Throwable) {
            Log.e(LOG_TAG, "Unable to enqueue reminder restoration", failure)
        }
    }

    private fun isRestoreAction(action: String?): Boolean = when (action) {
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        -> true
        AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ->
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        else -> false
    }

    companion object {
        private const val LOG_TAG = "TodoQuestReminder"
    }
}

class ReminderReconciliationWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val useCase = try {
            (applicationContext as? ReminderRuntimeProvider)?.reconcileAllRemindersUseCase
                ?: return terminalFailure(
                    IllegalStateException("Application does not provide reminder runtime"),
                )
        } catch (failure: Throwable) {
            return terminalFailure(failure)
        }

        return try {
            useCase()
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            if (failure.isTransientDatabaseFailure() && runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Log.w(
                    LOG_TAG,
                    "Reminder reconciliation will retry after transient database failure " +
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
            "Reminder reconciliation failed without another immediate retry " +
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
        private const val LOG_TAG = "TodoQuestReminderWork"
    }
}

object ReminderReconciliationWork {
    const val UNIQUE_WORK_NAME = "reminder-reconciliation-startup"

    fun enqueueStartup(context: Context) {
        enqueueStartup(WorkManager.getInstance(context.applicationContext))
    }

    fun enqueueStartup(workManager: WorkManager) {
        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ReminderReconciliationWorker>().build(),
        )
    }
}
