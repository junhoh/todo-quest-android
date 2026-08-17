# Step 4: remap-adventure-shoes

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/character/todo-quest-character-modular-sheet.png`
- `/.tmp/imagegen/21-adventure-outfit-overlap-remap/adventure-bottom-layer.png`
- `/scripts/validate_character_sheet.py`
- `/phases/020-029/21-adventure-outfit-overlap-remap/index.json`
- `~/.codex/skills/.system/imagegen/SKILL.md`
- `~/.codex/skills/.system/imagegen/references/prompting.md`
- `~/.codex/skills/.system/imagegen/references/sample-prompts.md`

## 작업

`adventure-shoes-layer` 한 레이어만 편집한다. 하의 임시 파일과 최종 시트를 수정하지 않고 `/.tmp/imagegen/21-adventure-outfit-overlap-remap/adventure-shoes-layer.png`에 64×64 RGBA 결과를 저장한다.

현재 시트, base body와 승인된 하의 후보를 이미지 보기 도구로 확인하고 built-in `image_gen`의 `precise-object-edit`만 사용한다. Image 1은 편집 대상 시트, Image 2는 불변 base body, Image 3은 변경하지 않는 bottom overlap reference다.

기존 파란색·아이보리·금색 신발 디자인과 현재 팔레트만 보존하면서 다음을 만족하도록 재매핑한다.

- 불투명 픽셀은 y=53..58에만 존재하고 soleY는 58이다.
- base-body의 y=53..58 발 alpha 픽셀을 모두 덮는다.
- 외곽은 base-body 발 alpha의 Chebyshev 1픽셀 밖으로 나가지 않는다.
- bottom과 y=53..54의 `x=24..31`, `x=33..40`에서 공유된다.
- y=53..54는 bottom 아래에 숨는 채움 영역이므로 `outlineDarkNavy`를 사용하지 않는다.
- 최종 보이는 y=55..58 외부 경계만 기존 1픽셀 외곽선을 사용한다.

배경은 평면 `#FF00FF`, hard pixel art, alpha 0/255, 무안티앨리어싱·무그라디언트·무디더로 제한한다. 앞 step과 같은 chroma-key 제거, nearest-neighbor, spec의 신발 허용 팔레트 매핑을 적용한다.

후보가 기존 디자인은 유지하지만 base 발을 일부 덮지 못하면 같은 열의 가장 가까운 생성 신발 픽셀을 필수 base 좌표로 확장한다. y=53..54는 인접 신발 채움색으로 채우고 outline을 제거한다. 새 색, 장식, 발 위치 또는 sole 모양을 만들지 않는다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe -c "import json,pathlib; from PIL import Image; s=json.loads(pathlib.Path('docs/art/character/character-modular-sheet-spec.json').read_text(encoding='utf-8')); d=pathlib.Path('.tmp/imagegen/21-adventure-outfit-overlap-remap'); bottom=Image.open(d/'adventure-bottom-layer.png').convert('RGBA'); im=Image.open(d/'adventure-shoes-layer.png').convert('RGBA'); base=Image.open('docs/art/character/todo-quest-character-base-body.png').convert('RGBA'); allowed={tuple(bytes.fromhex(s['palette'][n][1:])) for n in s['designContracts']['adventure-shoes']['allowedPaletteNames']}; outline=tuple(bytes.fromhex(s['palette']['outlineDarkNavy'][1:])); feet={(x,y) for y in range(53,59) for x in range(64) if base.getpixel((x,y))[3]}; assert im.size==(64,64); assert all(a in (0,255) and (a==0 or tuple(rgb) in allowed) for *rgb,a in im.getdata()); assert all(im.getpixel((x,y))[3]==0 for y in list(range(0,53))+list(range(59,64)) for x in range(64)); assert all(im.getpixel(p)[3] for p in feet); overlap={(x,y) for y in range(53,55) for x in list(range(24,32))+list(range(33,41))}; assert all(im.getpixel(p)[3] and bottom.getpixel(p)[3] for p in overlap); assert all(im.getpixel(p)[:3]!=outline for p in overlap); assert any(im.getpixel((x,58))[3] for x in range(64))"
git diff --check
```

## 검증 절차

1. AC를 실행한다.
2. 신발 단독, base-body 위 신발, bottom까지 합친 미리보기를 1배율과 8배율로 확인한다.
3. 발 전체 덮임, y=53..54 중첩, 숨겨진 outline 부재, 좌우 발 위치와 y=58 sole을 확인한다.
4. 최종 시트, 상·하의 임시 파일, spec, validator와 Android 파일이 변경되지 않았는지 확인한다.
5. 최종 built-in 프롬프트와 mode를 기록한다.
6. task index의 step 4를 `completed`로 바꾸고 임시 경로와 발목 중첩 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 최종 시트나 상·하의 임시 파일을 수정하지 마라. 이유: 이 step은 신발 한 레이어만 다룬다.
- soleY를 58에서 이동하지 마라. 이유: 베이스 신체의 절대 발바닥 기준이다.
- y=53..54에 진한 상단 외곽선을 넣지 마라. 이유: bottom 아래에서 이중 외곽선이 된다.
- CLI/API fallback이나 새 신발 디자인을 사용하지 마라. 이유: 승인 범위를 넘는다.
- 기존 테스트를 깨뜨리지 마라.
