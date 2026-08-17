# Step 4: Calendar에 실제 전투 상태 연결

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/app/TodoQuestApplication.kt`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/app/src/main/java/com/todoquest/domain/model/Combat.kt`
- `/app/src/main/java/com/todoquest/domain/repository/CombatRepository.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarUiState.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMapUiModel.kt`
- `/app/src/test/java/com/todoquest/feature/calendar/CalendarViewModelTest.kt`
- `/phases/020-029/29-calendar-battle-map/index.json`

## 작업

ViewModel test를 먼저 확장한 뒤 application container의 기존 `CombatRepository`를 Calendar presentation state에 연결한다.

- `TodoQuestApp`/`AppNavigation`/Calendar factory가 container의 `combatRepository`를 `CalendarViewModel`에 주입한다. 새 database나 Repository instance를 만들지 않는다.
- `CalendarUiState`에 immutable `battleMap` state를 추가하며 기본값은 Loading이다.
- `CombatRepository.observeCombat()`을 별도 Flow로 변환한다.
  - `onStart`: Loading.
  - snapshot: player 1명과 active monster 1명의 `BattleUnitUiModel`, current/max HP와 stageNumber를 가진 Content.
  - error: Unavailable. 오류를 다시 던져 Calendar의 selected date, occurrences, editor state까지 중단하지 않는다.
- player resource/frame은 기존 equipped character sheet 첫 cell과 `(0.20,0.82)`를 사용한다. monster resource/frame은 goblin runtime drawable과 `BattleMonsterSlots.forCount(1)`을 사용한다. resource·frame mapping은 Composable이 아니라 presentation mapper/factory에 둔다.
- 현재 모든 monster definition에 하나의 검증된 goblin visual을 사용하고 사용자 노출 이름은 한국어 `고블린 정찰병` resource로 표시한다. domain `nameKey`를 화면 문자열로 직접 노출하지 않는다.
- Room/CombatRepository의 단일 활성 몬스터와 combat transaction, occurrence 멱등성은 변경하지 않는다.
- 기존 CalendarViewModel fake/fixture에 CombatRepository를 추가하고 다음을 테스트한다: initial loading, snapshot→HP/stage/resource/slot mapping, combat Flow failure→Unavailable, failure 상태에서도 task occurrence가 계속 노출됨, 기존 edit/complete 흐름 유지.

## Acceptance Criteria

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.todoquest.feature.calendar.CalendarViewModelTest"
.\gradlew.bat testDebugUnitTest --tests "com.todoquest.feature.battle.*"
.\gradlew.bat :app:compileDebugKotlin
git diff --check
```

## 검증 절차

1. 실패하는 CalendarViewModel test를 먼저 확인한 뒤 구현하고 AC를 통과시킨다.
2. Compose가 Repository를 직접 관찰하지 않고 ViewModel state만 렌더링하는지 확인한다.
3. task index의 step 4를 `completed`로 바꾸고 실제 combat 연결과 failure isolation을 한국어 `summary`로 기록한다.

## 금지사항

- combat Flow 실패로 Calendar 전체 Flow를 종료하지 마라. 이유: 전투 UI는 핵심 일정 기능을 막으면 안 된다.
- UI에서 DAO나 `RoomCombatRepository` 구현을 직접 참조하지 마라. 이유: Repository abstraction 경계를 지켜야 한다.
- 다중 몬스터 domain/storage를 추가하지 마라. 이유: 현재 운영 backend는 단일 활성 몬스터다.
- 기존 테스트를 깨뜨리지 마라.
