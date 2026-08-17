# Step 1: persist-overdue-failure-reconciliation

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/data/repository/RoomCombatRepository.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomTaskRepository.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/MissedOccurrencePolicy.kt`
- `/app/src/main/java/com/todoquest/data/local/FailureLogDao.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomCombatRepositoryTest.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomTaskRepositoryTest.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/schemas/com.todoquest.data.local.TodoQuestDatabase/8.json`
- `/phases/030-039/37-overdue-hud-stat-allocation-fixes/index.json`

## 작업

repository 테스트를 먼저 추가해 마감 reconciliation이 몬스터 공격 event만 만들고 `TaskRepository.observeOccurrences()`에는 계속 `TODO`가 노출되는 회귀를 재현한다. 이후 `RoomCombatRepository.reconcileOverdue(now)`의 신규 마감 후보 처리 transaction에서 `FailureLogEntity`와 `MonsterAttackEventEntity`를 함께 확정한다.

각 신규 due candidate는 completion, failure, 기존 monster event가 모두 없을 때만 처리한다. `failure_logs`에는 candidate의 task id, occurrence 날짜, recurrence series id와 reconciliation `now`를 저장한다. damage limit 안의 `APPLIED` event와 limit 밖의 `SKIPPED_RECONCILIATION_CAP` event 모두 실패 log를 가져야 한다. event insert, HP update, reconciliation cursor 갱신 중 하나라도 실패하면 같은 transaction의 신규 failure log도 rollback되어야 한다.

수동 실패 command와 `processPendingFailureAttacks()`의 `MANUAL_FAILURE` 계약은 변경하지 않는다. 같은 occurrence의 자동·수동 원인은 기존 monster event primary key를 공유하며 damage와 transition은 한 번만 만들어야 한다. 사용자가 실패를 취소하면 failure log만 삭제되고 기존 event와 피해는 남는다. reconciliation cursor가 이미 지나간 occurrence를 다시 자동 실패로 만들지 않는다.

테스트는 다음을 포함한다.

- 시간이 있는 occurrence의 15분 grace 직후와 시간 없는 occurrence의 다음 날 시작 이후 `FAILED` 관찰
- 반복 일정의 날짜별 occurrence만 실패하며 원본 전체가 실패하지 않음
- applied와 cap-skipped candidate 모두 failure log 생성
- 완료·기존 실패·기존 event 제외와 반복 reconciliation 멱등성
- transaction 강제 실패 시 failure/event/HP/cursor 동시 rollback
- 실패 취소 후 task는 `TODO`지만 기존 damage/event가 재생되지 않음

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.repository.RoomCombatRepositoryTest" --tests "com.todoquest.data.repository.RoomTaskRepositoryTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.MissedOccurrencePolicyTest" --console=plain
git diff --check
```

## 검증 절차

1. 자동 공격 후 occurrence가 `TODO`로 남는 테스트를 먼저 작성하고 실패를 확인한다.
2. AC를 실행하고 failure log와 attack event가 동일 transaction 및 occurrence key를 사용하는지 검토한다.
3. UI가 DAO나 WorkManager를 직접 호출하지 않는지, 반복 원본과 occurrence가 분리되는지 확인한다.
4. task index의 step 1을 `completed`로 바꾸고 자동 실패 저장과 멱등성 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- UI 상태를 맞추기 위해 monster event를 직접 `FAILED`로 projection하지 마라. 이유: 실패 취소 뒤에도 event는 보존되므로 source state가 될 수 없다.
- 실패 취소 때 monster event나 HP 피해를 삭제하지 마라. 이유: 확정 전투 event는 비가역이라는 기존 계약을 지켜야 한다.
- WorkManager 실행 성공을 일정 조회의 전제조건으로 만들지 마라. 이유: 백그라운드 실행과 권한 실패가 핵심 일정 기능을 막아서는 안 된다.
- 기존 테스트를 깨뜨리지 마라.
