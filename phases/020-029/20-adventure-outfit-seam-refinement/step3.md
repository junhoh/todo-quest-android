# Step 3: refine-adventure-bottom-layer

## Completed artifact guard

The built-in image-generation edit for this step produced the approved trouser
design. The deterministic contract-normalized artifact is stored at
`.tmp/imagegen/20-adventure-outfit-seam-refinement/adventure-bottom-layer.png`.
Its SHA-256 at handoff is
`c35b3de2ec0debeb10cc0af9fb3d49829783197adaedbd16465643d0871c91b6`.

This step must not write, copy, replace, normalize, preview-export, or otherwise
modify `.tmp/imagegen/20-adventure-outfit-seam-refinement/adventure-top-layer.png`.
The approved top SHA-256 at handoff is
`1ed91690e8952d471d709659a41fab126102890ab87f95bce0be371040225422`.

The approved bottom was derived from the built-in image-generation candidate 9
trouser component, then limited to the existing three-color palette and exact
y=43..52 seam geometry. Do not select any jacket-shaped connected component from
the candidate sheets if this step is audited or repeated.

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/character/todo-quest-character-modular-sheet.png`
- `/.tmp/imagegen/20-adventure-outfit-seam-refinement/adventure-top-layer.png`
- `/scripts/validate_character_sheet.py`
- `/phases/020-029/20-adventure-outfit-seam-refinement/index.json`
- `~/.codex/skills/.system/imagegen/SKILL.md`
- `~/.codex/skills/.system/imagegen/references/prompting.md`
- `~/.codex/skills/.system/imagegen/references/sample-prompts.md`

## 작업

이 step은 `adventure-bottom-layer` 한 레이어만 편집한다. 최종 모듈 시트와 상의 임시 파일을 수정하지 않고 결과를 `.tmp/imagegen/20-adventure-outfit-seam-refinement/adventure-bottom-layer.png`에 64x64 RGBA 임시 산출물로 저장한다.

이미지 보기 도구로 현재 모듈 시트, base body와 step 2의 상의 후보를 먼저 확인한다. built-in `image_gen` edit 모드만 사용한다. Image 1은 edit target인 모듈 시트, Image 2는 불변 base body reference, Image 3은 변경하지 않는 상의 seam reference다.

프롬프트는 다음 구조와 불변 조건을 포함한다.

```text
Use case: identity-preserve
Asset type: modular paper-doll hard pixel-art character sheet for Todo Quest
Input images: Image 1 is the edit target; Image 2 is the immutable base-body coordinate reference; Image 3 is the approved short adventure_top seam reference and must not be changed.
Primary request: Change only the separated adventure_bottom layer into independent dark-navy trousers. Start with a continuous waist at y=43 directly below the jacket, end at y=52, make left and right legs clear and nearly symmetric, and use a simple one-pixel dark outline crotch divider centered at x=32.
Scene/backdrop: perfectly flat solid #FF00FF chroma-key background; no floor, shadow, grid, labels, text, or decoration
Style/medium: native hard-edged pixel art, uniform square logical pixels, no antialiasing, blur, gradients, dithering, or semitransparency
Composition/framing: preserve the exact 8x2 grid and 64x64 local equipped coordinates; character center x=32; trousers only y=43..52; continuous waist x=24..40 at y=43; shoes begin y=53 and must remain separate
Color palette: use only eyeDarkNavy #1D3557, underDark #3A3F45, and outlineDarkNavy #263B5A
Constraints: preserve body size, pose, leg and foot positions, shoes, short jacket and every other tile; clear waist contrast, two readable trouser legs, one-pixel center divider, matching leg lengths and near-matching widths, no exposed skin or base undergarment at the waist
Avoid: blue jacket or ivory shirt pixels, coat tails, skirt, robe, one-piece outfit, pixels above y=43 or below y=52, shoe pixels, extra colors, isolated pixels, irregular outline, text, logo, watermark
```

built-in 결과는 step 2와 같은 chroma-key 제거, 0/255 알파, nearest-neighbor, 무디더 팔레트 매핑 절차를 사용한다. 모델 결과에서 하의 레이어만 실제 64x64 장착 좌표로 추출한다. 위반 시 한 문제만 대상으로 targeted built-in edit을 수행한다.

built-in 후보가 짙은 남색 바지 디자인은 만족하지만 정확한 행 경계만 어긴 경우에는 생성 결과를 디자인 원본으로 유지한 채 다음 결정적 계약 정규화만 허용한다.

- `y=43..52` 밖 픽셀과 고립 픽셀을 투명하게 지운다.
- `y=43, x=24..40` 중 투명한 픽셀은 같은 x의 `y=44` 생성 픽셀을 복사해 연속 허리선을 만들고 양 끝은 `outlineDarkNavy`로 닫는다.
- 후보의 중앙 가랑이 명암을 `x=32`의 `outlineDarkNavy` 1px 선으로 정규화하고, 좌우 다리의 끝 y를 `52`로 맞춘다. 이때 후보의 다리 폭·실루엣·명암을 새 디자인으로 교체하지 않는다.
- 허용된 세 색만 사용하며 새 색상, 장식, 상의 또는 신발 픽셀을 만들지 않는다.

정규화 후에도 계약이나 시각 검사를 통과하지 못하면 step을 `error`로 기록한다.

임시 하의는 다음을 모두 만족해야 한다.

- 크기 64x64 RGBA, 알파 0/255, 투명 배경과 실제 장착 좌표
- 불투명 픽셀은 `y=43..52`에만 존재
- `y=43, x=24..40` 연속 허리선
- `x=32`의 1px 짙은 외곽선 중앙 분리선과 좌우 다리의 동일한 끝 y=52
- 허용된 세 색만 사용하고 파란색·아이보리·피부·신발 픽셀 없음
- 고립 픽셀, 불규칙 얼룩과 반투명 픽셀 없음
- step 2 상의의 `y=42`와 결합했을 때 `x=24..40`에 빈 줄이나 중복 좌표 없음

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe -c "import json,pathlib; from PIL import Image; s=json.loads(pathlib.Path('docs/art/character/character-modular-sheet-spec.json').read_text(encoding='utf-8')); d=pathlib.Path('.tmp/imagegen/20-adventure-outfit-seam-refinement'); top=Image.open(d/'adventure-top-layer.png').convert('RGBA'); bottom=Image.open(d/'adventure-bottom-layer.png').convert('RGBA'); allowed={tuple(bytes.fromhex(s['palette'][n][1:])) for n in s['designContracts']['adventure-bottom']['allowedPaletteNames']}; assert bottom.size==(64,64); assert all(a in (0,255) and (a==0 or tuple(rgb) in allowed) for *rgb,a in bottom.getdata()); assert all(bottom.getpixel((x,y))[3]==0 for y in list(range(0,43))+list(range(53,64)) for x in range(64)); assert all(bottom.getpixel((x,43))[3]==255 for x in range(24,41)); assert all(top.getpixel((x,42))[3]==255 and bottom.getpixel((x,43))[3]==255 for x in range(24,41)); assert all(not(top.getpixel((x,y))[3] and bottom.getpixel((x,y))[3]) for y in range(64) for x in range(64))"
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. 임시 하의와 상·하의 결합 미리보기를 1배율 및 nearest-neighbor 8배 확대 상태로 검사한다.
3. 연속 허리선, 두 다리 구분, 1px 중앙선, 같은 길이, 신발 비침범과 상의색 부재를 확인한다.
4. 최종 모듈 PNG, 상의 임시 파일, spec, validator, 테스트와 Android 파일이 변경되지 않았는지 확인한다.
5. imagegen 최종 프롬프트와 built-in mode 사용을 작업 기록에 남긴다.
6. task index의 step 3을 `completed`로 바꾸고 임시 하의 경로와 경계·다리 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 최종 모듈 시트나 step 2 상의를 수정하지 마라. 이유: 이 step은 하의 한 레이어 후보만 만든다.
- Python, SVG, HTML 또는 canvas로 바지 디자인을 새로 그리지 마라. 이유: 디자인 원본은 built-in imagegen 결과여야 하며, 코드는 위에 열거한 좌표 계약 정규화에만 사용한다.
- CLI/API fallback이나 다른 이미지 모델을 사용하지 마라. 이유: 승인되지 않은 실행 경로 전환이다.
- 바지를 상의 또는 신발과 합치지 마라. 이유: 모듈형 장착 계약을 위반한다.
- 기존 테스트를 깨뜨리지 마라.
