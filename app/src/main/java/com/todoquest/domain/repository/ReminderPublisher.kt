package com.todoquest.domain.repository

import com.todoquest.domain.model.ReminderNotificationPayload

fun interface ReminderPublisher {
    suspend fun publish(payload: ReminderNotificationPayload)
}
