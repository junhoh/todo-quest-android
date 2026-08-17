# Step 5: implement-android-reminder-delivery

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/app/build.gradle.kts`
- `/app/src/main/AndroidManifest.xml`
- `/app/src/main/java/com/todoquest/MainActivity.kt`
- `/app/src/main/java/com/todoquest/background/CombatReconciliationWorker.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/main/res/drawable/ic_splash_todoquest.xml`
- `/app/src/test/java/com/todoquest/background/CombatReconciliationWorkerTest.kt`
- `/phases/030-039/39-task-reminder-notifications/step1.md`
- `/phases/030-039/39-task-reminder-notifications/step4.md`
- `/phases/030-039/39-task-reminder-notifications/index.json`

## 작업

Robolectric·WorkManager test를 production 코드보다 먼저 작성하고 `notification/` Android module을 구현한다. 이 step은 Android scheduler, notification publisher, receivers, restore work, manifest registration과 관련 resources에 한정한다. production composition-root wiring과 Calendar UI는 step 7과 step 6에서 수행한다.

`AndroidReminderScheduler`는 minSdk 23을 지원한다. API 31+에서 `AlarmManager.canScheduleExactAlarms()`를 검사하고 `SCHEDULE_EXACT_ALARM` special access가 있을 때만 `setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ...)`를 호출한다. API 23..30은 exact alarm capability를 available로 본다. `USE_EXACT_ALARM`, inexact fallback, listener-based alarm은 사용하지 않는다.

PendingIntent는 explicit non-exported `ReminderAlarmReceiver`, immutable/update-current flag, 고정 action과 `todoquest://reminder/{taskId}/{occurrenceEpochDay}` data URI로 occurrence identity를 결정한다. schedule/cancel 모두 동일 factory를 사용하고 requestCode hash 충돌에 의존하지 않는다. intent extra는 typed key만 운반하며 title/memo를 신뢰 source로 넣지 않는다.

`AndroidReminderPublisher`는 `todo_task_reminders` channel을 API 26+에서 `IMPORTANCE_HIGH`, 기본 sound·vibration으로 idempotently 생성한다. `NotificationCompat` 또는 현재 dependency로 지원되는 공식 builder를 사용하며 전용 monochrome `ic_notification_reminder` small icon, task title, 한국어 reminder body, occurrence 시각, auto-cancel, `CATEGORY_REMINDER`, private lockscreen visibility를 설정한다. channel name·description·notification body·public lockscreen text는 모두 `strings.xml`에 둔다. API 33+ permission과 `NotificationManager.areNotificationsEnabled()`를 다시 검사한다. tap PendingIntent는 explicit `MainActivity`에 taskId·occurrenceDate를 전달하고 기존 back stack을 중복 생성하지 않는다.

`ReminderAlarmReceiver`는 `goAsync()`와 제한된 application-scope coroutine으로 `DeliverReminderUseCase`를 호출하고 항상 `PendingResult.finish()`를 보장한다. receiver는 DAO, AlarmManager 구현, NotificationManager를 직접 조합하지 않고 application이 제공하는 `ReminderRuntimeProvider` interface에서 use case를 받는다. provider가 없거나 예외가 발생하면 process를 crash시키지 않고 diagnostic log를 남긴다.

`ReminderRestoreReceiver`는 `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, API 31+ `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`를 처리하고 unique one-time `ReminderReconciliationWorker`만 enqueue한다. receiver가 Room을 직접 읽지 않는다. worker는 같은 provider의 `ReconcileAllRemindersUseCase`를 호출하고 transient database failure만 최대 3회 retry한다. 앱 시작 one-time enqueue helper도 제공하되 기존 combat periodic work 이름·정책과 분리한다.

manifest에 `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`와 두 receiver를 등록한다. receiver는 `android:exported="false"`를 기본으로 하고 system broadcast 수신에 필요한 intent filter만 둔다. notification runtime permission request와 special access settings launch를 위한 작은 capability/intent adapter를 제공하지만 UI를 직접 띄우지 않는다.

테스트는 API 23/31/33/35 capability, exact schedule/cancel identity, missing permission no AlarmManager call, channel properties, permission denied no post, notification content/tap extras, alarm receiver valid/stale delegation, restore broadcasts의 unique work, worker success/retry/failure를 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.notification.AndroidReminderSchedulerTest" --tests "com.todoquest.notification.ReminderAlarmReceiverTest" --tests "com.todoquest.notification.ReminderReconciliationWorkerTest" --console=plain
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
git diff --check
```

## 검증 절차

1. scheduler·receiver·worker test를 먼저 작성해 Android implementation 부재 실패를 확인한다.
2. notification module·manifest·resources를 구현하고 AC를 실행한다.
3. PendingIntent identity가 schedule/cancel에서 완전히 동일하고 immutable인지 확인한다.
4. UI, DAO, combat worker와 책임이 섞이지 않았는지 검토한다.
5. task index의 step 5를 `completed`로 바꾸고 exact delivery·restore 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- WorkManager로 reminder trigger를 예약하지 마라. 이유: 정확한 사용자 지정 시각을 보장할 수 없다.
- `USE_EXACT_ALARM`이나 inexact fallback을 추가하지 마라. 이유: 승인된 권한·정확도 계약과 배포 정책을 바꾼다.
- exported receiver나 mutable PendingIntent를 사용하지 마라. 이유: 외부 앱이 알림 payload나 schedule을 위조할 수 있다.
- receiver에서 Room DAO를 직접 호출하지 마라. 이유: repository/use case 경계를 유지해야 한다.
- 사용자 노출 문구를 Kotlin에 하드코딩하지 마라. 이유: 한국어 문자열 resource 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
