# Step 1: generate-fairy-guide-sprite

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/README.md`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/character/previews/default-equipped@8x.png`
- `/docs/art/character/previews/palette@8x.png`
- `/docs/art/npc/todo-quest-blacksmith-shopkeeper-front-idle.png`
- `/docs/art/npc/todo-quest-fairy-guide-front-idle-spec.json`
- `/scripts/validate_monster_sprite.py`
- `/scripts/test_validate_npc_sprite.py`
- `/phases/050-059/56-fairy-character-stat-guide/step0.md`
- `/phases/050-059/56-fairy-character-stat-guide/index.json`
- `~/.codex/skills/.system/imagegen/SKILL.md`
- `~/.codex/skills/.system/imagegen/references/prompting.md`
- `~/.codex/skills/.system/imagegen/references/sample-prompts.md`

## 작업

테스트를 먼저 추가하고 built-in `image_gen`으로 안내 페어리 요정 한 명을 새로 생성해 `/docs/art/npc/todo-quest-fairy-guide-front-idle.png`에 canonical 프로젝트 산출물로 저장한다. CLI/API fallback과 `gpt-image-1.5`는 사용하지 않는다.

먼저 `/scripts/test_validate_npc_sprite.py`에 fairy canonical PNG가 step 0 명세를 통과한다는 테스트를 추가하고 PNG 부재 실패를 확인한다. 기존 대장장이 테스트와 `/scripts/validate_monster_sprite.py`는 유지한다.

`view_image`로 플레이어 base-body, 기본 장착 8배율, 팔레트와 대장장이 NPC를 검사한다. 모두 스타일·logical pixel·외곽선·명암 비교용이며 edit target이 아니다. built-in 호출은 신규 generate로 실행하고 reference path나 최근 이미지를 입력으로 전달하지 않는다. 다음 내용을 빠짐없이 구조화한 프롬프트를 사용한다.

```text
Use case: stylized-concept
Asset type: one front-idle guide fairy NPC sprite for the Todo Quest Android pixel RPG
Primary request: Create exactly one original small humanoid fairy who is a trustworthy guide and companion. This NPC appears when the player needs a concise explanation of important functions, new content or cautions. Bright, kind, curious and focused, but not childish, prankish, combat-ready or casting magic.
Scene/backdrop: perfectly flat solid #FF00FF chroma-key background; one uniform color; no shadow, gradient, texture, floor, reflection, forest, flowers, grass, aura, magic circle, sparkle, stardust, particles, UI or text
Subject: fully front-facing 2.5-to-3-head-tall chibi humanoid fairy with a readable childlike human face but neutral friendly gender presentation. Jaw-length muted light-blonde hair with clear bangs, large bright eyes, soft smile and one tiny gold star hair ornament. One hand points naturally toward viewer-right at chest height; the other hand rests on a small brown waist pouch. Pale green and cream short simple fantasy tunic and soft leather boots. One pair of long rounded leaf-or-dragonfly-inspired wings spreads slightly behind the body without covering the head, face or arms. Wings are opaque pale cyan in two flat shades and each wing contains only one or two compact interior pixel clusters.
Style/medium: native hard-edged classic medieval fantasy RPG pixel art matching Todo Quest; one 64-by-64 logical pixel grid enlarged with uniform square blocks; continuous one-logical-pixel #263B5A external outline; flat two-or-three-step opaque shading; no antialiasing, blur, gradient, dithering or semitransparent pixels
Composition/framing: exactly one full-body fairy centered on x=32, floating slightly, opaque extent x=6..58 and y=22..52, both boot tips on y=52, at least 4 pixels from all canvas edges. Total height including ornament and wings is 31 pixels, about 59.6 percent of the 52-pixel player height. Keep the body balanced while only the guide hand points sideways. Make wings, hair, face, arms, tunic, pouch and both feet distinct at mobile size.
Color palette: only #263B5A, #11151C, #D99872, #FFD3AE, #A9824B, #D7B86D, #B7B0A3, #F4EFE3, #4F6B55, #789B6E, #AFC79A, #4A3225, #765238, #A9D9D4, #D9F1E8, #F2C14E. Use #263B5A for the external outline. Restrict #F2C14E to the single star ornament. Keep bright cyan limited to the wings.
Constraints: one 8-connected opaque silhouette; binary alpha after removal; one character, one pose, one frame; friendly explanation-ready gaze toward the player; clear sideways guide gesture; no isolated pixels or irregular noisy contour
Avoid: copied player face, hair, clothing or equipment; existing game character imitation; mascot baby proportions; crown, gems, frills, armor, weapon, firearm, machinery, electronics, modern elements, attack pose, threat, magic casting, glow, particles, speech bubble, punctuation icon, label, logo, watermark, extra fairy, sprite sheet, animation frame, #FF00FF inside the subject
```

