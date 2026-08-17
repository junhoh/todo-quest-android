# Step 7: wire-application-boundaries

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/DEVELOPMENT.md`
- `/app/src/main/java/com/todoquest/app/TodoQuestApplication.kt`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/app/src/main/java/com/todoquest/background/CombatReconciliationWorker.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/app/src/test/java/com/todoquest/background/CombatReconciliationWorkerTest.kt`
- `/app/src/androidTest/java/com/todoquest/app/AppNavigationTest.kt`
- `/app/src/androidTest/java/com/todoquest/app/MainActivityLaunchSmokeTest.kt`
- `/phases/030-039/33-calendar-combat-feedback/index.json`

## 작업

application composition과 navigation 회귀 테스트를 먼저 갱신한다. TodoQuestAppContainer에 FailOccurrenceUseCase와 UndoFailOccurrenceUseCase를 singleton Task/Combat Repository 조합으로 생성하고 Calendar factory에 주입한다. production Room builder에 `MIGRATION_5_6`을 등록한다. WorkManager는 같은 application-scope CombatRepository를 계속 사용해 pending player attack, pending manual failure, deadline failure를 복구한다.

top-level Material3 Scaffold의 `contentWindowInsets`를 safe drawing 기준으로 명시하고 기존 bottom navigation innerPadding을 NavHost에 한 번만 적용한다. Calendar 내부에서 system bar inset을 중복 적용하지 않는다. 고정 Battle Map, scroll Calendar, snackbar와 bottom navigation이 서로 겹치지 않는지 app-level connected test를 추가한다.

모든 fake TaskRepository/CombatRepository, ViewModel factory와 Worker test double을 새 interface에 맞춘다. event Flow 기본값을 임의 replay state로 만들지 말고 `emptyFlow` 또는 실제 replay 0 stream을 사용한다. 앱 재생성 후 Room FAILED·HP·active monster 영구 상태는 복원되지만 소비 완료 animation은 재생되지 않는 integration test를 가능한 수준에서 추가한다.

## Acceptance Criteria

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.todoquest.background.CombatReconciliationWorkerTest" --tests "com.todoquest.feature.calendar.CalendarViewModelTest" --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.app.AppNavigationTest,com.todoquest.app.MainActivityLaunchSmokeTest"
.\gradlew.bat lintDebug assembleDebug --console=plain
git diff --check
```

## 검증 절차

1. production database migration 등록, usecase wiring과 application-scope event stream을 확인한다.
2. status/navigation/bottom bar inset과 권한 비의존 task·combat 동작을 확인한다.
3. task index의 step 7을 `completed`로 바꾸고 composition root·migration·inset 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- UI에 database나 Worker를 직접 주입하지 마라. 이유: application container의 Repository/UseCase 경계를 유지해야 한다.
- safeDrawing과 Scaffold innerPadding을 중복 적용하지 마라. 이유: 상단과 하단에 불필요한 공간이 생긴다.
- Android 도구나 emulator가 없으면 설치하지 마라. 이유: AGENTS.md에 따라 blocked로 기록해야 한다.
- 기존 테스트를 깨뜨리지 마라.
