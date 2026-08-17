# Step 2: generate-harpy-sprite

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/monster/todo-quest-goblin-scout-front-idle.png`
- `/docs/art/monster/todo-quest-skeleton-soldier-front-idle.png`
- `/docs/art/monster/todo-quest-corrupted-tree-spirit-front-idle.png`
- `/docs/art/monster/todo-quest-harpy-front-idle-spec.json`
- `/scripts/validate_monster_sprite.py`
- `/scripts/test_validate_monster_sprite.py`
- `/phases/040-049/42-harpy-enemy/index.json`
- `~/.codex/skills/.system/imagegen/SKILL.md`
- `~/.codex/skills/.system/imagegen/references/prompting.md`
- `~/.codex/skills/.system/imagegen/references/sample-prompts.md`

## 작업

기본 제공 built-in `image_gen`으로 새로운 하피 한 명을 생성하고 `/docs/art/monster/todo-quest-harpy-front-idle.png`에 canonical 프로젝트 산출물로 저장한다. CLI/API fallback과 `gpt-image-1.5`는 사용하지 않는다.

먼저 `view_image`로 플레이어 base body와 기존 몬스터 세 종을 검사한다. 이 파일들은 스타일·논리 크기·외곽선·명암 비교용 reference이며 edit target이 아니다. built-in 호출은 새 이미지 생성으로 실행하고 filesystem reference/edit 인자를 전달하지 않는다. 다음 프롬프트를 사용한다.

```text
Use case: stylized-concept
Asset type: single front-idle common enemy sprite for the Todo Quest Android pixel RPG
Primary request: Create exactly one original fantasy harpy. It is a low-threat hostile enemy found in grasslands and mountains, associated visually with wind and flight, fierce, wary and fast. It fights at close range using short sharp talons and its wings. Keep it cute, simple and game-like while making its hostility unmistakable.
Scene/backdrop: perfectly flat solid #FF00FF chroma-key background for local removal; one uniform color with no shadows, gradients, texture, reflections, floor plane, lighting variation, wind effects, grass, stones or decoration
Subject: fully front-facing three-head-tall chibi harpy standing on two feet, not flying. Give it a large human-like head and short humanoid torso, messy dark-teal feather hair, large simple bird wings replacing both arms, short sturdy bird legs and visible but not grotesquely long talons. Both wings are held slightly away from the torso in an exactly mirrored alert stance and do not cover the body. Add a worn short cloth top and waist cloth. Use sharp eyes, downward aggressive eyebrows, a few angular feather tips and tiny red eye accents. Keep feather count restrained.
Style/medium: native hard pixel art matching the existing Todo Quest player and three monster sprites; one 64-by-64 logical pixel grid enlarged with uniform square pixel blocks; crisp one-logical-pixel dark navy external outline; flat two-or-three-step shading; maximum 15 opaque colors
Composition/framing: exactly one full-body character, centered on x=32, opaque extent x=11..53 and y=11..58 after normalization, both soles on y=58, completely upright and unrotated, at least 3 logical pixels from every canvas edge. The mirrored wing regions are x=11..24 and x=40..53 at y=26..47. The 48-pixel opaque height is about 7.7 percent shorter than the supplied 52-pixel player reference.
Color palette: only #263B5A, #11151C, #203B46, #2E5960, #3F7680, #6A9AA0, #A66B52, #D69870, #F0C09C, #4A3225, #765238, #A3744C, #737982, #B7B0A3, #E05252. Use #263B5A for the continuous external outline. Limit #E05252 to two-to-four eye pixels.
Materials/textures: simplified dark-teal feathers, worn brown cloth and compact bird talons expressed with deliberate flat pixel clusters; prioritize a clean mobile-readable wing and talon silhouette over feather detail
Constraints: opaque alpha only after background removal; one 8-connected silhouette; exact RGBA mirror symmetry for both wings; one-pixel continuous outline; clear harpy silhouette at mobile size; one character, one pose and one frame only
Avoid: player face, player hair, player equipment, copied existing character design, separate weapon, machinery, metal devices, guns, electronics, modern or sci-fi parts, gore, wounds, horror realism, flight pose, wind particles, cast shadow, ground shadow, background props, isolated pixels, disconnected feathers, excessive feather detail, jagged random noise, antialiasing, blur, gradients, dithering, semitransparent pixels, borders, labels, letters, numbers, UI, health bars, logos, trademarks, watermarks, #FF00FF inside the character
```

생성 후 다음 절차를 따른다.

1. 선택한 built-in 결과를 `$CODEX_HOME/generated_images`에서 `/.tmp/imagegen/42-harpy-enemy/source.png`로 복사한다.
2. 설치된 `~/.codex/skills/.system/imagegen/scripts/remove_chroma_key.py`를 `.venv/Scripts/python.exe`로 실행한다. 청록 캐릭터이므로 `#FF00FF`와 `--auto-key border`의 hard tolerance를 사용하고 soft matte, edge feather, blur와 despill은 사용하지 않는다.
3. Pillow `Image.Resampling.NEAREST`만 사용해 논리 64×64로 축소한다. 불투명 결과를 43×48 영역으로 nearest-neighbor 정규화해 `[11,11,53,58]`에 배치하고 팔레트 최근접 매핑, 알파 `0/255`, 외부 경계 `outlineDarkNavy` 정규화만 수행한다.
4. 날개는 더 깨끗한 한쪽 imagegen 결과를 중심축 기준으로 복제해 exact RGBA mirror contract를 맞출 수 있다. 얼굴·몸통·다리·의복을 Python/SVG/HTML/canvas로 새로 그리거나 기존 캐릭터 픽셀을 복사하지 않는다.
5. validator를 실행하고 1배율과 nearest-neighbor 8배율 보기를 직접 검사한다. 디자인이 실패하면 한 번에 한 문제만 대상으로 built-in imagegen 편집을 최대 두 번 수행한 뒤 다시 정규화한다.
6. 최종 결과는 한 명, 한 자세, 한 canonical PNG만 유지한다. 중간 파일과 검토 이미지는 `/.tmp/imagegen/42-harpy-enemy/`에만 두고 커밋하지 않는다.

