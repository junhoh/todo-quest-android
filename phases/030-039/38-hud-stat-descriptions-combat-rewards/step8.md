# Step 8: wire-room-v9-integration

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/DEVELOPMENT.md`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/app/TodoQuestApplication.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomTaskRepository.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomCombatRepository.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`
- `/app/src/main/java/com/todoquest/feature/character/CharacterScreen.kt`
- `/app/src/test/java/com/todoquest/data/local/TodoQuestDatabaseMigrationTest.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomTaskRepositoryTest.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomCombatRepositoryTest.kt`
- `/app/src/androidTest/java/com/todoquest/app/TodoQuestDatabaseIsolationTest.kt`
- `/app/src/androidTest/java/com/todoquest/app/AppNavigationTest.kt`
- `/phases/030-039/38-hud-stat-descriptions-combat-rewards/index.json`

## 작업

production과 모든 file-backed test Room builder를 검색해 `MIGRATION_8_9`를 빠짐없이 연결한다. in-memory current-schema test에는 불필요한 migration wiring을 추가하지 않는다.

application-scope 실제 Repository 통합 테스트를 먼저 보강한다. 신규 Calendar 완료가 direct award 없이 attack version 1을 만들고, 즉시 처리되면 실제 hit XP가 HUD에 반영되며 reward badge가 한 번 표시되어야 한다. 21번째 이후에도 PENDING/APPLIED player attack이 존재해야 한다. 처치 시 추가 XP·gold, level-up stat point와 Stage 전진을 확인한다.

v8 file fixture를 v9 application builder로 열어 기존 character/reward/APPLIED/PENDING attack을 보존하고 legacy pending 처리에 새 reward가 없음을 검증한다. Activity 재생성·탭 이동·Flow 재구독 뒤 확정 XP·gold·HP·Stage는 복원하되 reward badge는 replay하지 않는다. 권한 거부 상태에서도 일정 완료, attack reward와 Character 설명이 동작해야 한다.

연결 test fixture가 실제 database를 공유할 때 각 test 전후 격리를 유지한다. 현재 연결된 physical device 또는 emulator를 사용하고 새 SDK/AVD를 설치하지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.local.TodoQuestDatabaseMigrationTest" --tests "com.todoquest.data.repository.RoomTaskRepositoryTest" --tests "com.todoquest.data.repository.RoomCombatRepositoryTest" --console=plain
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.app.TodoQuestDatabaseIsolationTest,com.todoquest.app.AppNavigationTest,com.todoquest.feature.calendar.CalendarScreenTest,com.todoquest.feature.battle.BattleMapTest,com.todoquest.feature.character.CharacterScreenTest" --console=plain
git diff --check
```

## 검증 절차

1. `rg -n "addMigrations|MIGRATION_7_8" app/src`로 모든 builder를 찾아 v9 wiring 누락 테스트를 먼저 만든다.
2. AC를 실행해 migration, 실제 application graph, navigation recreation과 세 feature interaction을 확인한다.
3. notification/exact alarm 권한 없이도 핵심 일정·전투 reward가 동작하고 UI가 DAO를 직접 호출하지 않는지 확인한다.
4. 연결 기기가 없으면 도구를 설치하지 말고 step을 `blocked`로 기록한다.
5. task index의 step 8을 `completed`로 바꾸고 v9 wiring과 end-to-end 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- migration 누락을 destructive fallback으로 우회하지 마라. 이유: 기존 사용자 데이터를 보존해야 한다.
- 기존 APPLIED/PENDING event를 integration fixture에서 새 version으로 덮어쓰지 마라. 이유: 비소급 경계를 실제 upgrade에서 검증해야 한다.
- Android SDK, emulator 또는 system image를 설치하지 마라. 이유: 도구 변경은 별도 승인 phase에서만 허용된다.
- 기존 테스트를 깨뜨리지 마라.
