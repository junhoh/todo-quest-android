# Step 1: define-reminder-domain-policy

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/domain/model/TodoTask.kt`
- `/app/src/main/java/com/todoquest/domain/model/CreateTaskInput.kt`
- `/app/src/main/java/com/todoquest/domain/model/UpdateTaskInput.kt`
- `/app/src/main/java/com/todoquest/domain/model/RecurrenceRule.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/OccurrenceCalculator.kt`
- `/app/src/test/java/com/todoquest/domain/OccurrenceCalculatorTest.kt`
- `/phases/030-039/39-task-reminder-notifications/index.json`

## 작업

순수 Kotlin 테스트를 production 코드보다 먼저 작성해 step 0의 reminder 의미와 시간 계산을 고정한다. 이 step은 `domain/` model·repository contract·pure policy에 한정하고 Android, Room, Compose를 참조하지 않는다.

최소 public 계약은 다음과 같다. 이름은 기존 프로젝트 스타일에 맞춰 조정할 수 있지만 의미와 typed 값은 유지한다.

```kotlin
enum class ReminderMode { NONE, TEN_MINUTES_BEFORE, ONE_HOUR_BEFORE, CUSTOM_TIME }

data class ReminderSetting(
    val mode: ReminderMode = ReminderMode.NONE,
    val customTime: LocalTime? = null,
)

data class ReminderOccurrenceKey(val taskId: Long, val occurrenceDate: LocalDate)
data class ReminderPlan(val key: ReminderOccurrenceKey, val triggerAt: Instant)

enum class ReminderScheduleStatus {
    DISABLED,
    PENDING,
    SCHEDULED,
    POST_NOTIFICATIONS_REQUIRED,
    EXACT_ALARM_ACCESS_REQUIRED,
    DELIVERED,
    NO_FUTURE_OCCURRENCE,
    ERROR,
}
```

`ReminderSetting`은 `CUSTOM_TIME`일 때만 `customTime`을 요구하고 다른 mode에서는 custom 값을 거부하거나 정규화한다. `TEN_MINUTES_BEFORE`와 `ONE_HOUR_BEFORE`는 task time이 없으면 유효한 plan을 만들지 않는다. `CUSTOM_TIME`은 occurrence 당일 custom local time을 사용하며 task time과 독립적이다. `NONE`은 plan이 없다.

`TodoTask`, `CreateTaskInput`, `UpdateTaskInput`에 기본 `ReminderSetting()`을 추가해 기존 source 호출 호환성을 유지한다. `ReminderPlanner.triggerFor(task, occurrenceDate, setting, zoneId): Instant?`와 다음 미래 occurrence를 찾는 Android 비의존 policy를 제공한다. 기존 `OccurrenceCalculator`의 없음·매일·매주·매월·endDate 의미를 재사용하고 별도의 반복 규칙을 만들지 않는다. local date-time은 `atZone(zoneId)`의 표준 DST gap/overlap 해석을 사용하고 현재 instant보다 엄격히 뒤인 trigger만 미래 plan으로 인정한다.

`ReminderScheduler` contract는 capability 조회, exact plan schedule, key cancel을 제공하며 Android 타입을 노출하지 않는다. `ReminderRepository` contract는 configured task 조회, 예약 상태 조회/갱신, 다음 deliverable occurrence 조회와 발화 직전 활성 task·TODO occurrence 검증을 제공한다. platform 결과는 typed status로 반환하고 문자열 예외 메시지를 제어 흐름으로 사용하지 않는다.

테스트는 네 mode, custom invariant, 무시간 preset, 일회성·매일·매주·매월, 자정 이전 10/60분 trigger, endDate, 현재 시각과 같은 trigger, 과거 trigger 건너뛰기, `Asia/Seoul`과 DST gap/overlap zone을 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.ReminderPlannerTest" --tests "com.todoquest.domain.OccurrenceCalculatorTest" --console=plain
git diff --check
```

## 검증 절차

1. ReminderPlanner test를 먼저 추가하고 새 production 타입 부재로 실패하는지 확인한다.
2. 최소 domain model·interface·policy를 구현한 뒤 AC를 실행한다.
3. `domain/`에서 `android.*`, Room, Compose import가 없는지 검색한다.
4. step 0 문서와 mode·custom·past trigger·반복 의미를 대조한다.
5. task index의 step 1을 `completed`로 바꾸고 새 계약과 계산 경계를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- Android `Context`, `AlarmManager`, `PendingIntent`를 domain에 노출하지 마라. 이유: scheduler 정책을 순수 Kotlin으로 검증해야 한다.
- 별도의 반복 일정 계산을 만들지 마라. 이유: 기존 occurrence 규칙과 알림 날짜가 어긋날 수 있다.
- 현재보다 과거인 알림을 즉시 발화 plan으로 변환하지 마라. 이유: stale 알림을 몰아서 표시하지 않는 계약이다.
- `CUSTOM_TIME`을 task time 기준 offset으로 해석하지 마라. 이유: 사용자는 occurrence 당일 독립 시각을 선택했다.
- 기존 테스트를 깨뜨리지 마라.
