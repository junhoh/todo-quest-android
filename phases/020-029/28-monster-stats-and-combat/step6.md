# Step 6: 실패 공격 reconciliation 구현

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/monster-stats-and-growth.md`
- `/docs/game-design/character-stats/todo-combat-rewards.md`
- `/app/src/main/java/com/todoquest/domain/usecase/OccurrenceCalculator.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/OnTimePolicy.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomCombatRepository.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoTaskDao.kt`
- `/app/src/main/java/com/todoquest/data/local/CompletionLogDao.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomCombatRepositoryTest.kt`
- `/phases/020-029/28-monster-stats-and-combat/index.json`

## 작업

테스트를 먼저 추가하고 `RoomCombatRepository.reconcileOverdue(now)`를 완성한다. deadline 후보 계산은 Android Framework와 무관한 순수 정책으로 분리한다.

처리 규칙은 다음과 같다.

- 첫 combat progress 초기화의 cursor는 현재 시각이므로 이전 occurrence를 공격하지 않는다.
- 이후 cursor부터 `now`까지 관련 task occurrence를 계산한다. 시간 일정은 예정 local datetime +15분, 무시간 일정은 occurrence 다음 local date 자정이 지난 경우 due다.
- 삭제된 일정의 삭제 이후 미래 occurrence는 만들지 않으며, 과거 완료 기록과 반복 분할 `endDate`를 존중한다.
- completion log가 없고 `(taskId, occurrenceDate)` monster attack event도 없는 due occurrence만 `(occurrenceDate, taskId)`로 정렬한다.
- 새 due event 중 처음 3개만 실제 피해를 적용하고 나머지는 `SKIPPED_RECONCILIATION_CAP`으로 insert하여 다음 실행에서 재공격하지 않는다.
- source는 현재 활성 몬스터의 계산된 `DAMAGE`이고 player의 현재 계산된 defense에 공통 비율 공식을 적용한다. level 차이·task 난이도·권한에 따른 숨은 보정은 없다.
- 치명 피해면 `wasLethal=true`, 피해 적용 전후와 25% revived HP를 event에 저장하고 같은 transaction에서 character current HP를 revived HP로 쓴다. 사망 디버프, XP·골드 손실, 일정 잠금은 없다.
- 이미 player HP가 0인 비정상 legacy 상태는 먼저 25%로 정규화하되 해당 사유를 결과에 기록하고 음수 HP를 만들지 않는다.
- 실패 event가 먼저 있어도 나중 완료의 player attack과 경제 보상을 막지 않는다.
- pending player attacks를 먼저 drain한 뒤 missed attack을 처리하고 cursor는 전체 transaction이 성공한 경우에만 `now`로 전진한다.

장기 미사용 수백 occurrence에서도 event를 유한 batch로 처리하되 첫 run에서 발견한 나머지를 피해 skip으로 확정한다. 알림·AlarmManager 상태나 Android permission을 입력으로 받지 않는다.

테스트는 time/untimed 경계, DST/zone을 주입된 clock으로 처리, 정렬, 3회 상한, 재실행 멱등, 실패 후 늦은 완료 공존, 치명/비치명, 25% floor, 이미 0 HP, cursor rollback, deleted/split recurrence와 권한 비의존을 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.MissedOccurrence*" --tests "com.todoquest.data.repository.RoomCombatRepositoryTest"
git diff --check
```

## 검증 절차

1. 순수 정책과 Repository 테스트를 실행한다.
2. event insert, character HP와 cursor가 같은 transaction인지 확인한다.
3. phase index의 step 6을 완료 처리하고 reconciliation·부활 결정을 한국어 `summary`로 기록한다.

## 금지사항

- 한 reconciliation에서 실제 피해를 3회보다 많이 적용하지 마라. 이유: 장기 복귀 사용자를 과도하게 처벌하면 안 된다.
- skip event를 다음 실행에서 다시 피해로 바꾸지 마라. 이유: 멱등 확정 결과다.
- 알림 또는 exact alarm 권한을 전투 조건으로 사용하지 마라. 이유: 권한 거부에도 핵심 흐름이 동작해야 한다.
- 기존 테스트를 깨뜨리지 마라.
