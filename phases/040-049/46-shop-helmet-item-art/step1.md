# Step 1: generate-helmet-layer-assets

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/character/layers/hair_front_default.png`
- `/docs/art/character/layers/hair_back_default.png`
- `/docs/art/character/layers/headgear_adventure.png`
- `/docs/art/character/previews/body-default-hair@8x.png`
- `/docs/art/character/previews/headgear-on@8x.png`
- `/docs/art/equipment/todo-quest-helmet-layers-spec.json`
- `/scripts/validate_character_equipment_layers.py`
- `/phases/040-049/46-shop-helmet-item-art/index.json`
- `~/.codex/skills/.system/imagegen/SKILL.md`
- `~/.codex/skills/.system/imagegen/references/prompting.md`
- `~/.codex/skills/.system/imagegen/references/sample-prompts.md`

## 작업

`imagegen` 스킬을 읽고 기본 제공 built-in `image_gen`으로 가죽 모자와 철 투구를 각각 한 번의 독립 generate 호출로 만든다. 기존 캐릭터·머리·모자는 style/coordinate reference이며 edit target이 아니다. CLI/API fallback과 `gpt-image-1.5`는 사용하지 않는다.

두 호출의 공통 prompt는 다음 계약을 유지하고 `Primary request`, bounds와 재질만 각 item에 맞게 바꾼다.

```text
Use case: stylized-concept
Asset type: isolated wearable headgear layer for the Todo Quest Android pixel RPG and its shop thumbnail
Input images: Image 1: canonical front-facing character proportion and coordinate reference; Image 2: default hair occlusion reference; Image 3: existing isolated headgear style and alignment reference. References are not edit targets and their pixels must not be copied as a new item.
Primary request: Create exactly one original <lightweight brown leather cap / open-face iron helmet> as an isolated headgear-only sprite. It must fit the supplied character without changing the head, face, hair, pose or body.
Scene/backdrop: perfectly flat solid #FF00FF chroma-key background; no shadow, gradient, texture, reflection, floor, border or decoration
Subject: only the headgear, no character body and no second item
Style/medium: native hard-edged pixel art with uniform square logical pixels, one-pixel stepped diagonals, no antialiasing, blur, gradient, dithering or semitransparency
Composition/framing: one 64×64 logical canvas, center x=32, full-canvas origin preserved; opaque inclusive bounds exactly <[19,4,45,22] / [18,4,46,29]> after normalization; keep [23,20,41,28] transparent so eyes, nose, mouth and expression remain visible
Color palette: use only the 16 colors from the supplied Todo Quest character palette; use #263B5A for the continuous exterior outline
Constraints: one 8-connected headgear silhouette; open face; readable at small phone size; same final PNG must work as the character layer and shop artwork
Avoid: full character, face pixels, hair pixels, alternate pose, closed visor, horns, plume, crown, oversized brim, floating parts, isolated pixels, cast shadow, text, letters, numbers, UI, logos, trademarks, watermark, #FF00FF inside the item
```

가죽 모자는 `#D99872`를 leather midtone, `#3A3F45`를 shadow, `#F2C14E`를 제한된 clasp/highlight로 사용해 일반 등급의 단순하고 가벼운 캡으로 읽히게 한다. 철 투구는 `#3A3F45`, `#737982`, `#B7B0A3`, `#F4EFE3`의 단계로 희귀 등급의 강철을 표현하고 좌우 cheek guard는 얼굴 보호 영역 바깥에 둔다.

각 결과를 다음 절차로 정규화한다.

1. 결과를 `$CODEX_HOME/generated_images`에서 `/.tmp/imagegen/46-shop-helmet-item-art/<item>/source.png`로 복사한다.
2. 설치된 `remove_chroma_key.py`를 `.venv\Scripts\python.exe`로 실행해 평면 `#FF00FF`를 제거한다. hard pixel art이므로 최종 alpha는 반드시 `0/255`로 양자화하고 transparent RGB는 0으로 만든다.
3. Pillow `Image.Resampling.NEAREST`만 사용해 지정 bounds에 맞춘다. 지정 16색의 nearest mapping, 외부 경계 `#263B5A` 정규화와 고립된 생성 노이즈 제거만 허용한다.
4. 원안을 Python/SVG/HTML/canvas로 새로 그리지 않는다. 모델이 만든 디자인의 크기·위치·팔레트·외곽선만 결정적으로 정규화한다.
5. canonical 결과를 `/docs/art/equipment/layers/headgear_leather_hat.png`와 `/docs/art/equipment/layers/headgear_iron_helmet.png`에 저장한다.
6. 기존 canonical 캐릭터의 default appearance와 adventure outfit에서 `headgear_adventure`만 제외하고 새 layer를 source-over 합성해 두 item의 1×/8× preview를 만든다. preview는 검증용이며 runtime source가 아니다.
7. spec의 source/preview status, bounds, count, byte count와 hash를 실제 결과로 갱신한다.
8. validator와 1×/8× 직접 검사를 수행한다. 한 번에 한 문제만 대상으로 item별 최대 두 번 built-in edit한 뒤 재정규화한다.

최대 수정 후에도 얼굴 보호, bounds, outline, 연결성 또는 육안 착용 품질이 실패하거나 built-in tool을 사용할 수 없으면 CLI로 우회하지 말고 step을 `blocked`로 기록한다. 임시 파일은 `/.tmp/imagegen/46-shop-helmet-item-art/`에만 둔다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-helmet-layers-spec.json --check-sources
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_character_equipment_layers.py scripts\test_validate_character_sheet.py --basetemp build\pytest-46-helmet-art
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check
git diff --check
```

## 검증 절차

1. 기존 캐릭터와 headgear reference를 `view_image`로 먼저 확인한다.
2. 두 item을 별도 built-in 호출로 생성·정규화하고 AC를 실행한다.
3. canonical PNG와 두 합성 preview를 1×/8×로 확인해 얼굴 노출, 중심, 외곽선, 가죽/강철 식별성과 잘림이 없는지 검사한다.
4. imagegen 최종 prompt, built-in mode, canonical 경로와 검증 결과를 작업 기록에 남긴다.
5. task index의 step 1을 `completed`로 바꾸고 두 PNG와 preview 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 한 호출에서 두 투구를 batch sheet로 생성하지 마라. 이유: 서로 다른 item은 별도 prompt와 결과 검증이 필요하다.
- 별도 shop thumbnail PNG를 만들지 마라. 이유: 사용자가 착용 layer와 동일 원본 재사용을 선택했다.
- CLI/API fallback으로 전환하지 마라. 이유: true native transparency가 필요 없는 단순 opaque pixel layer다.
- 캐릭터 얼굴·머리·몸 픽셀을 최종 layer에 포함하지 마라. 이유: runtime source-over 합성에서 외형이 깨진다.
- 기존 테스트를 깨뜨리지 마라.
