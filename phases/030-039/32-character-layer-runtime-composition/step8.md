# Step 8: generate-character-previews

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/character/layers/`
- `/scripts/build_character_assets.py`
- `/scripts/test_build_character_assets.py`
- `/app/src/main/res/drawable-nodpi/todo_quest_character_modular_sheet.png`
- `/phases/030-039/32-character-layer-runtime-composition/index.json`

## 작업

`build_character_assets.py --write`로 canonical layers에서 preview와 runtime copies를 결정적으로 생성한다. PNG 저장 후 반드시 다시 열어 hashes와 bounds를 계산하고 spec에 반영한 뒤 `--check`로 재검증한다.

512×128 8×2 tile map을 다음으로 고정한다.

- row 0: `body-only`, `body-default-hair`, `default-equipped`, `adventure-equipped`, `mixed-default-top`, `mixed-adventure-top`, `headgear-off`, `headgear-on`.
- row 1: `accessory-off`, `accessory-on`, `weapon-off`, `weapon-on`, `anchors-preview`, `palette`, `layer-bounds-preview`, `runtime-equipped-reference`.

preview source files는 `/docs/art/character/previews/`, runtime byte-identical layers는 `/app/src/main/assets/character/layers/`, debug/golden sheet copy는 기존 Android drawable-nodpi path에 둔다. production runtime은 sheet를 읽지 않는다.

anchors/layer-bounds debug tile은 generated overlay mask와 `debugGuideColor`만 사용하고 source layer PNG에 guide pixel을 기록하지 않는다. palette tile은 production 16색을 보인다. `runtime-equipped-reference`는 기본 머리+adventure head/top/bottom/shoes/accessory+default sword 상태다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\build_character_assets.py --write
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-modular-sheet.png --spec docs\art\character\character-modular-sheet-spec.json
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_build_character_assets.py scripts\test_validate_character_sheet.py --basetemp .\.venv\pytest-tmp
git diff --check
```

## 검증 절차

1. 16 tiles와 별도 preview files를 1×/8×로 확인한다.
2. docs/app layer 및 sheet copies가 byte-identical이고 sheet raw serialization이 65,536 pixels인지 확인한다.
3. task index의 step 8을 `completed`로 바꾸고 sheet hash, preview/runtime 경로와 64-combination 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 기존 sheet를 잘라 canonical layer를 다시 만들지 마라. 이유: 독립 source layer가 원본 데이터다.
- preview를 runtime 유일 asset으로 사용하지 마라. 이유: 장비 교체 가능한 layer 합성이 목적이다.
- hash 계산 전에 메모리 image만 사용하지 마라. 이유: 저장된 실제 PNG를 다시 읽어야 한다.
- 기존 테스트를 깨뜨리지 마라.
