# Step 1: generate-top-bottom-layer-assets

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/character/layers/top_default.png`
- `/docs/art/character/layers/top_adventure.png`
- `/docs/art/character/layers/bottom_default.png`
- `/docs/art/character/layers/bottom_adventure.png`
- `/docs/art/character/layers/shoes_default.png`
- `/docs/art/character/layers/shoes_adventure.png`
- `/docs/art/character/previews/layer-bounds-preview@8x.png`
- `/docs/art/character/previews/palette@8x.png`
- `/docs/art/equipment/todo-quest-top-bottom-layers-spec.json`
- `/scripts/validate_character_equipment_layers.py`
- `/phases/040-049/47-shop-top-bottom-item-art/index.json`
- `~/.codex/skills/.system/imagegen/SKILL.md`
- `~/.codex/skills/.system/imagegen/references/prompting.md`
- `~/.codex/skills/.system/imagegen/references/sample-prompts.md`

## 작업

`imagegen` 스킬 전체와 지정 reference를 읽고 built-in `image_gen`으로 여섯 장비를 각각 한 번의 독립 generate 호출로 만든다. CLI/API fallback과 `gpt-image-1.5`는 사용하지 않는다. 기존 캐릭터와 outfit layer는 style·비율·좌표 reference이며 edit target이 아니다. 세트 일관성을 위해 각 하의 호출에는 같은 재질의 생성된 상의를 추가 reference로 사용한다.

여섯 호출은 아래 공통 prompt 골격을 유지하고 장비명·slot·bounds·재질만 바꾼다.

```text
Use case: stylized-concept
Asset type: isolated wearable top or bottom layer for the Todo Quest Android pixel RPG; the exact same PNG is also the shop thumbnail source
Input images: canonical body proportion/coordinate reference, existing isolated slot layers, production palette; for each bottom also the generated matching top as a set-style reference. References are not edit targets.
Primary request: Create exactly one original <item> as an isolated <top-only/bottom-only> sprite that fits the supplied front-facing character without changing body, face, hair, hands, pose, shoes or other equipment.
Scene/backdrop: perfectly flat solid #FF00FF chroma-key background; no shadow, gradient, texture, reflection, floor, border or decoration
Subject: only the requested clothing layer, no character and no second item
Style/medium: hard-edged native pixel art, uniform logical pixels, stepped one-pixel diagonals, no antialiasing, blur, gradient, dithering or semitransparency
Composition/framing: one 64×64 logical canvas with origin preserved, center x=32, exact inclusive bounds from the contract
Color palette: only the supplied 16 Todo Quest production colors; continuous exterior outline #263B5A
Constraints: one 8-connected silhouette; exact waist and ankle interface masks; readable at phone thumbnail size; same final PNG must work unmodified in full-origin character composition and opaque-bounds shop rendering
Avoid: body/skin/face/hair/hand/shoe pixels, alternate pose, floating pieces, isolated pixels, double seam outline, cast shadow, text, UI, logos, watermark, #FF00FF inside the item
```

디자인은 다음으로 고정한다.

- 천 세트: `lightCream`, `underLight`, `underMid` 중심의 단순한 회백색 튜닉·바지와 제한된 `tealAccent` 봉제선. 일반 등급답게 장식은 최소화한다.
- 가죽 세트: `skinShadow`를 가죽 중간색, `underDark`를 그림자·스트랩, `underLight`를 마모 highlight, 제한된 `goldAccent`를 버클로 사용한 저킨·보강 바지다.
- 철/강철 세트: `blueShadow` 내피 위 `underDark`, `underMid`, `underLight`, `lightCream`의 분절형 흉갑·무릎·정강이 보호대와 제한된 금속 rivet를 사용한다. 강철 각반은 신발을 포함하지 않는다.

각 결과는 다음 절차로 정규화한다.

1. built-in 결과를 `$CODEX_HOME/generated_images`에서 `/.tmp/imagegen/47-shop-top-bottom-item-art/<key>/source.png`로 복사한다.
2. 설치된 imagegen `remove_chroma_key.py`를 사용해 평면 `#FF00FF`를 제거하고 최종 alpha를 반드시 `0/255`, transparent RGB를 0으로 만든다.
3. Pillow `Image.Resampling.NEAREST`만 사용해 계약 bounds에 맞추고 16색 nearest mapping, 외부 경계 정규화와 고립 생성 노이즈 제거만 수행한다.
4. Python/SVG/HTML/canvas로 의상 디자인을 새로 그리거나 누락된 갑옷·바지를 합성하지 않는다. 모델 디자인의 크기·위치·팔레트·외곽선만 결정적으로 정규화한다.
5. canonical 결과를 `/docs/art/equipment/layers/<key>.png` 여섯 경로에 저장한다.
6. 각 상의는 기존 adventure 하의·신발과, 각 하의는 기존 adventure 상의·신발과 합성해 장비별 1×/8× preview를 만든다. 신규 상의 3×하의 3 조합의 1×/4× matrix도 만든다.
7. spec의 canonical/preview status, bounds, count, byte count와 file/raw-RGBA/alpha-mask hash를 실제 결과로 갱신한다.
8. validator와 1×/확대 preview를 확인하고 한 번에 한 문제만 대상으로 item별 최대 두 번 built-in edit 후 재정규화한다.

최대 수정 뒤에도 contract나 육안 착용 품질을 통과하지 못하거나 built-in tool을 사용할 수 없으면 CLI로 우회하지 말고 step을 `blocked`로 기록한다. 최종 prompt, built-in mode와 저장 경로를 summary에 남긴다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-top-bottom-layers-spec.json --check-sources
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-helmet-layers-spec.json --check
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_character_equipment_layers.py scripts\test_validate_character_sheet.py --basetemp build\pytest-47-outfit-art
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check
git diff --check
```

## 검증 절차

1. `view_image`로 base, 기존 top/bottom/shoes, bounds와 palette reference를 먼저 확인한다.
2. 여섯 item을 별도 built-in 호출로 생성·정규화하고 AC를 실행한다.
3. 장비별 preview와 3×3 matrix를 확인해 천→가죽→철/강철 진행, 세트 일관성, 혼합 착용, 손·허리·발목·신발 경계를 검사한다.
4. task index의 step 1을 `completed`로 바꾸고 canonical PNG 6개와 preview 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 여러 장비를 한 호출의 batch sheet로 생성하지 마라. 이유: 서로 다른 item은 독립 prompt와 결과 검증이 필요하다.
- 별도 shop thumbnail PNG를 만들지 마라. 이유: 동일 runtime layer의 bounds-fit 재사용이 승인 계약이다.
- CLI/API fallback으로 전환하지 마라. 이유: 단순 불투명 pixel layer는 built-in chroma-key 경로로 처리한다.
- 캐릭터나 다른 slot 픽셀을 최종 layer에 포함하지 마라. 이유: runtime source-over 합성에서 외형이 깨진다.
- 기존 테스트를 깨뜨리지 마라.
