# Step 4: wire-startup-permission-lifecycle

## 읽어야 할 파일

- /AGENTS.md
- /docs/ARCHITECTURE.md
- /docs/ADR.md
- /app/src/main/java/com/todoquest/app/TodoQuestApplication.kt
- /app/src/main/java/com/todoquest/app/TodoQuestApp.kt
- /app/src/main/java/com/todoquest/MainActivity.kt
- /app/src/androidTest/java/com/todoquest/app/AppNavigationTest.kt
- /app/src/androidTest/java/com/todoquest/app/MainActivityLaunchSmokeTest.kt
- /phases/050-059/50-notification-and-gameplay-balance/step3.md
- /phases/050-059/50-notification-and-gameplay-balance/index.json

## 작업

application integration test를 production wiring보다 먼저 작성한다. TodoQuestAppContainer가 application context로 SharedPreferencesFirstLaunchNotificationPromptStore와 PrepareFirstLaunchNotificationPromptUseCase를 application-scope 한 번 구성한다. test container는 fake store/usecase를 주입할 수 있어야 하며 production default만 Android implementation을 만든다.

TodoQuestApp와 AppNavigation이 prepare dependency를 CalendarViewModel factory에 전달한다. 첫 Calendar render에서 필요한 경우 한 번 안내하고, Activity recreation, bottom navigation 왕복, Calendar back stack 복원과 새 ViewModel 생성에서 preference가 prompt replay를 막아야 한다. capability 조회·preference 실패는 app launch와 navigation을 crash시키지 않는다. 기존 reminder scheduler/publisher/restore work graph는 변경하지 않는다.

## Acceptance Criteria

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.notification.NotificationPermissionEntryPointsTest" --tests "com.todoquest.feature.calendar.CalendarViewModelTest" --console=plain
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.app.AppNavigationTest,com.todoquest.app.MainActivityLaunchSmokeTest" --console=plain
git diff --check
~~~

## 검증 절차

1. production graph·recreation test를 먼저 추가한다.
2. container와 factory wiring을 구현한다.
3. AC를 실행하고 connected 환경 부재 시 blocked로 기록한다.
4. step 4를 completed와 한국어 summary로 갱신한다.

## 금지사항

- Activity나 App composable에서 SharedPreferences를 직접 읽지 마라. 이유: platform store와 UseCase 경계를 유지해야 한다.
- 기존 database 또는 reminder work 초기화를 prompt 성공에 의존시키지 마라. 이유: 권한 거부 시 핵심 기능이 계속 동작해야 한다.
- process recreation마다 prompt flag를 초기화하지 마라. 이유: 사용자가 자동 재요청을 원하지 않았다.
- 기존 테스트를 깨뜨리지 마라.
