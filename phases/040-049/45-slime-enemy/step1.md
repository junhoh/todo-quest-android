# Step 1: generate-slime-sprite

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
- `/docs/art/monster/todo-quest-harpy-front-idle.png`
- `/docs/art/monster/todo-quest-slime-front-idle-spec.json`
- `/scripts/validate_monster_sprite.py`
- `/scripts/test_validate_monster_sprite.py`
- `/phases/040-049/45-slime-enemy/index.json`
- `~/.codex/skills/.system/imagegen/SKILL.md`
- `~/.codex/skills/.system/imagegen/references/prompting.md`
- `~/.codex/skills/.system/imagegen/references/sample-prompts.md`

## 작업

기본 제공 built-in `image_gen`으로 새로운 슬라임 한 마리를 생성하고 `/docs/art/monster/todo-quest-slime-front-idle.png`에 canonical 프로젝트 산출물로 저장한다. CLI/API fallback과 `gpt-image-1.5`는 사용하지 않는다.

먼저 `view_image`로 플레이어 base body와 기존 몬스터 네 종을 검사한다. 이 파일들은 스타일·논리 크기·외곽선·명암 비교용 reference이며 edit target이 아니다. built-in 호출은 새 이미지 생성으로 실행한다. 다음 프롬프트를 사용한다.

```text
Use case: stylized-concept
Asset type: single front-idle lowest-rank common enemy sprite for the Todo Quest Android pixel RPG
Primary request: Create exactly one original classic fantasy slime. It is a water-and-mucus creature from the game's starting region, the weakest normal enemy, simple and instinct-driven but hostile to the player. It attacks by tensing its body and bouncing forward in a body slam. Keep it cute and simple while making it unmistakably an enemy rather than a friendly mascot. Do not imitate the signature slime shape of any specific existing game.
Scene/backdrop: perfectly flat solid #FF00FF chroma-key background for local removal; one uniform color with no shadows, gradients, texture, reflections, floor plane, lighting variation, puddles or decoration
Subject: fully front-facing opaque blue-teal slime with a rounded droplet or jelly body, a wide stable bottom and a subtly pointed top. The body is low and slightly compressed as if crouching forward with tension. Add two small sharply inward-slanted hostile eyes, tiny restricted red eye accents, a small dark internal mucus core with a tiny orange accent, and only one or two compact opaque bright highlight clusters. If a mouth is shown, make it a very short frown. No arms, legs, equipment, weapons, teeth, detached droplets or separate mucus fragments.
Style/medium: native hard pixel art matching the existing Todo Quest player and four monster sprites; one 64-by-64 logical pixel grid enlarged with uniform square pixel blocks; crisp one-logical-pixel dark navy external outline; flat two-or-three-step opaque shading; exactly 12 opaque colors
Composition/framing: exactly one slime centered on x=32, opaque extent x=17..47 and y=35..58 after normalization, wide body bottom on y=58, completely front-facing and unrotated, at least 3 logical pixels from every canvas edge. Its 24-pixel opaque height is about 46.2 percent of the supplied 52-pixel player reference. Keep left and right visual weight stable and the silhouette immediately readable on a small phone screen.
Color palette: only #263B5A, #11151C, #124A5A, #176778, #238FA0, #3DAEBC, #72CDD2, #C3F0E9, #173041, #2B4B5D, #E05252, #E58A3C. Use #263B5A for the continuous external outline. Restrict #E05252 to two-to-four eye pixels, #E58A3C to the small internal core, and #C3F0E9 to one or two compact highlight clusters.
Materials/textures: opaque watery mucus suggested only through deliberate flat blue-teal shading and compact pixel clusters; prioritize the droplet silhouette, hostile eyes and internal core over surface detail
Constraints: opaque alpha only after background removal; one 8-connected silhouette; one-pixel continuous external outline; no isolated pixels or irregular noisy outline; one character, one pose and one frame only
Avoid: translucency, semitransparent pixels, glass rendering, glow haze, antialiasing, blur, gradients, dithering, detached drops, splashes, puddles, ground shadow, background props, arms, legs, armor, clothing, equipment, weapons, teeth, gore, blood, graphic violence, overly cheerful expression, copied existing character design, borders, labels, letters, numbers, UI, health bars, logos, trademarks, watermarks, #FF00FF inside the character
```

