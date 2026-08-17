package com.todoquest

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import com.todoquest.app.TodoQuestApp
import com.todoquest.domain.model.ReminderOccurrenceKey
import com.todoquest.notification.ReminderAlarmIntents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {
    private val reminderNavigationViewModel: ReminderNavigationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            handleReminderIntent(intent)
        }
        setContent {
            val reminderNavigationEvent by
                reminderNavigationViewModel.navigationEvent.collectAsState()
            TodoQuestApp(
                reminderNavigationEvent = reminderNavigationEvent,
                onReminderNavigationConsumed = reminderNavigationViewModel::consume,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleReminderIntent(intent)
    }

    fun handleReminderIntent(intent: Intent?) {
        reminderNavigationViewModel.handleIntent(intent)
    }
}

data class ReminderNavigationEvent(
    val id: Long,
    val key: ReminderOccurrenceKey?,
)

class ReminderNavigationViewModel : ViewModel() {
    private val mutableNavigationEvent = MutableStateFlow<ReminderNavigationEvent?>(null)
    private var nextEventId = 1L

    val navigationEvent: StateFlow<ReminderNavigationEvent?> =
        mutableNavigationEvent.asStateFlow()

    fun handleIntent(intent: Intent?) {
        if (intent?.action != ReminderAlarmIntents.ACTION_OPEN_REMINDER) return
        mutableNavigationEvent.value = ReminderNavigationEvent(
            id = nextEventId++,
            key = ReminderAlarmIntents.reminderKeyFromContentIntent(intent),
        )
    }

    fun consume(eventId: Long) {
        if (mutableNavigationEvent.value?.id == eventId) {
            mutableNavigationEvent.value = null
        }
    }
}
