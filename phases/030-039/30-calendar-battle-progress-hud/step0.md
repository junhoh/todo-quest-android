# Step 0: Calendar 플레이어 진행 상태 확장

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/domain/model/CharacterSnapshot.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarUiState.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/app/src/test/java/com/todoquest/feature/calendar/CalendarViewModelTest.kt`
- `/phases/030-039/30-calendar-battle-progress-hud/index.json`

## 작업

Calendar presentation 상태와 ViewModel mapping 테스트를 먼저 수정한 뒤 HUD가 필요로 하는 기존 캐릭터 진행 상태를 노출한다.

- 기존 `CalendarCharacterSummary`를 재사용하고 다음 시그니처 수준의 필드를 갖게 한다: `isLoading: Boolean`, `level: Int`, `xpIntoCurrentLevel: Long`, `xpRequiredForNextLevel: Long`, `gold: Long`.
- 제거될 기존 Calendar 정보 Header만 사용하던 `totalXp` presentation 필드는 위 레벨 구간 XP 필드로 교체한다. Room의 `PlayerCharacter.totalXp`와 domain 모델은 변경하지 않는다.
- `CalendarViewModel`은 `CharacterRepository.observeCharacter()`가 제공하는 `CharacterSnapshot.level`, `xpIntoCurrentLevel`, `xpRequiredForNextLevel`, `character.currentGold`를 그대로 매핑하고 실제 snapshot 수신 뒤 `isLoading = false`로 둔다.
- `CalendarUiState`의 초기 `CalendarCharacterSummary`는 로딩 상태로 두어 UI가 임의의 플레이어 수치를 정상 데이터처럼 표시하지 않게 한다.
- snapshot 수신 전 로딩, 일반 진행도, 보상 반영, 레벨 경계와 별도 CharacterRepository 갱신을 `CalendarViewModelTest`에서 먼저 실패하도록 작성한 후 구현한다.

## Acceptance Criteria

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.todoquest.feature.calendar.CalendarViewModelTest"
git diff --check
```

## 검증 절차

1. 변경한 ViewModel test가 기존 구현에서 실패하는 것을 확인한 뒤 상태와 mapping을 구현한다.
2. UI가 DAO, WorkManager, AlarmManager 또는 Repository 구현을 직접 참조하지 않는지 확인한다.
3. task index의 step 0을 `completed`로 바꾸고 수정한 상태 필드와 mapping을 한국어 `summary`로 기록한다.

## 금지사항

- 새 PlayerStatus 또는 UserProgress 도메인 모델을 만들지 마라. 이유: 동일 정보가 `CharacterSnapshot`과 `CalendarCharacterSummary`에 이미 존재한다.
- 누적 XP 저장 방식이나 캐릭터 성장 정책을 변경하지 마라. 이유: 이번 step은 presentation mapping 범위다.
- 기존 테스트를 깨뜨리지 마라.
