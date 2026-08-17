# Step 8: 문서 동기화와 전체 검증

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/README.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/docs/game-design/README.md`
- `/docs/game-design/character-stats-design.md`
- `/docs/game-design/character-stats/implementation-and-validation.md`
- step 0~7에서 생성·수정한 모든 파일과 완료 summary
- `/phases/020-029/25-character-progression-foundation/index.json`

## 작업

구현 결과를 기준으로 핵심 문서와 게임 설계 인덱스의 상태를 동기화하고 전체 회귀 검증을 실행한다.

- `/docs/ARCHITECTURE.md`에 실제 `PlayerCharacter`, `CharacterCurrentState`, `CharacterRepository`, Room v3, reward snapshot과 Calendar/Character navigation 흐름을 반영한다.
- `/docs/UI_GUIDE.md`에 구현된 Character 화면, stat 표시, allocation/reset, bottom navigation과 실제 reward feedback을 반영한다.
- 게임 설계 README와 캐릭터 스탯 인덱스는 `스탯·성장·비전투 보상 기반 구현 완료`와 `자동 전투·몬스터 피해·장비 영속/획득 미구현`을 구분한다.
- 구현 계약 문서의 체크리스트에서 이번 phase가 실제로 검증한 항목만 완료 표시하고 전투 event/WorkManager/장비 persistence 항목은 미완료로 남긴다.
- PRD/ADR의 승인 범위와 실제 구현이 다르면 문서를 실제 구현으로 좁히되 승인되지 않은 기능을 추가 구현하지 않는다.
- public type, Room schema, UI 문구와 문서 용어가 일치하는지 확인한다.

전체 Gradle, harness pytest, 캐릭터 시트 검증을 실행한다. 현재 연결 기기가 없다는 이유로 phase를 실패시키지 않지만 device가 있으면 connected test를 추가 실행하고 결과를 summary에 남긴다.

## Acceptance Criteria

```powershell
.\gradlew.bat test lint assembleDebug assembleDebugAndroidTest
$env:PYTHONUTF8='1'; .\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\build\phase25-pytest-tmp
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-base-sheet.png --spec docs\art\character\character-base-spec.json
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-modular-sheet.png --spec docs\art\character\character-modular-sheet-spec.json
$doc = Get-Content -Raw -Encoding UTF8 -LiteralPath 'docs\game-design\character-stats-design.md'
@('구현','비전투 보상','자동 전투','장비') | ForEach-Object { if (-not $doc.Contains($_)) { throw "Missing final implementation status: $_" } }
git diff --check
```

## 검증 절차

1. AC 명령을 모두 실행한다.
2. AGENTS.md의 UI→Repository, occurrence 멱등성, 반복 원본 분리, 권한 독립 규칙을 확인한다.
3. `adb devices`에 실행 가능한 기기가 있으면 `.\gradlew.bat connectedDebugAndroidTest`를 실행하고, 없으면 compile gate 결과만 기록한다.
4. Room schema 3, 기존 migration chain과 committed asset hash를 확인한다.
5. task index step 8을 `completed`로 변경하고 전체 검증과 남은 후속 범위를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 구현되지 않은 자동 전투나 장비 persistence를 완료로 표시하지 마라. 이유: 문서가 실제 앱보다 앞서면 제품 계약이 다시 불명확해진다.
- Android test device가 없다는 이유만으로 임의 emulator나 SDK를 설치하지 마라. 이유: 도구 설치는 별도 사용자 승인 범위다.
- failing test를 삭제·skip해 AC를 통과시키지 마라. 이유: 회귀를 숨기게 된다.
- phase 전체를 별도 수동 커밋으로 합치지 마라. 이유: harness가 step 단위 커밋을 담당한다.
- 기존 테스트를 깨뜨리지 마라.
