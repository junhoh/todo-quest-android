# Step 3: add-calendar-permission-rationales

## 읽어야 할 파일

- /AGENTS.md
- /docs/UI_GUIDE.md
- /app/src/main/java/com/todoquest/feature/calendar/CalendarUiState.kt
- /app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt
- /app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt
- /app/src/main/res/values/strings.xml
- /app/src/test/java/com/todoquest/feature/calendar/CalendarViewModelTest.kt
- /app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt
- /phases/050-059/50-notification-and-gameplay-balance/step1.md
- /phases/050-059/50-notification-and-gameplay-balance/step2.md
- /phases/050-059/50-notification-and-gameplay-balance/index.json

## 작업

ViewModel unit test와 Compose test를 먼저 작성한다. CalendarUiState에 nullable NotificationPermissionPromptUiState를 추가하고 origin은 FIRST_LAUNCH와 REMINDER 두 값으로 제한한다. CalendarViewModel은 suspend prepareFirstLaunchNotificationPrompt dependency를 받아 init에서 한 번 호출하되 기본 no-op을 제공해 step 4 wiring 전 source compatibility를 유지한다.

최초 prompt confirm은 CalendarEvent.RequestPostNotificationsPermission을, reminder prompt confirm은 CalendarEvent.OpenNotificationSettings를 replay 없는 event channel로 보낸다. dismiss는 prompt만 닫고 자동 재표시하지 않는다. runtime 결과가 denied이면 기존 한국어 permission denied message를 표시하되 task editor나 일정 기능을 막지 않는다.

handleReminderStatus가 POST_NOTIFICATIONS_REQUIRED를 받으면 직접 permission event를 즉시 보내지 말고 REMINDER prompt를 표시한다. settings 복귀 시 lastSavedReminderTaskId가 있으면 reconcile하고, 없으면 prompt/message만 안전하게 정리한다. 승인 후 EXACT_ALARM_ACCESS_REQUIRED이면 기존 exact rationale을 이어서 표시한다.

CalendarScreen은 FIRST_LAUNCH confirm에서 adapter의 RuntimePermission/AppSettings/None을 각각 runtime launcher, notification settings launcher, granted callback으로 처리한다. REMINDER event는 항상 notificationSettingsIntent를 연다. 모든 dialog title/message/CTA/나중에 문구는 strings.xml의 한국어 resource를 사용한다. 재구성과 ActivityResult 복귀에서 system prompt를 replay하지 않는다.

## Acceptance Criteria

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.calendar.CalendarViewModelTest" --console=plain
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.calendar.CalendarScreenTest" --console=plain
git diff --check
~~~

## 검증 절차

1. first launch/dismiss/deny/reminder/settings/exact 순서 test를 먼저 추가한다.
2. UI state·ViewModel·Compose·resources를 구현한다.
3. AC를 실행하고 연결 기기가 없으면 도구를 설치하지 말고 blocked로 기록한다.
4. step 3을 completed와 한국어 summary로 갱신한다.

## 금지사항

- Compose나 ViewModel에서 DAO, AlarmManager, WorkManager를 호출하지 마라. 이유: CRITICAL UI boundary를 유지해야 한다.
- 사용자 노출 문구를 Kotlin에 하드코딩하지 마라. 이유: 한국어 resource 규칙을 지켜야 한다.
- permission 거부를 일정 저장 실패로 표시하지 마라. 이유: reminder capability는 핵심 transaction과 독립적이다.
- 최초 실행에 exact alarm dialog를 표시하지 마라. 이유: 사용자가 reminder를 설정하기 전에는 필요하지 않다.
- 기존 테스트를 깨뜨리지 마라.
