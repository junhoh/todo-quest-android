# Step 3: emit-idempotent-combat-transitions

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/game-design/monster-stats-and-growth.md`
- `/app/src/main/java/com/todoquest/data/local/CombatDao.kt`
- `/app/src/main/java/com/todoquest/data/local/CombatEntities.kt`
- `/app/src/main/java/com/todoquest/data/mapper/CombatEntityMapper.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomCombatRepository.kt`
- `/app/src/main/java/com/todoquest/domain/model/Combat.kt`
- `/app/src/main/java/com/todoquest/domain/repository/CombatRepository.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/MonsterCombatPolicy.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/ReconcileCombatUseCase.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomCombatRepositoryTest.kt`
- `/app/src/test/java/com/todoquest/data/local/CombatDaoTest.kt`
- `/app/src/test/java/com/todoquest/background/CombatReconciliationWorkerTest.kt`
- `/phases/030-039/33-calendar-combat-feedback/index.json`

## 작업

Combat DAO/Repository 테스트를 먼저 작성한다. RoomCombatRepository의 모든 mutating combat command를 하나의 coroutine Mutex로 직렬화하되 public method 간 재진입 deadlock이 없도록 lock 내부 helper를 분리한다. Room transaction이 성공한 뒤에만 `MutableSharedFlow(replay = 0)`로 `CombatTransition`을 발행하며, already-applied 또는 skipped event는 transition을 다시 발행하지 않는다.

`processFailedOccurrenceAttack(taskId, occurrenceDate)`는 failure log를 확인한다. 같은 key의 `monster_attack_events`가 있으면 `wasAlreadyApplied` 결과만 반환한다. 없으면 현재 active monster와 player 파생 stat으로 기존 damage/defense/25% lethal revival 정책을 적용하고, trigger `MANUAL_FAILURE` event와 CharacterCurrentState 갱신을 한 transaction에 저장한다. 전·후 CombatSnapshot과 MonsterAttackSnapshot을 transition에 담는다.

`processPendingFailureAttacks`는 monster event가 없는 failure log를 실패 시각·날짜·task id의 결정적 순서로 복구한다. `reconcileOverdue`는 pending player attack, pending manual failure, deadline failure 순으로 처리한다. deadline candidate는 completion 또는 failure가 있거나 monster event가 이미 있으면 제외한다. 기존 deadline catch-up damage 상한 3회와 skip event 규칙은 수동 failure에 적용하지 않는다.

PlayerAttack도 fresh apply마다 전·후 snapshot transition을 발행한다. nonlethal attack은 같은 monster의 HP 전환을, lethal attack은 HP 0인 outgoing monster와 기존 transaction이 만든 full-HP next monster/Stage 및 victory HP recovery를 after 정보로 보존한다. 중복 player attack은 reward, damage, recovery, monster 생성과 transition을 반복하지 않는다. 자동 deadline monster attack도 화면 구독자가 존재할 때 같은 MonsterAttack transition을 발행한다.

## Acceptance Criteria

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.todoquest.data.local.CombatDaoTest" --tests "com.todoquest.data.repository.RoomCombatRepositoryTest" --tests "com.todoquest.domain.CombatUseCaseTest" --tests "com.todoquest.background.CombatReconciliationWorkerTest" --console=plain
git diff --check
```

## 검증 절차

1. manual failure, duplicate, crash-repair, deadline collision과 transition 순서를 테스트로 먼저 고정한다.
2. lethal/nonlethal player·monster 공격의 HP, reward ledger 수, active monster id와 event 수를 확인한다.
3. task index의 step 3을 `completed`로 바꾸고 멱등 transaction과 replay 없는 transition 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- animation 완료를 기다린 뒤 다음 monster를 DB에 생성하지 마라. 이유: domain transaction의 원자성과 재시작 복구를 깨뜨린다.
- SharedFlow event를 Room transaction 성공 전에 emit하지 마라. 이유: rollback된 결과가 화면에 재생될 수 있다.
- monster 처치용 추가 reward ledger를 만들지 마라. 이유: 기존 reward 계약 밖이다.
- 기존 테스트를 깨뜨리지 마라.
