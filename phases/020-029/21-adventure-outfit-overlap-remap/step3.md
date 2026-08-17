# Step 3: remap-adventure-bottom

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/character/todo-quest-character-modular-sheet.png`
- `/.tmp/imagegen/21-adventure-outfit-overlap-remap/adventure-top-layer.png`
- `/scripts/validate_character_sheet.py`
- `/phases/020-029/21-adventure-outfit-overlap-remap/index.json`
- `~/.codex/skills/.system/imagegen/SKILL.md`
- `~/.codex/skills/.system/imagegen/references/prompting.md`
- `~/.codex/skills/.system/imagegen/references/sample-prompts.md`

## 작업

`adventure-bottom-layer` 한 레이어만 편집한다. 상의 임시 파일과 최종 시트를 수정하지 않고 `/.tmp/imagegen/21-adventure-outfit-overlap-remap/adventure-bottom-layer.png`에 64×64 RGBA 결과를 저장한다.

현재 시트, base body와 승인된 상의 후보를 이미지 보기 도구로 확인하고 built-in `image_gen`의 `precise-object-edit`만 사용한다. Image 1은 편집 대상 시트, Image 2는 불변 base body, Image 3은 변경하지 않는 상의 overlap reference다.

기존 짙은 남색 바지의 색과 명암을 보존하면서 다음 좌표로만 재매핑하라고 명시한다.

- y=41..48: `x=24..40` 불투명
- y=49..54: `x=24..31`, `x=33..40` 불투명, `x=32` 투명
- 상의와 `x=24..40, y=41..43`에서 공유되고 top이 나중에 합성
- 신발과 양쪽 다리 y=53..54에서 공유되고 bottom이 나중에 합성
- 숨겨진 y=41..43에는 `outlineDarkNavy`가 없고 y=54만 최종 보이는 한 행 바짓단
- 하의 3색만 사용하고 파란색·아이보리·피부·신발 색을 사용하지 않음

배경은 평면 `#FF00FF`, hard pixel art, alpha 0/255, 무안티앨리어싱·무그라디언트·무디더로 제한한다. top step과 같은 chroma-key 제거, nearest-neighbor, 기존 팔레트 매핑을 적용한다.

후보가 바지 디자인은 유지하지만 mask만 어기면 허용 좌표 밖을 지우고 같은 행·가까운 열의 생성 픽셀로 필수 mask를 채우는 결정적 정규화만 허용한다. y=41..43은 기존 바지 fill 색으로 채우고 outline을 제거한다. y=54는 `outlineDarkNavy` 한 행으로 닫는다. 새 장식이나 색을 만들지 않는다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe -c "import json,pathlib; from PIL import Image; s=json.loads(pathlib.Path('docs/art/character/character-modular-sheet-spec.json').read_text(encoding='utf-8')); d=pathlib.Path('.tmp/imagegen/21-adventure-outfit-overlap-remap'); top=Image.open(d/'adventure-top-layer.png').convert('RGBA'); im=Image.open(d/'adventure-bottom-layer.png').convert('RGBA'); allowed={tuple(bytes.fromhex(s['palette'][n][1:])) for n in s['designContracts']['adventure-bottom']['allowedPaletteNames']}; outline=tuple(bytes.fromhex(s['palette']['outlineDarkNavy'][1:])); required={(x,y) for y in range(41,49) for x in range(24,41)}|{(x,y) for y in range(49,55) for x in list(range(24,32))+list(range(33,41))}; opaque={(x,y) for y in range(64) for x in range(64) if im.getpixel((x,y))[3]}; assert im.size==(64,64); assert opaque==required; assert all(a in (0,255) and (a==0 or tuple(rgb) in allowed) for *rgb,a in im.getdata()); assert all(im.getpixel((x,y))[:3]!=outline for y in range(41,44) for x in range(24,41)); assert all(im.getpixel((x,54))[:3]==outline for x in list(range(24,32))+list(range(33,41))); assert all(top.getpixel((x,y))[3] and im.getpixel((x,y))[3] for y in range(41,44) for x in range(24,41))"
git diff --check
```

## 검증 절차

1. AC를 실행한다.
2. 하의 단독, base-body 위 하의, 상의까지 합친 미리보기를 1배율과 8배율로 확인한다.
3. 골반 부착, x=32 다리 간격, 상의와 3행 중첩, 신발 예정 영역과 2행 중첩, 숨겨진 outline 부재를 확인한다.
4. 최종 시트, 상의 임시 파일, spec, validator와 Android 파일이 변경되지 않았는지 확인한다.
5. 최종 built-in 프롬프트와 mode를 기록한다.
6. task index의 step 3을 `completed`로 바꾸고 임시 경로와 mask 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 최종 시트나 상의 임시 파일을 수정하지 마라. 이유: 이 step은 하의 한 레이어만 다룬다.
- x=32에 불투명 가랑이 선을 만들지 마라. 이유: 베이스 바디의 실제 다리 간격은 y=49부터 투명하다.
- 숨겨진 y=41..43에 외곽선을 넣지 마라. 이유: 최종 합성에서 이중 외곽선이 된다.
- CLI/API fallback이나 새 바지 디자인을 사용하지 마라. 이유: 승인 범위를 넘는다.
- 기존 테스트를 깨뜨리지 마라.
