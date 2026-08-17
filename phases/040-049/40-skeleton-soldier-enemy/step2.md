# Step 2: generate-skeleton-soldier-sprite

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/monster/todo-quest-goblin-scout-front-idle.png`
- `/docs/art/monster/todo-quest-skeleton-soldier-front-idle-spec.json`
- `/scripts/validate_monster_sprite.py`
- `/scripts/test_validate_monster_sprite.py`
- `/phases/040-049/40-skeleton-soldier-enemy/index.json`
- `~/.codex/skills/.system/imagegen/SKILL.md`
- `~/.codex/skills/.system/imagegen/references/prompting.md`
- `~/.codex/skills/.system/imagegen/references/sample-prompts.md`

## 작업

기본 제공 built-in `image_gen`으로 새로운 해골 병사 스프라이트 한 명을 생성하고 `/docs/art/monster/todo-quest-skeleton-soldier-front-idle.png`에 프로젝트 canonical 산출물로 저장한다. CLI/API fallback과 `gpt-image-1.5`는 사용하지 않는다.

먼저 `view_image`로 플레이어 base body와 고블린을 검사한다. 두 파일은 스타일·논리 크기·외곽선·명암 비교용 reference이며 edit target이 아니다. built-in 호출은 새로운 이미지 생성으로 실행하고 filesystem reference/edit 인자를 전달하지 않는다. 다음 프롬프트를 사용한다.

```text
Use case: stylized-concept
Asset type: single front-idle hostile enemy sprite for the Todo Quest Android pixel RPG
Primary request: Create exactly one original skeleton soldier, an undead and dark low-threat common enemy for the early game. It has no emotion, obeys orders, moves relentlessly, and fights at close range with one worn short one-handed sword. The design must be cute and simple while unmistakably hostile.
Scene/backdrop: perfectly flat solid #FF00FF chroma-key background for local removal; one uniform color with no shadows, gradients, texture, reflections, floor plane, lighting variation or decoration
Subject: fully front-facing three-head-tall chibi skeleton soldier with a large round skull, empty eye sockets, one or two small skull cracks, thick simplified bones, readable ribs and joints, and tiny red light limited to both eye sockets. It wears a rusty dented iron helmet, worn leather or cloth greaves, and a partially torn short cape or waist cloth. A short simple rusty medieval one-handed sword is gripped beside the body on the viewer's right, vertical or slightly diagonal, without covering the torso. The body is tense and ready to attack but remains in a neutral idle pose.
Style/medium: native hard pixel art matching a simple mobile 16-bit RPG sprite; one 64-by-64 logical pixel grid enlarged with uniform square pixel blocks; crisp one-logical-pixel edges; continuous dark navy external outline; flat two-or-three-step shading; no antialiasing
Composition/framing: exactly one full-body character, centered on x=32, opaque extent x=16..48 and y=13..58 after normalization, both soles on y=58, completely upright and unrotated, at least 3 logical pixels from every canvas edge. Keep the large skull, torso and both feet visually balanced. Use a torn cloth shape on the viewer's left to counterbalance the sword. The opaque height is 46 pixels, about 11.5 percent shorter than the player reference.
Color palette: only #263B5A, #11151C, #737982, #B7B0A3, #F4EFE3, #35445C, #56677A, #A3744C, #4A3225, #765238, #3A3F45, #5B6068, #4B5059, #9A9A94, #E05252. Use #263B5A for the continuous external outline, #E05252 only for 2 to 4 eye-glow pixels, #4B5059 and #9A9A94 only for the sword blade.
Materials/textures: old bone, rusted and dented iron, cracked leather and coarse torn cloth expressed only with clean flat pixel clusters and stepped shades
Constraints: opaque alpha only inside the subject; one 8-connected silhouette including hand, handle and blade; one-pixel continuous outline; two clearly separated feet; readable rib, leg and sword-to-body negative spaces; clear helmet, skull and short sword silhouette at mobile size; no extra pose, monster or sprite-sheet cell; no text
Avoid: player face, player hair, player equipment, copied character design, modern clothing, machinery, guns, electronics, sci-fi parts, excessive horror, gore, blood, flesh, wounds, cast shadow, ground shadow, background props, isolated pixels, jagged random noise, antialiasing, blur, gradients, dithering, semitransparent pixels, borders, labels, letters, numbers, UI, health bars, logos, trademarks, watermarks, #FF00FF inside the character
```

생성 후 다음 절차를 따른다.

1. 선택 결과를 `$CODEX_HOME/generated_images`에서 `/.tmp/imagegen/40-skeleton-soldier-enemy/source.png`로 복사한다.
2. 설치된 `~/.codex/skills/.system/imagegen/scripts/remove_chroma_key.py`를 `.venv/Scripts/python.exe`로 실행한다. hard pixel art이므로 `--auto-key border --tolerance 12`를 사용하고 `--soft-matte`, feather, blur는 사용하지 않는다.
3. Pillow `Image.Resampling.NEAREST`만 사용해 논리 64×64로 축소한다. 불투명 결과를 33×46 영역으로 nearest-neighbor 정규화해 `[16,13,48,58]`에 배치하고 15색 최근접 매핑, 알파 `0/255`, 외부 경계 `outlineDarkNavy` 정규화만 수행한다. dithering을 사용하지 않는다.
4. 정규화는 생성된 캐릭터의 크기·위치·색과 외부 경계만 보정한다. Python/SVG/HTML/canvas로 얼굴, 뼈, 투구, 천 또는 검을 새로 그리거나 누락 요소를 합성하지 않는다.
5. validator와 1배율·nearest-neighbor 8배율 시각 검사를 수행한다. 계약 또는 디자인이 실패하면 한 번에 한 문제만 대상으로 built-in imagegen 편집을 최대 두 번 수행한 뒤 다시 정규화한다.
6. 최종 결과는 한 명, 한 자세, 한 PNG만 유지한다. source와 8배율 검토 이미지는 `/.tmp/imagegen/40-skeleton-soldier-enemy/`에만 두고 커밋하지 않는다.

built-in 도구가 child 세션에 없거나 두 번의 수정 후에도 계약·시각 검사를 통과하지 못하면 CLI로 우회하지 말고 step을 `blocked`로 기록한다. `blocked_reason`에는 사용할 수 없는 도구 또는 실패 검증을 구체적으로 적는다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-skeleton-soldier-front-idle.png --spec docs\art\monster\todo-quest-skeleton-soldier-front-idle-spec.json
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-goblin-scout-front-idle.png --spec docs\art\monster\todo-quest-goblin-scout-front-idle-spec.json
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_monster_sprite.py scripts\test_validate_character_sheet.py scripts\test_execute.py --basetemp build\pytest-40-skeleton-art
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-base-sheet.png --spec docs\art\character\character-base-spec.json
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-modular-sheet.png --spec docs\art\character\character-modular-sheet-spec.json
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. 최종 PNG를 1배율과 8배율로 확인해 정면 3등신, 큰 금 간 두개골, 눈구멍의 제한된 붉은 빛, 낡은 투구·경갑·손상 천·측면 검, 두꺼운 뼈와 분리된 음영 공간을 확인한다.
3. 텍스트, UI, 그림자, 배경 장식, 현대 장비, 잔혹 표현, 추가 몬스터가 없는지 확인한다.
4. PRD, ARCHITECTURE, ADR, UI_GUIDE, AGENTS.md의 CRITICAL 규칙을 확인한다.
5. task index의 step 2를 `completed`로 변경하고 PNG 경로, built-in 사용, 프롬프트 핵심과 검증 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- built-in imagegen 대신 Python, SVG, HTML 또는 canvas로 해골 병사를 새로 그리지 마라. 이유: 최종 캐릭터 원안은 imagegen으로 생성해야 한다.
- reference PNG를 edit target으로 전달하거나 플레이어 외형을 복사하지 마라. 이유: 참조는 스타일과 상대 크기 비교용이다.
- CLI/API fallback 또는 `gpt-image-1.5`로 전환하지 마라. 이유: 사용자가 모델/path downgrade를 승인하지 않았다.
- 스프라이트 시트, 추가 방향·포즈·프레임 또는 다른 몬스터를 만들지 마라. 이유: 이번 canonical 결과물은 정면 대기 PNG 한 장이다.
- 기존 테스트를 깨뜨리지 마라.
