# Step 1: define-difficulty-and-reminder-domain-policy

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/character-stats/todo-combat-rewards.md`
- `/app/src/main/java/com/todoquest/domain/model/TaskDifficulty.kt`
- `/app/src/main/java/com/todoquest/domain/model/TaskOccurrence.kt`
- `/app/src/main/java/com/todoquest/domain/model/Reminder.kt`
- `/app/src/main/java/com/todoquest/domain/repository/ReminderScheduler.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/ReminderPlanner.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/ReminderUseCases.kt`
- `/app/src/test/java/com/todoquest/domain/ReminderPlannerTest.kt`
- `/app/src/test/java/com/todoquest/domain/ReminderUseCaseTest.kt`
- `/phases/050-059/52-notification-difficulty-calendar-polish/step0.md`
- `/phases/050-059/52-notification-difficulty-calendar-polish/index.json`

## 작업

순수 Kotlin 테스트를 production보다 먼저 작성하고 `domain/`만 수정한다.

난이도 전투 정책은 다음 의미의 public 계약을 제공한다. 프로젝트 명명 규칙에 맞춰 이름을 다듬을 수 있지만 version과 의미를 유지한다.

~~~kotlin
data class TaskDifficultyCombatBalanceConfig(
    val version: Int,
    val multiplierBpByDifficulty: Map<TaskDifficulty, Int>,
)

object TaskDifficultyCombatBalanceCatalog {
    const val LEGACY_VERSION: Int = 0
    const val CURRENT_VERSION: Int = 1
    fun configFor(version: Int): TaskDifficultyCombatBalanceConfig
}

object TaskDifficultyCombatPolicy {
    fun multiplierBp(difficulty: TaskDifficulty?, version: Int): Int
    fun scaleDamage(value: Int, difficulty: TaskDifficulty?, version: Int): Int
    fun scaleXp(value: Long, difficulty: TaskDifficulty?, version: Int): Long
}
~~~

version `0`은 nullable difficulty를 허용하고 항상 10,000bp다. version `1`은 non-null difficulty를 요구하고 EASY `10,000`, MEDIUM `15,000`, HARD `20,000`bp를 반환한다. 알 수 없는 version, 누락/중복 difficulty mapping, 0 이하 또는 비정상 배율은 거부한다. scale 함수는 `Math.multiplyExact`와 정수 내림을 사용하고 입력이 양수면 결과도 최소 1이며, 음수 입력은 거부한다.

`TaskOccurrence`에 source-compatible 기본값이 있는 아래 필드를 추가하고 `OccurrenceCalculator`가 `TodoTask.reminderSetting`을 occurrence로 복사하게 한다.

~~~kotlin
val reminderSetting: ReminderSetting = ReminderSetting()
val reminderScheduleStatus: ReminderScheduleStatus = ReminderScheduleStatus.DISABLED
~~~

`ReminderPlanner`에는 `occurrenceDate`, nullable task time, `ReminderSetting`에서 `LocalDateTime?`을 계산하는 Android 비의존 API를 추가하고 기존 Instant 계산이 이를 단일 원천으로 사용하게 한다. TEN_MINUTES_BEFORE와 ONE_HOUR_BEFORE는 task time이 필수이고 이전 날짜로 넘어갈 수 있다. CUSTOM_TIME은 occurrence 당일이며 NONE은 null이다. 기존 DST 해석과 엄격한 미래 plan 규칙은 유지한다.

notification channel 차단을 구분할 수 있게 `ReminderCapabilityStatus.CHANNEL_DISABLED`와 `ReminderScheduleStatus.NOTIFICATION_CHANNEL_DISABLED`를 추가한다. `PrepareFirstLaunchNotificationPromptUseCase`는 POST_NOTIFICATIONS가 AVAILABLE이 아닌 최초 상태를 prompt 대상으로 보되 exact capability는 조회하지 않는다. `ReconcileTaskReminderUseCase`는 CHANNEL_DISABLED를 새 schedule status로 보존한다.

알림 예약 race를 막기 위해 `ReconcileTaskReminderUseCase`의 순서를 `다음 plan 계산 → PENDING+plan 조건부 저장 → scheduler.scheduleExact → SCHEDULED 조건부 저장`으로 변경한다. scheduler 실패 또는 non-SCHEDULED 결과는 동일 key alarm을 best-effort 취소하고 그 key가 아직 현재 상태일 때만 plan 없는 ERROR로 바꾼다. scheduler 호출 중 receiver가 current key를 claim해 plan을 비웠다면 이후 성공/실패 정리가 DELIVERED를 덮어쓰지 않아야 한다. 취소·capability·schedule 오류는 일정 transaction을 되돌리지 않는다.

테스트는 세 난이도 mapping과 exact arithmetic/overflow, legacy/unknown version, reminder local date rollover, TaskOccurrence 기본 호환성, channel disabled mapping, scheduler 호출 시점에 staged plan 존재, callback 선점 뒤 상태 보존을 포함한다.

## Acceptance Criteria

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.TaskDifficultyCombatPolicyTest" --tests "com.todoquest.domain.ReminderPlannerTest" --tests "com.todoquest.domain.ReminderUseCaseTest" --tests "com.todoquest.domain.OccurrenceCalculatorTest" --console=plain
if (rg -n "^import android\.|androidx\.room|androidx\.compose" app/src/main/java/com/todoquest/domain) { exit 1 }
git diff --check
~~~

## 검증 절차

1. 새 domain test를 먼저 추가해 타입/동작 부재 실패를 확인한다.
2. 최소 모델·catalog·policy·UseCase 상태 전이를 구현한다.
3. AC와 Android import 검색을 실행한다.
4. step 1을 `completed`와 한국어 summary로 갱신한다.

## 금지사항

- Android, Room, Compose 타입을 domain에 노출하지 마라. 이유: 정책을 순수 Kotlin으로 검증해야 한다.
- floating point로 배율을 계산하지 마라. 이유: 공격 재시도와 snapshot 결과가 결정적이어야 한다.
- `RewardPolicy`의 legacy direct XP/gold 공식을 삭제하지 마라. 이유: 과거 데이터와 테스트 호환성을 유지해야 한다.
- plan을 platform scheduler 호출 뒤에만 저장하지 마라. 이유: 가까운 시각의 callback이 Room state보다 먼저 발화할 수 있다.
- 기존 테스트를 깨뜨리지 마라.
