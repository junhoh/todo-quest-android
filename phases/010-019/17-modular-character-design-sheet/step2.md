# Step 2: generate-modular-character-design-sheet

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/character-base-spec.json`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/character/todo-quest-character-base-sheet.png`
- `/scripts/validate_character_sheet.py`
- `/scripts/test_validate_character_sheet.py`
- `~/.codex/skills/.system/imagegen/SKILL.md`
- `~/.codex/skills/.system/imagegen/references/prompting.md`
- `~/.codex/skills/.system/imagegen/references/sample-prompts.md`

## 작업

기존 PNG를 먼저 이미지 보기 도구로 확인한 뒤, 기본 제공 `image_gen` 도구의 edit 모드로 첫 번째 파란색 장비 캐릭터를 유일한 정체성 기준으로 사용하는 새 디자인 시트를 만든다. 결과는 기존 파일을 덮어쓰지 않고 `/docs/art/character/todo-quest-character-modular-sheet.png`로 저장한다. CLI/API fallback과 `gpt-image-1.5`는 사용하지 않는다.

이미지 생성 프롬프트는 다음 내용을 그대로 보존해 구조화한다.

```text
Use case: identity-preserve
Asset type: modular paper-doll pixel-art character design sheet for Todo Quest
Input images: Image 1: edit target and sole identity/style reference; use only the first, leftmost, fully blue-equipped character from Image 1 as the canonical character. Ignore and remove every other character variation.
Primary request: Rebuild the sheet around that one blue-equipped character without redesigning it. Preserve its face, eyes, warm skin, head size, three-head-tall chibi proportions, existing pixel vocabulary, blue equipment design, and all existing colors. Correct only pose alignment, silhouette, modular separation, and sheet layout.
Scene/backdrop: perfectly flat solid #FF00FF chroma-key background for hard local removal; no floor, shadow, decoration, border, or grid lines
Subject: one fully front-facing neutral idle character, arms and legs symmetrically aligned, with fixed x=32 center, body head top y=7, shoulder y=25, waist y=39, ankle y=53, and sole y=58 in every 64x64 cell. The equipped silhouette may use y=4..6 only for original hair or head equipment.
Style/medium: native hard-edged pixel art, uniform square logical pixels, 1px stepped diagonals, no antialiasing, no blur, no gradients, no semitransparency
Composition/framing: 8 columns by 2 rows of aligned 64x64 logical cells. First row: fully equipped canonical character; bald base body wearing a plain light-cream white T-shirt and dark charcoal-black pants; base plus separated default rear/front hair; that default character with teal dotted anchor guides; exact 4x4 palette; then three completely empty cells. Second row: isolated rear hair; shoes; lower garment; upper garment; front hair; head equipment; accessories; exact full composite.
Color palette: only #11151C, #1D3557, #263B5A, #35445C, #3A3F45, #2853A6, #737982, #B7B0A3, #D99872, #FFD3AE, #F4EFE3, #4F86E8, #7FB3FF, #5CC8A7, #F2C14E, #E05252. Use #263B5A for the continuous one-logical-pixel exterior silhouette outline.
Constraints: every isolated layer stays at its actual equipped coordinates on a full 64x64 cell and contains no body or other equipment. Rear hair and front hair are separate. Upper ends at the waist seam and does not invade the lower region; lower ends before the shoes. All layers over the base must reproduce the fully equipped canonical character exactly. Hide base internal outlines wherever clothing covers them. Keep the original visible accessory pieces only; do not invent props or weapons. No text anywhere.
Avoid: new characters, red hair, alternate colors, alternate poses, changed anatomy, changed face, cropped cells, isolated stray pixels, dangling vertical fragments, 2px-thick accidental outlines, black exterior outlines, labels, letters, numbers, logos, trademarks, watermarks, #FF00FF inside artwork
```

생성 및 정규화 절차:

### 사용자 승인에 따른 blocked 재실행

사용자가 2026-07-20에 최종 PNG 완성을 지시하여, 이전 built-in imagegen 결과에 대한 **결정적 외부 경계색 교정**을 명시적으로 승인했다. 이번 재실행에서는 새 imagegen 호출을 하지 말고 `.tmp/imagegen/modular-sheet-8x-final.png` 후보를 입력으로 사용한다. 후보의 타일 좌표, 알파, 내부 색, 얼굴, 신체 비율, 포즈와 장비 디자인은 유지한다.

각 `body-base` 및 둘째 행 isolated layer 타일에서 타일 가장자리로부터 투명 영역을 flood-fill하고, 그 외부 투명 영역과 8방향으로 맞닿은 불투명 경계 픽셀만 `outlineColor: #263B5A`로 변경한다. 알파값과 경계가 아닌 픽셀은 바꾸지 않는다. 교정된 레이어를 spec의 `layerOrder`로 다시 합성하여 `default-hair-underwear`, `equipped`, `composite`를 재생성하고 `anchors`는 재생성된 default 타일 위에 기존 안내선 좌표를 다시 오버레이한다. 이 보정은 캐릭터 재드로잉이 아니라 사용자가 승인한 외곽선 팔레트 정규화다.

