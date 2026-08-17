# Step 5: 캐릭터 화면 구현

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/README.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/game-design/character-stats/stats-and-progression.md`
- `/docs/game-design/character-stats/implementation-and-validation.md`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`
- step 1~4에서 생성한 CharacterRepository, snapshot, command 결과와 usecase
- `/phases/020-029/25-character-progression-foundation/index.json`

## 작업

ViewModel unit test와 stateless Compose content 테스트를 먼저 추가해 실패를 확인한 다음 `feature/character` 모듈 경계에 별도 캐릭터 화면을 구현한다. 이 step에서는 navigation graph를 아직 수정하지 않는다.

- `CharacterUiState`는 loading 여부, level/XP 진행, gold, current/max HP, streak/MOMENTUM, unspent points, 네 기본 스탯, 8개 표시용 파생 스탯, reset 비용/가능 여부, confirm dialog와 오류를 포함한다.
- `CharacterViewModel`은 `CharacterRepository.observeCharacter(referenceDate)`를 immutable `StateFlow`로 노출하고 allocation/reset usecase만 호출한다.
- 화면 진입과 lifecycle resume에서 reference date를 갱신해 미래 정시 기록과 MOMENTUM 만료가 현재 날짜 기준으로 다시 계산되게 한다.
- stat `+` 버튼은 포인트가 없거나 해당 stat이 60이면 비활성화한다.
- reset은 확인 dialog를 거치며 무료/유료 비용, nothing-to-reset, gold 부족 결과를 명확히 표시한다.
- level 50은 MAX로 표시하고 그 이후 XP도 총 XP에는 보존한다.
- bp 필드는 내부 원시값 대신 문서의 half-up 소수점 한 자리 `%`로 표시한다.

기존 `/docs/art/character/todo-quest-character-modular-sheet.png`를 byte-for-byte 그대로 `/app/src/main/res/drawable-nodpi/`에 복사하고 `equipped` 셀 `(row=0, column=0, 64×64)`만 source rect로 렌더링한다. `FilterQuality.None`을 사용하고 실제 Canvas pixel 크기에서 가능한 가장 큰 정수 배율을 계산해 중앙 정렬한다. 원본 PNG를 자르거나 재인코딩하지 않는다. 캐릭터는 의미 있는 TalkBack 설명을 제공한다.

Compose 화면은 캐릭터 요약, 성장, 기본 스탯, 파생 스탯 순으로 읽히게 구성하고 큰 글꼴과 세로 스크롤에서도 조작이 잘리지 않게 한다.

테스트 클래스는 `CharacterViewModelTest`와 `CharacterScreenTest`를 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.todoquest.feature.character.CharacterViewModelTest"
.\gradlew.bat assembleDebug assembleDebugAndroidTest
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-modular-sheet.png --spec docs\art\character\character-modular-sheet-spec.json
$sourceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath 'docs\art\character\todo-quest-character-modular-sheet.png').Hash
$appHash = (Get-FileHash -Algorithm SHA256 -LiteralPath 'app\src\main\res\drawable-nodpi\todo_quest_character_modular_sheet.png').Hash
if ($sourceHash -ne $appHash) { throw 'Runtime character sheet must be an exact copy' }
git diff --check
```

## 검증 절차

1. ViewModel과 stateless screen 테스트를 구현보다 먼저 추가하고 실패를 확인한다.
2. AC를 실행하고 원본과 앱 resource hash가 같은지 확인한다.
3. 큰 글꼴, TalkBack label과 disabled action 상태를 Compose test에서 확인한다.
4. task index step 5를 `completed`로 변경하고 화면·에셋 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- Compose 화면에서 DAO나 Room entity를 직접 사용하지 마라. 이유: UI는 ViewModel state와 event만 다뤄야 한다.
- 캐릭터 PNG를 crop 파일로 새로 만들거나 재인코딩하지 마라. 이유: canonical sheet와 픽셀 무결성을 유지해야 한다.
- 장비 slot이나 획득 UI를 표시하지 마라. 이유: 장비 영속과 제품 흐름은 이번 범위 밖이다.
- bp 원시값을 일반 사용자 UI에 노출하지 마라. 이유: UI 가이드는 사람이 읽을 수 있는 백분율 표시를 요구한다.
- 기존 테스트를 깨뜨리지 마라.
