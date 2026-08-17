# Step 6: split-headgear-accessory-layers

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/character/todo-quest-character-modular-sheet.png`
- `/docs/art/character/todo-quest-character-base-sheet.png`
- `/docs/art/character/character-base-spec.json`
- `/docs/art/character/layers/hair_front_default.png`
- `/docs/art/character/layers/top_adventure.png`
- `/scripts/build_character_assets.py`
- `/phases/030-039/32-character-layer-runtime-composition/index.json`

## 작업

current modular sheet의 `head-gear-layer`와 `accessory-layer` 디자인을 각각 `/docs/art/character/layers/headgear_adventure.png`, `accessory_adventure.png`로 64×64 원점 그대로 분리하고 schema v4에서 headgear_front/accessory_front slot으로 등록한다.

legacy `accessory-weapon-layer`는 디자인 비교에만 사용하고 하나의 output layer로 복사하지 않는다. current headgear/accessory가 face feature, hair split, shoulder/top과 합성될 때 시각적으로 필요한 픽셀만 유지한다. back portion이 실제로 없으므로 headgear_back/accessory_back file을 만들지 않고 catalog에서 생략한다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check-layer headgear_adventure
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check-layer accessory_adventure
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_build_character_assets.py -k "headgear or accessory or legacy_combined" --basetemp .\.venv\pytest-tmp
git diff --check
```

## 검증 절차

1. 장착/해제 composite를 1×/8×로 확인한다.
2. legacy combined tile path가 canonical/runtime layer 목록에 없는지 확인한다.
3. task index의 step 6을 `completed`로 바꾸고 front-only 결정과 layer 경로를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- legacy accessory-weapon tile 전체를 runtime에 등록하지 마라. 이유: headgear/accessory/weapon 책임이 혼합돼 있다.
- 빈 back layer를 불필요하게 생성하지 마라. 이유: 사용하지 않는 optional layer는 catalog에서 생략한다.
- 색상만으로 legacy guide와 장식 픽셀을 삭제하지 마라. 이유: red/teal은 실제 제작 색이다.
- 기존 테스트를 깨뜨리지 마라.
