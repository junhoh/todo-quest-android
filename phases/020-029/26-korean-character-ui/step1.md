# Step 1: Character presentation 한국어화

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/game-design/character-stats/implementation-and-validation.md`
- `/app/src/main/java/com/todoquest/feature/character/CharacterUiState.kt`
- `/app/src/main/java/com/todoquest/feature/character/CharacterViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/character/CharacterScreen.kt`
- `/app/src/test/java/com/todoquest/feature/character/CharacterViewModelTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/character/CharacterScreenTest.kt`
- step 0에서 수정한 `/AGENTS.md`와 `/docs/UI_GUIDE.md`
- `/phases/020-029/26-korean-character-ui/index.json`

## 작업

ViewModel unit test와 stateless Compose test의 한국어·semantic state 기대값을 구현보다 먼저 추가해 실패를 확인한 다음 Character presentation 전체를 한국어 리소스 기반으로 전환한다.

- `/app/src/main/res/values/strings.xml`을 만들고 Character 화면의 제목, 섹션, 수치 라벨, 버튼, 확인 dialog, 오류 dialog, TalkBack 설명을 step 0의 한국어 용어로 정의한다. `values-ko`와 `values-en`은 만들지 않는다.
- `/app/src/main/java/com/todoquest/feature/character/CharacterUiState.kt`의 표시 상태를 언어에 독립적으로 바꾼다.
  - `BaseStatUiState(type: StatType, value: Int)`
  - `DerivedStatUiState(type: DerivedStatType, displayValue: String)`
  - `CharacterUiState.error: CharacterUiMessage?`
  - `CharacterUiState.resetUnavailableReason: CharacterUiMessage?`
- 같은 파일에 다음 의미를 표현하는 immutable sealed `CharacterUiMessage`를 정의한다: load 실패, 미배분 포인트 없음, `StatType`과 투자 상한을 포함한 능력치 상한, 배분 실패, 초기화 대상 없음, 필요·보유 골드를 포함한 골드 부족, 현재 초기화 불가, 초기화 실패.
- `CharacterViewModel`은 표시용 영문 label과 문장을 만들지 않고 type, 수치, `CharacterUiMessage`만 노출한다. caught exception의 원문은 사용자에게 표시하지 않고 load·배분·초기화별 일반 실패 message로 치환한다.
- `CharacterScreen`에서 `StatType`, `DerivedStatType`, `CharacterUiMessage`를 한국어 string resource로 해석한다. `MAX`, `HP`, `XP`, `MOMENTUM`은 각각 `최대 레벨`, `체력`, `경험치`, `기세`로 표시한다.
- 동적 숫자는 string resource format argument를 사용하고 기존 천 단위 구분, basis point half-up 소수점 한 자리, 레벨 진행률을 유지한다.
- 캐릭터 이미지는 `Todo Quest 모험가 캐릭터`, 능력치 증가 버튼은 `{능력치} 올리기`, XP progress는 현재·필요 경험치를 포함한 한국어 TalkBack 설명을 제공한다.
- 무료·유료 초기화 버튼과 dialog, 미배분 포인트·상한·골드 부족·일반 실패 오류가 모두 한국어로 렌더링되게 한다.
- 기존 세로 스크롤, 큰 글꼴, 최소 48dp 터치 영역, disabled 조건, 캐릭터 sprite source rect와 `FilterQuality.None`은 변경하지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.todoquest.feature.character.CharacterViewModelTest"
.\gradlew.bat lintDebug assembleDebug assembleDebugAndroidTest
git diff --check
```

## 검증 절차

1. ViewModel과 Character Compose test를 구현보다 먼저 수정하고 기존 구현에서 assertion 또는 compilation이 실패함을 확인한다.
2. AC를 실행한다.
3. `adb devices`에 실행 가능한 기기가 있으면 `.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.character.CharacterScreenTest"`를 추가 실행한다. 기기가 없으면 test APK 조립 성공을 기록하고 mandatory gate로 처리하지 않는다.
4. `CharacterScreenTest`에서 `캐릭터`, `기본 능력치`, `골드 획득 보너스`, `최대 레벨`, `누적 경험치`, 유료 초기화 문장과 한국어 TalkBack 설명을 검증한다.
5. `/phases/020-029/26-korean-character-ui/index.json`의 step 1을 `completed`로 변경하고 생성한 문자열 리소스와 semantic message 계약을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- ViewModel에 Android `Context`를 주입하지 마라. 이유: 번역은 Compose UI가 리소스를 해석하는 presentation 책임이다.
- caught exception의 원문을 사용자에게 그대로 표시하지 마라. 이유: 영문·내부 구현 정보가 화면에 노출될 수 있다.
- 도메인 `StatType`이나 `DerivedStatType` 이름을 변경하지 마라. 이유: 화면 번역 때문에 계산·저장 계약을 흔들면 안 된다.
- Character sprite를 수정하거나 재인코딩하지 마라. 이유: canonical pixel asset의 byte·좌표 계약을 유지해야 한다.
- 기존 테스트를 깨뜨리지 마라.
