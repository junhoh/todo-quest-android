# Step 5: occurrence 플레이어 공격 outbox 연결

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/monster-stats-and-growth.md`
- `/docs/game-design/character-stats/todo-combat-rewards.md`
- `/app/src/main/java/com/todoquest/data/repository/RoomTaskRepository.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomCombatRepository.kt`
- `/app/src/main/java/com/todoquest/data/local/RewardLedgerDao.kt`
- `/app/src/main/java/com/todoquest/data/local/CombatDao.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomTaskRepositoryTest.kt`
- `/phases/020-029/28-monster-stats-and-combat/index.json`

## 작업

CRITICAL 테스트 우선으로 occurrence 완료 transaction에 player attack pending outbox를 연결한다. 이 step은 `RoomTaskRepository`와 같은 data/repository 레이어만 수정하고 실제 공격 처리는 호출하지 않는다.

새 경제 RewardLedger insert에 성공하고 `dailyOrdinal <= 20`인 경우에만 `(taskId, occurrenceDateEpochDay)` unique player attack event를 `PENDING`으로 insert-ignore한다. 같은 완료 transaction에서 다음을 snapshot한다.

- `recurrenceSeriesId`, post-reward player level.
- XP 반영 후 계산된 attack, critical chance, critical damage.
- 완료 instant의 로컬 날짜를 reference date로 계산한 현재 MOMENTUM bp. 미래 occurrence를 조기 완료한 경우 미래 날짜를 현재 streak로 소급 사용하지 않는다.
- character stat balance version, monster/combat balance version과 생성 시각.

경제 ledger와 pending event 생성까지만 같은 transaction에 두므로 몬스터 상태가 없거나 후속 처리에 실패해도 완료·보상 결과는 이미 확정되고 pending event가 재시도 근거가 된다. 이미 경제 보상을 받은 occurrence인데 pending이 누락된 legacy/중단 경로는 RewardLedger의 `combatEligible`와 snapshot 가능한 현재 입력을 사용해 reconciliation이 보완할 수 있도록 명시적 query/repair 경계를 제공한다. 기존 v3 ledger는 `combatEligible=false`라 소급 공격을 만들지 않는다.

완료 취소는 player attack event를 삭제하지 않는다. 반복 원본을 미래 시점에서 분할해 task id가 바뀌면 completion/reward와 같은 transaction에서 player/monster attack event의 task id와 recurrence lineage도 재귀속한다.

테스트는 최초 완료, duplicate tap, 취소·재완료, 일일 20/21 경계, 다중 레벨업 post-reward attack, MOMENTUM snapshot, transaction rollback, 반복 분할과 v3 비소급을 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.repository.RoomTaskRepositoryTest"
git diff --check
```

## 검증 절차

1. Repository 테스트를 실행한다.
2. 경제 보상과 pending event가 occurrence별 한 번이고 event 처리와 분리됐는지 확인한다.
3. phase index의 step 5를 완료 처리하고 outbox·재귀속 결정을 한국어 `summary`로 기록한다.

## 금지사항

- 완료 transaction 안에서 몬스터 HP를 직접 변경하지 마라. 이유: 전투 오류가 핵심 일정·보상을 롤백하면 안 된다.
- 21번째 이후 일일 완료에 공격 event를 만들지 마라. 이유: 기존 combatEligible 상한을 유지해야 한다.
- 완료 취소 시 전투 event를 삭제하지 마라. 이유: 재완료 공격 중복을 막아야 한다.
- 기존 테스트를 깨뜨리지 마라.
