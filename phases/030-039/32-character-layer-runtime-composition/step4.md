# Step 4: remap-default-outfit-layers

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/character/todo-quest-character-modular-sheet.png`
- `/docs/art/character/todo-quest-character-base-sheet.png`
- `/docs/art/character/character-base-spec.json`
- `/docs/art/character/layers/hands_front.png`
- `/scripts/build_character_assets.py`
- `/phases/030-039/32-character-layer-runtime-composition/index.json`
- `~/.codex/skills/.system/imagegen/SKILL.md`
- `~/.codex/skills/.system/imagegen/references/prompting.md`

## 작업

테스트 계약을 먼저 확인하고 built-in imagegen의 `precise-object-edit`를 layer별 한 번씩 사용해 `top_default.png`, `bottom_default.png`, `shoes_default.png`를 만든다. 각 호출 전에 local input을 이미지 보기 도구로 확인하고 Image 1은 immutable base-body geometry, Image 2는 current default layer edit target, Image 3은 legacy upper/lower/shoes design reference로 역할을 명시한다.

출력은 flat `#FF00FF` chroma background의 hard pixel art로 제한하고, 선택 결과를 phase 임시 경로로 복사한 뒤 hard key removal, nearest-neighbor 64×64, binary alpha와 production 16색 최근접 매핑을 적용한다. built-in 실패 시 CLI/API로 전환하지 말고 step을 blocked로 기록한다.

공통 장비 interface를 만족한다.

- top: face protected mask 투명, collar는 y=29의 base alpha 위에서만 허용, shoulder band/arms에 부착, protected hands 투명, torso neutral underwear 전부 덮고 waist `[24,41,40,43]` 51픽셀 불투명.
- bottom: waist 51픽셀 불투명, y=41..48 x=24..40, y=49..54 left/right leg mask 불투명, x=32 투명, ankle overlap 32픽셀 불투명.
- shoes: y=53..58만 사용, 모든 base foot alpha를 덮고 ankle overlap을 제공하며 sole y=58, left/right 같은 size와 sole을 유지.
- bottom hidden waist와 shoes hidden ankle에는 outlineDarkNavy를 두지 않고 최종 visible boundary만 1픽셀 outline으로 만든다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check-layer top_default
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check-layer bottom_default
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check-layer shoes_default
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_build_character_assets.py -k "default_outfit or common_interface" --basetemp .\.venv\pytest-tmp
git diff --check
```

## 검증 절차

1. 각 layer 단독, base 위, default full composite를 1×/8×로 확인한다.
2. 얼굴·손 침범 0, waist/ankle required points 전부 opaque, base neutral outfit 비노출을 확인한다.
3. task index의 step 4를 `completed`로 바꾸고 built-in prompt 요약과 최종 layer 경로를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- current y=24..28 clothing pixels를 그대로 복사하지 마라. 이유: 얼굴/턱을 장비로 오인한 잘못된 좌표다.
- layer를 crop하거나 center align하지 마라. 이유: 공통 canvas 원점이 런타임 배치 계약이다.
- 반투명·antialias·새 팔레트 색을 남기지 마라. 이유: 기존 pixel art 계약을 위반한다.
- 기존 테스트를 깨뜨리지 마라.
