# Step 7: wire-reminder-application-flow

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/DEVELOPMENT.md`
- `/app/src/main/java/com/todoquest/app/TodoQuestApplication.kt`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/app/src/main/java/com/todoquest/MainActivity.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/notification/ReminderAlarmReceiver.kt`
- `/app/src/main/java/com/todoquest/notification/ReminderReconciliationWorker.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/app/src/test/java/com/todoquest/data/local/TodoQuestDatabaseMigrationTest.kt`
- `/app/src/test/java/com/todoquest/notification/ReminderReconciliationWorkerTest.kt`
- `/app/src/androidTest/java/com/todoquest/app/TodoQuestDatabaseIsolationTest.kt`
- `/app/src/androidTest/java/com/todoquest/app/AppNavigationTest.kt`
- `/phases/030-039/39-task-reminder-notifications/step2.md`
- `/phases/030-039/39-task-reminder-notifications/step3.md`
- `/phases/030-039/39-task-reminder-notifications/step4.md`
- `/phases/030-039/39-task-reminder-notifications/step5.md`
- `/phases/030-039/39-task-reminder-notifications/step6.md`
- `/phases/030-039/39-task-reminder-notifications/index.json`

## 작업

application-scope integration test를 production wiring보다 먼저 작성한다. 이 step은 composition root, production Room migration wiring, Activity notification destination과 end-to-end 연결에 한정한다.

`TodoQuestAppContainer`는 `RoomReminderRepository`, `AndroidReminderScheduler`, `AndroidReminderPublisher`, diagnostic sink와 step 4의 create/update/delete/reconcile/deliver use case를 application scope 단일 instance로 구성한다. test container가 fake repository/scheduler/publisher를 주입할 수 있게 optional constructor injection을 제공하되 production default만 Android implementation을 만든다. 기존 Task·Character·Combat·Equipment Repository가 공유하는 동일 database와 clock을 사용한다.

`TodoQuestApplication`은 `ReminderRuntimeProvider`를 구현해 alarm receiver와 worker에 application-scope use case를 제공한다. `onCreate()`는 기존 combat work와 별도 unique reminder startup reconciliation을 enqueue하고 notification channel을 준비한다. 이 초기화 실패가 앱 launch나 Calendar를 crash시키지 않게 diagnostic으로 격리한다.

production과 모든 file-backed test `Room.databaseBuilder`에 `MIGRATION_9_10`을 연결한다. in-memory current schema test에는 불필요한 migration을 추가하지 않는다. v9 file fixture를 production graph로 v10 reopen해 기존 데이터와 legacy NONE fallback을 검증한다. destructive fallback을 추가하지 않는다.

`TodoQuestApp`의 Calendar factory는 step 4 create/update/delete/reconcile use case와 reminder capability adapter를 ViewModel에 실제 주입해 legacy direct repository save 경로를 production에서 사용하지 않게 한다. 기존 complete/fail/undo use case도 reminder reconcile dependency가 연결된 instance를 사용한다.

notification tap의 explicit intent에서 task id와 occurrence epoch day를 안전하게 parse한다. `MainActivity`의 cold start와 `onNewIntent`를 모두 처리하고 Calendar destination으로 이동해 occurrence 날짜를 선택한다. 잘못된 extra, 삭제된 task, 범위 밖 epoch day는 crash 없이 기본 Calendar로 연다. configuration change와 탭 재선택에서 intent event를 중복 소비하지 않는다.

connected integration은 실제 Room create/update→AlarmManager schedule, permission denial 시 task 저장 유지, receiver valid/stale delivery, notification tap date navigation, app startup·restore work, Activity recreation을 검증한다. 별도 background smoke fixture는 15~60초 뒤 custom reminder를 repository/use case로 seed하고 target process가 background/종료된 뒤 알림이 게시되는지 확인한다. Android 13+ notification과 exact special access가 필요한 실제 smoke에서는 현재 기기 설정을 확인하고 사용자 환경을 임의 변경하지 않는다. `adb shell cmd activity stop-app com.todoquest` 또는 일반 process kill을 사용하고 `am force-stop`은 사용하지 않는다. Force Stop은 PendingIntent를 취소하는 OS 의도 동작이므로 별도 제한으로 기록한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.local.TodoQuestDatabaseMigrationTest" --tests "com.todoquest.notification.ReminderReconciliationWorkerTest" --tests "com.todoquest.feature.calendar.CalendarViewModelTest" --console=plain
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.app.TodoQuestDatabaseIsolationTest,com.todoquest.app.AppNavigationTest,com.todoquest.notification.ReminderIntegrationTest" --console=plain
git diff --check
```

## 검증 절차

1. application graph·migration·notification destination integration test를 먼저 작성한다.
2. production container, Application, factory, Activity wiring을 구현하고 AC를 실행한다.
3. 실제 production graph에서 Calendar mutation이 use case를 거쳐 schedule/cancel되는지 확인한다.
4. 연결 기기가 없거나 special access를 사람이 허용해야 하는 smoke만 남으면 Android 도구를 설치하지 말고 구체적인 `blocked_reason`을 기록한다.
5. task index의 step 7을 `completed`로 바꾸고 application wiring·process-absent 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- migration 누락을 destructive fallback으로 우회하지 마라. 이유: 기존 사용자 일정·캐릭터·전투·장비 데이터를 보존해야 한다.
- production Calendar factory에 direct repository save fallback을 남겨 실제 알림 조정을 건너뛰지 마라. 이유: UI와 scheduler lifecycle이 분리된다.
- notification tap extra를 검증 없이 LocalDate로 변환하지 마라. 이유: malformed intent로 앱이 crash할 수 있다.
- `am force-stop` 결과를 일반 앱 종료 알림 실패로 판단하지 마라. 이유: Android가 stopped package의 PendingIntent를 의도적으로 취소한다.
- Android SDK, emulator, system image 또는 사용자 환경 변수를 설치·변경하지 마라. 이유: 도구 준비는 별도 승인 phase 범위다.
- 기존 테스트를 깨뜨리지 마라.
