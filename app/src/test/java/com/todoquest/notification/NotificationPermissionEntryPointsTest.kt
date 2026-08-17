package com.todoquest.notification

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.todoquest.domain.repository.ReminderCapabilityStatus
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class NotificationPermissionEntryPointsTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ShadowAlarmManager.reset()
    }

    @Test
    fun firstLaunchCheckPersistsAcrossStoreRecreation() {
        val firstStore = SharedPreferencesFirstLaunchNotificationPromptStore(context)

        assertTrue(firstStore.consumeFirstLaunchCheck())
        assertFalse(firstStore.consumeFirstLaunchCheck())
        assertFalse(
            SharedPreferencesFirstLaunchNotificationPromptStore(context)
                .consumeFirstLaunchCheck(),
        )
    }

    @Test
    fun concurrentStoresConsumeTheFirstLaunchCheckExactlyOnce() {
        val stores = List(32) {
            SharedPreferencesFirstLaunchNotificationPromptStore(context)
        }
        val ready = CountDownLatch(stores.size)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(stores.size)

        val futures = stores.map { store ->
            executor.submit<Boolean> {
                ready.countDown()
                start.await()
                store.consumeFirstLaunchCheck()
            }
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS))
        start.countDown()
        val results = futures.map { it.get(5, TimeUnit.SECONDS) }
        executor.shutdownNow()

        assertEquals(1, results.count { it })
    }

    @Test
    fun failedCommitDoesNotConsumeTheFirstLaunchCheck() {
        val preferences = CommitControlledSharedPreferences(
            delegate = context.getSharedPreferences("commit_failure_test", Context.MODE_PRIVATE),
            commitResults = ArrayDeque(listOf(false, true)),
        )
        val store = SharedPreferencesFirstLaunchNotificationPromptStore(preferences)

        assertFalse(store.consumeFirstLaunchCheck())
        assertTrue(store.consumeFirstLaunchCheck())
        assertFalse(store.consumeFirstLaunchCheck())
    }

    @Test
    @Config(sdk = [23])
    fun api23DisabledNotificationsUsePackageApplicationDetailsFallback() {
        val appOpsManager = context.getSystemService(AppOpsManager::class.java)
        val postNotificationOperation = AppOpsManager::class.java
            .getDeclaredField("OP_POST_NOTIFICATION")
            .getInt(null)
        shadowOf(appOpsManager).setMode(
            postNotificationOperation,
            context.applicationInfo.uid,
            context.packageName,
            AppOpsManager.MODE_IGNORED,
        )

        val action = AndroidReminderCapabilityAdapter(context)
            .firstLaunchNotificationPermissionAction()

        val settings = action as NotificationPermissionLaunchAction.AppSettings
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settings.intent.action)
        assertEquals("package", settings.intent.data?.scheme)
        assertEquals(context.packageName, settings.intent.data?.schemeSpecificPart)
        assertNoExternalSideEffects()
    }

    @Test
    @Config(sdk = [32])
    fun api32DisabledNotificationsUsePackageNotificationSettings() {
        shadowOf(context.getSystemService(NotificationManager::class.java))
            .setNotificationsEnabled(false)

        val action = AndroidReminderCapabilityAdapter(context)
            .firstLaunchNotificationPermissionAction()

        val settings = action as NotificationPermissionLaunchAction.AppSettings
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, settings.intent.action)
        assertEquals(
            context.packageName,
            settings.intent.getStringExtra(Settings.EXTRA_APP_PACKAGE),
        )
        assertNoExternalSideEffects()
    }

    @Test
    @Config(sdk = [33])
    fun api33MissingRuntimePermissionReturnsRuntimePermissionBeforeSettings() {
        shadowOf(context as Application)
            .denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(context.getSystemService(NotificationManager::class.java))
            .setNotificationsEnabled(false)

        val action = AndroidReminderCapabilityAdapter(context)
            .firstLaunchNotificationPermissionAction()

        val runtimePermission = action as NotificationPermissionLaunchAction.RuntimePermission
        assertEquals(Manifest.permission.POST_NOTIFICATIONS, runtimePermission.permission)
        assertNoExternalSideEffects()
    }

    @Test
    @Config(sdk = [35])
    fun api35GrantedRuntimePermissionWithDisabledNotificationsUsesSettings() {
        shadowOf(context as Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(context.getSystemService(NotificationManager::class.java))
            .setNotificationsEnabled(false)

        val action = AndroidReminderCapabilityAdapter(context)
            .firstLaunchNotificationPermissionAction()

        val settings = action as NotificationPermissionLaunchAction.AppSettings
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, settings.intent.action)
        assertEquals(
            context.packageName,
            settings.intent.getStringExtra(Settings.EXTRA_APP_PACKAGE),
        )
        assertNoExternalSideEffects()
    }

    @Test
    @Config(sdk = [35])
    fun channelDisabledFirstLaunchUsesPackageScopedChannelSettings() {
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

        val action = AndroidReminderCapabilityAdapter(context)
            .firstLaunchNotificationPermissionAction()

        val settings = action as NotificationPermissionLaunchAction.AppSettings
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, settings.intent.action)
        assertEquals(
            context.packageName,
            settings.intent.getStringExtra(Settings.EXTRA_APP_PACKAGE),
        )
        assertEquals(
            AndroidReminderPublisher.CHANNEL_ID,
            settings.intent.getStringExtra(Settings.EXTRA_CHANNEL_ID),
        )
        assertNoExternalSideEffects()
    }

    @Test
    @Config(sdk = [35])
    fun channelSettingsIntentFallsBackToPackageNotificationSettingsWhenChannelIsAbsent() {
        val intent = AndroidReminderCapabilityAdapter(context)
            .notificationChannelSettingsIntent()

        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(
            context.packageName,
            intent.getStringExtra(Settings.EXTRA_APP_PACKAGE),
        )
        assertNoExternalSideEffects()
    }

    @Test
    @Config(sdk = [35])
    fun availableCapabilityReturnsNoneWithoutLaunchingOrScheduling() {
        shadowOf(context as Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(context.getSystemService(NotificationManager::class.java))
            .setNotificationsEnabled(true)

        val action = AndroidReminderCapabilityAdapter(context)
            .firstLaunchNotificationPermissionAction()

        assertEquals(NotificationPermissionLaunchAction.None, action)
        assertNoExternalSideEffects()
    }

    @Test
    @Config(sdk = [35])
    fun manualRequiredEntryRequestsRuntimePermissionWhenPostNotificationsIsMissing() {
        shadowOf(context as Application)
            .denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val action = AndroidReminderCapabilityAdapter(context)
            .notificationPermissionSettingsAction(ReminderCapabilityStatus.REQUIRED)

        val runtimePermission = action as NotificationPermissionLaunchAction.RuntimePermission
        assertEquals(Manifest.permission.POST_NOTIFICATIONS, runtimePermission.permission)
        assertNoExternalSideEffects()
    }

    @Test
    @Config(sdk = [35])
    fun manualAvailableAndAppBlockedEntriesUsePackageNotificationSettings() {
        shadowOf(context as Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val adapter = AndroidReminderCapabilityAdapter(context)

        listOf(
            ReminderCapabilityStatus.AVAILABLE,
            ReminderCapabilityStatus.REQUIRED,
        ).forEach { status ->
            val action = adapter.notificationPermissionSettingsAction(status)
            val settings = action as NotificationPermissionLaunchAction.AppSettings
            assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, settings.intent.action)
            assertEquals(
                context.packageName,
                settings.intent.getStringExtra(Settings.EXTRA_APP_PACKAGE),
            )
        }
        assertNoExternalSideEffects()
    }

    @Test
    @Config(sdk = [35])
    fun manualChannelDisabledEntryUsesPackageScopedReminderChannelSettings() {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                AndroidReminderPublisher.CHANNEL_ID,
                "Reminder test",
                NotificationManager.IMPORTANCE_NONE,
            ),
        )

        val action = AndroidReminderCapabilityAdapter(context)
            .notificationPermissionSettingsAction(ReminderCapabilityStatus.CHANNEL_DISABLED)

        val settings = action as NotificationPermissionLaunchAction.AppSettings
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, settings.intent.action)
        assertEquals(
            context.packageName,
            settings.intent.getStringExtra(Settings.EXTRA_APP_PACKAGE),
        )
        assertEquals(
            AndroidReminderPublisher.CHANNEL_ID,
            settings.intent.getStringExtra(Settings.EXTRA_CHANNEL_ID),
        )
        assertNoExternalSideEffects()
    }

    private fun assertNoExternalSideEffects() {
        assertNull(shadowOf(context as Application).nextStartedActivity)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    private class CommitControlledSharedPreferences(
        private val delegate: SharedPreferences,
        private val commitResults: ArrayDeque<Boolean>,
    ) : SharedPreferences by delegate {
        override fun edit(): SharedPreferences.Editor {
            val delegateEditor = delegate.edit()
            return object : SharedPreferences.Editor by delegateEditor {
                override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                    delegateEditor.putBoolean(key, value)
                    return this
                }

                override fun remove(key: String): SharedPreferences.Editor {
                    delegateEditor.remove(key)
                    return this
                }

                override fun commit(): Boolean {
                    val shouldCommit = if (commitResults.isEmpty()) {
                        true
                    } else {
                        commitResults.removeFirst()
                    }
                    return shouldCommit && delegateEditor.commit()
                }
            }
        }
    }
}
