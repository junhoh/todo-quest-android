# Step 6: wire-room-v8-integration

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/app/TodoQuestApplication.kt`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomCombatRepository.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`
- `/app/src/main/java/com/todoquest/feature/character/CharacterViewModel.kt`
- `/app/src/androidTest/java/com/todoquest/app/TodoQuestDatabaseIsolationTest.kt`
- `/app/src/androidTest/java/com/todoquest/app/AppNavigationTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/character/CharacterScreenTest.kt`
- `/phases/030-039/37-overdue-hud-stat-allocation-fixes/index.json`

## 작업

application/integration 테스트를 먼저 확장한다. 모든 file-backed `Room.databaseBuilder`와 production `TodoQuestApplication` builder에 `MIGRATION_7_8`을 등록해 v1..v8 chain을 완성한다. test 전용 builder도 current schema를 열 때 누락 migration이 없게 갱신하되 in-memory test isolation은 유지한다.

실제 application-scope Repository와 Room Flow를 사용하는 통합 테스트에서 자동 마감 reconciliation 후 해당 occurrence가 `FAILED`로 표시되고 `complete-task-*`, `fail-task-*` action이 사라지며 `undo-fail-task-*`만 남는지 확인한다. 실패 취소 후 완료·실패 action은 복원되지만 이미 적용된 HP 피해와 monster event는 유지되고 effect가 재생되지 않아야 한다. Activity recreation 뒤에도 Room failure 상태는 복원되지만 transient effect는 replay되지 않아야 한다.

Character 통합 경로는 `-/+` 동안 Room profile, HUD, Combat snapshot과 파생 수치가 바뀌지 않고 저장 성공 뒤에만 네 기본 능력치, 미배분 포인트와 필요 시 HP가 한 번 갱신되는지 검증한다. Calendar·Character·Shop·Inventory navigation과 application-scope database/repository 공유를 유지한다.

UI는 Room DAO, AlarmManager, WorkManager 또는 concrete database를 직접 호출하지 않고 ViewModel과 Repository/UseCase 경계를 사용해야 한다. 새 schema나 migration을 fallback destructive 방식으로 열지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.local.TodoQuestDatabaseMigrationTest" --tests "com.todoquest.data.repository.RoomCombatRepositoryTest" --tests "com.todoquest.data.repository.RoomCharacterRepositoryTest" --console=plain
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.app.TodoQuestDatabaseIsolationTest,com.todoquest.app.AppNavigationTest,com.todoquest.feature.calendar.CalendarScreenTest,com.todoquest.feature.character.CharacterScreenTest" --console=plain
git diff --check
```

## 검증 절차

1. production migration registration, 자동 실패 UI와 저장 전후 Character source state 통합 테스트를 먼저 추가한다.
2. AC를 실행하고 Activity recreation과 application-scope single source 동작을 확인한다.
3. UI의 DAO/WorkManager 직접 호출과 destructive migration fallback이 없는지 검토한다.
4. 연결 기기나 emulator가 없으면 설치하지 말고 step을 `blocked`로 기록한다.
5. 성공하면 task index의 step 6을 `completed`로 바꾸고 migration wiring과 화면 통합 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- `fallbackToDestructiveMigration`을 추가하지 마라. 이유: 기존 사용자 일정·성장·장비·전투 데이터를 잃게 된다.
- Calendar UI에서 reconciliation이나 DAO를 직접 호출하지 마라. 이유: 백그라운드·데이터 변경은 Repository/UseCase 경계를 따라야 한다.
- Activity 재생성 시 전투 transition이나 능력치 저장 command를 재실행하지 마라. 이유: 확정 event와 command가 중복될 수 있다.
- 기존 테스트를 깨뜨리지 마라.
