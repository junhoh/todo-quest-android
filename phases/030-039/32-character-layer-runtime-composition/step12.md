# Step 12: verify-character-layer-migration

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/README.md`
- `/docs/art/character/character-layer-migration-report.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/character/layers/`
- `/docs/art/character/previews/`
- `/scripts/build_character_assets.py`
- `/scripts/validate_character_sheet.py`
- `/phases/030-039/32-character-layer-runtime-composition/index.json`

## 작업

전체 구현을 검증하고 문서와 final report를 실제 결과로 갱신한다. README/UI_GUIDE에서 single sheet first-tile runtime 설명을 independent layer runtime/cached composer 설명으로 교체한다. PRD/ARCHITECTURE/ADR에는 외형 loadout persistence와 no-selection-UI 범위, CharacterRepository 경계, Calendar/Character shared render state를 기록한다.

`character-layer-migration-report.md` 최종 섹션에 다음을 포함한다.

- 수정/생성 파일 목록과 canonical/runtime/generated 역할.
- 최종 semantic anchor 표와 z-order.
- 모든 layer의 실제 inclusive opaque bounds, file/raw RGBA/alpha hashes.
- legacy/current palette set 비교, redAccent/debugGuideColor 분리 결과.
- 10개 required 조합 preview와 64개 자동 조합 결과.
- DB v5 migration/default loadout/Repository API 결과.
- Character/Battle runtime golden equality, Android build/test/emulator 결과.
- 남은 제약: 선택 UI·inventory·ownership·장비 stat은 후속 범위.

가능한 emulator에서 Character와 Calendar 화면을 캡처해 build artifact에 저장하고 이미지 보기 도구로 확인한다. Android 도구 또는 device가 없으면 임의 설치하지 않고 step을 blocked로 기록한다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-base-sheet.png --spec docs\art\character\character-base-spec.json
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-modular-sheet.png --spec docs\art\character\character-modular-sheet-spec.json
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_build_character_assets.py scripts\test_validate_character_sheet.py scripts\test_execute.py --basetemp .\.venv\pytest-tmp
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
git diff --check
```

## 검증 절차

1. 모든 AC를 실행하고 report에 명령별 결과를 기록한다.
2. AGENTS.md CRITICAL 규칙, base-body file/raw/alpha hash 불변, production sheet-reference 부재를 최종 확인한다.
3. task index의 step 12를 `completed`로 바꾸고 전체 검증·문서·남은 후속 범위를 한국어 `summary` 한 줄로 기록한다.
4. 모든 step이 completed면 task index와 `/phases/index.json`의 phase 32를 `completed`로 바꾸고 완료 시각과 한국어 summary를 기록한다.

## 금지사항

- 실패한 test나 연결 검증을 성공으로 기록하지 마라. 이유: phase status가 실제 Acceptance Criteria의 근거다.
- base body, preview 또는 spec만 육안 확인하고 완료하지 마라. 이유: 저장 픽셀·runtime composer·Android 화면까지 검증해야 한다.
- 미완료 후속 UI/ownership을 구현된 것으로 문서화하지 마라. 이유: 승인 범위를 정확히 유지해야 한다.
- Android 도구가 없으면 설치하지 마라. 이유: AGENTS.md에 따라 blocked로 기록해야 한다.
- 기존 테스트를 깨뜨리지 마라.
