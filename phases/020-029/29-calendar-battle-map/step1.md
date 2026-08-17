# Step 1: Battle Map 아트 자산 준비

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/README.md`
- `/docs/art/character/todo-quest-character-modular-sheet.png`
- `/docs/art/monster/README.md`
- `/docs/art/monster/todo-quest-goblin-scout-front-idle.png`
- `/docs/art/monster/todo-quest-goblin-scout-front-idle-spec.json`
- `/scripts/validate_monster_sprite.py`
- `/phases/020-029/29-calendar-battle-map/index.json`
- `~/.codex/skills/.system/imagegen/SKILL.md`
- `~/.codex/skills/.system/imagegen/references/prompting.md`
- `~/.codex/skills/.system/imagegen/references/sample-prompts.md`

## 작업

검증 계약을 먼저 작성한 뒤 built-in `image_gen`으로 캐릭터가 없는 기본 초원 배경 하나를 생성하고 현재 아트 자산 관례에 맞춰 canonical 문서 자산과 runtime resource를 분리한다.

- `docs/art/battle/`에 README, `battle-map-grassland-spec.json`, 최종 `todo-quest-battle-map-grassland.png`를 둔다.
- `scripts/validate_battle_map.py`와 단위 테스트를 먼저 추가한다. validator는 최종 1200×500, 2.4:1, RGB 또는 완전 불투명 RGBA, spec의 SHA-256, runtime 사본 byte equality를 검증한다. 의미 요소와 안전 영역은 수동 시각 검수 항목으로 spec에 기록한다.
- 아래 prompt를 built-in 새 이미지 생성으로 사용한다. CLI/API fallback, `gpt-image-1.5`, Python/SVG/Canvas로 배경을 새로 그리는 우회는 사용하지 않는다.

```text
Use case: stylized-concept
Asset type: 2.4:1 Android casual fantasy RPG battle-map background layer, environment only
Primary request: Create one bright, friendly low-saturation fantasy grassland battle arena at the outskirts of a small village for Todo Quest. This image is only the replaceable background layer behind separately rendered pixel-art combat units.
Scene/backdrop: pale blue sky; soft distant mountains and forest silhouettes; a few small village roofs or a fence in the middle distance; muted green meadow; a gently curved dirt path; broad flat foreground combat ground with sparse grass tufts and tiny stones
Subject: environment only, with clear uncluttered standing zones around normalized x=0.20 and x=0.55..0.95, y=0.72..0.88
Style/medium: polished casual mobile fantasy game environment, softly painted with simplified readable shapes, friendly and understated, compatible with small chibi pixel-art sprites
Composition/framing: very wide 2.4:1 side-view establishing shot, level horizon, foreground ground plane nearly horizontal, scenery concentrated near outer edges and horizon, center-left and right combat lanes kept clear
Lighting/mood: soft daytime light, cheerful calm adventure mood, restrained contrast so foreground units remain highly legible
Color palette: desaturated sky blue, sage and moss greens, warm beige dirt, muted blue-gray mountains, restrained brown wood
Constraints: background only; no characters; no monsters; no animals; no people; no silhouettes resembling living creatures; no health bars; no damage numbers; no HUD; no icons; no text; no logos; no watermark; no large foreground prop in any standing zone
Avoid: photorealism, dense foliage, dramatic perspective, dark horror mood, high-frequency detail, buildings covering the arena, weapons, battle effects, cast shadows belonging to absent units, borders or UI cards
```

- built-in 결과를 `$CODEX_HOME/generated_images`에서 workspace 임시 디렉터리로 복사한 뒤, 원본 구도를 훼손하지 않는 center crop과 `Image.Resampling.LANCZOS` resize만 사용해 1200×500 opaque PNG로 정규화한다. 색상·물체를 로컬 코드로 추가하거나 삭제하지 않는다.
- `view_image`로 최종 이미지를 원본 detail로 검사해 사람/캐릭터/몬스터/HUD/text가 없고 player와 monster safe zone, 평평한 ground, 낮은 채도와 필수 원·중·전경 요소가 충족되는지 확인한다. 단일 문제만 대상으로 built-in edit을 최대 두 번 수행한다.
- 최종 canonical PNG를 `app/src/main/res/drawable-nodpi/battle_map_grassland.png`로 byte-for-byte 복사한다.
- 기존 고블린 canonical PNG도 `app/src/main/res/drawable-nodpi/todo_quest_goblin_scout_front_idle.png`로 byte-for-byte 복사한다. 플레이어 sheet runtime resource는 변경하지 않는다.
- 첨부 참고 이미지 `/.tmp/todo-sample-image.jpg`가 존재하면 구도·밝기 참고로만 `view_image`하고 저장소에 복사하거나 편집 대상으로 사용하지 않는다.
- built-in `image_gen`을 사용할 수 없거나 두 번의 단일 수정 후에도 의미 검수·validator를 통과하지 못하면 step을 `blocked`로 기록한다. CLI fallback으로 전환하지 않는다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\validate_battle_map.py --image docs\art\battle\todo-quest-battle-map-grassland.png --spec docs\art\battle\battle-map-grassland-spec.json --runtime app\src\main\res\drawable-nodpi\battle_map_grassland.png
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-goblin-scout-front-idle.png --spec docs\art\monster\todo-quest-goblin-scout-front-idle-spec.json
$left = (Get-FileHash -Algorithm SHA256 docs\art\monster\todo-quest-goblin-scout-front-idle.png).Hash
$right = (Get-FileHash -Algorithm SHA256 app\src\main\res\drawable-nodpi\todo_quest_goblin_scout_front_idle.png).Hash
if ($left -ne $right) { throw 'Runtime monster sprite differs from canonical art' }
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_battle_map.py scripts\test_validate_monster_sprite.py --basetemp .\.venv\pytest-29-battle-art
.\gradlew.bat :app:processDebugResources
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. 최종 배경을 직접 보고 캐릭터·몬스터·생명체·HUD·문자 부재, 평평한 접지면과 safe zone을 확인한다.
3. task index의 step 1을 `completed`로 바꾸고 생성 자산·runtime 사본·검증 결과를 한국어 `summary`로 기록한다.

## 금지사항

- 캐릭터나 몬스터를 배경 PNG에 합성하지 마라. 이유: 전투 개체는 독립 레이어여야 한다.
- 참고 이미지를 저장소에 복사하지 마라. 이유: 외부 UI screenshot은 구도 참고 자료일 뿐 프로젝트 자산이 아니다.
- built-in 실패 시 CLI/API fallback을 사용하지 마라. 이유: imagegen skill의 명시적 승인 규칙을 지켜야 한다.
- 고블린과 플레이어의 원본 픽셀을 수정하지 마라. 이유: 기존 canonical sprite 계약을 보존해야 한다.
- 기존 테스트를 깨뜨리지 마라.
