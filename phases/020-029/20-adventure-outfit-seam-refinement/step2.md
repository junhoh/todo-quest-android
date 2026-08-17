# Step 2: refine-adventure-top-layer

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
- `/phases/020-029/20-adventure-outfit-seam-refinement/index.json`
- `~/.codex/skills/.system/imagegen/SKILL.md`
- `~/.codex/skills/.system/imagegen/references/prompting.md`
- `~/.codex/skills/.system/imagegen/references/sample-prompts.md`

## 작업

이 step은 `adventure-top-layer` 한 레이어만 편집한다. 최종 모듈 시트와 다른 타일은 수정하지 않고, 결과를 `.tmp/imagegen/20-adventure-outfit-seam-refinement/adventure-top-layer.png`에 64x64 RGBA 임시 산출물로 저장한다.

작업 시작 시 위 최종 임시 파일이 이미 존재하면 새 후보를 만들기 전에 Acceptance Criteria 명령을 그대로 실행하고 1배율·8배율로 검사한다. AC와 시각 검사를 통과하면 이전 개별 후보의 실패 여부와 관계없이 해당 최종 파일을 채택하고 step을 `completed`로 기록한다. 기존 최종 파일이 없거나 AC를 실패할 때만 아래 imagegen 흐름을 수행한다.

이미지 보기 도구로 현재 모듈 시트와 base body를 먼저 확인한 뒤 built-in `image_gen` edit 모드만 사용한다. Image 1은 edit target인 현재 모듈 시트, Image 2는 불변 신체·좌표 reference인 base body다. CLI/API fallback, `gpt-image-1.5`와 다른 모델을 사용하지 않는다.

프롬프트는 사용자 요구를 창작적으로 확장하지 말고 다음 구조와 불변 조건을 포함한다.

```text
Use case: identity-preserve
Asset type: modular paper-doll hard pixel-art character sheet for Todo Quest
Input images: Image 1 is the edit target; Image 2 is the immutable base-body coordinate and anatomy reference.
Primary request: Change only the separated adventure_top layer into a waist-length blue adventurer jacket. Remove the long center front tail and both side coat tails. Keep the ivory inner shirt visible, keep the small teal shoulder and sleeve accents, and end every torso, shirt, button, zipper, and vertical decoration pixel at y=42.
Scene/backdrop: perfectly flat solid #FF00FF chroma-key background; no floor, shadow, grid, labels, text, or decoration
Style/medium: native hard-edged pixel art, uniform square logical pixels, no antialiasing, blur, gradients, dithering, or semitransparency
Composition/framing: preserve the exact 8x2 grid and 64x64 local equipped coordinates; character center x=32; jacket torso x=24..40 and y=29..42; final near-horizontal hem y=42; absolutely no adventure_top pixel at y>=43
Color palette: use only bluePrimary #4F86E8, blueShadow #2853A6, blueHighlight #7FB3FF, tealAccent #5CC8A7, lightCream #F4EFE3, and outlineDarkNavy #263B5A from the existing palette
Constraints: preserve body size, pose, arms, hands, face, hair, hat, gloves, shoes and every other sheet tile; keep hand-protection pixels uncovered; preserve one-pixel dark-navy outline; make it read as a short jacket, not a coat, robe, skirt, or one-piece outfit
Avoid: long front tail, side coat tails, pixels below y=42, covered hands, trousers or shoe pixels inside the top layer, new colors, new equipment, isolated pixels, irregular hem, text, logo, watermark
```

built-in 결과를 `$CODEX_HOME/generated_images`에서 지정된 `.tmp` 디렉터리로 복사하고 설치된 `remove_chroma_key.py`로 `#FF00FF`를 제거한다. 최종 알파는 0/255로 양자화하고 nearest-neighbor만 사용하며, 기존 16색 중 허용된 상의색으로 dithering 없이 최근접 매핑한다. 모델 결과 전체를 최종 시트로 사용하지 말고 `adventure-top-layer`만 실제 64x64 로컬 좌표로 추출한다.

후보가 계약을 위반하면 한 번에 하나의 문제만 지적하는 targeted built-in edit을 수행한다. built-in imagegen 후보가 짧은 재킷 디자인은 만족하지만 정확한 로컬 seam이나 손 보호 좌표만 어긴 경우에는 생성 결과를 디자인 원본으로 유지한 채 다음 결정적 계약 정규화만 허용한다.

