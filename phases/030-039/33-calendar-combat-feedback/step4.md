# Step 4: serialize-battle-presentation

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/domain/model/Combat.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMapUiModel.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMapLayout.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/app/src/test/java/com/todoquest/feature/battle/BattleMapUiModelTest.kt`
- `/app/src/test/java/com/todoquest/feature/calendar/CalendarViewModelTest.kt`
- `/phases/030-039/33-calendar-combat-feedback/index.json`

## 작업

Android UI와 독립적으로 test dispatcher에서 검증 가능한 battle presentation 상태 머신을 `feature/battle`에 테스트 우선으로 구현한다. 상태는 `IDLE`, `PLAYER_ATTACKING`, `MONSTER_HIT`, `MONSTER_DYING`, `MONSTER_SPAWN_ALERT`, `MONSTER_SPAWNING`, `MONSTER_ATTACKING`, `PLAYER_HIT`, `PLAYER_DYING`, `PLAYER_REVIVING`을 사용한다.

controller는 buffered `Channel` actor로 transition을 한 번에 하나씩 소비하고 sequence id/event key, attacker/target, damage, critical/lethal, 표시할 scene override와 input lock을 StateFlow로 노출한다. event key를 ViewModel 수명 동안 중복 소비하지 않으며 queue가 비어 있고 phase가 IDLE일 때만 input을 허용한다. Repository event Flow는 replay 0이므로 화면 재구독이나 회전이 이미 끝난 transition을 새로 enqueue하지 않는다. 회전 중인 phase는 현재 presentation state만 다시 렌더링하고 domain command를 반복하지 않는다.

기본 timeline은 player/monster 전진 140ms, hit 180ms, death 320ms, monster spawn alert 300ms, spawn 또는 player revive 280ms다. nonlethal player는 PLAYER_ATTACKING→MONSTER_HIT→IDLE, lethal player attack은 PLAYER_ATTACKING→MONSTER_HIT(HP 0)→MONSTER_DYING→MONSTER_SPAWN_ALERT(빈 slot)→MONSTER_SPAWNING(new full HP)→IDLE 순서다. monster attack은 반대 방향이며 lethal이면 PLAYER_HIT에서 HP 0을 보인 뒤 PLAYER_DYING→PLAYER_REVIVING(기존 25% HP)→IDLE로 간다.

전·후 CombatSnapshot을 현재 CharacterRenderState와 결합하는 mapper를 presentation layer에 둔다. outgoing monster와 new monster의 id를 구분하고 alert phase에는 monster list를 비워 이전 HP나 sprite가 남지 않게 한다.

## Acceptance Criteria

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.todoquest.feature.battle.BattleAnimationControllerTest" --tests "com.todoquest.feature.battle.BattleMapUiModelTest" --console=plain
git diff --check
```

## 검증 절차

1. 가상 시간으로 각 transition의 정확한 상태 순서와 duration을 검증한다.
2. 연속 enqueue, duplicate key, collector 재구독, lethal spawn/revive scene을 확인한다.
3. task index의 step 4를 `completed`로 바꾸고 queue와 presentation state 경계를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 일반 StateFlow 값 변화만 보고 새 공격 event를 추론하지 마라. 이유: 재구성 시 중복 animation 원인이 된다.
- presentation controller에서 Repository나 DAO를 호출하지 마라. 이유: animation은 이미 확정된 domain 결과만 표현해야 한다.
- delay 동안 Main thread를 blocking하지 마라. 이유: Calendar 입력과 Compose rendering을 막는다.
- 기존 테스트를 깨뜨리지 마라.
