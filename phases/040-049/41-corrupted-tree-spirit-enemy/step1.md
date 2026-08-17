# Step 1: generate-corrupted-tree-spirit-sprite

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/monster/todo-quest-goblin-scout-front-idle.png`
- `/docs/art/monster/todo-quest-skeleton-soldier-front-idle.png`
- `/docs/art/monster/todo-quest-corrupted-tree-spirit-front-idle-spec.json`
- `/scripts/validate_monster_sprite.py`
- `/scripts/test_validate_monster_sprite.py`
- `/phases/040-049/41-corrupted-tree-spirit-enemy/index.json`
- `~/.codex/skills/.system/imagegen/SKILL.md`
- `~/.codex/skills/.system/imagegen/references/prompting.md`
- `~/.codex/skills/.system/imagegen/references/sample-prompts.md`

## 작업

기본 제공 built-in `image_gen`으로 새로운 타락한 나무 정령 한 명을 생성하고 `/docs/art/monster/todo-quest-corrupted-tree-spirit-front-idle.png`에 canonical 프로젝트 산출물로 저장한다. CLI/API fallback과 `gpt-image-1.5`는 사용하지 않는다.

먼저 `view_image`로 플레이어 base body와 두 기존 몬스터를 검사한다. 이 파일들은 스타일·논리 크기·외곽선·명암 비교용 reference이며 edit target이 아니다. built-in 호출은 새 이미지 생성으로 실행하고 filesystem reference/edit 인자를 전달하지 않는다. 다음 프롬프트를 사용한다.

```text
Use case: stylized-concept
Asset type: single front-idle common enemy sprite for the Todo Quest Android pixel RPG
Primary request: Create exactly one original corrupted tree spirit, formerly a gentle forest guardian but now contaminated by darkness and aggressively obstructing the player. It is a low-threat normal enemy that attacks at close range with thick branch arms, short pointed branch claws and sharp roots. It moves slowly but looks stubborn and persistent. Keep it cute, simple and game-like while making the corruption and hostility unmistakable.
Scene/backdrop: perfectly flat solid #FF00FF chroma-key background for local removal; one uniform color with no shadows, gradients, texture, reflections, floor plane, lighting variation, grass, stones or decoration
Subject: fully front-facing three-head-tall chibi tree spirit with a large head flowing naturally into a short thick trunk. Its body is old twisted dark-brown wood with rough simplified bark and a few readable growth rings. Short horn-like branches extend from both sides of the head with only a few attached withered or blackened leaves. Thick branch arms are held slightly away from the torso; hands end in short angular twig claws. Short sturdy trunk legs end in split root feet. Aggressive slanted bark brows frame dim red eyes inside bark gaps. A few torso cracks glow dark purple, with a tiny amount of black sticky corrupted sap near the mouth or bark gaps. Sparse simplified moss is allowed.
Style/medium: native hard pixel art matching the existing Todo Quest player, goblin and skeleton sprites; one 64-by-64 logical pixel grid enlarged with uniform square pixel blocks; crisp one-logical-pixel dark navy external outline; flat two-or-three-step shading; maximum 15 opaque colors
Composition/framing: exactly one full-body character, centered on x=32, opaque extent x=14..50 and y=11..58 after normalization, both root soles on y=58, completely upright and unrotated, at least 3 logical pixels from every canvas edge. Keep head branches, trunk, arms and root feet balanced around the center. The 48-pixel opaque height is about 7.7 percent shorter than the supplied 52-pixel player reference.
Color palette: only #263B5A, #11151C, #2E211C, #4A3225, #765238, #A3744C, #C08A5A, #42543A, #657A4B, #8EA565, #3A3F45, #1C1318, #45294F, #9A4E8A, #E05252. Use #263B5A for the continuous external outline. Limit #E05252 to tiny eye accents and the purple corruption colors to a few torso cracks.
Materials/textures: rough cracked bark, simplified moss, a few withered leaves and sticky black sap expressed with deliberate flat pixel clusters; prioritize a clean mobile-readable silhouette over fine twigs or bark noise
Constraints: opaque alpha only after background removal; one connected silhouette including every leaf and branch; one-pixel continuous outline; clear tree-spirit silhouette at mobile size; no extra pose, monster, sprite-sheet cell, effect or text
Avoid: player face, player hair, player equipment, copied existing character design, armor, clothing, manufactured weapons, machinery, metal devices, guns, electronics, modern or sci-fi parts, gore, wounds, dismemberment, horror realism, smoke, poison fog, particles, cast shadow, ground shadow, background props, isolated pixels, disconnected leaves, thin broken twigs, jagged random noise, antialiasing, blur, gradients, dithering, semitransparent pixels, borders, labels, letters, numbers, UI, health bars, logos, trademarks, watermarks, #FF00FF inside the character
```

생성 후 다음 절차를 따른다.

1. 선택한 built-in 결과를 `$CODEX_HOME/generated_images`에서 `/.tmp/imagegen/41-corrupted-tree-spirit-enemy/source.png`로 복사한다.
2. 설치된 `~/.codex/skills/.system/imagegen/scripts/remove_chroma_key.py`를 `.venv/Scripts/python.exe`로 실행한다. 녹색 계열 캐릭터이므로 `#FF00FF`와 `--auto-key border`의 hard tolerance를 사용하고 `--soft-matte`, edge feather, blur와 despill은 사용하지 않는다.
3. Pillow `Image.Resampling.NEAREST`만 사용해 논리 64×64로 축소한다. 불투명 결과를 37×48 영역으로 nearest-neighbor 정규화해 `[14,11,50,58]`에 배치하고 팔레트 최근접 매핑, 알파 `0/255`, 외부 경계 `outlineDarkNavy` 정규화만 수행한다.
4. 정규화는 기존 불투명 픽셀의 크기·위치·색과 경계색만 보정한다. 캐릭터, 얼굴, 가지, 잎, 팔 또는 뿌리 실루엣을 Python/SVG/HTML/canvas로 새로 그리거나 누락 요소를 합성하지 않는다.
5. validator를 실행하고 1배율과 nearest-neighbor 8배율 보기를 직접 검사한다. 계약 또는 디자인이 실패하면 한 번에 한 문제만 대상으로 built-in imagegen 편집을 최대 두 번 수행한 뒤 다시 정규화한다.
6. 최종 결과는 한 명, 한 자세, 한 canonical PNG만 유지한다. 생성 중간 파일과 검토 이미지는 `/.tmp/imagegen/41-corrupted-tree-spirit-enemy/`에만 두고 커밋하지 않는다.

