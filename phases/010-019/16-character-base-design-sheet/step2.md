# Step 2: generate-character-design-sheet

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/character-base-spec.json`
- `/scripts/validate_character_sheet.py`
- `~/.codex/skills/.system/imagegen/SKILL.md`
- `~/.codex/skills/.system/imagegen/references/prompting.md`

## 작업

기본 제공 `image_gen` 도구로 새로운 픽셀아트 캐릭터 디자인 시트를 생성하고 `/docs/art/character/todo-quest-character-base-sheet.png`에 프로젝트 산출물로 저장한다. CLI/API fallback이나 `gpt-image-1.5`는 사용하지 않는다.

다음 프롬프트를 기반으로 생성하되, 생성 결과를 검사한 뒤 한 번에 한 가지 문제만 대상으로 최대 두 번까지 수정한다.

```text
Use case: stylized-concept
Asset type: modular paper-doll game character design sheet for an Android productivity RPG
Primary request: Create one native-pixel design sheet for a cute, gender-neutral young humanoid adventurer that encourages achievement. Show a fully equipped urban quest runner, the identical neutral bodysuit base, a color-coded layer map, fixed body anchors, separated front/rear hair logic, isolated equipment-layer examples, and the exact used palette.
Scene/backdrop: perfectly flat solid #FF00FF chroma-key background for local removal; no floor plane or decoration
Subject: front-facing 3-head-tall chibi human, oversized head, relaxed smile, arms straight beside the torso, nearly symmetrical legs, warm light skin, near-black hair, dark navy eyes. Representative equipment is a blue short jacket, compact cap, earpiece, utility pants, sneaker boots, quest pouch, and short energy blade held vertically within the body silhouette.
Style/medium: simple modern 16-bit game pixel art; uniform hard square pixels; crisp 1-pixel logic; no antialiasing
Composition/framing: a 6-by-2 grid of twelve aligned 64-by-64 logical tiles, overall 3:1 sheet. Every character and isolated layer uses the same local 64-by-64 coordinates, center x=32, top y=4, sole baseline y=58, upright and unrotated.
Color palette: use only #11151C, #1D3557, #263B5A, #35445C, #3A3F45, #2853A6, #737982, #B7B0A3, #D99872, #FFD3AE, #F4EFE3, #4F86E8, #7FB3FF, #5CC8A7, #F2C14E, #E05252. Dark navy outlines, never black outlines.
Constraints: equipped and base views must preserve exactly the same face, body proportions, pose, anchors, and outer bounding box. Neutral base wears a gray fitted bodysuit; no exposed torso or legs. Equipment stays inside the common silhouette. Hair supports separate rear and front layers. Upper/lower boundary is y=39 and shoes begin at the y=53 ankles. The first row is equipped, base, slot map, anchors, hair split, palette. The second row is head, face, upper, lower, shoes, combined accessory and weapon examples. No text anywhere.
Avoid: gradients, blur, antialiasing, semitransparent edges, shadows, reflections, isometric view, tilt, rotation, extra poses, background objects, borders, labels, letters, numbers, logos, trademarks, watermarks, #FF00FF inside the artwork
```

생성 후 다음 절차를 따른다.

1. 선택한 built-in 결과를 `$CODEX_HOME/generated_images`에서 작업공간의 `.tmp/imagegen/` 아래로 복사한다.
2. 설치된 `~/.codex/skills/.system/imagegen/scripts/remove_chroma_key.py`를 `.venv/Scripts/python.exe`로 실행해 `#FF00FF`를 제거한다. 픽셀아트이므로 반투명 matte를 남기지 않으며 최종 알파는 `0/255`로 정리한다.
3. Pillow의 nearest-neighbor 축소와 계약 팔레트 최근접 매핑만 사용해 정확히 `384x128` RGBA로 정규화한다. dithering, blur, gradient, edge feather는 사용하지 않는다. 생성된 캐릭터 디자인을 Python으로 새로 그리거나 대체하지 않는다.
4. spec의 타일 배치와 기준 좌표를 맞추고 validator를 실행한다. 자동 검증 실패 시 생성 결과의 해당 문제 하나만 imagegen 편집으로 수정한다.
5. 원본 크기와 nearest-neighbor 확대 보기로 최종 PNG를 직접 검사한다. 모든 캐릭터 모습의 얼굴, 비율, 자세가 같고 도시형 퀘스트 러너 착장, 베이스 바디슈트, 레이어 구분, 기준점, 팔레트가 보이는지 확인한다.
6. 최종 이미지 외의 생성 중간 파일은 `.tmp/imagegen/`에만 두고 커밋하지 않는다.

built-in `image_gen`이 child 세션에 없거나, 두 번의 단일 수정 후에도 투명도와 validator를 통과하지 못하면 CLI로 우회하지 말고 step을 `blocked`로 기록한다. `blocked_reason`에는 실패한 검증 항목 또는 사용할 수 없는 도구를 구체적으로 적는다.

## Acceptance Criteria

```powershell
.\\.venv\\Scripts\\python.exe scripts\\validate_character_sheet.py --image docs\\art\\character\\todo-quest-character-base-sheet.png --spec docs\\art\\character\\character-base-spec.json
$env:PYTHONUTF8='1'
.\\.venv\\Scripts\\python.exe -m pytest scripts/test_validate_character_sheet.py scripts/test_execute.py --basetemp .\\.venv\\pytest-tmp
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. PNG를 시각 검사하여 텍스트, 로고, 워터마크, 그림자, 배경 장식이 없고 모든 모습의 얼굴·비율·포즈가 동일한지 확인한다.
3. PRD, ARCHITECTURE, ADR, UI_GUIDE, AGENTS.md의 CRITICAL 규칙을 확인한다.
4. `/phases/010-019/16-character-base-design-sheet/index.json`의 step 2를 `completed`로 변경하고 최종 PNG 경로, built-in imagegen 사용, 핵심 디자인을 한국어 `summary` 한 줄로 기록한다.
5. 모든 step이 완료되면 `/phases/index.json`의 `16-character-base-design-sheet` 상태를 `completed`로 변경한다.

## 금지사항

- built-in imagegen 대신 Python, SVG, HTML, canvas로 캐릭터를 새로 그리지 마라. 이유: 사용자가 요청한 픽셀아트 원안은 imagegen으로 생성해야 한다.
- CLI/API fallback 또는 `gpt-image-1.5`로 전환하지 마라. 이유: 사용자가 모델/path downgrade를 승인하지 않았다.
- 장착 모습과 베이스 모습의 얼굴, 비율, 자세, bounding box를 다르게 만들지 마라. 이유: 페이퍼돌 장비 교체 좌표가 깨진다.
- 최종 PNG에 반투명 픽셀, 크로마키, 17번째 색을 남기지 마라. 이유: 하드 에지 16색 투명 픽셀아트 규격을 위반한다.
- 최종 장비별 독립 PNG나 앱 통합 코드를 만들지 마라. 이유: 이번 phase의 범위를 넘는다.
- `.tmp/imagegen/` 중간 파일을 커밋하지 마라. 이유: 최종 산출물과 생성 임시 파일을 구분해야 한다.
- 기존 테스트를 깨뜨리지 마라.