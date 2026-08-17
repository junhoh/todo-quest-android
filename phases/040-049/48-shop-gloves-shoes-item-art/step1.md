# Step 1: generate-gloves-shoes-layer-assets

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/character/layers/hands_front.png`
- `/docs/art/character/layers/shoes_adventure.png`
- `/docs/art/character/layers/top_adventure.png`
- `/docs/art/character/layers/bottom_adventure.png`
- `/docs/art/character/layers/weapon_held_default_sword.png`
- `/docs/art/character/layers/weapon_front_default_sword.png`
- `/docs/art/character/previews/adventure-equipped@8x.png`
- `/docs/art/character/previews/palette@8x.png`
- `/docs/art/equipment/todo-quest-gloves-shoes-layers-spec.json`
- `/scripts/validate_character_equipment_layers.py`
- `/phases/040-049/48-shop-gloves-shoes-item-art/index.json`
- `~/.codex/skills/.system/imagegen/SKILL.md`
- `~/.codex/skills/.system/imagegen/references/prompting.md`
- `~/.codex/skills/.system/imagegen/references/sample-prompts.md`

## 작업

`imagegen` 스킬 전체와 지정 reference를 읽고 built-in `image_gen`으로 네 장비를 각각 한 번의 독립 generate 호출로 만든다. CLI/API fallback과 `gpt-image-1.5`는 사용하지 않는다. 기존 캐릭터·손·신발·검 레이어는 style·비율·좌표 reference이며 edit target이 아니다.

네 호출은 아래 prompt 골격을 유지하고 item·slot·재질만 바꾼다.

```text
Use case: stylized-concept
Asset type: isolated wearable gloves or shoes layer for the Todo Quest Android pixel RPG; the exact same PNG is also the shop thumbnail source
Input images: canonical front-facing character, existing isolated hands or shoes alpha-mask reference, production palette, and sword grip reference for gloves. References are coordinate/style references and are not edit targets.
Primary request: Create exactly one original <item> as an isolated <paired gloves-only / paired shoes-only> sprite. It must fit the supplied character without changing body, face, hair, pose, clothing, weapon or other equipment.
Scene/backdrop: perfectly flat solid #FF00FF chroma-key background; no shadow, gradient, texture, reflection, floor, border or decoration
Subject: only the requested paired item, no character and no second item
Style/medium: hard-edged native pixel art with uniform logical pixels, one-pixel stepped details, no antialiasing, blur, gradient, dithering or semitransparency
Composition/framing: one 64×64 logical canvas with full origin preserved; exact alpha mask, component bounds and opaque count from the contract
Color palette: only the supplied 16 Todo Quest production colors; continuous exterior outline #263B5A where the mask permits
Constraints: readable at phone thumbnail size; same final PNG must work unmodified in full-origin character composition and opaque-bounds shop rendering
Avoid: body/skin/face/hair/clothing/weapon pixels, alternate pose, floating pieces, isolated noise, cast shadow, text, UI, logos, watermark, #FF00FF inside the item
```

디자인은 다음으로 고정한다.

- `gloves_leather`: 갈색 가죽 중간색, 어두운 손바닥·봉제선과 제한된 금색 손목 디테일. 기존 밝은 피부색 장갑처럼 보이지 않게 한다.
- `gloves_steel_gauntlets`: 은회색 분절 강철 관절, 짙은 남색 내피와 제한된 청색 highlight. 손 mask 밖으로 금속 plate를 확장하지 않는다.
- `shoes_travelers_boots`: 짙은 갈색 여행용 가죽, 견고한 strap과 밝은 밑창. 다리나 바지를 포함하지 않는다.
- `shoes_windwalker_boots`: 가벼운 남청색 장화, 청록색 바람 문양과 밝은 밑창. 날개나 mask 밖 장식을 추가하지 않는다.

각 결과는 다음 절차로 정규화한다.

1. built-in 결과를 `$CODEX_HOME/generated_images`에서 `/.tmp/imagegen/48-shop-gloves-shoes-item-art/<key>/source.png`로 복사한다.
2. 설치된 imagegen `remove_chroma_key.py`로 평면 `#FF00FF`를 제거하고 alpha를 `0/255`, transparent RGB를 0으로 만든다.
3. Pillow `Image.Resampling.NEAREST`만 사용해 계약 alpha mask에 투영하고 16색 nearest mapping, 외곽선 정규화와 고립 노이즈 제거만 수행한다.
4. Python/SVG/HTML/canvas로 장비 디자인을 새로 그리지 않는다. 모델 디자인의 색·재질·디테일만 계약 mask에 결정적으로 정규화한다.
5. canonical 결과를 `/docs/art/equipment/layers/`의 네 계약 경로에 저장한다.
6. 기존 adventure appearance에서 `hands_front` 또는 `shoes_adventure`만 대상 layer로 교체해 장비별 1×/8× preview와 2×2 조합 matrix를 만든다. 장갑 preview는 기본 검을 포함해 grip을 검증한다.
7. spec의 source/preview status, bounds, count, byte count와 file/raw-RGBA/alpha-mask hash를 실제 결과로 갱신한다.
8. validator와 preview를 확인하고 한 번에 한 문제만 대상으로 item별 최대 두 번 built-in edit 후 재정규화한다.

최대 수정 뒤에도 계약이나 육안 착용 품질을 통과하지 못하거나 built-in tool을 사용할 수 없으면 CLI로 우회하지 말고 step을 `blocked`로 기록한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-gloves-shoes-layers-spec.json --check-sources
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-helmet-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-top-bottom-layers-spec.json --check
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_character_equipment_layers.py scripts\test_validate_character_sheet.py --basetemp build\pytest-48-gloves-shoes-art
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check
git diff --check
```

## 검증 절차

1. `view_image`로 base, 손, 신발, 검 grip과 palette reference를 먼저 확인한다.
2. 네 item을 별도 built-in 호출로 생성·정규화하고 AC를 실행한다.
3. 장비별 preview와 matrix를 확인해 소재 구분, 검 grip, 발목 seam, 중심과 잘림 여부를 검사한다.
4. task index의 step 1을 `completed`로 바꾸고 built-in mode, 네 canonical PNG와 preview 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 여러 장비를 한 호출의 batch sheet로 생성하지 마라. 이유: 각 item은 독립 prompt와 결과 검증이 필요하다.
- 별도 shop thumbnail PNG를 만들지 마라. 이유: 동일 runtime layer의 bounds-fit 재사용이 승인 계약이다.
- CLI/API fallback으로 전환하지 마라. 이유: built-in chroma-key 경로가 승인된 생성 방식이다.
- 캐릭터나 다른 slot 픽셀을 최종 layer에 포함하지 마라. 이유: runtime source-over 합성에서 외형이 깨진다.
- 기존 테스트를 깨뜨리지 마라.
