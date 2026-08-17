# Step 3: apply-severe-injury-in-combat

## 읽어야 할 파일

- /AGENTS.md
- /docs/ARCHITECTURE.md
- /phases/050-059/51-severe-injury-status-effect/index.json
- /phases/050-059/51-severe-injury-status-effect/step0.md
- /phases/050-059/51-severe-injury-status-effect/step1.md
- /phases/050-059/51-severe-injury-status-effect/step2.md
- /app/src/main/java/com/todoquest/data/repository/RoomCombatRepository.kt
- /app/src/main/java/com/todoquest/domain/usecase/ReconcileCombatUseCase.kt
- /app/src/main/java/com/todoquest/background/CombatReconciliationWorker.kt
- /app/src/main/java/com/todoquest/data/local/CombatEntities.kt
- /app/src/test/java/com/todoquest/data/repository/RoomCombatRepositoryTest.kt
- /app/src/test/java/com/todoquest/domain/CombatUseCaseTest.kt
- /app/src/test/java/com/todoquest/background/CombatReconciliationWorkerTest.kt

## 작업

테스트를 먼저 작성하고 실제 피해 처리 transaction에서 패배, 중상 적용/갱신, 응급 회복을 멱등하게 수행한다.

1. 전투 도메인 이벤트를 확장해 고유 ID를 가진 PlayerDefeated, StatusEffectApplied, PlayerEmergencyRecovered, StatusEffectRefreshed, StatusEffectRemoved를 표현한다. 기존 combat event/transition 구조를 재사용하고 발생 순서를 보존하는 batch 또는 동등한 ordered representation을 사용한다.
2. 치명 피해의 같은 DB transaction에서 HP 0과 패배를 확정한 뒤 중상을 apply 또는 refresh한다. 신규 적용과 갱신 모두 revision을 1 증가시키고 해당 revision의 회복 조건을 3개와 defeatedAt + 24h로 재설정한다. 이미 활성일 때 새 row나 추가 modifier를 만들지 않는다.
3. 갱신 시 이전 revision의 occurrence credit은 더 이상 현재 조건에 영향을 주지 않게 하고, 유효 중상 modifier로 스탯을 다시 계산한 후 max(1, effectiveMaxHp * 50 / 100) HP를 저장한다.
4. 치명 공격 하나의 ordered lifecycle은 정확히 PlayerDefeated → StatusEffectApplied 또는 StatusEffectRefreshed → PlayerEmergencyRecovered이다. 각 event ID는 attack event/mutation ID와 effect revision으로 결정적으로 만들고 한 번만 발생시킨다.
5. 동일 attack idempotency key가 재처리되면 저장된 결과를 반환하고 HP, 중상 revision, 남은 횟수, 만료 시각, event를 다시 변경하지 않는다. 빠른 연속 overdue attack은 기존 정렬대로 하나씩 처리하며 각 패배는 한 번만 갱신한다.
6. 치명 피해에서도 몬스터 HP, 스테이지, 처치 보상, 예정된 다음 공격 등 관련 없는 상태를 임의로 초기화하지 않는다. 비치명 공격과 기존 완료·실패·몬스터 공격 흐름을 보존한다.
7. 재시작 후 active 상태 복원, 24시간 만료 reconciliation, 정확히 0/음수 피해 결과, 이미 중상인 재패배, 낮은 HP, event 순서와 멱등성을 테스트한다.

## Acceptance Criteria

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.repository.RoomCombatRepositoryTest" --tests "com.todoquest.domain.CombatUseCaseTest" --tests "com.todoquest.background.CombatReconciliationWorkerTest" --tests "com.todoquest.domain.MonsterCombatPolicyTest" --console=plain
git diff --check
~~~

## 검증 절차

1. 동일 치명 attack을 두 번 reconcile했을 때 attack row, lifecycle event 수, effect revision, 회복 HP가 변하지 않는 테스트를 먼저 만든다.
2. 중상 중 재패배는 20% modifier가 한 번뿐이고 remaining=3, expiresAt=새 패배+24h이며 회복 HP는 중상 유효 최대 체력 기준인지 확인한다.
3. 여러 overdue attack의 시각/ID 순서와 각 batch 내부 lifecycle 순서를 확인한다.
4. 몬스터 HP와 보상 snapshot이 패배 처리 때문에 초기화되지 않는지 회귀 검증한다.
5. AC를 실행하고 phase index의 step 3을 completed와 한국어 summary로 갱신한다.

## 금지사항

- Composable 또는 HP Flow observer에서 패배/중상 저장을 호출하지 마라. 이유: 재구성과 화면 회전이 도메인 mutation을 반복시킨다.
- MutableSharedFlow의 단일 최신 값으로 lifecycle event를 덮어쓰지 마라. 이유: 한 공격의 여러 이벤트 순서가 유실된다.
- 중상 갱신 때 감소율을 누적하거나 상태 row를 추가하지 마라. 이유: 중상은 중첩되지 않는다.
- 패배 처리 중 몬스터 HP, 스테이지 또는 보상을 초기화하지 마라.
- DB의 레거시 revivedHp 컬럼명을 바꾸려고 combat table을 파괴적으로 재작성하지 마라.