1. 원본의 첫 번째 타일을 정체성 기준으로 명시한 built-in edit 결과를 생성한다. 한 번에 새 디자인 시트 한 장만 생성한다.
2. 선택 결과를 `$CODEX_HOME/generated_images`에서 작업공간 `.tmp/imagegen/`으로 복사한다.
3. 설치된 `~/.codex/skills/.system/imagegen/scripts/remove_chroma_key.py`를 사용해 평면 `#FF00FF`만 제거한다. 픽셀아트이므로 최종 알파는 반드시 `0/255`로 양자화하고 edge feather, blur, soft shadow는 남기지 않는다.
4. Pillow nearest-neighbor 리샘플링, 16색 최근접 매핑, 64x64 타일 분리/재배치와 위에서 승인된 외부 경계색 교정만 사용해 512x128 RGBA로 정규화한다. dithering은 사용하지 않는다.
5. 캐릭터 아트를 코드로 새로 그리지 않는다. 다만 모델이 생성한 레이어 타일의 픽셀을 수정 없이 source-over 합성해 `default-hair-underwear`, `equipped`, `composite` 타일을 만들고, spec에 고정된 점선 안내선과 4x4 팔레트 칸을 정확히 배치하는 결정적 조립은 허용한다.
6. 첫째 행 columns 5..7은 완전 투명으로 지운다. 장비 레이어를 crop하거나 셀 중심으로 이동하지 않는다.
7. validator를 실행한다. 이번 재실행에서는 이전 시도에서 imagegen edit 횟수를 모두 사용했으므로 추가 imagegen edit을 호출하지 않는다.
8. 1배율과 nearest-neighbor 확대 이미지로 직접 검사한다. 각 isolated layer와 `body-base`, `default-hair-underwear`, `equipped`, `composite`의 외부 경계 픽셀이 모두 `#263B5A`인지 계산해 `invalid boundary pixels = 0`을 확인한다. 외곽선 연결성, 1px 두께, 계단식 대각선, 좌우 대칭, 고립 픽셀, 잔여 선, 레이어 경계, 내부 외곽선 노출, 잘림 여부도 확인한다.
9. 중간 파일은 `.tmp/imagegen/`에만 두고 커밋하지 않는다.

승인된 후보 파일이 없거나 외부 경계색 교정 뒤에도 validator 또는 확대 픽셀 검사가 실패하면 다른 이미지 모델이나 캐릭터 재드로잉으로 우회하지 않는다. `/phases/010-019/17-modular-character-design-sheet/index.json`의 step 2를 `blocked`로 기록하고 실패한 검증 항목을 `blocked_reason`에 구체적으로 남긴다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-modular-sheet.png --spec docs\art\character\character-modular-sheet-spec.json
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-base-sheet.png --spec docs\art\character\character-base-spec.json
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_character_sheet.py scripts\test_execute.py --basetemp .\.venv\pytest-tmp
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. 최종 PNG를 원본 및 확대 상태로 시각 검사한다.
3. 기존 기준 PNG와 spec이 변경되지 않았고 개별 장비 PNG나 Android resource가 생성되지 않았는지 확인한다.
4. PRD, ARCHITECTURE, ADR, UI_GUIDE, AGENTS.md의 CRITICAL 규칙을 확인한다.
5. `/phases/010-019/17-modular-character-design-sheet/index.json`의 step 2를 `completed`로 변경하고 PNG 경로, built-in imagegen 사용, 합성 계약을 한국어 `summary` 한 줄로 기록한다.
6. 모든 step이 완료되면 `/phases/index.json`의 `17-modular-character-design-sheet` 상태를 `completed`로 변경한다.

## 금지사항

- 기존 기준 PNG와 spec을 덮어쓰지 마라. 이유: 원본 캐릭터를 비파괴 기준으로 보존해야 한다.
- built-in imagegen 대신 CLI/API fallback이나 다른 이미지 모델을 사용하지 마라. 이유: 승인되지 않은 실행 경로 전환이다.
- Python, SVG, HTML, canvas로 캐릭터를 새로 그리지 마라. 이유: 캐릭터 개선은 제공된 이미지의 imagegen 편집이어야 한다. 단, 사용자가 승인한 외부 투명 경계 픽셀의 `#263B5A` 재색칠과 기존 레이어 재합성은 허용한다.
- 새로운 캐릭터, 포즈, 색상, 장비, 배경, 텍스트를 추가하지 마라. 이유: 사용자가 금지한 재디자인이다.
- 개별 게임 리소스 PNG나 앱 통합 코드를 만들지 마라. 이유: 이번 단계는 개선 디자인 시트 한 장만 산출한다.
- `.tmp/imagegen/` 중간 파일을 커밋하지 마라. 이유: 최종 프로젝트 산출물만 추적해야 한다.
- 기존 테스트를 깨뜨리지 마라.
