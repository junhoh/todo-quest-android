# Step 2: generate-goblin-scout-sprite

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/monster/todo-quest-goblin-scout-front-idle-spec.json`
- `/scripts/validate_monster_sprite.py`
- `/scripts/test_validate_monster_sprite.py`
- `/phases/020-029/22-goblin-scout-sprite/index.json`
- `~/.codex/skills/.system/imagegen/SKILL.md`
- `~/.codex/skills/.system/imagegen/references/prompting.md`
- `~/.codex/skills/.system/imagegen/references/sample-prompts.md`

## 작업

기본 제공 built-in `image_gen`으로 새로운 고블린 정찰병 스프라이트 한 명을 생성하고 `/docs/art/monster/todo-quest-goblin-scout-front-idle.png`에 프로젝트 산출물로 저장한다. CLI/API fallback과 `gpt-image-1.5`는 사용하지 않는다.

먼저 `view_image`로 `/docs/art/character/todo-quest-character-base-body.png`를 검사한다. 이 파일은 스타일·논리 크기·외곽선 비교용이며 편집 대상이 아니다. built-in 호출은 새 이미지 생성으로 실행하고 reference/edit 인자를 전달하지 않는다. 다음 프롬프트를 기반으로 생성한다.

```text
Use case: stylized-concept
Asset type: single front-idle enemy sprite for the Todo Quest Android pixel RPG
Primary request: Create exactly one original goblin scout, a low-threat common hostile enemy for the early game. It has forest and poison attributes, a cunning agile personality, and fights at close range with one short simple dagger. It must look cute and game-like while remaining unmistakably hostile.
Scene/backdrop: perfectly flat solid #FF00FF chroma-key background for local removal; one uniform color with no shadows, gradients, texture, reflections, floor plane, lighting variation or decoration
Subject: fully front-facing three-head-tall chibi goblin with a large head, gray-green skin, pointed ears, sharp red eyes, slightly frowning mouth, aggressive eyebrows and an alert neutral combat stance. It wears worn natural-leather armor and a small cloth pouch. A short poisoned medieval dagger hangs beside the body on the viewer's right without covering the torso; the pouch balances the silhouette on the viewer's left.
Style/medium: native hard pixel art matching a simple mobile 16-bit RPG sprite; a single 64-by-64 logical pixel grid enlarged with uniform square pixel blocks; crisp one-logical-pixel edges; dark navy external outline; flat stepped shading
Composition/framing: exactly one full-body character, centered on x=32, opaque extent x=16..48 and y=13..58 after normalization, soles on y=58, completely upright and unrotated, at least 3 logical pixels from every canvas edge. Pointed ears create the wide upper silhouette; the body core remains 10–15 percent smaller and slimmer than the supplied player reference.
Color palette: only #263B5A, #11151C, #3A3F45, #737982, #35445C, #B7B0A3, #F4EFE3, #42543A, #657A4B, #8EA565, #4A3225, #765238, #A3744C, #5CC8A7, #E05252. Use #263B5A for the continuous external outline. Use #E05252 only for tiny eye accents and #5CC8A7 only as a tiny poison accent on the dagger.
Materials/textures: worn leather, coarse cloth and simple dull medieval metal expressed with flat pixel clusters and two or three stepped shades
Constraints: opaque alpha only; one connected silhouette including the hand, dagger handle and blade; one-pixel continuous outline; clear pointed ears and dagger silhouette at mobile size; weapon stays beside the body; no extra pose, monster or sprite-sheet cell; no text
Avoid: player face, player hair, player equipment, copied character design, modern clothing, machines, guns, electronics, sci-fi parts, gore, wounds, horror, cast shadow, ground shadow, background props, isolated pixels, jagged random noise, antialiasing, blur, gradients, dithering, semitransparent pixels, borders, labels, letters, numbers, UI, health bars, logos, trademarks, watermarks, #FF00FF inside the character
```

생성 후 다음 절차를 따른다.

1. 선택한 built-in 결과를 `$CODEX_HOME/generated_images`에서 `/.tmp/imagegen/22-goblin-scout-sprite/source.png`로 복사한다.
2. 설치된 `~/.codex/skills/.system/imagegen/scripts/remove_chroma_key.py`를 `.venv/Scripts/python.exe`로 실행한다. 픽셀아트이므로 `--auto-key border`와 hard tolerance를 사용하고 `--soft-matte`, edge feather는 사용하지 않는다.
3. Pillow `Image.Resampling.NEAREST`만 사용해 논리 64×64로 축소한다. 불투명 결과를 33×46 영역으로 nearest-neighbor 정규화해 `[16,13,48,58]`에 배치하고 팔레트 최근접 매핑, 알파 `0/255`, 외부 경계의 `outlineDarkNavy` 정규화만 수행한다. dithering, blur, gradient, feather를 사용하지 않는다.
4. 정규화는 기존 불투명 픽셀의 크기·위치·색과 경계색만 보정한다. 캐릭터, 얼굴, 귀, 장비나 단검 실루엣을 Python/SVG/HTML/canvas로 새로 그리거나 누락 요소를 합성하지 않는다.
5. validator를 실행하고 원본 1배율과 nearest-neighbor 8배율 보기를 직접 검사한다. 계약 또는 디자인이 실패하면 한 번에 한 문제만 대상으로 built-in imagegen 편집을 최대 두 번 수행한 뒤 다시 정규화한다.
6. 최종 결과는 한 명, 한 자세, 한 PNG만 유지한다. 생성 중간 파일과 8배율 검토 이미지는 `/.tmp/imagegen/22-goblin-scout-sprite/`에만 두고 커밋하지 않는다.

built-in `image_gen`이 child 세션에 없거나 최대 두 번의 단일 수정 후에도 투명도·validator·시각 검사를 통과하지 못하면 CLI로 우회하지 말고 step을 `blocked`로 기록한다. `blocked_reason`에는 사용할 수 없는 도구 또는 실패한 검증 항목을 구체적으로 적는다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-goblin-scout-front-idle.png --spec docs\art\monster\todo-quest-goblin-scout-front-idle-spec.json
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_monster_sprite.py scripts\test_validate_character_sheet.py scripts\test_execute.py --basetemp .\.venv\pytest-22-goblin-scout
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-base-sheet.png --spec docs\art\character\character-base-spec.json
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-modular-sheet.png --spec docs\art\character\character-modular-sheet-spec.json
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. 최종 PNG를 1배율과 8배율로 확인해 정면 3등신, 작은 체형, 회녹색 피부, 귀, 적대적 표정, 낡은 가죽 갑옷, 천 가방, 측면 독 단검, 투명 배경과 모바일 실루엣 가독성을 확인한다.
3. 텍스트, UI, 그림자, 배경 장식, 현대 장비, 잔혹 표현, 추가 몬스터가 없는지 확인한다.
4. PRD, ARCHITECTURE, ADR, UI_GUIDE, AGENTS.md의 CRITICAL 규칙을 확인한다.
5. `/phases/020-029/22-goblin-scout-sprite/index.json`의 step 2를 `completed`로 변경하고 최종 PNG 경로, built-in imagegen 사용, 최종 프롬프트 핵심과 검증 결과를 한국어 `summary` 한 줄로 기록한다.
6. 모든 step이 완료되면 `/phases/index.json`의 `22-goblin-scout-sprite` 상태를 `completed`로 변경한다.

## 금지사항

- built-in imagegen 대신 Python, SVG, HTML 또는 canvas로 고블린을 새로 그리지 마라. 이유: 최종 캐릭터 원안은 imagegen으로 생성해야 한다.
- 플레이어 PNG를 edit target으로 전달하거나 얼굴·장비를 복사하지 마라. 이유: 참조 이미지는 스타일과 크기 비교용이다.
- CLI/API fallback 또는 `gpt-image-1.5`로 전환하지 마라. 이유: 사용자가 모델/path downgrade를 승인하지 않았다.
- 최종 PNG에 반투명 픽셀, 크로마키, 16번째를 넘는 색을 남기지 마라. 이유: 하드 에지 최대 16색 규격을 위반한다.
- 스프라이트 시트, 추가 포즈, 다른 몬스터 또는 앱 통합 코드를 만들지 마라. 이유: 이번 결과물은 정면 기본 대기 PNG 한 장이다.
- `/.tmp/imagegen/` 중간 파일을 커밋하지 마라. 이유: 프로젝트 최종 산출물과 생성 임시 파일을 분리해야 한다.
- 기존 테스트를 깨뜨리지 마라.
