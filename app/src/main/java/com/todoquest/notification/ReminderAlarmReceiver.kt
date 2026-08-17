package com.todoquest.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.todoquest.domain.usecase.DeliverReminderUseCase
import com.todoquest.domain.usecase.ReconcileAllRemindersUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

interface ReminderRuntimeProvider {
    val deliverReminderUseCase: DeliverReminderUseCase
    val reconcileAllRemindersUseCase: ReconcileAllRemindersUseCase
}

class ReminderAlarmReceiver(
    private val coroutineScope: CoroutineScope = ReminderReceiverScope.scope,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = ReminderAlarmIntents.reminderKeyFromAlarm(intent)
        if (key == null) {
            Log.w(LOG_TAG, "Ignoring malformed reminder alarm callback")
            return
        }
        val pendingResult = goAsync()
        val applicationContext = context.applicationContext
        coroutineScope.launch {
            try {
                val provider = applicationContext as? ReminderRuntimeProvider
                    ?: error("Application does not provide reminder runtime")
                provider.deliverReminderUseCase(key)
            } catch (failure: Throwable) {
                Log.e(
                    LOG_TAG,
                    "Reminder delivery failed for task=${key.taskId} " +
                        "occurrence=${key.occurrenceDate}",
                    failure,
                )
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        private const val LOG_TAG = "TodoQuestReminder"
    }
}

private object ReminderReceiverScope {
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO.limitedParallelism(1),
    )
}
