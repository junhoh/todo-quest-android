# Step 0: task-edit-domain-contracts

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/phases/000-009/4-schedule-entry-completion/index.json`
- `/app/src/main/java/com/todoquest/domain/model/TodoTask.kt`
- `/app/src/main/java/com/todoquest/domain/model/TaskOccurrence.kt`
- `/app/src/main/java/com/todoquest/domain/model/CreateTaskInput.kt`
- `/app/src/main/java/com/todoquest/domain/model/RecurrenceRule.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/OccurrenceCalculator.kt`
- `/app/src/test/java/com/todoquest/domain/OccurrenceCalculatorTest.kt`

## 작업

테스트를 먼저 작성한 뒤 반복 일정 미래분 수정/삭제를 표현할 수 있는 순수 도메인 계약을 추가한다. Room, Repository 구현, Compose UI는 이 step에서 수정하지 않는다.

`TodoTask`를 다음 의미로 확장한다.

- `val endDate: LocalDate? = null`을 추가한다.
- `endDate == null`이면 기존처럼 무기한 또는 단일 일정으로 동작한다.
- `endDate != null`이면 `startDate..endDate` 범위에서만 occurrence가 발생한다.
- 기존 테스트와 호출부가 대량으로 깨지지 않도록 기본값은 `null`로 둔다.

`OccurrenceCalculator`를 다음 규칙으로 확장한다.

- `occurrencesFor()`와 `occursOn()`은 `endDate` 이후 날짜를 occurrence로 만들지 않는다.
- `endDate`가 `startDate`보다 이전이면 occurrence는 빈 목록이어야 한다.
- 반복 원본을 미래분부터 닫는 용도이므로 완료/보상 계산은 변경하지 않는다.

`domain/model/UpdateTaskInput.kt`를 추가한다.

```kotlin
data class UpdateTaskInput(
    val taskId: Long,
    val effectiveDate: LocalDate,
    val title: String,
    val memo: String,
    val time: LocalTime?,
    val difficulty: TaskDifficulty,
    val category: String,
    val recurrenceRule: RecurrenceRule,
)
```

`domain/model/TaskCategory.kt` 또는 동등한 파일을 추가해 한국어 카테고리 프리셋을 정의한다.

- 기본값: `일반`
- 프리셋: `일반`, `업무`, `공부`, `건강`, `집안일`, `개인`
- `fun normalize(value: String): String` 또는 동등한 함수로 공백 제거 후 프리셋 외 값은 `일반`로 돌린다.

`OccurrenceCalculatorTest`에 다음 테스트를 추가한다.

- `endDate` 이후 반복 occurrence가 생성되지 않는다.
- `endDate`가 시작일보다 이전이면 occurrence가 생성되지 않는다.
- 월간/주간 반복에서도 `endDate`를 넘지 않는다.
- 기존 반복 완료 테스트가 그대로 통과한다.

## Acceptance Criteria

```powershell
.\gradlew.bat test
git diff --check
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. domain layer가 Android, Room, Compose 타입을 import하지 않는지 확인한다.
3. occurrence 단위 완료와 보상 정책 파일을 변경하지 않았는지 확인한다.
4. `/phases/000-009/7-task-entry-editing/index.json`의 step 0 상태와 결과 필드를 업데이트한다.

## 금지사항

- Room entity나 DAO를 수정하지 마라. 이유: 이 step은 순수 도메인 계약만 다룬다.
- Repository interface를 수정하지 마라. 이유: 구현체와 fake repository 변경은 후속 step에서 함께 처리해야 빌드가 깨지지 않는다.
- Compose UI 타입을 domain model에 넣지 마라. 이유: 도메인 모델은 UI 프레임워크와 독립적이어야 한다.
- 완료 보상 로직을 변경하지 마라. 이유: occurrence 단위 멱등 보상 규칙을 훼손할 수 있다.
- 기존 테스트를 깨뜨리지 마라.
