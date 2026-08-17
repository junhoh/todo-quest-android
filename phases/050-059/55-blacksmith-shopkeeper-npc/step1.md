# Step 1: generate-blacksmith-shopkeeper-sprite

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/README.md`
- `/docs/art/character/README.md`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/character/previews/default-equipped@8x.png`
- `/docs/art/character/previews/palette@8x.png`
- `/docs/art/npc/todo-quest-blacksmith-shopkeeper-front-idle-spec.json`
- `/scripts/validate_monster_sprite.py`
- `/scripts/test_validate_monster_sprite.py`
- `/phases/050-059/55-blacksmith-shopkeeper-npc/step0.md`
- `/phases/050-059/55-blacksmith-shopkeeper-npc/index.json`
- `~/.codex/skills/.system/imagegen/SKILL.md`
- `~/.codex/skills/.system/imagegen/references/prompting.md`
- `~/.codex/skills/.system/imagegen/references/sample-prompts.md`

## 작업

테스트를 먼저 추가하고 built-in `image_gen`으로 대장장이 NPC 한 명을 새로 생성해 `/docs/art/npc/todo-quest-blacksmith-shopkeeper-front-idle.png`에 canonical 프로젝트 산출물로 저장한다. CLI/API fallback과 `gpt-image-1.5`는 사용하지 않는다.

먼저 `/scripts/test_validate_npc_sprite.py`를 작성한다. 기존 `/scripts/validate_monster_sprite.py`의 spec-driven `validate_sprite()`를 사용해 canonical PNG가 step 0 명세를 통과한다는 테스트를 추가하고 PNG 생성 전 실패를 확인한다. validator 구현과 기존 몬스터 테스트는 수정하지 않는다.

`view_image`로 플레이어 base-body, 기본 장착 8배율, 팔레트 preview를 검사한다. 이 파일들은 스타일·논리 픽셀·공통 외곽선·명암 단계 비교용이며 edit target이 아니다. built-in 호출은 신규 generate로 실행하고 `referenced_image_paths`와 최근 이미지 입력을 전달하지 않는다. 아래 프롬프트를 그대로 기반으로 사용한다.

```text
Use case: stylized-concept
Asset type: single front-idle friendly shopkeeper NPC sprite for the Todo Quest Android pixel RPG
Primary request: Create exactly one original middle-aged dwarf blacksmith who owns a village equipment shop. He crafts, repairs and sells weapons and armor. He looks stern at first glance but responsible, kind and quietly confident. This is a calm greeting idle pose for a player entering the shop, not a combat character.
Scene/backdrop: perfectly flat solid #FF00FF chroma-key background for local removal; one uniform color with no shadows, gradients, texture, reflections, floor plane, lighting variation, forge light, fire, smoke, sparks, particles, walls, floor, anvil, furnace, shelves, merchandise or decoration
Subject: fully front-facing three-head-tall chibi dwarf with a large head, short torso, broad shoulders, thick arms and sturdy short legs. Neatly groomed dark auburn hair and a full beard reaching the upper chest, tied at the end with one small brass ring. Thick eyebrows, round nose, soft eyes and a very faint friendly smile. A few restrained soot marks on the face and forearms. Rolled-up dark work clothes under a thick worn brown leather apron, a minimal tool belt with tongs and one small hammer, thick leather gloves and work boots with metal toe caps.
Pose: upright and unrotated, both soles level, one hand on the viewer-left hip, the other hand holding one short heavy blacksmith hammer down beside the body on the viewer-right. The hammer is a practical crafting tool, not a weapon; it must not cover the face, beard or torso. No sword and no attack posture.
Style/medium: native hard-edged pixel art matching a clean mobile classic fantasy RPG; one 64-by-64 logical pixel grid enlarged with uniform square pixel blocks; crisp one-logical-pixel dark navy external outline; flat two-or-three-step opaque shading; simple connected pixel clusters; no isolated detail pixels
Composition/framing: exactly one full-body NPC centered on x=32, normalized opaque extent x=14..50 and y=13..58, both soles on y=58, at least 3 logical pixels from every canvas edge. The final opaque height is 46 pixels, about 11.5 percent shorter than the 52-pixel player, while the shoulder and torso core are visibly wider. Keep hair, beard, apron, gloves, boots, tool belt and hammer fully inside the canvas and visually separated at mobile size.
Color palette: only #263B5A, #11151C, #3A3F45, #737982, #35445C, #B7B0A3, #F4EFE3, #D99872, #FFD3AE, #4A2A26, #7C3F32, #B25F43, #4A3225, #765238, #A3744C, #F2C14E. Use #263B5A for the continuous external outline. Restrict #F2C14E to the single beard ring. Use dark auburn shades with restrained warm highlights; keep red, orange and brass accents limited.
Materials/textures: worn leather apron and gloves, coarse dark work cloth, dull forged iron hammer and boot toes, tidy dense hair and beard. Express wear and soot only through a few compact flat pixel clusters, not noise.
Constraints: final subject is one 8-connected silhouette; binary opaque alpha after background removal; one-pixel continuous outline; both feet distinct; clear friendly shopkeeper expression; readable beard, apron and downward work hammer at small phone size; exactly one character, one pose and one frame
Avoid: player face, player hair, player equipment, copied existing game character, royal or ornate blacksmith, warrior armor, sword, combat weapon, attack pose, machinery, guns, electronics, sci-fi parts, modern workwear, gore, fire, smoke, glow, particles, cast shadow, ground shadow, background props, extra tools floating away from the body, isolated pixels, jagged random noise, antialiasing, blur, gradients, dithering, semitransparent pixels, borders, labels, letters, numbers, UI, logos, trademarks, watermarks, #FF00FF inside the character
```