생성 후 다음 절차를 따른다.

1. 선택한 built-in 결과를 `$CODEX_HOME/generated_images`에서 `/.tmp/imagegen/45-slime-enemy/source.png`로 복사한다.
2. 설치된 `~/.codex/skills/.system/imagegen/scripts/remove_chroma_key.py`를 `.venv/Scripts/python.exe`로 실행한다. 청록 캐릭터와 충돌하지 않는 `#FF00FF`, `--auto-key border`와 하드 픽셀 경계를 사용하며 soft matte, edge feather, blur와 반투명 despill 결과를 남기지 않는다.
3. Pillow `Image.Resampling.NEAREST`만 사용해 논리 64×64로 축소한다. 불투명 결과를 31×24 영역으로 nearest-neighbor 정규화해 `[17,35,47,58]`에 배치하고 지정 12색으로 최근접 매핑하며 알파 `0/255`와 외부 경계 `outlineDarkNavy`만 정규화한다.
4. 정규화는 생성된 몸체의 크기·위치·색과 경계를 보정하는 데만 쓴다. 슬라임 실루엣, 눈, 핵, 표면 반사를 Python/SVG/HTML/canvas로 새로 그리거나 기존 캐릭터 픽셀을 합성하지 않는다.
5. validator를 실행하고 1배율과 nearest-neighbor 8배율을 직접 검사한다. 계약 또는 디자인이 실패하면 한 번에 한 문제만 대상으로 built-in imagegen 편집을 최대 두 번 수행한 뒤 다시 정규화한다.
6. 최종 결과는 한 마리, 한 자세, 한 canonical PNG만 유지한다. 생성 중간 파일과 검토 이미지는 `/.tmp/imagegen/45-slime-enemy/`에만 두고 커밋하지 않는다.

built-in `image_gen`이 child 세션에 없거나 최대 두 번의 단일 수정 후에도 투명도·validator·시각 검사를 통과하지 못하면 CLI로 우회하지 말고 step을 `blocked`로 기록한다. `blocked_reason`에는 사용할 수 없는 도구 또는 실패 검증을 구체적으로 적는다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-slime-front-idle.png --spec docs\art\monster\todo-quest-slime-front-idle-spec.json
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_monster_sprite.py scripts\test_validate_character_sheet.py scripts\test_execute.py --basetemp build\pytest-45-slime-art
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-base-sheet.png --spec docs\art\character\character-base-spec.json
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-modular-sheet.png --spec docs\art\character\character-modular-sheet-spec.json
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. 최종 PNG를 1배율과 8배율로 확인해 24px 높이, 넓은 바닥과 뾰족한 윗부분, 청록색 불투명 점액, 적대적 눈, 내부 핵과 1~2개 반사 덩어리를 확인한다.
3. 텍스트, UI, 그림자, 배경, 분리된 물방울, 팔다리, 장비, 이빨, 잔혹 표현, 반투명 픽셀과 추가 몬스터가 없는지 확인한다.
4. 사용자 규격과 AGENTS.md의 CRITICAL 규칙을 확인한다.
5. task index의 step 1을 `completed`로 변경하고 PNG 경로, built-in imagegen 사용, 정규화와 검증 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- built-in imagegen 대신 Python, SVG, HTML 또는 canvas로 슬라임 원안을 새로 그리지 마라. 이유: 최종 캐릭터 원안은 imagegen으로 생성해야 한다.
- 플레이어 또는 기존 몬스터를 edit target으로 전달하거나 픽셀을 복사하지 마라. 이유: 기존 이미지는 스타일과 크기 비교용이다.
- CLI/API fallback 또는 `gpt-image-1.5`로 전환하지 마라. 이유: 사용자가 모델/path downgrade를 승인하지 않았다.
- 최종 PNG에 반투명 픽셀, chroma key 또는 13번째 색을 남기지 마라. 이유: 하드 에지 12색 규격을 위반한다.
- 스프라이트 시트, 추가 포즈, 효과 또는 다른 몬스터를 만들지 마라. 이유: 이번 결과물은 정면 기본 대기 PNG 한 장이다.
- `/.tmp/imagegen/` 중간 파일을 커밋하지 마라. 이유: 프로젝트 최종 산출물과 임시 생성물을 분리해야 한다.
- 기존 테스트를 깨뜨리지 마라.
