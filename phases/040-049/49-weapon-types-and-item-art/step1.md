# Step 1: generate-weapon-layer-assets

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/character/layers/hands_front.png`
- `/docs/art/character/layers/weapon_back_default_sword.png`
- `/docs/art/character/layers/weapon_held_default_sword.png`
- `/docs/art/character/layers/weapon_front_default_sword.png`
- `/docs/art/character/previews/runtime-equipped-reference@8x.png`
- `/docs/art/character/previews/palette@8x.png`
- `/docs/art/equipment/todo-quest-weapon-layers-spec.json`
- `/scripts/validate_character_equipment_layers.py`
- `/phases/040-049/49-weapon-types-and-item-art/index.json`
- `~/.codex/skills/.system/imagegen/SKILL.md`
- `~/.codex/skills/.system/imagegen/references/prompting.md`
- `~/.codex/skills/.system/imagegen/references/sample-prompts.md`

## 작업

`imagegen` 스킬 전체와 두 reference를 읽고 built-in `image_gen`으로 네 무기를 각각 한 번의 독립 generate 호출로 만든다. CLI/API fallback과 `gpt-image-1.5`는 사용하지 않는다. 기존 캐릭터·손·기본 검·palette 이미지는 좌표와 style reference이며 edit target이 아니다.

각 호출은 다음 골격을 유지하고 subject만 바꾼다.

```text
Use case: stylized-concept
Asset type: isolated equippable weapon layer for the Todo Quest Android pixel RPG; the same PNG is the shop thumbnail source
Input images: canonical front-facing character, existing sword layer pieces, hand/grip reference, and production palette. References are coordinate/style references, not edit targets.
Primary request: Create exactly one original <worn longsword / iron longsword / ash-wood spear / steel mace> as an isolated weapon-only sprite that fits the supplied character at primary grip anchor (42,42).
Scene/backdrop: perfectly flat solid #FF00FF chroma-key background; no shadow, gradient, texture, reflection, floor, border or decoration
Subject: only the requested complete weapon, no character, hand, clothing, UI or second item
Style/medium: hard-edged native pixel art with uniform logical pixels, one-pixel stepped details, no antialiasing, blur, gradient, dithering or semitransparency
Composition/framing: one 64×64 logical canvas with full origin preserved; opaque content inside [40,4,58,58], connected to the grip, and outside the protected face region
Color palette: only the supplied 16 Todo Quest production colors; continuous exterior outline #263B5A
Constraints: readable as a phone thumbnail; the same final PNG must work unmodified in full-origin character composition; all weapon pixels render above every character layer
Avoid: character pixels, face overlap, floating pieces, isolated noise, cast shadow, text, UI, logos, watermark, #FF00FF inside the weapon
```

디자인은 다음으로 고정한다.

- `weapon_worn_sword`: 닳고 살짝 이가 빠진 회색 장검 날, 어두운 가죽 손잡이, 최소한의 금색 guard. 일반 등급답게 장식은 절제한다.
- `weapon_iron_longsword`: 더 길고 정제된 밝은 철날, 짙은 남색 손잡이, 금색 guard와 청색 highlight. 낡은 검과 silhouette가 명확히 달라야 한다.
- `weapon_ash_spear`: 물푸레나무색 장대, 철제 창날, grip의 가죽 감개. 긴 silhouette가 얼굴 쪽이 아니라 캐릭터 우측 바깥으로 기울어진다.
- `weapon_steel_mace`: 묵직한 강철 타격 머리, 짙은 손잡이와 금속 pommel, 제한된 밝은 highlight. 창이나 검처럼 날이 있는 silhouette를 만들지 않는다.

각 결과는 다음 절차로 정규화한다.

1. `view_image`로 모든 reference를 먼저 확인한다.
2. built-in 결과를 `$CODEX_HOME/generated_images`에서 `/.tmp/imagegen/49-weapon-types-and-item-art/<key>/source.png`로 복사한다.
3. 설치된 imagegen `remove_chroma_key.py`로 `#FF00FF`를 제거한다.
4. Pillow nearest-neighbor만 사용해 64×64 full origin, 이진 alpha, transparent RGB 0, 16색 nearest mapping, outline, grip/face/envelope 계약으로 정규화한다. Python/SVG/HTML/canvas로 무기 디자인을 새로 그리지 않는다.
5. canonical 결과를 `/docs/art/equipment/layers/`의 네 계약 경로에 저장한다.
6. 현재 adventure appearance에서 gameplay weapon만 교체해 장비별 1×/8× preview와 2×2 matrix를 만든다. 무기 source는 preview에서도 모든 다른 layer 뒤에 합성한다.
7. spec의 source/preview status, bounds, count, byte count와 file/raw-RGBA/alpha-mask hash를 실제 결과로 갱신한다.
8. validator와 preview를 확인하고 한 번에 한 문제만 대상으로 item별 최대 두 번 built-in edit 후 재정규화한다.

최대 수정 뒤에도 계약이나 육안 착용 품질을 통과하지 못하거나 built-in tool을 사용할 수 없으면 CLI로 우회하지 말고 step을 `blocked`로 기록한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-weapon-layers-spec.json --check-sources
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_character_equipment_layers.py scripts\test_validate_character_sheet.py --basetemp build\pytest-49-weapon-art
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check
git diff --check
```

## 검증 절차

1. 네 item을 별도 built-in 호출로 생성·정규화하고 AC를 실행한다.
2. 장비별 preview와 matrix를 직접 확인해 종류·재질·grip·얼굴 비침범·잘림 여부를 검사한다.
3. 동일 PNG가 상점 bounds-fit과 full-origin 착용 양쪽에 적합한지 확인한다.
4. task index의 step 1을 `completed`로 바꾸고 built-in mode, 네 canonical PNG와 preview 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 여러 무기를 한 호출의 batch sheet로 생성하지 마라. 이유: item별 독립 prompt와 결과 검증이 필요하다.
- 별도 shop thumbnail PNG를 만들지 마라. 이유: 동일 runtime layer를 재사용해야 한다.
- CLI/API fallback으로 전환하지 마라. 이유: 승인된 방식은 built-in chroma-key 경로다.
- 캐릭터나 손 픽셀을 최종 layer에 포함하지 마라. 이유: runtime 합성에서 다른 장비를 깨뜨린다.
- 기존 테스트를 깨뜨리지 마라.
