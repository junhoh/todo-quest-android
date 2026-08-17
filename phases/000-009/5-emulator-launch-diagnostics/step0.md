# Step 0: launch-smoke-test

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/app/build.gradle.kts`
- `/app/src/main/AndroidManifest.xml`
- `/app/src/main/java/com/todoquest/MainActivity.kt`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt`

## 작업

Android emulator에서 build 이후 앱 화면이 검은 화면으로 보이는 문제를 자동으로 판별할 수 있는 instrumentation smoke test를 추가한다. 새 테스트는 기존 기능 흐름 테스트와 분리해서 앱 최초 실행과 실제 렌더링 상태만 검증한다.

- `/app/src/androidTest/java/com/todoquest/app/MainActivityLaunchSmokeTest.kt`를 추가한다.
- 테스트 클래스는 `createAndroidComposeRule<MainActivity>()`를 사용해 실제 `MainActivity`를 실행한다.
- 첫 테스트는 초기 화면의 핵심 Compose semantics가 표시되는지 검증한다.
  - `onNodeWithText("Todo Quest").assertIsDisplayed()`
  - `onNodeWithTag("calendar-month-grid").assertIsDisplayed()`
  - `onNodeWithTag("task-list").assertIsDisplayed()`
  - `onNodeWithTag("add-task-button").assertIsDisplayed()`
- 두 번째 테스트는 `InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()`으로 실제 화면 bitmap을 캡처한다.
- 캡처한 bitmap은 `context.getExternalFilesDir(null)` 또는 instrumentation additional output으로 수집 가능한 앱 외부 파일 영역에 `main-activity-launch.png` 이름으로 저장한다.
- bitmap의 중심 영역을 일정 간격으로 샘플링해 거의 검정인 픽셀 비율과 평균 밝기를 계산한다.
- 검은 화면 판정 기준은 다음 상수로 테스트 내부에 명시한다.
  - 픽셀 밝기 계산: `(red + green + blue) / 3`
  - 검정 픽셀: 밝기 `16` 이하
  - 실패 조건: 샘플 중 검정 픽셀 비율이 `0.95` 이상이면서 평균 밝기가 `24` 이하
- 실패 메시지에는 검정 픽셀 비율, 평균 밝기, screenshot 저장 경로를 포함한다.
- screenshot을 얻지 못하면 테스트를 실패시키고, 실패 메시지에 `takeScreenshot returned null`을 포함한다.
- 테스트는 앱 코드 경로를 우회하지 말고 `MainActivity -> TodoQuestApp -> CalendarScreen` 실제 경로를 사용한다.

## Acceptance Criteria

```powershell
adb devices
.\gradlew.bat connectedDebugAndroidTest
```

## 검증 절차

1. `adb devices`에서 emulator 또는 실제 기기가 `device` 상태인지 확인한다.
2. 연결된 Android 기기가 없으면 `/phases/000-009/5-emulator-launch-diagnostics/index.json`의 step 0을 `blocked`로 기록하고, `blocked_reason`에 연결 기기 부재를 한국어로 남긴다.
3. 연결된 Android 기기가 있으면 Acceptance Criteria 명령을 실행한다.
4. 테스트 실패 시 Gradle report, generated logcat, screenshot 저장 경로를 확인해 검은 화면인지 앱 크래시인지 구분한다.
5. 성공하면 step 0 상태를 `completed`로 바꾸고 `completed_at`, 한국어 `summary`를 기록한다.

## 금지사항

- 테스트를 통과시키기 위해 `MainActivity` 대신 테스트 전용 fake 화면만 렌더링하지 마라. 이유: 사용자가 겪는 build 이후 실제 앱 실행 문제를 검증해야 한다.
- UI 레이어에서 Room DAO, AlarmManager, WorkManager를 직접 호출하지 마라. 이유: AGENTS.md의 CRITICAL 아키텍처 규칙을 위반한다.
- 검은 화면을 색상 테마만으로 단정하지 마라. 이유: 현재 앱은 dark theme이므로 semantics 표시와 screenshot 픽셀 판정을 함께 봐야 한다.
- Android 도구나 SDK를 임의 설치하지 마라. 이유: 개발 환경 준비는 별도 승인된 phase에서만 수행한다.
- 기존 테스트를 깨뜨리지 마라.
