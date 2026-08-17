# Step 5: stage-character-stat-allocation-ui

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/game-design/character-stats/stats-and-progression.md`
- `/app/src/main/java/com/todoquest/feature/character/CharacterUiState.kt`
- `/app/src/main/java/com/todoquest/feature/character/CharacterViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/character/CharacterScreen.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/CharacterCommandUseCases.kt`
- `/app/src/test/java/com/todoquest/feature/character/CharacterViewModelTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/character/CharacterScreenTest.kt`
- `/app/src/main/res/values/strings.xml`
- `/phases/030-039/37-overdue-hud-stat-allocation-fixes/index.json`

## 작업

ViewModel unit test와 stateless Compose test를 먼저 작성해 `+` 클릭이 즉시 Repository를 호출하는 현재 동작을 실패로 확인한 뒤 능력치 배분을 ViewModel 소유의 비영속 draft로 바꾼다.

`CharacterUiState`는 각 기본 stat의 확정값과 pending 증가량, 표시 예상값, 남은 미배분 포인트, 전체 pending 포인트, `hasPendingStatAllocation`, `isSavingStatAllocation`을 immutable state로 제공한다. ViewModel command는 `increaseStat(type)`, `decreaseStat(type)`, `saveStatAllocation()`으로 교체한다. `increase`와 `decrease`는 Repository를 호출하지 않고 draft만 바꾼다. `decrease`는 같은 draft에서 추가한 포인트만 0까지 되돌리며 저장된 기본 능력치를 내리지 않는다.

`+`는 남은 포인트가 없거나 예상 stat이 60이거나 저장 중이면 비활성화한다. `-`는 해당 pending 증가량이 0이거나 저장 중이면 비활성화한다. 저장은 pending이 있을 때 한 번만 step 2의 `AllocateStatPointsUseCase`를 호출한다. 성공과 `NoChanges`에서는 draft를 비우고 Room Flow의 확정 snapshot을 사용한다. 포인트 부족, cap, 예외에서는 draft를 유지하고 한국어 오류를 표시해 사용자가 `-`로 조정하거나 재시도할 수 있게 한다. 중복 저장 command를 허용하지 않는다.

미배분 능력치 포인트 표시는 Growth 영역에서 제거하고 Base Stats 영역 제목 다음으로 이동한다. 각 row는 `-`, 예상 기본값, `+`를 48dp touch target으로 표시하고 pending이 있으면 `(+N 저장 전)`을 보조 텍스트로 명시한다. Base Stats 아래에 full-width `능력치 배분 저장` 버튼을 추가한다. pending이 있거나 저장 중에는 stat reset을 비활성화하고 저장 또는 `-`로 되돌려야 한다는 한국어 설명을 제공한다. 파생 능력치와 현재 HP는 저장 전 preview하지 않고 기존 Room snapshot의 확정값을 유지한다.

draft는 Room이나 `SavedStateHandle`에 저장하지 않는다. 동일 ViewModel의 configuration change 동안에는 유지할 수 있지만 process 종료 후 복원하지 않는다. 새 문구와 TalkBack 설명은 모두 `strings.xml`의 한국어 resource로 정의하고 Compose/ViewModel에 표시 문장을 하드코딩하지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.character.CharacterViewModelTest" --console=plain
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.character.CharacterScreenTest" --console=plain
git diff --check
```

## 검증 절차

1. `-/+` draft와 저장 전 repository 미호출, 일괄 저장 1회, 성공/오류, 파생값 비preview 테스트를 먼저 작성한다.
2. Compose test에서 미배분 포인트 위치, 48dp action, pending 표시, 저장/초기화 enablement와 한국어 semantics를 확인한다.
3. AC를 실행하고 UI가 Repository/UseCase 경계를 통해서만 저장하는지 확인한다.
4. 연결 기기나 emulator가 없으면 설치하지 말고 step을 `blocked`로 기록한다.
5. 성공하면 task index의 step 5를 `completed`로 바꾸고 draft UI와 일괄 저장 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- `+` 또는 `-` 클릭마다 Room을 갱신하지 마라. 이유: 저장 전 배분은 확정되지 않아야 한다.
- `-`로 저장된 투자점을 반환하지 마라. 이유: 유료 stat reset 규칙을 우회하게 된다.
- 저장 전 파생 능력치나 현재 HP를 예상값으로 바꾸지 마라. 이유: 사용자가 선택한 preview 범위는 기본 능력치와 남은 포인트뿐이다.
- 사용자 문구를 Compose나 ViewModel에 영문으로 하드코딩하지 마라. 이유: 한국어 기본 문자열 resource 정책을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
