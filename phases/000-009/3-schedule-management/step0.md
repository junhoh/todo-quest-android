# Step 0: schedule-domain-models

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/phases/000-009/2-android-skeleton/index.json`
- `/phases/000-009/2-android-skeleton/step2.md`
- `/app/build.gradle.kts`
- `/app/src/main/java/com/todoquest/domain/DomainPackageMarker.kt`

## 작업

일정관리의 순수 Kotlin 도메인 모델과 반복 occurrence 계산, 보상 정책을 테스트 먼저 작성한 뒤 구현한다. Android, Room, Compose API에 의존하지 않는다.

추가할 도메인 계약은 다음 수준으로 고정한다.

- `domain/model/TodoTask.kt`: `id: Long`, `title: String`, `memo: String`, `startDate: LocalDate`, `time: LocalTime?`, `difficulty: TaskDifficulty`, `category: String`, `recurrenceRule: RecurrenceRule`
- `domain/model/RecurrenceRule.kt`: `NONE`, `DAILY`, `WEEKLY`, `MONTHLY`
- `domain/model/TaskDifficulty.kt`: `EASY`, `MEDIUM`, `HARD`
- `domain/model/TaskOccurrence.kt`: `taskId`, `title`, `memo`, `occurrenceDate`, `time`, `difficulty`, `category`, `recurrenceRule`, `isCompleted`
- `domain/model/CharacterProfile.kt`: `level`, `totalXp`, `currentGold`, `default()`, `withReward(xp: Int, gold: Int)`
- `domain/model/CreateTaskInput.kt`: 일정 생성 입력값
- `domain/model/CompletionResult.kt`: `awardedXp`, `awardedGold`, `alreadyRewarded`
- `domain/usecase/OccurrenceCalculator.kt`: `occurrencesFor(task, rangeStart, rangeEnd, completedDates)`와 `occursOn(task, date)`
- `domain/usecase/RewardPolicy.kt`: 난이도별 예측 가능한 XP/골드 매핑

핵심 규칙은 다음과 같다.

- occurrence identity는 `taskId + occurrenceDate` 조합으로 취급한다.
- 반복 원본과 날짜별 occurrence를 분리한다.
- 월간 반복은 시작일과 같은 day-of-month가 있는 달에만 occurrence를 만든다.
- 보상 공식은 MVP 기본값으로 `EASY = 10 XP/5 gold`, `MEDIUM = 20 XP/10 gold`, `HARD = 35 XP/20 gold`를 사용한다.
- 레벨은 `totalXp / 100 + 1`로 계산한다.

테스트를 먼저 추가한다.

- `app/src/test/java/com/todoquest/domain/OccurrenceCalculatorTest.kt`
- `app/src/test/java/com/todoquest/domain/RewardPolicyTest.kt`

## Acceptance Criteria

```powershell
.\gradlew.bat test
git diff --check
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. 테스트가 반복 일정 없음/매일/매주/매월과 보상 정책을 검증하는지 확인한다.
3. Android, Room, Compose import가 도메인 파일에 들어가지 않았는지 확인한다.
4. `/phases/000-009/3-schedule-management/index.json`의 step 0 상태와 결과 필드를 업데이트한다.

## 금지사항

- Room entity, DAO, database를 만들지 마라. 이유: 이 step은 순수 도메인 규칙만 다룬다.
- ViewModel이나 Compose 화면을 만들지 마라. 이유: UI state 계약은 도메인 계약 확정 뒤 별도 step에서 다룬다.
- AlarmManager, WorkManager, Google Calendar 연동을 추가하지 마라. 이유: MVP 일정관리 phase 범위를 벗어난다.
- 테스트 없이 도메인 로직을 먼저 구현하지 마라. 이유: AGENTS.md의 test-first 규칙을 위반한다.
- 기존 테스트를 깨뜨리지 마라.
