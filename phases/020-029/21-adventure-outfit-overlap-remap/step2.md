# Step 2: remap-adventure-top

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/character/todo-quest-character-modular-sheet.png`
- `/scripts/validate_character_sheet.py`
- `/phases/020-029/21-adventure-outfit-overlap-remap/index.json`
- `~/.codex/skills/.system/imagegen/SKILL.md`
- `~/.codex/skills/.system/imagegen/references/prompting.md`
- `~/.codex/skills/.system/imagegen/references/sample-prompts.md`

## 작업

`adventure-top-layer` 한 레이어만 편집한다. 최종 시트와 다른 타일은 수정하지 않고 `/.tmp/imagegen/21-adventure-outfit-overlap-remap/adventure-top-layer.png`에 64×64 RGBA 임시 결과를 저장한다.

재실행 시 최종 임시 파일이 이미 존재하면 built-in imagegen을 새로 호출하기 전에 아래 Acceptance Criteria 전체를 실행한다. 디자인·팔레트·overlap이 맞고 geometry 또는 외부 outline만 실패하면 기존 파일을 built-in provenance가 있는 디자인 원본으로 유지하고 아래 결정적 정규화만 수행한다. 기존 파일의 디자인 자체가 요구와 다를 때만 새 built-in edit을 호출한다.

현재 시트와 base body를 이미지 보기 도구로 원본 해상도에서 먼저 확인한 뒤 built-in `image_gen` edit 모드만 사용한다. Image 1은 편집 대상 시트, Image 2는 불변 신체·좌표 reference다. Use case는 `precise-object-edit`이다. CLI/API fallback 또는 다른 모델로 전환하지 않는다.

프롬프트에는 다음을 명시한다.

```text
Use case: precise-object-edit
Asset type: Todo Quest modular paper-doll hard pixel-art character sheet
Input images: Image 1 is the edit target; Image 2 is the immutable base-body anatomy and coordinate reference.
Primary request: Remap only the existing adventure_top pixels onto the base torso and arms. Keep the current short blue jacket, ivory inner shirt, teal accents and color placement; this is coordinate and silhouette correction, not a redesign. Make top and bottom intentionally share x=24..40 at y=41..43, with the top drawn later.
Scene/backdrop: perfectly flat solid #FF00FF chroma-key background with no grid, shadow, labels, text or decoration
Style/medium: native hard pixel art, uniform square logical pixels, alpha only 0 or 255, no antialiasing, blur, gradient, dithering or semitransparency
Composition/framing: preserve the exact 8x2 grid and 64x64 local coordinates; center x=32; primary jacket mapping y=29..43; existing collar pixels y=24..28 only where they directly overlap base-body alpha; garment may extend at most one pixel outside the base alpha silhouette
Constraints: cover the base neutral shirt pixels; follow shoulders and arms; leave every handProtectionContract coordinate transparent; use the existing six top palette colors only; use fill colors at y=41..42 and a single visible one-pixel hem at y=43; preserve every other tile exactly
Avoid: long coat tails, lower-body pixels, covered hands, floating shoulders or sleeves, two-row dark hem, new colors, new equipment, text, logo or watermark
```

built-in 결과를 `$CODEX_HOME/generated_images`에서 phase 전용 `.tmp`로 복사하고 설치된 `remove_chroma_key.py`로 `#FF00FF`를 제거한다. 알파를 0/255로 양자화하고 nearest-neighbor와 기존 상의 허용 팔레트로만 무디더 최근접 매핑한다. 전체 생성물을 최종 시트로 사용하지 말고 target layer만 실제 로컬 좌표로 추출한다.

후보가 디자인은 보존하지만 좌표만 위반하면 다음 결정적 정규화만 허용한다.

