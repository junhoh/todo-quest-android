# Step 6: 캘린더 보상 표시와 피드백 갱신

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/UI_GUIDE.md`
- `/docs/game-design/character-stats/todo-combat-rewards.md`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarUiState.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`
- `/app/src/test/java/com/todoquest/feature/calendar/CalendarViewModelTest.kt`
- step 2와 4에서 수정한 RewardPolicy, CompletionResult, TaskRepository, CharacterRepository
- `/phases/020-029/25-character-progression-foundation/index.json`

## 작업

Calendar ViewModel unit test와 Compose test를 먼저 갱신해 실패를 확인한 다음 기존 일정 UX를 새 보상 계약에 연결한다.

- `CalendarViewModel`은 `TaskRepository`와 별도로 `CharacterRepository`를 주입받아 level, total XP와 gold 요약을 관찰한다. DAO나 Room entity를 직접 참조하지 않는다.
- 일정 행의 완료 전 보상은 정확한 확정값처럼 표시하지 않는다. 난이도별 `Base 10/5`, `20/10`, `35/20 XP/gold`와 `On-time +10% · daily limits may apply` 조건을 간결하게 표시한다.
- 최초 완료에서 반환된 `CompletionResult`의 실제 XP/gold를 `CalendarEvent.RewardGranted` 같은 일회성 event stream으로 발행한다.
- `CalendarScreen`은 `SnackbarHostState`로 실제 `+N XP · +M gold`와 정시 여부를 알린다. recomposition이나 configuration change로 같은 event를 반복 표시하지 않는다.
- `alreadyRewarded=true`인 중복 완료·취소 후 재완료는 보상 snackbar를 다시 만들지 않는다.
- 기존 일정 생성·수정·삭제, 월간 표시, occurrence 완료/취소와 오류 메시지 흐름을 유지한다.
- 기존 static `CharacterProfile` 의존을 새 character summary로 교체하되 캘린더 상단의 level/XP/gold 가독성은 유지한다.

테스트는 실제 지급량 event, 중복 event 억제, character summary 갱신, 기본 보상 조건 문구와 기존 calendar regression을 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.todoquest.feature.calendar.CalendarViewModelTest"
.\gradlew.bat assembleDebug assembleDebugAndroidTest
git diff --check
```

## 검증 절차

1. 변경된 repository 계약에 맞춘 실패 테스트를 먼저 작성한다.
2. AC를 실행하고 기존 Calendar unit/Compose test가 컴파일·통과하는지 확인한다.
3. UI가 base 예상값과 transaction의 실제 지급값을 혼동하지 않는지 확인한다.
4. task index step 6을 `completed`로 변경하고 캘린더 보상 피드백을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 완료 전에 anti-abuse 적용 후 보상을 확정값으로 표시하지 마라. 이유: 실제 순번은 transaction 시점에 결정된다.
- snackbar를 persistent UI state의 문자열로 반복 소비하지 마라. 이유: recomposition에서 같은 보상이 중복 노출될 수 있다.
- Calendar UI에서 DAO를 호출하지 마라. 이유: 레이어 규칙을 위반한다.
- 기존 일정 편집·삭제 UX를 제거하지 마라. 이유: 성장 기능은 생산성 기능을 방해하면 안 된다.
- 기존 테스트를 깨뜨리지 마라.

