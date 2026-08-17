# Step 0: connected-ui-test-determinism

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/app/MainActivityLaunchSmokeTest.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`

## 작업

전체 connected instrumentation suite에서 `CalendarScreenTest.timeInputModeTypedValueShowsFormattedTimeInMetadata`가 동일 metadata 텍스트 2개를 만나 실패할 수 있다. 목록 metadata는 시간/반복/난이도/카테고리가 같으면 여러 일정에서 중복될 수 있으므로, 테스트가 전역 단일 노드를 가정하지 않도록 수정한다.

- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt`의 `assertListTextDisplayed(text: String)`를 보강한다.
  - `task-lazy-list`에서 `performScrollToNode(hasText(text))`를 먼저 유지한다.
  - 이후 전역 `onNodeWithText(text).assertIsDisplayed()` 대신 `onAllNodesWithText(text).fetchSemanticsNodes().any { ... }` 또는 Compose test API의 표시 여부 검사로, 같은 텍스트 노드가 여러 개여도 표시된 노드가 하나 이상이면 통과하도록 한다.
  - 테스트 helper만 변경하고 production UI 의미론을 테스트 편의용으로 왜곡하지 않는다.
- 필요하면 `assertListTextDisplayed`가 title과 memo처럼 원래 유일한 텍스트에도 동일하게 동작하도록 유지한다.
- `MainActivityLaunchSmokeTest`의 screenshot 검은 화면 판정 기준과 semantics assertion은 삭제하거나 완화하지 않는다.
- UI production 코드는 이 step에서 수정하지 않는다.

## Acceptance Criteria

```powershell
adb devices
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.calendar.CalendarScreenTest
.\gradlew.bat test
git diff --check
```

## 검증 절차

1. `adb devices`에서 emulator 또는 실제 기기가 `device` 상태인지 확인한다.
2. 연결된 Android 기기가 없으면 step 0을 `blocked`로 기록하고, `blocked_reason`에 연결 기기 부재를 한국어로 남긴다.
3. Acceptance Criteria 명령을 실행한다.
4. `CalendarScreenTest`에서 동일 metadata 텍스트 중복으로 실패하지 않는지 확인한다.
5. `AGENTS.md`, `ARCHITECTURE.md`, `ADR.md`의 CRITICAL 규칙을 확인한다.
6. 성공하면 `/phases/010-019/10-emulator-connected-regression-fix/index.json`의 step 0을 `completed`로 바꾸고 `completed_at`, 한국어 `summary`를 기록한다.
7. 3회 수정 후에도 실패하면 step 0을 `error`로 바꾸고 `failed_at`, `error_message`를 기록한다.

## 금지사항

- metadata 텍스트가 앱에서 유일하다고 가정하지 마라. 이유: 같은 시간, 반복, 난이도, 카테고리를 가진 일정은 정상 데이터다.
- `MainActivityLaunchSmokeTest`를 삭제하거나 검은 화면 판정 기준을 완화하지 마라. 이유: 에뮬레이터 렌더링 회귀를 잡는 선행 테스트다.
- UI 레이어에서 Room DAO, AlarmManager, WorkManager를 직접 호출하지 마라. 이유: AGENTS.md의 CRITICAL 아키텍처 규칙을 위반한다.
- 외부 Google Calendar 연동을 추가하지 마라. 이유: MVP 제외 범위다.
- 기존 테스트를 깨뜨리지 마라.