built-in `image_gen`이 child 세션에 없거나 최대 두 번의 수정 후에도 투명도·validator·시각 검사를 통과하지 못하면 CLI로 우회하지 말고 step을 `blocked`로 기록한다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-harpy-front-idle.png --spec docs\art\monster\todo-quest-harpy-front-idle-spec.json
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_monster_sprite.py scripts\test_validate_character_sheet.py scripts\test_execute.py --basetemp build\pytest-42-harpy-art
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-base-sheet.png --spec docs\art\character\character-base-spec.json
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-modular-sheet.png --spec docs\art\character\character-modular-sheet-spec.json
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. 1배율과 8배율에서 정면 3등신, 48px 높이, 짙은 청록 깃털, 대칭 날개, 인간형 머리·몸통, 짧은 새 다리·발톱, 낡은 천과 적대적 눈을 확인한다.
3. 텍스트, UI, 그림자, 배경, 효과, 과도한 깃털, 무기, 현대 요소와 추가 캐릭터가 없는지 확인한다.
4. task index의 step 2를 `completed`로 변경하고 PNG 경로, built-in imagegen 사용과 검증 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- built-in imagegen 대신 Python, SVG, HTML 또는 canvas로 하피 원안을 새로 그리지 마라. 이유: 최종 캐릭터 원안은 imagegen으로 생성해야 한다.
- 플레이어 또는 기존 몬스터를 edit target으로 전달하거나 픽셀을 복사하지 마라. 이유: 기존 이미지는 스타일과 크기 비교용이다.
- CLI/API fallback 또는 `gpt-image-1.5`로 전환하지 마라. 이유: 사용자가 모델/path downgrade를 승인하지 않았다.
- 최종 PNG에 반투명 픽셀, chroma key 또는 16번째를 넘는 색을 남기지 마라. 이유: 픽셀아트 계약을 위반한다.
- 스프라이트 시트, 추가 포즈, 이펙트 또는 다른 몬스터를 만들지 마라. 이유: 이번 결과물은 정면 기본 대기 PNG 한 장이다.
- 기존 테스트를 깨뜨리지 마라.
