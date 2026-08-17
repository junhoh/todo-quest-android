# Step 7: 전투 UseCase 오케스트레이션

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/monster-stats-and-growth.md`
- `/app/src/main/java/com/todoquest/domain/repository/TaskRepository.kt`
- `/app/src/main/java/com/todoquest/domain/repository/CombatRepository.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/CompleteOccurrenceUseCase.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/UndoCompleteOccurrenceUseCase.kt`
- `/app/src/test/java/com/todoquest/feature/calendar/CalendarViewModelTest.kt`
- `/phases/020-029/28-monster-stats-and-combat/index.json`

## 작업

UseCase 레이어에서 완료와 전투 처리를 오케스트레이션하되 UI에 전투 타입을 노출하지 않는다. 테스트를 먼저 수정한다.

`CompleteOccurrenceUseCase`는 기존 `TaskRepository.completeOccurrence`를 먼저 완료한 뒤 해당 occurrence의 pending player attack을 `CombatRepository.processPlayerAttack`으로 best-effort 처리한다. 다음 규칙을 지킨다.

- 일정·보상 transaction이 실패하면 전투를 호출하지 않고 기존 오류를 전달한다.
- 일정·보상 성공 뒤 전투 처리 오류는 `CancellationException`만 다시 던지고, 그 외에는 pending event를 남겨 worker reconciliation이 재시도하게 한다. 빈 catch로 숨기지 말고 주입 가능한 diagnostic sink 또는 명시적 result를 사용한다.
- `CompletionResult`의 XP·골드·정시·효율 계약은 변경하지 않는다. backend-only 범위이므로 전투 결과를 snackbar 문구로 추가하지 않는다.
- 이미 처리된 occurrence는 Repository 멱등 결과를 신뢰하고 추가 공격하지 않는다.

별도 `ReconcileCombatUseCase`는 clock의 현재 instant를 명시적으로 받거나 `AppClock`을 주입해 `CombatRepository.reconcileOverdue`를 호출한다. UI/ViewModel이 DAO, WorkManager나 Room을 직접 알지 않게 한다.

Calendar용 fake에 CombatRepository를 추가하되 기존 화면 동작과 테스트 의미는 유지한다. 전투 repository가 실패해도 완료 UI에 경제 보상 성공이 한 번만 표시되는 테스트를 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.*Combat*UseCaseTest" --tests "com.todoquest.feature.calendar.CalendarViewModelTest"
git diff --check
```

## 검증 절차

1. UseCase와 Calendar 회귀 테스트를 실행한다.
2. UI·ViewModel이 DAO나 WorkManager를 직접 참조하지 않는지 검색한다.
3. phase index의 step 7을 완료 처리하고 best-effort outbox 오케스트레이션을 한국어 `summary`로 기록한다.

## 금지사항

- 전투 오류로 이미 성공한 완료·보상 결과를 실패로 바꾸지 마라. 이유: 일정 기능이 우선이다.
- `CancellationException`을 삼키지 마라. 이유: coroutine cancellation 계약을 지켜야 한다.
- 새 사용자 노출 영문 문구를 하드코딩하지 마라. 이유: AGENTS 한국어 string resource 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