- `y=24..42` 밖 픽셀과 `handProtectionContract.pixelCoordinates`의 픽셀을 투명하게 지운다.
- 몸통 바깥의 좌우 코트 자락과 고립 픽셀을 제거하되, 유효 영역 안의 재킷·셔츠·청록 장식 디자인은 재배치하거나 새로 그리지 않는다.
- `y=42, x=24..40` 중 투명한 픽셀은 같은 x의 `y=41` 생성 픽셀을 한 행 아래로 복사해 연속 밑단을 만든다. `x=24`와 `x=40` 끝점은 기존 `outlineDarkNavy` 외곽선으로 닫는다. 같은 열의 `y=41`도 투명하면 가장 가까운 좌우 생성 픽셀을 사용한다.
- 정규화에는 후보에 이미 존재하는 허용 팔레트색만 사용하고 새 색상, 새 장식, 새 실루엣을 만들지 않는다.

이미 생성돼 있는 `.tmp/imagegen/20-adventure-outfit-seam-refinement/candidate-*-extracted.png` 후보가 있으면 먼저 직접 검사해 가장 나은 짧은 재킷 후보를 재사용한다. 유효 후보 파일이 없을 때만 built-in imagegen을 다시 호출한다. 정규화 후에도 계약이나 시각 검사를 통과하지 못하면 step을 `error`로 기록한다.

임시 상의는 다음을 모두 만족해야 한다.

- 크기 64x64 RGBA, 알파 0/255, 실제 장착 좌표, 투명 배경
- `y=24..42` 밖 불투명 픽셀 없음, 몸통 `x=24..40, y=29..42`
- `y=42, x=24..40` 연속 밑단과 `y>=43` 완전 투명
- 손 보호 좌표 완전 투명
- 파란 재킷, 아이보리 셔츠, 작은 청록 어깨·소매 장식과 짙은 남색 1px 외곽선
- 피부, 바지, 신발, 얼굴, 머리카락, 모자와 액세서리 픽셀 없음
- 고립 픽셀과 반투명 픽셀 없음

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe -c "import json,pathlib; from PIL import Image; s=json.loads(pathlib.Path('docs/art/character/character-modular-sheet-spec.json').read_text(encoding='utf-8')); p=pathlib.Path('.tmp/imagegen/20-adventure-outfit-seam-refinement/adventure-top-layer.png'); im=Image.open(p).convert('RGBA'); allowed={tuple(bytes.fromhex(s['palette'][n][1:])) for n in s['designContracts']['adventure-top']['allowedPaletteNames']}; assert im.size==(64,64); assert all(a in (0,255) and (a==0 or tuple(rgb) in allowed) for *rgb,a in im.getdata()); assert all(im.getpixel((x,y))[3]==0 for y in range(43,64) for x in range(64)); assert all(im.getpixel((x,42))[3]==255 for x in range(24,41)); assert all(im.getpixel(tuple(pt))[3]==0 for pt in s['handProtectionContract']['pixelCoordinates'])"
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. 임시 상의를 1배율과 nearest-neighbor 8배 확대 상태로 검사해 짧은 재킷, 수평 밑단, 셔츠 종료, 손 비침범과 고립 픽셀 부재를 확인한다.
3. 최종 모듈 PNG, spec, validator, 테스트와 Android 파일이 변경되지 않았는지 확인한다.
4. imagegen 최종 프롬프트와 built-in mode 사용을 작업 기록에 남긴다.
5. task index의 step 2를 `completed`로 바꾸고 임시 상의 경로와 디자인·경계 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 최종 모듈 시트를 수정하지 마라. 이유: 이 step은 상의 한 레이어 후보만 만든다.
- Python, SVG, HTML 또는 canvas로 재킷 디자인을 새로 그리지 마라. 이유: 디자인 원본은 built-in imagegen 결과여야 하며, 코드는 위에 열거한 좌표 계약 정규화에만 사용한다.
- CLI/API fallback이나 다른 이미지 모델을 사용하지 마라. 이유: 승인되지 않은 실행 경로 전환이다.
- 상의와 하의를 합치거나 다른 타일을 후보에서 가져오지 마라. 이유: 모듈형 레이어 계약을 위반한다.
- 기존 테스트를 깨뜨리지 마라.
