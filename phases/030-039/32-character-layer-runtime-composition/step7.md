# Step 7: build-default-sword-layers

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/character/todo-quest-character-base-sheet.png`
- `/docs/art/character/character-base-spec.json`
- `/docs/art/character/layers/hands_front.png`
- `/scripts/build_character_assets.py`
- `/phases/030-039/32-character-layer-runtime-composition/index.json`

## 작업

legacy `accessory-weapon-layer`에서 4/8-connected component bounds `[41,17,44,58]`인 screen-right 검만 분류한다. legacy 좌표를 runtime anchor로 쓰지 않고 source grip `[42,46]`을 base-body의 screen-right/anatomical-left primary grip `[42,42]`에 맞추는 translation `[0,-4]`를 적용한다. scaling, rotation, centering을 하지 않는다.

원본 의미별로 다음 canonical files를 생성한다.

- source y=17..42 blade pixels → `weapon_back_default_sword.png`, target y=13..38.
- source y=44..58 handle/pommel pixels → `weapon_held_default_sword.png`, target y=40..54.
- source y=43 guard pixels → `weapon_front_default_sword.png`, target y=39.

분리 전 세 부분의 source pixel union이 해당 legacy component와 정확히 같아야 한다. `weapon_back → body/outfit → weapon_held → hands_front → weapon_front` 합성에서 손이 handle을 감싸고 guard만 explicit grip occlusion mask로 손 위에 보이게 한다. source의 `#E05252`는 redAccent 제작 픽셀로 보존한다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check-layer weapon_back_default_sword
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check-layer weapon_held_default_sword
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check-layer weapon_front_default_sword
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_build_character_assets.py -k "weapon or grip or hands_front" --basetemp .\.venv\pytest-tmp
git diff --check
```

## 검증 절차

1. 무기 세 layer와 손 합성을 1×/8×로 확인한다.
2. primary grip `[42,42]`, appearance envelope, face preservation과 soleY 58 composite를 확인한다.
3. task index의 step 7을 `completed`로 바꾸고 component/source grip/translation/split 경로를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- legacy sword bbox를 캐릭터 중앙에 정렬하지 마라. 이유: base-body 손 anchor에 맞춰야 한다.
- guard/handle/blade를 하나의 topmost layer로 남기지 마라. 이유: 손과 몸의 앞뒤 관계를 표현할 수 없다.
- redAccent를 guide로 제거하지 마라. 이유: 검 장식의 실제 제작 픽셀이다.
- 기존 테스트를 깨뜨리지 마라.
