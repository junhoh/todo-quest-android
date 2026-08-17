# Step 1: orchestrator-database-isolation

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/010-019/13-api37-emulator-compositor-recovery/index.json`
- `/phases/010-019/13-api37-emulator-compositor-recovery/step0.md`
- `/app/build.gradle.kts`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarScreenTest.kt`

## 작업

Android Test Orchestrator의 process 격리는 유지하면서 매 test 뒤 `pm clear`가 API 37의 `PackageUpdateActivity` transition을 누적하지 않게 한다.

- 먼저 `/app/src/androidTest/java/com/todoquest/app/TodoQuestDatabaseIsolationTest.kt`를 추가한다.
  - 서로 다른 두 test가 각각 `todo-quest.db`가 비어 있는지 확인한 뒤 marker task를 저장한다.
  - Orchestrator invocation 사이 DB reset이 없으면 두 test 중 하나가 실패해야 한다.
- `/app/src/androidTest/java/com/todoquest/app/TodoQuestTestRunner.kt`를 추가한다.
  - `AndroidJUnitRunner`를 상속한다.
  - 각 invocation 시작 전에 `targetContext.deleteDatabase("todo-quest.db")`를 호출한다.
  - DB가 존재했는데 삭제에 실패하면 test 실행을 중단한다.
- `/app/build.gradle.kts`에서 test runner를 `com.todoquest.app.TodoQuestTestRunner`로 변경하고 `clearPackageData=true`를 제거한다.
- `testOptions.execution = "ANDROIDX_TEST_ORCHESTRATOR"`와 orchestrator dependency는 유지한다.

## Acceptance Criteria

```powershell
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.app.TodoQuestDatabaseIsolationTest"
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
git diff --check
```

## 검증 절차

1. 격리 회귀 테스트를 구현 코드보다 먼저 추가한다.
2. 두 test가 별도 Orchestrator invocation으로 실행되고 각각 빈 DB에서 시작하는지 확인한다.
3. logcat과 window dump에 test마다 새 `PackageUpdateActivity`가 생기지 않는지 확인한다.
4. 성공하면 phase index의 step 1을 `completed`로 업데이트하고 한국어 summary를 기록한다.

## 금지사항

- Orchestrator를 제거하지 마라. 이유: test process와 in-memory 상태 격리는 계속 필요하다.
- production database schema나 DAO를 test 격리를 위해 수정하지 마라. 이유: test lifecycle 문제를 production 데이터 모델에 전파하면 안 된다.
- UI에서 DAO를 직접 호출하지 마라. 이유: AGENTS.md의 CRITICAL 아키텍처 규칙을 위반한다.
- 기존 테스트를 깨뜨리지 마라.
