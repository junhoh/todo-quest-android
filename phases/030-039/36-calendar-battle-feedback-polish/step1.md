# Step 1: shorten-dismissible-reward-feedback

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarUiState.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/test/java/com/todoquest/feature/calendar/CalendarViewModelTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/app/AppNavigationTest.kt`
- `/phases/030-039/33-calendar-combat-feedback/step6.md`
- `/phases/030-039/36-calendar-battle-feedback-polish/step0.md`
- `/phases/030-039/36-calendar-battle-feedback-polish/index.json`

## 작업

Calendar reward snackbar의 짧은 자동 종료와 외부 tap dismiss 동작을 Compose test로 먼저 재현한 뒤 `feature/calendar` 안에서 구현한다. 보상 계산, `CalendarEvent.RewardGranted` 타입, ViewModel event channel과 최초 지급에서만 event를 만드는 기존 계약은 변경하지 않는다.

`CalendarScreen`의 reward event collector는 snackbar를 `SnackbarDuration.Indefinite`로 표시하되 `withTimeoutOrNull(600L)` 같은 취소 가능한 timeout 경계로 감싸 표시 시작 후 최대 600ms 안에 제거한다. 사용자가 먼저 dismiss하면 즉시 collector가 다음 event를 받을 수 있어야 한다. buffered reward event의 기존 순서는 유지하며 각 event는 최대 600ms만 표시한다. 실패, 완료 취소와 `alreadyRewarded` 재완료에는 reward snackbar를 만들지 않는다.

snackbar 밖 Calendar content에서 pointer down을 관찰해 `SnackbarHostState.currentSnackbarData?.dismiss()`를 즉시 호출한다. pointer event를 consume하거나 별도 전체 화면 click overlay를 추가하지 말고, 같은 tap이 월 이동·scroll·할 일 action·추가 버튼 등 원래 대상에도 전달되어 정상 동작하게 한다. snackbar 자체 bounds와 bottom navigation 동작은 방해하지 않으며 Calendar destination을 벗어나면 기존 composition 수명에 따라 표시가 종료된다.

Compose test는 보상 event 이후 snackbar가 표시되고 600ms 전에는 존재하지만 600ms가 지나면 사라지는지, 표시 중 다음 달 버튼처럼 snackbar 밖의 기존 action을 누르면 snackbar가 즉시 사라지는 동시에 원래 action도 수행되는지 검증한다. 기존 완료·실패·완료 취소·중복 보상 비표시와 `Battle Map → Calendar scroll → snackbar → bottom navigation` 비중첩 회귀도 유지한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.calendar.CalendarViewModelTest" --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.calendar.CalendarScreenTest,com.todoquest.app.AppNavigationTest" --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
git diff --check
```

## 검증 절차

1. 600ms 자동 종료와 non-consuming 외부 tap dismiss Compose test를 먼저 작성하고 기존 snackbar에서 실패하는지 확인한다.
2. AC 명령을 실행하고 최초 보상만 표시되는 occurrence 멱등성과 기존 Calendar action 전달을 확인한다.
3. Snackbar가 Battle Map이나 bottom navigation을 덮지 않고 권한과 무관하게 일정 완료·보상이 동작하는지 확인한다.
4. task index의 step 1을 `completed`로 바꾸고 reward timeout·tap dismiss 구현과 검증 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- reward feedback 종료를 위해 blocking sleep을 사용하지 마라. 이유: Compose coroutine 취소와 화면 수명을 따라야 한다.
- 전체 화면 투명 click overlay로 입력을 가로채지 마라. 이유: snackbar dismiss와 사용자의 원래 Calendar action이 같은 tap에서 모두 동작해야 한다.
- `CalendarEvent.RewardGranted`를 persistent UI state로 옮기지 마라. 이유: 재생 없는 일회성 reward feedback 계약을 보존해야 한다.
- 이미 보상된 occurrence에 snackbar를 다시 표시하지 마라. 이유: occurrence 단위 멱등 보상과 화면 피드백이 일치해야 한다.
- 기존 테스트를 깨뜨리지 마라.
