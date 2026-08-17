# Step 5: harden-android-reminder-delivery

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/AndroidManifest.xml`
- `/app/src/main/java/com/todoquest/app/TodoQuestApplication.kt`
- `/app/src/main/java/com/todoquest/notification/AndroidReminderCapabilityAdapter.kt`
- `/app/src/main/java/com/todoquest/notification/AndroidReminderScheduler.kt`
- `/app/src/main/java/com/todoquest/notification/AndroidReminderPublisher.kt`
- `/app/src/main/java/com/todoquest/notification/ReminderAlarmReceiver.kt`
- `/app/src/test/java/com/todoquest/notification/AndroidReminderSchedulerTest.kt`
- `/app/src/test/java/com/todoquest/notification/NotificationPermissionEntryPointsTest.kt`
- `/app/src/test/java/com/todoquest/notification/ReminderAlarmReceiverTest.kt`
- `/app/src/androidTest/java/com/todoquest/notification/ReminderIntegrationTest.kt`
- `/phases/030-039/39-task-reminder-notifications/step5.md`
- `/phases/050-059/52-notification-difficulty-calendar-polish/step1.md`
- `/phases/050-059/52-notification-difficulty-calendar-polish/step2.md`
- `/phases/050-059/52-notification-difficulty-calendar-polish/step3.md`
- `/phases/050-059/52-notification-difficulty-calendar-polish/index.json`

## 작업

Robolectric·integration test를 production보다 먼저 작성하고 Android notification/app wiring 모듈만 수정한다.

`AndroidReminderCapabilityAdapter.status(POST_NOTIFICATIONS)`는 다음 우선순위로 effective delivery capability를 판정한다.

1. API 33+ runtime permission이 없거나 앱 전체 notification이 꺼졌으면 기존 REQUIRED.
2. API 26+에서 `AndroidReminderPublisher.CHANNEL_ID` channel이 존재하고 importance가 `IMPORTANCE_NONE`이면 CHANNEL_DISABLED.
3. channel이 아직 없거나 importance가 0보다 크면 AVAILABLE. channel 부재는 publisher/application이 high importance로 생성할 수 있으므로 차단으로 오판하지 않는다.

channel 차단 복구를 위한 package-scoped intent API를 추가한다. API 26+이고 channel이 존재하면 `Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS`에 현재 package와 channel id를 넣고, 그렇지 않으면 기존 app notification settings 또는 application details로 fallback한다. 첫 Calendar 진입 typed action도 app permission은 켜져 있지만 channel만 차단된 경우 이 channel intent를 반환한다. UI를 adapter가 직접 launch하지 않는다.

`AndroidReminderPublisher`는 publish 전에 channel을 보장한 뒤 effective capability를 다시 확인한다. API 23~25에는 high priority와 함께 default sound/vibration을 notification에 명시한다. API 26+에서는 high importance channel의 기본 소리·진동을 유지하며 이미 존재하는 사용자 channel 설정을 삭제하거나 새 id로 우회하지 않는다. private/public visibility, occurrence별 tag, tap intent, small icon과 한국어 본문은 유지한다.

`AndroidReminderScheduler`는 domain step의 staged plan 계약을 그대로 사용하고 `setExactAndAllowWhileIdle()`만 호출한다. exact permission이 없을 때 inexact fallback을 만들지 않는다. receiver의 goAsync/provider 경계, duplicate claim과 error logging을 유지한다.

production Room builder에 `MIGRATION_13_14`를 추가하고 app startup channel 생성·restore work 순서를 유지한다. manifest permission/receiver는 새 권한이나 full-screen intent를 추가하지 않는다.

테스트는 runtime missing, app notifications off, channel absent, channel high, channel importance NONE, channel settings intent package/id, pre-O defaults, staged PENDING receiver delivery, duplicate/stale suppression, v13 production migration wiring을 포함한다.

## Acceptance Criteria

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.notification.AndroidReminderSchedulerTest" --tests "com.todoquest.notification.NotificationPermissionEntryPointsTest" --tests "com.todoquest.notification.ReminderAlarmReceiverTest" --tests "com.todoquest.domain.ReminderUseCaseTest" --console=plain
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.notification.ReminderIntegrationTest" --console=plain
git diff --check
~~~

## 검증 절차

1. channel importance·settings intent·PENDING delivery test를 먼저 실패시킨다.
2. capability adapter, publisher, scheduler와 production migration wiring을 구현한다.
3. AC를 실행하고 emulator가 없거나 Android 도구가 없으면 설치하지 말고 step을 `blocked`로 기록한다.
4. 성공 시 step 5를 `completed`와 한국어 summary로 갱신한다.

## 금지사항

- channel을 삭제하거나 새 channel id로 사용자 차단을 우회하지 마라. 이유: 사용자가 선택한 notification 설정을 존중해야 한다.
- full-screen intent, alarm clock API 또는 DND 우회를 추가하지 마라. 이유: 일반 일정 reminder 범위와 Android 정책을 벗어난다.
- inexact alarm fallback을 추가하지 마라. 이유: 기존 Task Reminder exact-time 계약과 typed 실패를 유지해야 한다.
- UI에서 AlarmManager, NotificationManager, DAO 또는 WorkManager를 직접 호출하게 하지 마라. 이유: CRITICAL architecture boundary를 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
