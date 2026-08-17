# Step 7: stabilize-and-explain-character-stats

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/UI_GUIDE.md`
- `/docs/game-design/character-stats/stats-and-progression.md`
- `/app/src/main/java/com/todoquest/feature/character/CharacterUiState.kt`
- `/app/src/main/java/com/todoquest/feature/character/CharacterViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/character/CharacterScreen.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/test/java/com/todoquest/feature/character/CharacterViewModelTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/character/CharacterScreenTest.kt`
- `/phases/030-039/38-hud-stat-descriptions-combat-rewards/index.json`

## 작업

Character unit·Compose test를 먼저 작성해 pending 문구가 `-` 위치를 바꾸는 회귀와 stat 설명 interaction 부재를 실패로 확인한다.

`CharacterUiState`에 nullable `StatDescriptionTarget`을 추가한다. target은 `Base(StatType)`와 `Derived(DerivedStatType)`를 구분하고 표시 문자열은 포함하지 않는다. `CharacterViewModel.showBaseStatDescription(type)`, `showDerivedStatDescription(type)`, `dismissStatDescription()`이 선택 상태를 소유한다. snapshot refresh, draft 변경과 configuration change 동안 선택을 유지하되 process 복원용 Room/SavedState에는 저장하지 않는다.

기본 능력치 main row는 `이름 영역 + 고정 폭 control cluster`로 구성한다. cluster는 `48dp - 버튼`, 고정 숫자 slot, `48dp + 버튼`의 위치와 내부 간격을 유지한다. `(+N 저장 전)`은 main row 측정에 참여하지 않는 별도 보조 행에서 같은 cluster 아래 중앙 정렬한다. pending `0→1→0`, 값 `9→10`, font scale `1.0/2.0`에서도 `remove-*`와 `add-*`의 left/right bounds 차이는 0.6px 이하여야 한다. 기존 48dp target, enablement, draft semantics와 저장 전 파생값 비preview를 유지한다.

기본 stat은 이름 영역만 설명 열기 button semantics를 가져 `- / +` 클릭과 충돌하지 않게 한다. 파생 stat은 전체 48dp 행을 설명 target으로 만든다. dialog는 stat 이름을 title로, 한국어 기본 설명을 body로, 기존 `확인` resource를 단일 action으로 사용한다.

설명은 다음 의미를 정확히 반영한다: 힘은 공격력·치명타 피해, 활력은 최대 체력·방어력, 집중은 공격력·치명타 확률, 의지는 상태 이상 저항·체력 회복. 파생 설명은 최대 체력, 공격 기본 수치, 점감 방어, 치명타 확률·피해 배율, 상태 적용 확률 감소, 몬스터 처치 HP 회복, 일정 및 몬스터 처치 gold bonus를 설명한다. 모든 문구와 `설명 보기` 접근성 문자열은 `strings.xml`에 둔다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.character.CharacterViewModelTest" --console=plain
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.character.CharacterScreenTest" --console=plain
git diff --check
```

## 검증 절차

1. mutable state로 pending 전후 button bounds와 12개 dialog target을 검증하는 테스트를 먼저 작성한다.
2. AC를 실행해 320dp·font scale 2.0 scroll, 48dp target, plus/minus command 분리와 한국어 dialog를 확인한다.
3. 선택 상태가 ViewModel에 있고 표시 문장이 ViewModel/domain에 하드코딩되지 않았는지 확인한다.
4. 연결 기기가 없으면 도구를 설치하지 말고 step을 `blocked`로 기록한다.
5. task index의 step 7을 `completed`로 바꾸고 고정 cluster와 stat 설명 state를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- pending 보조 문구를 main control Row의 가변 폭 child로 두지 마라. 이유: `-`와 `+` 위치가 다시 이동한다.
- 전체 기본 stat row에 하나의 clickable을 덮지 마라. 이유: 중첩된 `- / +` action과 접근성 click이 충돌한다.
- 설명 문구를 ViewModel이나 Compose에 하드코딩하지 마라. 이유: 한국어 문자열 resource 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