생성 후 다음 절차를 따른다.

1. 선택한 built-in 결과를 `$CODEX_HOME/generated_images`에서 `/.tmp/imagegen/55-blacksmith-shopkeeper-npc/source.png`로 복사한다.
2. 설치된 `~/.codex/skills/.system/imagegen/scripts/remove_chroma_key.py`를 `.venv/Scripts/python.exe`로 실행해 평면 `#FF00FF`를 제거한다. 픽셀아트이므로 `--auto-key border`의 hard removal을 사용하고 soft matte, edge feather, blur와 반투명 despill 결과를 남기지 않는다.
3. Pillow `Image.Resampling.NEAREST`만 사용해 논리 64×64로 축소한다. 불투명 결과를 37×46 영역으로 nearest-neighbor 정규화해 `[14,13,50,58]`에 배치하고, 명세 팔레트 최근접 매핑에는 dithering을 사용하지 않는다. 최종 alpha는 `0/255`, 투명 픽셀 RGB는 `0`, 외부 경계는 `#263B5A`로 정규화한다.
4. 정규화는 생성 결과의 크기·위치·팔레트·binary alpha·외부 경계만 보정한다. 얼굴, 수염, 앞치마, 팔, 망치나 누락 장비를 Python/SVG/HTML/canvas로 새로 그리거나 기존 캐릭터 픽셀을 합성하지 않는다.
5. validator와 테스트를 실행하고 1배율과 nearest-neighbor 8배율을 직접 검사한다. 계약 또는 디자인 실패 시 한 번에 한 문제만 대상으로 built-in imagegen edit를 최대 두 번 수행한 뒤 다시 정규화한다.
6. `/docs/art/npc/README.md`에 canonical PNG·JSON 계약, 플레이어 reference의 비편집 역할, runtime 사본은 다음 step에서 추가된다는 경계를 기록하고 `/docs/README.md`의 아트 인덱스에 NPC 아트를 연결한다.
7. 최종 결과는 한 명, 한 자세, 한 canonical PNG만 유지한다. source와 검토용 8배율 이미지는 `/.tmp/imagegen/55-blacksmith-shopkeeper-npc/`에만 두고 커밋하지 않는다.

built-in `image_gen`이 child 세션에 없거나 최대 두 번의 단일 수정 후에도 투명도·validator·시각 검사를 통과하지 못하면 CLI로 우회하지 말고 step을 `blocked`로 기록한다. `blocked_reason`에는 사용할 수 없는 도구 또는 실패 검증을 구체적으로 적는다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\npc\todo-quest-blacksmith-shopkeeper-front-idle.png --spec docs\art\npc\todo-quest-blacksmith-shopkeeper-front-idle-spec.json
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_npc_sprite.py scripts\test_validate_monster_sprite.py scripts\test_validate_character_sheet.py scripts\test_execute.py --basetemp build\pytest-55-blacksmith-art
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-base-sheet.png --spec docs\art\character\character-base-spec.json
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-modular-sheet.png --spec docs\art\character\character-modular-sheet-spec.json
git diff --check
```

## 검증 절차

1. canonical 계약 테스트를 먼저 작성해 PNG 부재로 실패하는 것을 확인한 뒤 생성한다.
2. AC를 실행하고 PNG를 1배율과 8배율로 확인한다.
3. 수염·금속 고리·앞치마·공구 벨트·작업용 망치·부드러운 표정과 넓은 체형이 구분되는지 확인한다.
4. 배경·그림자·불꽃·연기·모루·판매품·전투 자세·추가 인물·현대 장비가 없는지 확인한다.
5. AGENTS.md와 관련 문서의 CRITICAL 규칙을 확인한다.
6. phase index의 step 1을 `completed`로 변경하고 canonical PNG, built-in imagegen, 정규화와 검증 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- built-in imagegen 대신 Python, SVG, HTML 또는 canvas로 NPC 원안을 새로 그리지 마라. 이유: 최종 캐릭터 원안은 imagegen으로 생성해야 한다.
- 플레이어 PNG를 edit target으로 전달하거나 얼굴·머리·장비 픽셀을 복사하지 마라. 이유: 플레이어는 스타일과 크기 참조일 뿐이다.
- CLI/API fallback 또는 `gpt-image-1.5`로 전환하지 마라. 이유: 사용자가 모델/path downgrade를 승인하지 않았다.
- 최종 PNG에 반투명 픽셀, chroma key 또는 17번째 색을 남기지 마라. 이유: 하드 에지 최대 16색 규격을 위반한다.
- sprite sheet, 추가 인물·포즈·표정·방향 또는 배경을 만들지 마라. 이유: 이번 산출물은 정면 기본 대기 NPC 한 명이다.
- `/.tmp/imagegen/` 중간 파일을 커밋하지 마라. 이유: 프로젝트 산출물과 임시 생성물을 분리해야 한다.
- 기존 테스트를 깨뜨리지 마라.
