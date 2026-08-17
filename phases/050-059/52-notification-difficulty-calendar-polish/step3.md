# Step 3: snapshot-difficulty-and-reminder-occurrences

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/data/repository/RoomTaskRepository.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomReminderRepository.kt`
- `/app/src/main/java/com/todoquest/data/mapper/TaskReminderMapper.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/OccurrenceCalculator.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomTaskRepositoryTest.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomReminderRepositoryTest.kt`
- `/phases/050-059/52-notification-difficulty-calendar-polish/step1.md`
- `/phases/050-059/52-notification-difficulty-calendar-polish/step2.md`
- `/phases/050-059/52-notification-difficulty-calendar-polish/index.json`

## 작업

Repository 테스트를 production보다 먼저 작성하고 `RoomTaskRepository`, `RoomReminderRepository`와 필요한 data mapper만 수정한다.

신규 occurrence 최초 완료 transaction이 만드는 `PlayerAttackEventEntity`에 완료 시점 `task.difficulty.name`과 `TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION`을 snapshot한다. RewardLedger, completion, recovery credit, player attack outbox는 계속 하나의 transaction에서 occurrence key당 한 번만 생성한다. 완료 취소·재완료, process retry, 반복 분할/reassign은 기존 event의 difficulty/version을 바꾸거나 새 event를 만들지 않는다.

`repairMissingPlayerAttackEvents()`는 역사적 task 난이도를 신뢰할 수 없으므로 기존처럼 combat reward version `0`을 사용하고 difficulty `null`, difficulty balance version `0`으로 생성한다. 기존 PENDING attack을 현재 task에서 다시 읽어 갱신하지 않는다.

`observeOccurrences()`는 active tasks, completions, failures와 `TaskReminderDao.observeAll()`을 결합한다. task별 reminder row가 있으면 `TaskReminderMapper`의 validation을 거쳐 setting과 schedule status를 occurrence에 복사하고, row가 없는 legacy task는 NONE/DISABLED로 투영한다. reminder row mode/status 변경은 같은 task 목록 Flow를 다시 emit해야 한다. 반복 occurrence마다 설정은 공유하되 occurrence local trigger는 해당 날짜를 기준으로 후속 UI에서 계산한다.

발화 직전 조회는 persisted key가 일치하고 status가 `PENDING` 또는 `SCHEDULED`인 경우에만 active TODO task를 반환한다. DELIVERED/ERROR/권한 상태, stale key, 완료·실패·삭제 occurrence는 반환하지 않는다. callback이 staged PENDING을 claim한 뒤 scheduler 후속 update가 상태를 역행시키지 않는 repository 조건부 update 테스트를 추가한다.

테스트는 EASY/MEDIUM/HARD 신규 outbox snapshot, task 편집 뒤 snapshot 불변, 중복 완료/취소/재완료, legacy repair 중립, reminder row 부재·설정·status Flow, PENDING/SCHEDULED delivery와 stale suppression을 포함한다.

## Acceptance Criteria

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.repository.RoomTaskRepositoryTest" --tests "com.todoquest.data.repository.RoomReminderRepositoryTest" --tests "com.todoquest.domain.OccurrenceCalculatorTest" --console=plain
git diff --check
~~~

## 검증 절차

1. snapshot·Flow·delivery race test를 먼저 실패시킨다.
2. Repository와 mapper를 최소 수정해 통과시킨다.
3. 같은 occurrence의 completion/reward/attack 수가 하나인지 재검증하고 AC를 실행한다.
4. step 3을 `completed`와 한국어 summary로 갱신한다.

## 금지사항

- UI가 사용할 reminder 데이터를 별도 일회성 N+1 조회로 만들지 마라. 이유: Room Flow가 source 변경을 일관되게 다시 방출해야 한다.
- 완료 transaction 밖에서 difficulty snapshot을 추가하지 마라. 이유: completion과 attack source가 부분 성공할 수 있다.
- 완료 취소 시 player attack event를 삭제하지 마라. 이유: 재완료 공격 중복 방지 계약을 유지해야 한다.
- PENDING이라는 이유만으로 key가 다른 callback을 허용하지 마라. 이유: stale alarm이 새 예약을 claim할 수 있다.
- 기존 테스트를 깨뜨리지 마라.