생성 후 다음을 수행한다.

1. 선택한 built-in 결과를 `$CODEX_HOME/generated_images`에서 `/.tmp/imagegen/56-fairy-character-stat-guide/source.png`로 복사한다.
2. 설치된 `remove_chroma_key.py`를 `.venv/Scripts/python.exe`로 실행해 평면 `#FF00FF`를 hard removal한다. soft matte, feather, blur와 반투명 despill을 사용하지 않는다.
3. Pillow `Image.Resampling.NEAREST`만 사용해 논리 64×64로 축소하고 불투명 결과를 `[6,22,58,52]`에 정규화한다. 명세 팔레트 최근접 매핑에는 dithering을 사용하지 않는다. alpha는 `0/255`, 투명 RGB는 0, 외부 경계는 `#263B5A`로 정규화한다.
4. 후처리는 크기·위치·팔레트·binary alpha·외부 경계만 보정한다. 얼굴·날개·손짓·의상·주머니·부츠를 Python/SVG/HTML/canvas로 새로 그리거나 플레이어 픽셀을 합성하지 않는다.
5. validator와 테스트를 실행하고 1배율 및 nearest-neighbor 8배율을 직접 검사한다. 계약 또는 디자인 실패 시 한 번에 한 문제만 대상으로 built-in imagegen edit를 최대 두 번 수행한다.
6. source와 8배율 검토본은 `/.tmp/imagegen/56-fairy-character-stat-guide/`에만 두고 커밋하지 않는다.

built-in imagegen이 child 세션에 없거나 두 번의 수정 후에도 계약·시각 검사를 통과하지 못하면 CLI로 우회하지 말고 step을 `blocked`로 기록한다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\npc\todo-quest-fairy-guide-front-idle.png --spec docs\art\npc\todo-quest-fairy-guide-front-idle-spec.json
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_npc_sprite.py scripts\test_validate_monster_sprite.py scripts\test_validate_character_sheet.py scripts\test_execute.py --basetemp build\pytest-56-fairy-art
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check
git diff --check
```

## 검증 절차

1. canonical 테스트를 먼저 추가해 PNG 부재 실패를 확인한 뒤 생성한다.
2. AC를 실행하고 최종 PNG를 1배율과 8배율로 확인한다.
3. 금발·별 장식·친근한 표정·가리키는 손·주머니·날개·튜닉·부츠가 분리되는지 확인한다.
4. 배경·그림자·입자·텍스트·마법·무기·추가 캐릭터가 없는지 확인한다.
5. phase index의 step 1을 `completed`로 바꾸고 생성·정규화·검증 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- built-in imagegen 대신 코드로 원안을 새로 그리지 마라. 이유: 캐릭터 원안은 imagegen 산출물이어야 한다.
- 플레이어 이미지를 edit target으로 전달하거나 픽셀을 복사하지 마라. 이유: 스타일 참조일 뿐이다.
- CLI/API fallback으로 전환하지 마라. 이유: 사용자가 모델/path downgrade를 승인하지 않았다.
- 반투명 픽셀, chroma key 또는 17번째 색을 남기지 마라. 이유: 최종 규격을 위반한다.
- 여러 캐릭터·방향·표정·프레임을 만들지 마라. 이유: 이번 산출물은 정면 대기 한 명이다.
- 기존 테스트를 깨뜨리지 마라.