- 허용 범위와 base-body 1픽셀 dilation 밖 픽셀을 지운다.
- 모든 픽셀을 top-level `commonAllowedBox`의 x=20..44 안으로 제한하고 opaque bbox의 좌우 끝 합이 64가 되게 해 중심축 x=32를 맞춘다. 한쪽이 1픽셀 더 멀면 그 바깥 행만 자르고 내부 디자인은 이동시키지 않는다.
- y=24..28의 base alpha 밖 픽셀과 손 보호 좌표를 지운다.
- 기존 후보 픽셀을 가장 가까운 base 장착 좌표로 최대 1픽셀 이동한다.
- `x=24..40, y=41..43` 누락 픽셀을 같은 열 바로 위 생성 픽셀로 채우고 y=43만 `outlineDarkNavy`로 정규화한다.
- 불투명 픽셀의 상하좌우 중 하나가 투명 또는 캔버스 밖이면 그 픽셀을 최종 외부 경계로 보고 `outlineDarkNavy`로 바꾼다. 허리 y=41..42에서는 좌우 외부 끝점만 이에 해당하며 행 전체를 outline으로 만들지 않는다.
- 후보에 없는 색, 장식, 실루엣을 새로 만들지 않는다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe -c "import json,pathlib; from PIL import Image; s=json.loads(pathlib.Path('docs/art/character/character-modular-sheet-spec.json').read_text(encoding='utf-8')); d=pathlib.Path('.tmp/imagegen/21-adventure-outfit-overlap-remap'); im=Image.open(d/'adventure-top-layer.png').convert('RGBA'); base=Image.open('docs/art/character/todo-quest-character-base-body.png').convert('RGBA'); allowed={tuple(bytes.fromhex(s['palette'][n][1:])) for n in s['designContracts']['adventure-top']['allowedPaletteNames']}; outline=tuple(bytes.fromhex(s['palette']['outlineDarkNavy'][1:])); assert im.size==(64,64); assert all(a in (0,255) and (a==0 or tuple(rgb) in allowed) for *rgb,a in im.getdata()); assert all(im.getpixel((x,y))[3]==255 for y in range(41,44) for x in range(24,41)); assert all(im.getpixel(tuple(p))[3]==0 for p in s['handProtectionContract']['pixelCoordinates']); assert all(im.getpixel((x,y))[3]==0 for y in range(44,64) for x in range(64)); assert all(not im.getpixel((x,y))[3] or base.getpixel((x,y))[3] for y in range(24,29) for x in range(64)); assert all(im.getpixel((x,43))[:3]==outline for x in range(24,41))"
.\.venv\Scripts\python.exe -c "import json,pathlib; from PIL import Image; s=json.loads(pathlib.Path('docs/art/character/character-modular-sheet-spec.json').read_text(encoding='utf-8')); im=Image.open('.tmp/imagegen/21-adventure-outfit-overlap-remap/adventure-top-layer.png').convert('RGBA'); base=Image.open('docs/art/character/todo-quest-character-base-body.png').convert('RGBA'); pts={(x,y) for y in range(64) for x in range(64) if im.getpixel((x,y))[3]}; bp={(x,y) for y in range(64) for x in range(64) if base.getpixel((x,y))[3]}; dil={(x+dx,y+dy) for x,y in bp for dx in (-1,0,1) for dy in (-1,0,1) if 0<=x+dx<64 and 0<=y+dy<64}; outline=tuple(bytes.fromhex(s['palette']['outlineDarkNavy'][1:])); box=(min(x for x,y in pts),min(y for x,y in pts),max(x for x,y in pts),max(y for x,y in pts)); boundary={p for p in pts if any((p[0]+dx,p[1]+dy) not in pts for dx,dy in ((-1,0),(1,0),(0,-1),(0,1)))}; assert all(20<=x<=44 for x,y in pts); assert box[0]+box[2]==64; assert all((x,y) in dil for x,y in pts if 29<=y<=43); assert all(im.getpixel(p)[:3]==outline for p in boundary)"
git diff --check
```

## 검증 절차

1. AC를 실행한다.
2. 임시 상의를 1배율과 nearest-neighbor 8배율로 확인해 어깨·소매 부착, 손 노출, 짧은 재킷과 단일 hem을 확인한다.
3. 최종 시트, 다른 임시 레이어, spec, validator와 Android 파일이 변경되지 않았는지 확인한다.
4. 최종 built-in 프롬프트와 mode를 작업 기록에 남긴다.
5. task index의 step 2를 `completed`로 바꾸고 임시 경로와 매핑 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 최종 시트나 다른 타일을 수정하지 마라. 이유: 이 step은 상의 한 레이어만 다룬다.
- Python, SVG, HTML 또는 canvas로 새 재킷을 디자인하지 마라. 이유: 기존 디자인을 보존한 built-in edit이어야 한다.
- CLI/API fallback으로 전환하지 마라. 이유: 사용자가 승인하지 않은 경로다.
- 얼굴, 신체, 하의 또는 신발 픽셀을 후보에 복사하지 마라. 이유: 레이어 분리를 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
