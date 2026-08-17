# Step 2: schedule-repository-usecases

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/phases/000-009/3-schedule-management/index.json`
- `/phases/000-009/3-schedule-management/step0.md`
- `/phases/000-009/3-schedule-management/step1.md`
- `/app/src/main/java/com/todoquest/domain/model/`
- `/app/src/main/java/com/todoquest/domain/usecase/`
- `/app/src/main/java/com/todoquest/data/local/`
- `/app/src/main/java/com/todoquest/data/mapper/TodoTaskMapper.kt`

## 작업

Repository 계약과 Room 기반 구현, 완료/완료취소 UseCase를 테스트 먼저 작성한 뒤 구현한다. UI나 Compose 화면은 만들지 않는다.

추가할 계약은 다음과 같다.

- `domain/repository/TaskRepository.kt`
  - `fun observeOccurrences(rangeStart: LocalDate, rangeEnd: LocalDate): Flow<List<TaskOccurrence>>`
  - `fun observeCharacterProfile(): Flow<CharacterProfile>`
  - `suspend fun createTask(input: CreateTaskInput): Long`
  - `suspend fun updateTask(task: TodoTask)`
  - `suspend fun deleteTask(taskId: Long)`
  - `suspend fun completeOccurrence(taskId: Long, occurrenceDate: LocalDate): CompletionResult`
  - `suspend fun undoCompleteOccurrence(taskId: Long, occurrenceDate: LocalDate)`
- `domain/usecase/CompleteOccurrenceUseCase.kt`
- `domain/usecase/UndoCompleteOccurrenceUseCase.kt`
- `core/AppClock.kt`
- `data/repository/RoomTaskRepository.kt`

Repository 구현 규칙은 다음과 같다.

- `observeOccurrences`는 저장된 task 원본과 completion log를 조합해 요청 기간의 occurrence를 계산한다.
- `completeOccurrence`는 Room transaction 안에서 completion log 생성, reward ledger 생성, character profile 갱신을 처리한다.
- 같은 `taskId + occurrenceDate`에 reward ledger가 이미 있으면 XP와 gold를 추가 지급하지 않는다.
- 완료취소는 completion log만 삭제하고 MVP에서는 이미 지급된 reward ledger와 character profile을 되돌리지 않는다.
- 반복 일정의 특정 날짜 완료가 원본 task 전체 완료로 전파되면 안 된다.

테스트는 in-memory Room 또는 fake DAO가 아니라 실제 repository transaction 경계를 검증할 수 있는 방식으로 작성한다.

## Acceptance Criteria

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
git diff --check
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. 중복 완료, 완료취소 후 재완료, 반복 occurrence별 완료가 테스트되는지 확인한다.
3. `completeOccurrence`가 Room transaction 안에서 completion/reward/profile을 처리하는지 확인한다.
4. UI, ViewModel, Compose 화면이 추가되지 않았는지 확인한다.
5. `/phases/000-009/3-schedule-management/index.json`의 step 2 상태와 결과 필드를 업데이트한다.

## 금지사항

- 완료 보상을 Repository 바깥 여러 호출로 나누지 마라. 이유: 중간 실패 시 completion과 reward/profile 상태가 어긋난다.
- 보상 지급 여부를 UI 상태로 판단하지 마라. 이유: 멱등성은 저장소와 ledger 기준으로 보장해야 한다.
- 완료취소 시 XP와 gold를 자동 회수하지 마라. 이유: ARCHITECTURE.md의 MVP 정책은 회수하지 않는 것이다.
- AlarmManager, WorkManager를 호출하지 마라. 이유: 일정 저장과 알림 예약은 이번 step 범위가 아니다.
- 기존 테스트를 깨뜨리지 마라.
