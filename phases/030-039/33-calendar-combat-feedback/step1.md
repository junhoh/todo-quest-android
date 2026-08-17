# Step 1: model-occurrence-outcomes

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/monster-stats-and-growth.md`
- `/app/src/main/java/com/todoquest/domain/model/TaskOccurrence.kt`
- `/app/src/main/java/com/todoquest/domain/model/Combat.kt`
- `/app/src/main/java/com/todoquest/domain/repository/TaskRepository.kt`
- `/app/src/main/java/com/todoquest/domain/repository/CombatRepository.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/CompleteOccurrenceUseCase.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/OccurrenceCalculator.kt`
- `/app/src/test/java/com/todoquest/domain/OccurrenceCalculatorTest.kt`
- `/app/src/test/java/com/todoquest/domain/CombatUseCaseTest.kt`
- `/phases/030-039/33-calendar-combat-feedback/index.json`

## 작업

순수 Kotlin 테스트를 먼저 작성한다. domain model에 `TaskOccurrenceStatus { TODO, COMPLETED, FAILED }`를 추가하고 `TaskOccurrence.status`를 source로 사용한다. 기존 UI와 점진적으로 호환할 수 있도록 `isCompleted`, `isFailed`, `isPending`은 status에서 계산하는 read-only property로 둔다. `OccurrenceCalculator.occurrencesFor`는 날짜별 status map 또는 completed/failed set을 받아 하나의 명시적 status로 occurrence를 만든다.

TaskRepository에 다음 command 경계를 추가한다.

```kotlin
suspend fun failOccurrence(taskId: Long, occurrenceDate: LocalDate): FailureResult
suspend fun undoFailOccurrence(taskId: Long, occurrenceDate: LocalDate)
```

`FailureResult`는 새 FAILED 저장인지 기존 FAILED 반복 요청인지 구분한다. 완료된 occurrence에 fail, 실패한 occurrence에 complete를 요청하면 source state를 바꾸지 않는 명시적 domain conflict로 처리한다.

Combat domain에 `MonsterAttackTrigger`, `MonsterAttackSnapshot`, `MonsterAttackResult`와 `CombatTransition`을 추가한다. transition은 stable event key, attack snapshot, 전투 전·후 `CombatSnapshot`을 포함하고 `PlayerAttack`과 `MonsterAttack`을 구분한다. CombatRepository에는 replay 없는 `events: Flow<CombatTransition>`, `processFailedOccurrenceAttack`, `processPendingFailureAttacks` 시그니처를 추가한다. 기존 player attack API와 결과는 유지한다.

`FailOccurrenceUseCase`는 TaskRepository 실패 저장이 새로 성공했을 때만 즉시 monster attack을 호출하고, combat failure는 완료 UseCase와 같은 진단 sink에 보고하되 저장된 FAILED를 롤백하지 않는다. `UndoFailOccurrenceUseCase`는 표시 상태만 되돌린다. cancellation은 삼키지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.todoquest.domain.OccurrenceCalculatorTest" --tests "com.todoquest.domain.CombatUseCaseTest" --console=plain
git diff --check
```

## 검증 절차

1. 테스트를 먼저 실패시킨 뒤 최소 domain 구현으로 통과시킨다.
2. 완료/실패 conflict, 실패 반복, 실패 취소 후 재처리, cancellation과 combat 진단을 확인한다.
3. task index의 step 1을 `completed`로 바꾸고 추가한 type·Repository·UseCase 경계를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- domain model에 Room entity, Android resource 또는 Compose type을 넣지 마라. 이유: 순수 Kotlin 경계를 유지해야 한다.
- animation timing이나 UI 문자열을 domain에 넣지 마라. 이유: 전투 결과와 presentation을 분리해야 한다.
- 실패 취소 시 combat event rollback API를 만들지 마라. 이유: occurrence combat 멱등성을 깨뜨린다.
- 기존 테스트를 깨뜨리지 마라.
