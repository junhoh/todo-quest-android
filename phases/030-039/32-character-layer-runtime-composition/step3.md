# Step 3: build-appearance-layers

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/character/todo-quest-character-modular-sheet.png`
- `/scripts/build_character_assets.py`
- `/scripts/validate_character_sheet.py`
- `/phases/030-039/32-character-layer-runtime-composition/index.json`

## 작업

`/docs/art/character/layers/`에 appearance layer만 생성한다.

- `hair_back_default.png`와 `hair_front_default.png`는 current modular sheet의 해당 isolated tile을 64×64 local coordinates 그대로 복사한다. crop, translation, center alignment를 하지 않는다.
- `hands_front.png`는 base-body의 left/right protected hand regions 안에서 skinLight/skinShadow인 38개 픽셀만 원래 RGBA와 좌표 그대로 복사한다.
- body_base canonical source는 기존 `/docs/art/character/todo-quest-character-base-body.png` 자체다. 수정하거나 별도 docs body를 만들지 않는다.

모든 투명 픽셀 RGBA를 `[0,0,0,0]`으로 정규화한다. body+hair 합성은 기존 `default-hair-preview` tile과 픽셀 단위로 같아야 한다. hands_front를 body 위에 다시 합성해도 body pixel이 변하지 않아야 한다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check-layer hair_back_default
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check-layer hair_front_default
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check-layer hands_front
.\.venv\Scripts\python.exe -c "from PIL import Image; import pathlib; b=Image.open('docs/art/character/todo-quest-character-base-body.png').convert('RGBA'); h=Image.open('docs/art/character/layers/hands_front.png').convert('RGBA'); assert h.size==(64,64); assert sum(a>0 for *_,a in h.getdata())==38; assert all(not p[3] or p==b.getpixel((i%64,i//64)) for i,p in enumerate(h.getdata()))"
git diff --check
```

## 검증 절차

1. 세 layer를 1×와 nearest-neighbor 8×로 확인한다.
2. base-body file/raw RGBA/alpha hash가 step 0과 같은지 확인한다.
3. task index의 step 3을 `completed`로 바꾸고 layer 경로와 손 픽셀 수를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- base body를 수정하거나 이동하지 마라. 이유: 유일한 geometry canonical reference다.
- hair layer opaque bounds를 crop하지 마라. 이유: 64×64 공통 원점을 유지해야 한다.
- face overlay를 만들지 마라. 이유: 얼굴은 body_base에 보존한다.
- 기존 테스트를 깨뜨리지 마라.