built-in `image_gen`이 child 세션에 없거나 최대 두 번의 단일 수정 후에도 투명도·validator·시각 검사를 통과하지 못하면 CLI로 우회하지 말고 step을 `blocked`로 기록한다. `blocked_reason`에는 사용할 수 없는 도구 또는 실패한 검증 항목을 구체적으로 적는다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-corrupted-tree-spirit-front-idle.png --spec docs\art\monster\todo-quest-corrupted-tree-spirit-front-idle-spec.json
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_monster_sprite.py scripts\test_validate_character_sheet.py scripts\test_execute.py --basetemp build\pytest-41-tree-spirit-art
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-base-sheet.png --spec docs\art\character\character-base-spec.json
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-modular-sheet.png --spec docs\art\character\character-modular-sheet-spec.json
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. 최종 PNG를 1배율과 8배율로 확인해 정면 3등신, 48px 높이, 양쪽 머리 가지, 짙은 갈색 고목 몸통, 굵은 가지 팔, 갈라진 뿌리발, 적대적 눈, 제한된 보라 균열·수액·시든 잎과 투명 배경을 확인한다.
3. 텍스트, UI, 그림자, 배경 장식, 과도한 잔가지, 인공 장비, 현대 요소, 잔혹 표현, 추가 몬스터가 없는지 확인한다.
4. PRD, ARCHITECTURE, ADR, UI_GUIDE, AGENTS.md의 CRITICAL 규칙을 확인한다.
5. task index의 step 1을 `completed`로 변경하고 PNG 경로, built-in imagegen 사용, 핵심 프롬프트와 검증 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- built-in imagegen 대신 Python, SVG, HTML 또는 canvas로 나무 정령을 새로 그리지 마라. 이유: 최종 캐릭터 원안은 imagegen으로 생성해야 한다.
- 플레이어 또는 기존 몬스터 PNG를 edit target으로 전달하거나 픽셀을 복사하지 마라. 이유: 기존 이미지는 스타일과 크기 비교용이다.
- CLI/API fallback 또는 `gpt-image-1.5`로 전환하지 마라. 이유: 사용자가 모델/path downgrade를 승인하지 않았다.
- 최종 PNG에 반투명 픽셀, 크로마키, 16번째를 넘는 색을 남기지 마라. 이유: 하드 에지 최대 16색 규격을 위반한다.
- 스프라이트 시트, 추가 포즈, 이펙트 또는 다른 몬스터를 만들지 마라. 이유: 이번 결과물은 정면 기본 대기 PNG 한 장이다.
- `/.tmp/imagegen/` 중간 파일을 커밋하지 마라. 이유: 프로젝트 최종 산출물과 임시 생성물을 분리해야 한다.
- 기존 테스트를 깨뜨리지 마라.
