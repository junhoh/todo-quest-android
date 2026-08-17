# Step 3: queue-all-completions-for-combat

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/domain/model/CompletionResult.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/CompleteOccurrenceUseCase.kt`
- `/app/src/main/java/com/todoquest/data/local/RewardLedgerEntity.kt`
- `/app/src/main/java/com/todoquest/data/local/CombatEntities.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomTaskRepository.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomTaskRepositoryTest.kt`
- `/app/src/test/java/com/todoquest/domain/CombatUseCaseTest.kt`
- `/phases/030-039/38-hud-stat-descriptions-combat-rewards/index.json`

## 작업

Repository와 UseCase 테스트를 먼저 수정해 신규 TODO 직접 보상을 전투 outbox로 대체한다.

`RoomTaskRepository.completeOccurrence`는 신규 reward ledger를 `rewardMode = COMBAT_ATTACK`, `xpAward = 0`, `goldAward = 0`, `combatEligible = true`로 기록한다. 기존 `dailyOrdinal <= 20` 전투 상한과 `DAILY_COMBAT_ELIGIBLE_LIMIT`을 제거해 21번째·31번째를 포함한 모든 신규 완료가 PENDING player attack event를 만든다. event에는 `combatRewardVersion = 1`을 기록한다.

정시 여부, reward local date, repeat/daily ordinal과 on-time occurrence 조회는 연속일·MOMENTUM을 위해 계속 유지한다. task 난이도·정시 reward multiplier·효율·`GOLD_GAIN_BONUS`로 직접 XP·gold를 더하지 않는다. 새 ledger를 만들 때 character profile과 current state가 없으면 기존 기본 row를 원자적으로 보장하지만 XP·gold·HP는 변경하지 않는다. player attack source는 완료 직후 현재 character level·공격·치명타·MOMENTUM과 실제 장착 modifier snapshot을 사용한다.

이미 존재하는 reward ledger는 저장된 `rewardMode`를 `CompletionResult`에 반환하고 완료·ledger·event를 다시 만들지 않는다. `repairMissingPlayerAttackEvents`는 ledger mode가 `TODO_COMPLETION`이면 legacy reward version `0`, `COMBAT_ATTACK`이면 version `1` event를 만들어 과거 직접 보상에 새 전투 보상을 덧붙이지 않는다. 완료 취소·재완료와 recurrence split/reassign도 기존 occurrence key를 유지한다.

`CompleteOccurrenceUseCase`는 일정 transaction 성공 뒤 모든 신규 attack을 기존 방식으로 즉시 best-effort 처리하고, 일반 예외는 diagnostic sink에 보고한 뒤 PENDING을 남긴다. cancellation은 다시 던진다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.repository.RoomTaskRepositoryTest" --tests "com.todoquest.domain.CombatUseCaseTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.RewardPolicyTest" --tests "com.todoquest.domain.StreakPolicyTest" --console=plain
git diff --check
```

## 검증 절차

1. 신규 완료 직접 award 0, attack version 1, 21·31번째 event 생성과 legacy repair version 0 테스트를 먼저 작성한다.
2. AC를 실행해 occurrence 중복, undo/recomplete, split recurrence, failure 뒤 PENDING 보존과 cancellation을 확인한다.
3. UI/DAO가 아닌 Repository·UseCase 경계에서만 완료와 outbox를 변경하는지 검토한다.
4. task index의 step 3을 `completed`로 바꾸고 전투 상한 제거와 legacy/new outbox 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 신규 completion에서 기존 RewardPolicy XP·gold를 character에 더하지 마라. 이유: 사용자가 직접 TODO 보상을 전투 보상으로 대체했다.
- 기존 `TODO_COMPLETION` ledger를 `COMBAT_ATTACK`으로 변환하지 마라. 이유: 과거 직접 보상에 전투 보상이 중복될 수 있다.
- 전투 처리 오류로 완료 transaction을 실패 처리하지 마라. 이유: 일정 완료와 best-effort 전투 경계를 유지해야 한다.
- 기존 테스트를 깨뜨리지 마라.
