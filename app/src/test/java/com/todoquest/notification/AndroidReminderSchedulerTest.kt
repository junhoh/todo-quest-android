package com.todoquest.notification

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.todoquest.MainActivity
import com.todoquest.R
import com.todoquest.domain.model.ReminderNotificationPayload
import com.todoquest.domain.model.ReminderOccurrenceKey
import com.todoquest.domain.model.ReminderPlan
import com.todoquest.domain.model.ReminderScheduleStatus
import com.todoquest.domain.repository.ReminderCapability
import com.todoquest.domain.repository.ReminderCapabilityStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(application = PlainReminderTestApplication::class)
class AndroidReminderSchedulerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ShadowAlarmManager.reset()
    }

    @Test
    @Config(sdk = [23])
    fun api23TreatsExactAlarmAsAvailable() = runTest {
        val scheduler = AndroidReminderScheduler(context)

        assertEquals(
            ReminderCapabilityStatus.AVAILABLE,
            scheduler.capabilityStatus(ReminderCapability.EXACT_ALARM),
        )
        assertEquals(
            ReminderCapabilityStatus.AVAILABLE,
            scheduler.capabilityStatus(ReminderCapability.POST_NOTIFICATIONS),
        )
    }

    @Test
    @Config(sdk = [31])
    fun api31RequiresSpecialAccessAndDoesNotCallAlarmManagerWhenMissing() = runTest {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val scheduler = AndroidReminderScheduler(context)

        assertEquals(
            ReminderCapabilityStatus.REQUIRED,
            scheduler.capabilityStatus(ReminderCapability.EXACT_ALARM),
        )
        assertEquals(
            ReminderScheduleStatus.EXACT_ALARM_ACCESS_REQUIRED,
            scheduler.scheduleExact(plan(taskId = 1L, occurrenceEpochDay = 20L)),
        )

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Test
    @Config(sdk = [33])
    fun api33RequiresPostNotificationsPermission() = runTest {
        shadowOf(context as android.app.Application)
            .denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        shadowOf(notificationManager).setNotificationsEnabled(true)

        val scheduler = AndroidReminderScheduler(context)

        assertEquals(
            ReminderCapabilityStatus.REQUIRED,
            scheduler.capabilityStatus(ReminderCapability.POST_NOTIFICATIONS),
        )
    }

    @Test
    @Config(sdk = [35])
    fun appNotificationSwitchOffRequiresPostNotificationsRecovery() = runTest {
        shadowOf(context as Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        shadowOf(notificationManager).setNotificationsEnabled(false)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                AndroidReminderPublisher.CHANNEL_ID,
                "Reminder test",
                NotificationManager.IMPORTANCE_NONE,
            ),
        )

        val scheduler = AndroidReminderScheduler(context)

        assertEquals(
            ReminderCapabilityStatus.REQUIRED,
            scheduler.capabilityStatus(ReminderCapability.POST_NOTIFICATIONS),
        )
    }

    @Test
    @Config(sdk = [35])
    fun absentReminderChannelIsAvailableBecausePublisherCanCreateIt() = runTest {
        shadowOf(context as Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        shadowOf(notificationManager).setNotificationsEnabled(true)
        assertNull(
            notificationManager.getNotificationChannel(AndroidReminderPublisher.CHANNEL_ID),
        )

        val scheduler = AndroidReminderScheduler(context)

        assertEquals(
            ReminderCapabilityStatus.AVAILABLE,
            scheduler.capabilityStatus(ReminderCapability.POST_NOTIFICATIONS),
        )
    }

    @Test
    @Config(sdk = [35])
    fun highImportanceReminderChannelIsAvailable() = runTest {
        shadowOf(context as Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        shadowOf(notificationManager).setNotificationsEnabled(true)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                AndroidReminderPublisher.CHANNEL_ID,
                "Reminder test",
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )

        val scheduler = AndroidReminderScheduler(context)

        assertEquals(
            ReminderCapabilityStatus.AVAILABLE,
            scheduler.capabilityStatus(ReminderCapability.POST_NOTIFICATIONS),
        )
    }

    @Test
    @Config(sdk = [35])
    fun disabledReminderChannelHasDistinctCapabilityStatus() = runTest {
        shadowOf(context as Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        shadowOf(notificationManager).setNotificationsEnabled(true)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                AndroidReminderPublisher.CHANNEL_ID,
                "Reminder test",
                NotificationManager.IMPORTANCE_NONE,
            ),
        )

        val scheduler = AndroidReminderScheduler(context)

        assertEquals(
            ReminderCapabilityStatus.CHANNEL_DISABLED,
            scheduler.capabilityStatus(ReminderCapability.POST_NOTIFICATIONS),
        )
    }

    @Test
    @Config(sdk = [35])
    fun api35ReportsBothCapabilitiesAvailableWhenGranted() = runTest {
        shadowOf(context as android.app.Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(context.getSystemService(NotificationManager::class.java))
            .setNotificationsEnabled(true)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val scheduler = AndroidReminderScheduler(context)

        assertEquals(
            ReminderCapabilityStatus.AVAILABLE,
            scheduler.capabilityStatus(ReminderCapability.POST_NOTIFICATIONS),
        )
        assertEquals(
            ReminderCapabilityStatus.AVAILABLE,
            scheduler.capabilityStatus(ReminderCapability.EXACT_ALARM),
        )
    }

    @Test
    @Config(sdk = [31])
    fun exactScheduleAndCancelUseSameImmutableOccurrenceIdentity() = runTest {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val scheduler = AndroidReminderScheduler(context)
        val first = plan(taskId = 12L, occurrenceEpochDay = 20_000L)
        val second = plan(taskId = 12L, occurrenceEpochDay = 20_001L)

        assertEquals(ReminderScheduleStatus.SCHEDULED, scheduler.scheduleExact(first))
        assertEquals(ReminderScheduleStatus.SCHEDULED, scheduler.scheduleExact(second))

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertEquals(2, alarms.size)
        assertTrue(alarms.all { it.type == AlarmManager.RTC_WAKEUP })
        assertTrue(alarms.all { it.allowWhileIdle })
        val pendingIntents = alarms.map { it.operation }
        assertEquals(1, pendingIntents.map { shadowOf(it).requestCode }.distinct().size)
        assertTrue(pendingIntents.all { shadowOf(it).isImmutable })
        assertTrue(pendingIntents.all { shadowOf(it).isBroadcast })
        assertNotEquals(
            shadowOf(pendingIntents[0]).savedIntent.data,
            shadowOf(pendingIntents[1]).savedIntent.data,
        )
        pendingIntents.forEach { pendingIntent ->
            val intent = shadowOf(pendingIntent).savedIntent
            assertEquals(ReminderAlarmIntents.ACTION_DELIVER_REMINDER, intent.action)
            assertEquals(ReminderAlarmReceiver::class.java.name, intent.component?.className)
            assertNull(intent.getStringExtra("title"))
            assertNull(intent.getStringExtra("memo"))
        }

        assertEquals(ReminderScheduleStatus.DISABLED, scheduler.cancel(first.key))

        val remaining = shadowOf(alarmManager).scheduledAlarms.single()
        assertEquals(second.key.taskId, shadowOf(remaining.operation).savedIntent.taskIdExtra())
        assertEquals(
            second.key.occurrenceDate.toEpochDay(),
            shadowOf(remaining.operation).savedIntent.occurrenceEpochDayExtra(),
        )
    }

    @Test
    @Config(sdk = [35])
    fun capabilityAdapterProvidesRequestsWithoutLaunchingUi() {
        val adapter = AndroidReminderCapabilityAdapter(context)

        assertEquals(
            listOf(Manifest.permission.POST_NOTIFICATIONS),
            adapter.notificationRuntimePermissions().toList(),
        )
        val intent = adapter.exactAlarmAccessIntent()
        assertNotNull(intent)
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, intent?.action)
        assertEquals("package", intent?.data?.scheme)
        assertEquals(context.packageName, intent?.data?.schemeSpecificPart)
    }

    @Test
    @Config(sdk = [35])
    fun publisherCreatesHighImportanceChannelAndPrivateReminderWithTapExtras() = runTest {
        shadowOf(context as android.app.Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        shadowOf(notificationManager).setNotificationsEnabled(true)
        val publisher = AndroidReminderPublisher(context)
        val key = ReminderOccurrenceKey(42L, LocalDate.of(2026, 7, 29))

        publisher.publish(
            ReminderNotificationPayload(
                key = key,
                title = "주간 회고",
                memo = "지난주 기록 확인",
                occurrenceDate = key.occurrenceDate,
                taskTime = LocalTime.of(9, 5),
            ),
        )

        val channel = notificationManager.getNotificationChannel(
            AndroidReminderPublisher.CHANNEL_ID,
        )
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertTrue(channel.shouldVibrate())
        assertNotNull(channel.sound)
        assertEquals(
            context.getString(R.string.reminder_notification_channel_name),
            channel.name,
        )
        assertEquals(
            context.getString(R.string.reminder_notification_channel_description),
            channel.description,
        )

        val notification = shadowOf(notificationManager).allNotifications.single()
        val notificationShadow = shadowOf(notification)
        assertEquals("주간 회고", notificationShadow.contentTitle)
        assertTrue(notificationShadow.contentText.toString().contains("2026년 7월 29일"))
        assertTrue(notificationShadow.contentText.toString().contains("09:05"))
        assertEquals(Notification.CATEGORY_REMINDER, notification.category)
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertEquals(R.drawable.ic_notification_reminder, notification.smallIcon.resId)
        assertNotNull(notification.publicVersion)
        assertEquals(
            context.getString(R.string.reminder_notification_public_text),
            shadowOf(notification.publicVersion).contentText,
        )

        val tapPendingIntent = requireNotNull(notification.contentIntent)
        val tapIntent = shadowOf(tapPendingIntent).savedIntent
        assertTrue(shadowOf(tapPendingIntent).isImmutable)
        assertTrue(shadowOf(tapPendingIntent).isActivity)
        assertEquals(MainActivity::class.java.name, tapIntent.component?.className)
        assertEquals(key.taskId, tapIntent.taskIdExtra())
        assertEquals(key.occurrenceDate.toEpochDay(), tapIntent.occurrenceEpochDayExtra())
        assertTrue(tapIntent.flags and android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(tapIntent.flags and android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test
    @Config(sdk = [23])
    fun preOreoPublisherRequestsHighPriorityDefaultSoundAndVibration() = runTest {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val publisher = AndroidReminderPublisher(context)
        val key = ReminderOccurrenceKey(43L, LocalDate.of(2026, 7, 30))

        publisher.publish(
            ReminderNotificationPayload(
                key = key,
                title = "오전 일정",
                memo = "",
                occurrenceDate = key.occurrenceDate,
                taskTime = null,
            ),
        )

        val notification = shadowOf(notificationManager).allNotifications.single()
        assertEquals(NotificationCompat.PRIORITY_HIGH, notification.priority)
        assertTrue(notification.defaults and Notification.DEFAULT_SOUND != 0)
        assertTrue(notification.defaults and Notification.DEFAULT_VIBRATE != 0)
    }

    @Test
    @Config(sdk = [35])
    fun publisherPreservesDisabledChannelAndDoesNotPost() = runTest {
        shadowOf(context as Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        shadowOf(notificationManager).setNotificationsEnabled(true)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                AndroidReminderPublisher.CHANNEL_ID,
                "사용자 차단 채널",
                NotificationManager.IMPORTANCE_NONE,
            ),
        )
        val publisher = AndroidReminderPublisher(context)
        val key = ReminderOccurrenceKey(44L, LocalDate.of(2026, 7, 31))

        var failure: Throwable? = null
        try {
            publisher.publish(
                ReminderNotificationPayload(
                    key = key,
                    title = "차단된 알림",
                    memo = "",
                    occurrenceDate = key.occurrenceDate,
                    taskTime = null,
                ),
            )
        } catch (caught: Throwable) {
            failure = caught
        }

        assertTrue(failure is ReminderNotificationUnavailableException)
        assertEquals(
            NotificationManager.IMPORTANCE_NONE,
            notificationManager.getNotificationChannel(
                AndroidReminderPublisher.CHANNEL_ID,
            ).importance,
        )
        assertEquals(0, shadowOf(notificationManager).size())
    }

    @Test
    @Config(sdk = [35])
    fun publisherDoesNotPostWhenRuntimePermissionIsDenied() = runTest {
        shadowOf(context as android.app.Application)
            .denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        shadowOf(notificationManager).setNotificationsEnabled(true)
        val publisher = AndroidReminderPublisher(context)
        val key = ReminderOccurrenceKey(1L, LocalDate.of(2026, 7, 29))

        var failure: Throwable? = null
        try {
            publisher.publish(
                ReminderNotificationPayload(
                    key = key,
                    title = "알림",
                    memo = "",
                    occurrenceDate = key.occurrenceDate,
                    taskTime = null,
                ),
            )
        } catch (caught: Throwable) {
            failure = caught
        }

        assertTrue(failure is ReminderNotificationUnavailableException)
        assertEquals(0, shadowOf(notificationManager).size())
        assertEquals(
            PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS),
        )
    }

    private fun plan(taskId: Long, occurrenceEpochDay: Long) = ReminderPlan(
        key = ReminderOccurrenceKey(taskId, LocalDate.ofEpochDay(occurrenceEpochDay)),
        triggerAt = Instant.parse("2026-07-29T00:00:00Z").plusSeconds(occurrenceEpochDay),
    )
}

class PlainReminderTestApplication : Application()
